import { ComponentFixture, TestBed } from '@angular/core/testing';
import { App } from './app';
import { Router, provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, TestRequest, provideHttpClientTesting } from '@angular/common/http/testing';
import { API_BASE_URL } from './api';

describe('App global search', () => {
  let httpTesting: HttpTestingController;
  let fixtures: ComponentFixture<App>[];

  beforeEach(async () => {
    localStorage.clear();
    fixtures = [];
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTesting.verify({ ignoreCancelled: true });
    for (const fixture of fixtures) fixture.destroy();
    localStorage.clear();
  });

  function createApp(): { fixture: ComponentFixture<App>; app: App } {
    const fixture = TestBed.createComponent(App);
    fixtures.push(fixture);
    fixture.detectChanges();
    return { fixture, app: fixture.componentInstance };
  }

  function moduleRequests(): TestRequest[] {
    return httpTesting.match((request) => request.method === 'GET' && request.url.startsWith(API_BASE_URL));
  }

  async function waitForDebounce(): Promise<void> {
    await new Promise((resolve) => setTimeout(resolve, 275));
  }

  function flushEmpty(requests: TestRequest[]): void {
    for (const request of requests) request.flush([]);
  }

  it('renders the shell without starting search requests', () => {
    const { fixture, app } = createApp();
    expect(app).toBeTruthy();
    expect((fixture.nativeElement as HTMLElement).querySelector('.brand-name')?.textContent).toContain('OrderCraft');
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('OrderCraft workspace');
    expect((fixture.nativeElement as HTMLElement).textContent).not.toContain('All services operational');
    expect(moduleRequests()).toHaveLength(0);
  });

  it('keeps navigation and global search reachable through the mobile controls', () => {
    const { fixture, app } = createApp();
    const page = fixture.nativeElement as HTMLElement;
    const menu = page.querySelector<HTMLButtonElement>('.mobile-menu-toggle');
    const search = page.querySelector<HTMLButtonElement>('.mobile-search-toggle');
    expect(menu?.getAttribute('aria-label')).toBe('Open navigation');
    expect(search?.getAttribute('aria-label')).toBe('Search records');

    menu?.click();
    fixture.detectChanges();
    expect(app.mobileNavOpen).toBe(true);
    expect(page.querySelector('.sidebar')?.classList.contains('mobile-open')).toBe(true);
    page.querySelector<HTMLButtonElement>('.mobile-nav-backdrop')?.click();
    fixture.detectChanges();
    expect(app.mobileNavOpen).toBe(false);

    search?.click();
    fixture.detectChanges();
    expect(app.mobileSearchVisible).toBe(true);
    expect(page.querySelector('.topbar')?.classList.contains('mobile-search-visible')).toBe(true);
    app.closeSearch();
    fixture.detectChanges();
    expect(app.mobileSearchVisible).toBe(false);
  });

  it('returns matching persisted data case-insensitively after debounce and opens its module', async () => {
    const { fixture, app } = createApp();
    app.searchGlobal('  ACME   manufacturing ');
    expect(app.isSearching).toBe(true);
    await waitForDebounce();

    const requests = moduleRequests();
    expect(requests).toHaveLength(7);
    const customerRequest = requests.find((request) => request.request.url === `${API_BASE_URL}/customers`);
    customerRequest?.flush([
      { customerId: 42, name: 'Acme Manufacturing', email: 'ops@acme.example', phone: '555-0100' },
    ]);
    for (const request of requests) if (request !== customerRequest) request.flush([]);
    fixture.detectChanges();

    expect(app.isSearching).toBe(false);
    expect(app.searchResults).toEqual([{
      title: 'Acme Manufacturing (#42)',
      description: 'ops@acme.example',
      section: 'Customers',
      route: '/customers',
    }]);
    const page = fixture.nativeElement as HTMLElement;
    expect(page.textContent).toContain('Acme Manufacturing');
    expect(page.textContent).not.toContain('Searching records');

    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    app.openSearchResult(app.searchResults[0]);
    expect(navigate).toHaveBeenCalledWith(['/customers']);
    expect(app.searchOpen).toBe(false);
  });

  it('shows a no-match state after successful module responses', async () => {
    const { app } = createApp();
    app.searchGlobal('missing record');
    await waitForDebounce();
    flushEmpty(moduleRequests());
    expect(app.isSearching).toBe(false);
    expect(app.searchResults).toEqual([]);
    expect(app.searchError).toBe('');
  });

  it('keeps successful module results when one module request fails', async () => {
    const { app } = createApp();
    app.searchGlobal('Kishan');
    await waitForDebounce();
    const requests = moduleRequests();
    const customerRequest = requests.find((request) => request.request.url.endsWith('/customers'));
    const failedRequest = requests.find((request) => request.request.url.endsWith('/products'));
    customerRequest?.flush([{ customerId: 3, name: 'Kishan Singh', email: 'kishan@example.test' }]);
    failedRequest?.flush({ message: 'Products service unavailable' }, { status: 503, statusText: 'Service Unavailable' });
    for (const request of requests) {
      if (request !== customerRequest && request !== failedRequest) request.flush([]);
    }

    expect(app.isSearching).toBe(false);
    expect(app.searchResults[0]?.title).toContain('Kishan Singh');
    expect(app.searchWarning).toContain('Products');
    expect(app.searchError).toBe('');
  });

  it('reports complete module failure instead of looking like an empty successful search', async () => {
    const { app } = createApp();
    app.searchGlobal('Kishan');
    await waitForDebounce();
    const requests = moduleRequests();
    for (const request of requests) request.flush({ message: 'unavailable' }, { status: 503, statusText: 'Service Unavailable' });
    expect(app.isSearching).toBe(false);
    expect(app.searchError).toContain('all modules failed');
    expect(app.searchResults).toEqual([]);
  });

  it('waits for a slow module to complete without leaving loading stuck afterward', async () => {
    const { app } = createApp();
    app.searchGlobal('Kishan');
    await waitForDebounce();
    const requests = moduleRequests();
    for (const request of requests.slice(0, 6)) request.flush([]);
    expect(app.isSearching).toBe(true);
    requests[6].flush([]);
    expect(app.isSearching).toBe(false);
    expect(app.searchResults).toEqual([]);
  });

  it('cancels an older query immediately so it cannot replace a rapid newer query', async () => {
    const { app } = createApp();
    app.searchGlobal('Kishan');
    await waitForDebounce();
    const staleRequests = moduleRequests();
    expect(staleRequests).toHaveLength(7);

    app.searchGlobal('Kishan Singh');
    expect(staleRequests.every((request) => request.cancelled)).toBe(true);
    await waitForDebounce();
    const currentRequests = moduleRequests();
    expect(currentRequests).toHaveLength(7);
    const customerRequest = currentRequests.find((request) => request.request.url.endsWith('/customers'));
    customerRequest?.flush([{ customerId: 3, name: 'Kishan Singh' }]);
    for (const request of currentRequests) if (request !== customerRequest) request.flush([]);
    expect(app.searchResults[0]?.title).toContain('Kishan Singh');
  });

  it('clears loading/results and cancels pending requests when the query is cleared', async () => {
    const { app } = createApp();
    app.searchGlobal('Kishan');
    await waitForDebounce();
    const requests = moduleRequests();
    app.searchGlobal('   ');
    expect(requests.every((request) => request.cancelled)).toBe(true);
    expect(app.isSearching).toBe(false);
    expect(app.searchResults).toEqual([]);
    expect(app.searchError).toBe('');
  });

  it('reuses successful collections for repeated searches and resets loading state', async () => {
    const { app } = createApp();
    app.searchGlobal('alpha');
    await waitForDebounce();
    flushEmpty(moduleRequests());
    expect(app.isSearching).toBe(false);

    app.searchGlobal('beta');
    await waitForDebounce();
    expect(moduleRequests()).toHaveLength(0);
    expect(app.isSearching).toBe(false);

    app.searchGlobal('beta');
    expect(app.isSearching).toBe(false);
    expect(moduleRequests()).toHaveLength(0);
  });

  it('unsubscribes active module requests when the shell is destroyed', async () => {
    const { fixture, app } = createApp();
    app.searchGlobal('Kishan');
    await waitForDebounce();
    const requests = moduleRequests();
    fixture.destroy();
    expect(requests.every((request) => request.cancelled)).toBe(true);
  });
});
