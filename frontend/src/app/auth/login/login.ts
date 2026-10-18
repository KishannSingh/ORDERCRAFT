import { ChangeDetectorRef, Component } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { API_BASE_URL, apiErrorMessage } from '../../api';
import { AuthService, AuthUser } from '../auth.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './login.html',
  styleUrl: './login.css',
})
export class Login {

  private readonly LOGIN_API = `${API_BASE_URL}/api/auth/login`;
  private readonly REGISTER_API = `${API_BASE_URL}/api/auth/register`;

  email = '';
  password = '';
  confirmPassword = '';
  isRegisterMode = false;

  isLoading = false;
  errorMessage = '';
  successMessage = '';

  constructor(
    private readonly router: Router,
    private readonly http: HttpClient,
    private readonly authService: AuthService,
    private readonly cdr: ChangeDetectorRef,
  ) {}

  submit(): void {
    if (this.isRegisterMode) {
      this.register();
      return;
    }
    this.login();
  }

  toggleMode(): void {
    if (this.isLoading) return;
    this.isRegisterMode = !this.isRegisterMode;
    this.errorMessage = '';
    this.successMessage = '';
    this.password = '';
    this.confirmPassword = '';
  }

  login(): void {

    if (this.isLoading) return;

    this.errorMessage = '';
    this.successMessage = '';

    const email = this.email.trim();
    const password = this.password;

    if (!email || !password) {
      this.errorMessage =
        'Please enter your email and password.';

      return;
    }

    this.isLoading = true;

    const loginData = {
      email,
      password
    };

    this.http.post<any>(
      this.LOGIN_API,
      loginData
    ).subscribe({

      next: (response) => {

        if (!response) {

          this.isLoading = false;

          this.errorMessage =
            'No response received from server.';
          this.cdr.markForCheck();

          return;
        }

        if (!response.token) {

          this.isLoading = false;

          this.errorMessage =
            'Login successful but JWT token was not received.';
          this.cdr.markForCheck();

          return;
        }

        const user: AuthUser = {
          userId: response.userId,
          email: response.email || email,
          role: response.role,
        };
        this.authService.setSession(response.token, user);

        this.isLoading = false;
        this.cdr.markForCheck();

        this.router
          .navigate(['/dashboard'])
          .then((success) => {

            if (!success) {

              this.errorMessage =
                'Login successful but dashboard navigation failed.';
              this.cdr.markForCheck();
            }

          })
          .catch(() => {

            this.errorMessage =
              'Login successful but dashboard could not be opened.';
            this.cdr.markForCheck();
          });

      },

      error: (error) => {

        this.isLoading = false;

        if (error.status === 401) {
          this.errorMessage = 'Invalid email or password.';
          this.cdr.markForCheck();

          return;
        }

        if (error.status === 0) {

          this.errorMessage = apiErrorMessage(error, 'Login failed.');
          this.cdr.markForCheck();

          return;
        }

        if (error.status === 400) {

          this.errorMessage = apiErrorMessage(error, 'Invalid login details.');
          this.cdr.markForCheck();

          return;
        }

        this.errorMessage = apiErrorMessage(error, 'Login failed. Please try again.');
        this.cdr.markForCheck();
      }

    });
  }

  private register(): void {
    if (this.isLoading) return;
    this.errorMessage = '';
    this.successMessage = '';

    const email = this.email.trim();
    if (!email || !this.password) {
      this.errorMessage = 'Please enter an email address and password.';
      return;
    }
    if (this.password.length < 8 || this.password.length > 72) {
      this.errorMessage = 'Password must contain between 8 and 72 characters.';
      return;
    }
    if (this.password !== this.confirmPassword) {
      this.errorMessage = 'The passwords do not match.';
      return;
    }

    this.isLoading = true;
    this.http.post(`${this.REGISTER_API}`, { email, password: this.password }).subscribe({
      next: () => {
        this.isLoading = false;
        this.isRegisterMode = false;
        this.password = '';
        this.confirmPassword = '';
        this.successMessage = 'Account created. Sign in with your new credentials.';
        this.cdr.markForCheck();
      },
      error: (error) => {
        this.isLoading = false;
        this.errorMessage = error?.status === 409
          ? 'An account with this email already exists. Sign in instead.'
          : apiErrorMessage(error, 'Registration failed. Please try again.');
        this.cdr.markForCheck();
      },
    });
  }
}
