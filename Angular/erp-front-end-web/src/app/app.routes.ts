import { Routes } from '@angular/router';
import { Home } from './pages/home/home';
import { TenantLogin } from './pages/login/login';
import { authGuard } from './util/auth.guard';
import {WebLayout} from './components/web-layout/web-layout';
import {GrupoClientes} from './pages/cadastros/grupo-clientes/grupo-clientes';

export const routes: Routes = [
    { path: '', redirectTo: 'login', pathMatch: 'full' },
    { path: 'login', component: TenantLogin },
    {
        path: 'web',
        component: WebLayout,
        children: [
          { path: '', redirectTo: 'home', pathMatch: 'full' },
          { path: 'home', component: Home },
          { path: 'cadastros/grp_c', component: GrupoClientes  },
        ]
        //canActivate: [authGuard]
    },
    { path: '**', redirectTo: 'login' }
];
