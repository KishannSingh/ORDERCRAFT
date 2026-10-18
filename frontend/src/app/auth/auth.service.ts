import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, tap, throwError } from 'rxjs';
import { API_BASE_URL } from '../api';

export interface AuthUser {
  userId: number;
  email: string;
  role: string;
}

@Injectable({
  providedIn: 'root',
})
export class AuthService {
  private readonly TOKEN_KEY = 'token';
  private readonly USER_KEY = 'user';
  private readonly http = inject(HttpClient);

  isLoggedIn(): boolean {
    return !!localStorage.getItem(this.TOKEN_KEY);
  }

  setToken(token: string): void {
    localStorage.setItem(this.TOKEN_KEY, token);
  }

  setSession(token: string, user: AuthUser): void {
    this.setToken(token);
    localStorage.setItem(this.USER_KEY, JSON.stringify(user));
  }

  getToken(): string | null {
    return localStorage.getItem(this.TOKEN_KEY);
  }

  validateSession(): Observable<AuthUser> {
    if (!this.getToken()) {
      return throwError(() => new Error('No authentication token is available'));
    }

    return this.http.get<AuthUser>(`${API_BASE_URL}/api/auth/me`).pipe(
      tap((user) => localStorage.setItem(this.USER_KEY, JSON.stringify(user))),
    );
  }

  logout(): void {
    localStorage.removeItem(this.TOKEN_KEY);
    localStorage.removeItem(this.USER_KEY);
  }
}
