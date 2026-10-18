import { ActivatedRouteSnapshot, Router, RouterStateSnapshot, UrlTree } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../api';
import { AuthService } from './auth.service';
import { authGuard } from './auth.guard';
import { jwtInterceptor } from './jwt.interceptor';

describe('authGuard', () => {
  let httpTesting: HttpTestingController;
  let router: Router;
  let auth: AuthService;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([jwtInterceptor])),
        provideHttpClientTesting(),
        provideRouter([]),
      ],
    });
    httpTesting = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    auth = TestBed.inject(AuthService);
  });

  afterEach(() => {
    httpTesting.verify();
    localStorage.clear();
  });

  it('redirects when no token is stored', () => {
    const decision = TestBed.runInInjectionContext(() =>
      authGuard({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot),
    );

    expect(decision instanceof UrlTree).toBe(true);
    expect(router.serializeUrl(decision as UrlTree)).toBe('/login');
    httpTesting.expectNone(`${API_BASE_URL}/api/auth/me`);
  });

  it('validates the bearer token with the backend before allowing navigation', () => {
    auth.setToken('signed-token');
    let navigationAllowed = false;
    const decision = TestBed.runInInjectionContext(() =>
      authGuard({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot),
    ) as Observable<boolean | UrlTree>;
    decision.subscribe((result) => (navigationAllowed = result === true));

    const request = httpTesting.expectOne(`${API_BASE_URL}/api/auth/me`);
    expect(request.request.headers.get('Authorization')).toBe('Bearer signed-token');
    request.flush({ userId: 7, email: 'user@example.test', role: 'USER' });

    expect(navigationAllowed).toBe(true);
    expect(JSON.parse(localStorage.getItem('user') ?? '{}').email).toBe('user@example.test');
  });

  it('clears an expired session and redirects to login', () => {
    auth.setSession('expired-token', { userId: 7, email: 'user@example.test', role: 'USER' });
    let redirect: boolean | UrlTree | undefined;
    const decision = TestBed.runInInjectionContext(() =>
      authGuard({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot),
    ) as Observable<boolean | UrlTree>;
    decision.subscribe((result) => (redirect = result));

    const request = httpTesting.expectOne(`${API_BASE_URL}/api/auth/me`);
    request.flush({ status: 401 }, { status: 401, statusText: 'Unauthorized' });

    expect(redirect instanceof UrlTree).toBe(true);
    expect(router.serializeUrl(redirect as UrlTree)).toBe('/login');
    expect(auth.isLoggedIn()).toBe(false);
    expect(localStorage.getItem('user')).toBeNull();
  });

  it('logout clears the token and cached profile', () => {
    auth.setSession('signed-token', { userId: 7, email: 'user@example.test', role: 'USER' });

    auth.logout();

    expect(auth.isLoggedIn()).toBe(false);
    expect(localStorage.getItem('user')).toBeNull();
  });
});
