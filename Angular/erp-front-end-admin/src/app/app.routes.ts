import { Routes } from '@angular/router';
import { Login } from './pages/login/login';
import { AdminLayout } from './components/admin-layout/admin-layout';
import { Home } from './pages/admin/home/home';
import { Tenant } from './pages/cadastros/tenant/tenant/tenant';
import { TenantDetail } from './pages/cadastros/tenant/tenant-detail/tenant-detail';
import { Audit } from './pages/admin/audit/audit';
import { Users } from './pages/cadastros/tenant/users/users';
import { Roles } from './pages/cadastros/roles/roles/roles';
import { Permission } from './pages/cadastros/permission/permission';
import { RolePermissions } from './pages/admin/security/role-permissions/role-permissions';
import { UserRolesComponent } from './pages/admin/security/user-roles/user-roles';
import { Parceiros } from './pages/cadastros/parceiros/parceiros';
import { Indicacoes } from './pages/cadastros/parceiros/indicacoes/indicacoes';
import { Plans } from './pages/cadastros/plans/plans';
import { Subscription } from './pages/cadastros/subscription/subscription';
import { Invoices } from './pages/cadastros/invoices/invoices';
import { FilaInterna } from './pages/admin/fila-interna/fila-interna';
import { Rastreador } from './pages/admin/diagnostico/rastreador';
import { Saude } from './pages/admin/diagnostico/saude';
import { Logs } from './pages/admin/diagnostico/logs';
import { Jobs } from './pages/admin/diagnostico/jobs';
import { authGuard } from './util/authguard';

export const routes: Routes = [
  { path: '', redirectTo: 'login-admin', pathMatch: 'full' },
  {
    path: 'login-admin',
    component: Login,
  },
  {
    path: 'admin',
    component: AdminLayout,
    canActivate: [authGuard],
    children: [
      { path: '', redirectTo: 'home', pathMatch: 'full' },
      { path: 'home', component: Home },
      { path: 'cadastros/tenants', component: Tenant },
      { path: 'cadastros/tenants/:id', component: TenantDetail },
      { path: 'cadastros/users', component: Users },
      { path: 'cadastros/roles', component: Roles },
      { path: 'cadastros/permission', component: Permission },
      { path: 'security/role-permissions', component: RolePermissions },
      { path: 'security/user-roles', component: UserRolesComponent },
      { path: 'parceiros/contadores', component: Parceiros },
      { path: 'parceiros/indicacoes', component: Indicacoes },
      { path: 'cadastros/plans', component: Plans },
      { path: 'cadastros/subscription', component: Subscription },
      { path: 'cadastros/invoices', component: Invoices },
      { path: 'audit', component: Audit },
      { path: 'fila-interna', component: FilaInterna },
      { path: 'diagnostico/rastreador', component: Rastreador },
      { path: 'diagnostico/saude', component: Saude },
      { path: 'diagnostico/logs', component: Logs },
      { path: 'diagnostico/jobs', component: Jobs },
    ],
  },
];
