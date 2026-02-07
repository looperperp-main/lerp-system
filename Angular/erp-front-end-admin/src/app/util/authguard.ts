import { CanActivateFn, Router } from '@angular/router';
import { inject } from '@angular/core';

export const authGuard: CanActivateFn = () => {
  const router = inject(Router);
  const token = sessionStorage.getItem('auth-token');

  if (token) {
    return true;
  }

  router.navigate(['/login-admin']);
  return false;
};
