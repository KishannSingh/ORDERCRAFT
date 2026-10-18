import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';

export const jwtInterceptor: HttpInterceptorFn = (req, next) => {
  const token = localStorage.getItem('token');
  const isAuthAction = /\/api\/auth\/(login|register)(?:\?|$)/.test(req.url);
  const isSessionCheck = /\/api\/auth\/me(?:\?|$)/.test(req.url);

  if (!token || isAuthAction) {
    return next(req);
  }

  const authReq = req.clone({
    setHeaders: {
      Authorization: `Bearer ${token}`,
    },
  });

  const router = inject(Router);
  return next(authReq).pipe(
    catchError((error: unknown) => {
      if (error instanceof HttpErrorResponse && error.status === 401) {
        localStorage.removeItem('token');
        localStorage.removeItem('user');
        if (!isSessionCheck) void router.navigate(['/login']);
      }
      return throwError(() => error);
    }),
  );
};
