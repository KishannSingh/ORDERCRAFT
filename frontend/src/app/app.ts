import { ChangeDetectorRef, Component, DestroyRef, ElementRef, HostListener, ViewChild, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router, RouterLink, RouterLinkActive, RouterOutlet, NavigationEnd } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { catchError, distinctUntilChanged, filter, map, merge, Observable, of, scan, Subject, switchMap, timer } from 'rxjs';
import { AuthService } from './auth/auth.service';
import { API_BASE_URL, apiErrorMessage } from './api';

interface GlobalSearchResult {
  title: string;
  description: string;
  section: string;
  route: string;
}

interface SearchCollections {
  customers: Record<string, any>[];
  products: Record<string, any>[];
  boms: Record<string, any>[];
  orders: Record<string, any>[];
  purchaseOrders: Record<string, any>[];
  invoices: Record<string, any>[];
  payments: Record<string, any>[];
}

type SearchCollectionKey = keyof SearchCollections;

interface SearchModuleOutcome {
  key: SearchCollectionKey;
  records: Record<string, any>[];
  error?: string;
}

interface SearchLoadResult {
  collections: SearchCollections;
  failures: SearchModuleOutcome[];
  completedCount: number;
  complete: boolean;
}

interface SearchState {
  term: string;
  results: GlobalSearchResult[];
  failures: SearchModuleOutcome[];
  completedCount: number;
  error?: string;
  isSearching: boolean;
}

interface CachedSearchCollection {
  records: Record<string, any>[];
  loadedAt: number;
}

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './app.html',
  styleUrl: './app.css',
})
export class App {
  isLoginPage = false;
  userName = 'Signed in user';
  userRole = 'User';
  userInitials = 'U';
  searchQuery = '';
  searchOpen = false;
  isSearching = false;
  searchError = '';
  searchWarning = '';
  searchCompletedModules = 0;
  searchResults: GlobalSearchResult[] = [];
  mobileNavOpen = false;
  mobileSearchVisible = false;

  @ViewChild('globalSearchInput') private globalSearchInput?: ElementRef<HTMLInputElement>;

  private readonly searchTerms = new Subject<string>();
  private readonly searchApi = API_BASE_URL;
  private readonly destroyRef = inject(DestroyRef);
  private readonly changeDetector = inject(ChangeDetectorRef);
  private readonly searchCache: Partial<Record<SearchCollectionKey, CachedSearchCollection>> = {};
  private currentSearchTerm = '';
  private readonly searchCacheTtlMs = 15_000;

  constructor(private router: Router, private authService: AuthService, private http: HttpClient) {
    this.isLoginPage = this.router.url === '/login';
    this.loadUser();

    this.searchTerms
      .pipe(
        distinctUntilChanged(),
        switchMap((term) => {
          if (term.length < 2) {
            return of<SearchState>({ term, results: [], failures: [], completedCount: 0, isSearching: false });
          }

          return timer(250).pipe(
            switchMap(() => this.loadSearchCollections()),
            map(({ collections, failures, completedCount, complete }): SearchState => ({
              term,
              results: this.findSearchResults(collections, term),
              failures,
              completedCount,
              error: complete && failures.length === 7
                ? `Search is unavailable because all modules failed: ${this.formatSearchFailures(failures)}`
                : undefined,
              isSearching: !complete,
            })),
            catchError((error): Observable<SearchState> => of({
              term,
              results: [],
              failures: [],
              completedCount: 0,
              error: apiErrorMessage(error, 'Search is unavailable right now.'),
              isSearching: false,
            })),
          );
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((state) => {
        if (state.term !== this.currentSearchTerm) return;
        this.searchResults = state.results;
        this.searchCompletedModules = state.completedCount;
        this.searchError = state.error ?? '';
        this.searchWarning = state.failures.length > 0 && !state.error
          ? `Some records could not be searched: ${this.formatSearchFailures(state.failures)}`
          : '';
        this.isSearching = state.isSearching;
        this.changeDetector.markForCheck();
      });

    this.router.events
      .pipe(
        filter((event): event is NavigationEnd => event instanceof NavigationEnd),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((event) => {
        this.isLoginPage = event.urlAfterRedirects === '/login';
        this.mobileNavOpen = false;
        this.mobileSearchVisible = false;
        if (!this.isLoginPage) this.loadUser();
        this.changeDetector.markForCheck();
      });
  }

  logout(): void {
    this.closeSearch();
    this.mobileNavOpen = false;
    for (const key of Object.keys(this.searchCache) as SearchCollectionKey[]) delete this.searchCache[key];
    this.authService.logout();
    void this.router.navigate(['/login']);
  }

  toggleMobileNav(): void {
    this.mobileNavOpen = !this.mobileNavOpen;
    if (this.mobileNavOpen) this.mobileSearchVisible = false;
  }

  closeMobileNav(): void {
    this.mobileNavOpen = false;
  }

  toggleMobileSearch(): void {
    this.mobileSearchVisible = !this.mobileSearchVisible;
    if (this.mobileSearchVisible) {
      this.mobileNavOpen = false;
      this.focusSearchInput();
    } else {
      this.closeSearch();
    }
  }

  searchGlobal(query: string): void {
    this.searchQuery = query;
    this.searchOpen = query.trim().length > 0;
    const term = this.normalizeText(query);
    const changedTerm = term !== this.currentSearchTerm;
    this.currentSearchTerm = term;
    if (term.length < 2) {
      this.searchResults = [];
      this.searchError = '';
      this.searchWarning = '';
      this.isSearching = false;
      this.searchCompletedModules = 0;
    } else if (changedTerm) {
      this.searchResults = [];
      this.searchError = '';
      this.searchWarning = '';
      this.isSearching = true;
      this.searchCompletedModules = 0;
    }
    this.searchTerms.next(term);
  }

  openSearchResult(result: GlobalSearchResult): void {
    this.closeSearch();
    void this.router.navigate([result.route]);
  }

  closeSearch(): void {
    this.mobileSearchVisible = false;
    this.searchOpen = false;
    this.searchQuery = '';
    this.currentSearchTerm = '';
    this.searchResults = [];
    this.searchError = '';
    this.searchWarning = '';
    this.isSearching = false;
    this.searchCompletedModules = 0;
    this.searchTerms.next('');
  }

  onSearchKeydown(event: KeyboardEvent): void {
    if (event.key === 'Escape') {
      this.closeSearch();
    } else if (event.key === 'Enter' && this.searchResults.length > 0) {
      event.preventDefault();
      this.openSearchResult(this.searchResults[0]);
    }
  }

  @HostListener('window:keydown', ['$event'])
  focusGlobalSearch(event: KeyboardEvent): void {
    if (event.key === 'Escape') {
      if (this.mobileNavOpen) this.closeMobileNav();
      if (this.mobileSearchVisible) this.closeSearch();
      return;
    }
    if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'k') {
      event.preventDefault();
      if (window.matchMedia('(max-width: 700px)').matches) {
        this.mobileSearchVisible = true;
        this.mobileNavOpen = false;
        this.focusSearchInput();
      } else {
        this.globalSearchInput?.nativeElement.focus();
      }
    }
  }

  private focusSearchInput(): void {
    if (typeof requestAnimationFrame === 'function') {
      requestAnimationFrame(() => this.globalSearchInput?.nativeElement.focus());
    } else {
      this.globalSearchInput?.nativeElement.focus();
    }
  }

  private findSearchResults(collections: SearchCollections, term: string): GlobalSearchResult[] {
    const results: GlobalSearchResult[] = [];
    const addMatches = (
      records: Record<string, any>[],
      section: string,
      route: string,
      title: (record: Record<string, any>) => string,
      description: (record: Record<string, any>) => string,
    ) => {
      for (const record of Array.isArray(records) ? records : []) {
        const titleText = title(record);
        const descriptionText = description(record);
        const searchable = this.normalizeText(
          `${titleText} ${descriptionText} ${Object.values(record).join(' ')}`,
        );
        if (searchable.includes(term)) {
          results.push({ title: titleText, description: descriptionText, section, route });
        }
      }
    };

    addMatches(collections.customers, 'Customers', '/customers',
      (r) => `${r['name'] || 'Customer'} (#${r['customerId']})`,
      (r) => String(r['email'] || r['phone'] || 'Customer record'));
    addMatches(collections.products, 'Products', '/products',
      (r) => `${r['name'] || 'Product'} (#${r['productId']})`,
      (r) => String(r['productCode'] || 'Product record'));
    addMatches(collections.boms, 'Bill of Materials', '/bom',
      (r) => `${r['materialName'] || 'Material'} (#${r['bomId']})`,
      (r) => `Product #${r['productId']} · ${r['quantity'] ?? ''} ${r['unit'] ?? ''}`.trim());
    addMatches(collections.orders, 'Sales Orders', '/orders',
      (r) => `Sales Order #${r['orderId']}`,
      (r) => `Customer #${r['customerId']} · ${r['status'] || 'Order'}`);
    addMatches(collections.purchaseOrders, 'Purchase Orders', '/purchase-orders',
      (r) => `Purchase Order #${r['poId']}`,
      (r) => `${r['materialName'] || 'Material'} · ${r['status'] || 'Purchase order'}`);
    addMatches(collections.invoices, 'Invoices', '/invoices',
      (r) => `Invoice #${r['invoiceId']}`,
      (r) => `Order #${r['orderId']} · ${r['status'] || 'Invoice'}`);
    addMatches(collections.payments, 'Payments', '/payments',
      (r) => `Payment #${r['paymentId']}`,
      (r) => `Invoice #${r['invoiceId']} · ${r['mode'] || 'Payment'}`);

    return results.slice(0, 10);
  }

  private loadSearchCollections(): Observable<SearchLoadResult> {
    const requests = [
      this.loadSearchCollection('customers', '/customers'),
      this.loadSearchCollection('products', '/products'),
      this.loadSearchCollection('boms', '/boms'),
      this.loadSearchCollection('orders', '/orders'),
      this.loadSearchCollection('purchaseOrders', '/purchase-orders'),
      this.loadSearchCollection('invoices', '/invoices'),
      this.loadSearchCollection('payments', '/payments'),
    ];

    return merge(...requests).pipe(
      scan((outcomes, outcome) => [...outcomes, outcome], [] as SearchModuleOutcome[]),
      map((outcomes) => this.toSearchLoadResult(outcomes, outcomes.length === requests.length)),
    );
  }

  private toSearchLoadResult(outcomes: SearchModuleOutcome[], complete: boolean): SearchLoadResult {
    const collections: SearchCollections = {
      customers: [], products: [], boms: [], orders: [],
      purchaseOrders: [], invoices: [], payments: [],
    };
    const failures: SearchModuleOutcome[] = [];
    for (const outcome of outcomes) {
      collections[outcome.key] = outcome.records;
      if (outcome.error) failures.push(outcome);
    }
    return { collections, failures, completedCount: outcomes.length, complete };
  }

  private loadSearchCollection(key: SearchCollectionKey, path: string): Observable<SearchModuleOutcome> {
    const cached = this.searchCache[key];
    if (cached && Date.now() - cached.loadedAt < this.searchCacheTtlMs) {
      return of({ key, records: cached.records });
    }

    return this.http.get<unknown>(`${this.searchApi}${path}`).pipe(
      map((response): SearchModuleOutcome => {
        if (!Array.isArray(response)) {
          return { key, records: [], error: 'The module returned an invalid records response.' };
        }
        const records = response as Record<string, any>[];
        this.searchCache[key] = { records, loadedAt: Date.now() };
        return { key, records };
      }),
      catchError((error) => of({
        key,
        records: [],
        error: apiErrorMessage(error, 'Request failed.'),
      })),
    );
  }

  private formatSearchFailures(failures: SearchModuleOutcome[]): string {
    return failures.map(({ key, error }) => `${this.searchModuleName(key)}: ${error}`).join('; ');
  }

  private searchModuleName(key: SearchCollectionKey): string {
    const names: Record<SearchCollectionKey, string> = {
      customers: 'Customers', products: 'Products', boms: 'BOM', orders: 'Sales Orders',
      purchaseOrders: 'Purchase Orders', invoices: 'Invoices', payments: 'Payments',
    };
    return names[key];
  }

  private normalizeText(value: unknown): string {
    return String(value ?? '').trim().replace(/\s+/g, ' ').toLocaleLowerCase();
  }

  private loadUser(): void {
    try {
      const rawUser = localStorage.getItem('user');
      const user = rawUser ? JSON.parse(rawUser) : null;
      const email = typeof user?.email === 'string' ? user.email.trim() : '';
      const role = typeof user?.role === 'string' ? user.role.trim() : '';
      this.userName = email || 'Signed in user';
      this.userRole = role ? role.toLowerCase() : 'User';
      this.userInitials = email ? email.slice(0, 1).toUpperCase() : 'U';
    } catch {
      this.userName = 'Signed in user';
      this.userRole = 'User';
      this.userInitials = 'U';
    }
  }
}
