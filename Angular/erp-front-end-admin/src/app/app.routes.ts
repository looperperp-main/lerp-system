import { Routes } from '@angular/router';
import {Login} from './pages/login/login';
import {AdminLayout} from './components/admin-layout/admin-layout';
import {Home} from './pages/admin/home/home';
import {authGuard} from './util/authguard';

export const routes: Routes = [
  { path: '', redirectTo: 'login-admin', pathMatch: 'full' },
  {
    path: 'login-admin',
    component: Login
  },
  {
    path: 'admin',
    component: AdminLayout,
    canActivate: [authGuard],
    children: [
      { path: '', redirectTo: 'home', pathMatch: 'full' },
      { path: 'home', component: Home }
    ]
  }
];
