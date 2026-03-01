import { HttpInterceptorFn } from '@angular/common/http';
import {inject, PLATFORM_ID} from '@angular/core';
import {isPlatformBrowser} from '@angular/common';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const platformId = inject(PLATFORM_ID);

  // Verifica se o código está rodando no navegador (Browser) e não no Servidor Node (SSR)
  if (isPlatformBrowser(platformId)) {
    const token = sessionStorage.getItem('auth-token');

    // Se a requisição for para a rota de login, não anexa o token
    if (req.url.includes('/auth/login')) {
      return next(req);
    }

    // Anexa o token se ele existir
    if (token) {
      const cloned = req.clone({
        setHeaders: { Authorization: `Bearer ${token}` }
      });
      return next(cloned);
    }
  }

  return next(req);
};
