import {Routes} from '@angular/router';
import {Login} from './pages/login/login';
import {AdminLayout} from './components/admin-layout/admin-layout';
import {Home} from './pages/admin/home/home';
import {Tenant} from './pages/cadastros/tenant/tenant/tenant';
import {Audit} from './pages/admin/audit/audit';
import {Users} from './pages/cadastros/tenant/users/users';
import {Roles} from './pages/cadastros/roles/roles/roles';
import {Permission} from './pages/cadastros/permission/permission';

export const routes: Routes = [
  { path: '', redirectTo: 'login-admin', pathMatch: 'full' },
  {
    path: 'login-admin',
    component: Login
  },
  {
    path: 'admin',
    component: AdminLayout,
    //canActivate: [authGuard],
    children: [
      { path: '', redirectTo: 'home', pathMatch: 'full' },
      { path: 'home', component: Home },
      { path: 'cadastros/tenants', component: Tenant },
      { path: 'cadastros/users', component: Users },
      { path: 'cadastros/roles', component: Roles },
      { path: 'cadastros/permission', component: Permission },
      { path: 'audit', component: Audit}
    ]
  }
];
