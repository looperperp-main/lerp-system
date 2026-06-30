import { Routes } from '@angular/router';
import { authGuard } from './util/auth.guard';

export const routes: Routes = [
    { path: '', loadComponent: () => import('./pages/landing/landing').then(m => m.Landing) },
    { path: 'login', loadComponent: () => import('./pages/login/login').then(m => m.TenantLogin) },
    { path: 'ativar', loadComponent: () => import('./pages/ativar/ativar').then(m => m.AtivarConta) },
    { path: 'cadastrar-parceiro', loadComponent: () => import('./pages/parceiro/parceiro-cadastro').then(m => m.ParceiroCadastro) },
    { path: 'criar-conta', loadComponent: () => import('./pages/criar-conta/criar-conta').then(m => m.CriarConta) },
    {
        path: 'web',
        loadComponent: () => import('./components/web-layout/web-layout').then(m => m.WebLayout),
        canActivate: [authGuard],
        children: [
            { path: '', redirectTo: 'home', pathMatch: 'full' },
            { path: 'home', loadComponent: () => import('./pages/home/home').then(m => m.Home) },
            { path: 'assinar', loadComponent: () => import('./pages/assinar/assinar').then(m => m.Assinar) },
            { path: 'cadastros/grp_c', loadComponent: () => import('./pages/cadastros/grupo-clientes/grupo-clientes').then(m => m.GrupoClientes) },
            { path: 'cadastros/depositos', loadComponent: () => import('./pages/cadastros/deposito/depositos').then(m => m.Depositos) },
            { path: 'cadastros/cond-pagamento', loadComponent: () => import('./pages/cadastros/cond-pagamento/cond-pagamentos').then(m => m.CondPagamentos) },
            { path: 'cadastros/pessoas', loadComponent: () => import('./pages/cadastros/pessoas/pessoas').then(m => m.Pessoas) },
            { path: 'cadastros/vendedores', loadComponent: () => import('./pages/cadastros/vendedores/vendedores').then(m => m.Vendedores) },
            { path: 'cadastros/clientes', loadComponent: () => import('./pages/cadastros/cliente/clientes').then(m => m.Clientes) },
            { path: 'cadastros/categorias', loadComponent: () => import('./pages/cadastros/produto-categoria/produto-categoria').then(m => m.ProdutoCategoria) },
            { path: 'cadastros/fornecedores', loadComponent: () => import('./pages/cadastros/fornecedores/fornecedores').then(m => m.Fornecedores) },
            { path: 'cadastros/transportadoras', loadComponent: () => import('./pages/cadastros/transportadoras/transportadoras').then(m => m.Transportadoras) },
            { path: 'cadastros/tabela-preco', loadComponent: () => import('./pages/cadastros/tabela-precos/tabela-precos').then(m => m.TabelaPrecos) },
            { path: 'cadastros/tabela-preco-grupo', loadComponent: () => import('./pages/cadastros/grupo-cliente-tabela-preco/grupo-cliente-tabela-preco').then(m => m.GrupoClienteTabelaPrecoComponent) },
            { path: 'cadastros/produtos', loadComponent: () => import('./pages/cadastros/produtos/produtos').then(m => m.Produtos) },
            { path: 'security/users', loadComponent: () => import('./pages/security/users/users').then(m => m.SecurityUsers) },
            { path: 'security/roles', loadComponent: () => import('./pages/security/roles/roles').then(m => m.SecurityRoles) },
            { path: 'security/role-permissions', loadComponent: () => import('./pages/security/role-permissions/role-permissions').then(m => m.SecurityRolePermissions) },
            { path: 'security/user-roles', loadComponent: () => import('./pages/security/user-roles/user-roles').then(m => m.SecurityUserRoles) },
        ],
    },
    { path: '**', redirectTo: 'login' }
];