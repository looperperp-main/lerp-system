import { HttpInterceptorFn, HttpErrorResponse } from '@angular/common/http';
import { inject, PLATFORM_ID } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const platformId = inject(PLATFORM_ID);
  const router = inject(Router);

  // Verifica se está rodando no navegador (não SSR)
  if (isPlatformBrowser(platformId)) {
    const token = sessionStorage.getItem('auth-token');

    // Rotas públicas que não precisam de token
    const publicRoutes = ['/auth/login', '/auth/tenant/login', '/auth/refresh'];
    const isPublicRoute = publicRoutes.some((route) => req.url.includes(route));

    if (isPublicRoute) {
      return next(req);
    }

    // Anexa o token se existir
    if (token) {
      const clonedReq = req.clone({
        setHeaders: {
          Authorization: `Bearer ${token}`,
        },
      });

      return next(clonedReq).pipe(
        catchError((error: HttpErrorResponse) => {
          // Só 401 (token expirado/inválido) desloga. 403 = autenticado sem permissão → deixa o componente tratar.
          if (error.status === 401) {
            sessionStorage.clear();
            localStorage.clear(); // encerra também a sessão "manter conectado"
            router.navigate(['/login']);
          }
          return throwError(() => error);
        }),
      );
    }
  }

  return next(req);
};
