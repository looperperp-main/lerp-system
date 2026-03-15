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
