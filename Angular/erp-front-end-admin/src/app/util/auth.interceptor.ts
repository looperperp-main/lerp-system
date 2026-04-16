import {HttpErrorResponse, HttpInterceptorFn} from '@angular/common/http';
import {inject, PLATFORM_ID} from '@angular/core';
import {isPlatformBrowser} from '@angular/common';
import {catchError, throwError} from 'rxjs';
import {Router} from '@angular/router';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const platformId = inject(PLATFORM_ID);
  const router = inject(Router);

  // Verifica se o código está rodando no navegador (Browser) e não no Servidor Node (SSR)
  if (isPlatformBrowser(platformId)) {
    const token = sessionStorage.getItem('auth-token');

    // Se a requisição for para a rota de login, não anexa o token
    if (req.url.includes('/auth/login')) {
      return next(req);
    }

    // Anexa o token se ele existir
    if (token) {
      const clonedReq = req.clone({
        setHeaders: {
          Authorization: `Bearer ${token}`
        }
      });

      return next(clonedReq).pipe(
        catchError((error: HttpErrorResponse) => {
          // Token expirado ou inválido
          if (error.status === 401 || error.status === 403) {
            sessionStorage.clear();
            router.navigate(['/login']);
          }
          return throwError(() => error);
        })
      );
    }
  }

  return next(req);
};
