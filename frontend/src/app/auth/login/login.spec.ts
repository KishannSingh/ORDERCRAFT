import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router, provideRouter } from '@angular/router';
import { Login } from './login';
import { API_BASE_URL } from '../../api';

describe('Login', () => {
  let fixture: ComponentFixture<Login>;
  let component: Login;
  let httpTesting: HttpTestingController;

  beforeEach(async () => {
    localStorage.clear();
    await TestBed.configureTestingModule({
      imports: [Login],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(Login);
    component = fixture.componentInstance;
    httpTesting = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  afterEach(() => {
    httpTesting.verify();
    localStorage.clear();
  });

  it('registers through the API and returns to sign in with feedback', async () => {
    component.toggleMode();
    component.email = 'new.user@example.test';
    component.password = 'SecurePassword123!';
    component.confirmPassword = 'SecurePassword123!';
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('#confirmPassword')).toBeTruthy();

    component.submit();
    const request = httpTesting.expectOne(`${API_BASE_URL}/api/auth/register`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({
      email: 'new.user@example.test',
      password: 'SecurePassword123!',
    });
    request.flush({ message: 'Registration successful' }, { status: 201, statusText: 'Created' });

    await fixture.whenStable();
    fixture.detectChanges();
    expect(component.isRegisterMode).toBe(false);
    expect(component.successMessage).toContain('Account created');
    expect(fixture.nativeElement.textContent).toContain('Account created');
    expect(localStorage.getItem('token')).toBeNull();
  });

  it('does not submit registration when the password confirmation differs', () => {
    component.toggleMode();
    component.email = 'new.user@example.test';
    component.password = 'SecurePassword123!';
    component.confirmPassword = 'different-password';

    component.submit();

    expect(component.errorMessage).toBe('The passwords do not match.');
    httpTesting.expectNone(`${API_BASE_URL}/api/auth/register`);
  });

  it('shows a clear message when login credentials are rejected', () => {
    component.email = 'user@example.test';
    component.password = 'incorrect-password';

    component.submit();
    const request = httpTesting.expectOne(`${API_BASE_URL}/api/auth/login`);
    request.flush(
      { status: 401, message: 'Invalid email or password' },
      { status: 401, statusText: 'Unauthorized' },
    );

    expect(component.errorMessage).toBe('Invalid email or password.');
    expect(component.isLoading).toBe(false);
    expect(localStorage.getItem('token')).toBeNull();
  });

  it('stores a valid backend session and navigates to the dashboard', () => {
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    component.email = 'user@example.test';
    component.password = 'correct-password';

    component.submit();

    const request = httpTesting.expectOne(`${API_BASE_URL}/api/auth/login`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ email: 'user@example.test', password: 'correct-password' });
    request.flush({ token: 'test-jwt', userId: 17, email: 'user@example.test', role: 'USER' });

    expect(localStorage.getItem('token')).toBe('test-jwt');
    expect(JSON.parse(localStorage.getItem('user') ?? '{}')).toEqual({
      userId: 17,
      email: 'user@example.test',
      role: 'USER',
    });
    expect(component.isLoading).toBe(false);
    expect(navigate).toHaveBeenCalledWith(['/dashboard']);
  });
});
