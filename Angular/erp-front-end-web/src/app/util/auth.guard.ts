import { inject } from '@angular/core';
import { Router, CanActivateFn } from '@angular/router';
import { TenantLoginService } from '../pages/login/service/tenant-login.service';

export const authGuard: CanActivateFn = () => {
  const loginService = inject(TenantLoginService);
  const router = inject(Router);

  if (loginService.isAuthenticated()) {
    return true;
  }

  router.navigate(['/login']);
  return false;
};

// Inverso do authGuard: barra ANTES de carregar o chunk lazy da landing quando já há sessão
// válida, evitando o flash de "renderiza landing → só depois redireciona" que o check no
// construtor do Landing causava (router.navigate() é assíncrono, então o template chegava a pintar).
export const guestGuard: CanActivateFn = () => {
  const loginService = inject(TenantLoginService);
  const router = inject(Router);

  if (loginService.isAuthenticated()) {
    router.navigate(['/web/home'], { replaceUrl: true });
    return false;
  }

  return true;
};
