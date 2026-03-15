import { Routes } from '@angular/router';
import { Dashboard } from './pages/dashboard/dashboard';
import { TenantLogin } from './pages/login/login';
import { authGuard } from './util/auth.guard';

export const routes: Routes = [
    { path: '', redirectTo: 'login', pathMatch: 'full' },
    { path: 'login', component: TenantLogin },
    {
        path: 'dashboard',
        component: Dashboard,
        canActivate: [authGuard]
    },
    { path: '**', redirectTo: 'login' }
];
