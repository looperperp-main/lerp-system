import { Routes } from '@angular/router';

export const routes: Routes = [
  { path: '', redirectTo: 'login', pathMatch: 'full' },
  {
    path: 'login',
    loadComponent: () => import('./pages/login/login').then(m => m.LoginComponent),
  },
  {
    path: '',
    loadComponent: () => import('./components/partner-layout/partner-layout').then(m => m.PartnerLayout),
    children: [
      {
        path: 'dashboard',
        loadComponent: () => import('./pages/dashboard/dashboard').then(m => m.DashboardComponent),
      },
      {
        path: 'clientes',
        loadComponent: () => import('./pages/clientes/clientes').then(m => m.Clientes),
      },
      {
        path: 'comissoes',
        loadComponent: () => import('./pages/comissoes/comissoes').then(m => m.ComissoesComponent),
      },
      {
        path: 'configuracoes',
        loadComponent: () =>
          import('./pages/configuracoes/configuracoes').then(m => m.ConfiguracoesComponent),
      },
    ],
  },
];