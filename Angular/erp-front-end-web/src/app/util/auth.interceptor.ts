import { HttpInterceptorFn, HttpErrorResponse } from '@angular/common/http';
import { inject, PLATFORM_ID } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { Router } from '@angular/router';
import { ToastrService } from 'ngx-toastr';
import { catchError, throwError } from 'rxjs';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const platformId = inject(PLATFORM_ID);
  const router = inject(Router);
  const toast = inject(ToastrService);

  // Verifica se está rodando no navegador (não SSR)
  if (!isPlatformBrowser(platformId)) {
    return next(req);
  }

  // Rotas públicas que não precisam de token
  const publicRoutes = ['/auth/login', '/auth/tenant/login', '/auth/refresh'];
  const isPublicRoute = publicRoutes.some((route) => req.url.includes(route));

  const token = sessionStorage.getItem('auth-token');
  // Anexa o token se existir e a rota não for pública
  const outgoingReq =
    !isPublicRoute && token ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : req;

  return next(outgoingReq).pipe(
    catchError((error: HttpErrorResponse) => {
      if (error.status === 429) {
        // Mensagem única e amigável pra qualquer tela — evita vazar o texto cru do backend.
        toast.warning(
          'Muitas requisições em pouco tempo. Aguarde alguns instantes e tente novamente.',
          'Calma lá',
          { timeOut: 6000 },
        );
        return throwError(() => error);
      }

      // Só 401 (token expirado/inválido) em rota autenticada desloga. 403 = autenticado sem
      // permissão → deixa o componente tratar. Rota pública (ex.: login) nunca desloga por 401.
      if (!isPublicRoute && error.status === 401) {
        sessionStorage.clear();
        localStorage.clear(); // encerra também a sessão "manter conectado"
        router.navigate(['/login']);
      }
      return throwError(() => error);
    }),
  );
};
