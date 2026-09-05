import { Routes } from '@angular/router';
import { authGuard, guestGuard } from './util/auth.guard';

export const routes: Routes = [
  {
    path: '',
    loadComponent: () => import('./pages/landing/landing').then((m) => m.Landing),
    canActivate: [guestGuard],
  },
  { path: 'login', loadComponent: () => import('./pages/login/login').then((m) => m.TenantLogin) },
  {
    path: 'ativar',
    loadComponent: () => import('./pages/ativar/ativar').then((m) => m.AtivarConta),
  },
  {
    path: 'cadastrar-parceiro',
    loadComponent: () =>
      import('./pages/parceiro/parceiro-cadastro').then((m) => m.ParceiroCadastro),
  },
  {
    path: 'criar-conta',
    loadComponent: () => import('./pages/criar-conta/criar-conta').then((m) => m.CriarConta),
  },
  {
    path: 'esqueci-senha',
    loadComponent: () => import('./pages/esqueci-senha/esqueci-senha').then((m) => m.EsqueciSenha),
  },
  {
    path: 'redefinir-senha',
    loadComponent: () =>
      import('./pages/esqueci-senha/redefinir-senha').then((m) => m.RedefinirSenha),
  },
  {
    path: 'web',
    loadComponent: () => import('./components/web-layout/web-layout').then((m) => m.WebLayout),
    canActivate: [authGuard],
    children: [
      { path: '', redirectTo: 'home', pathMatch: 'full' },
      {
        path: 'home',
        loadComponent: () => import('./pages/home/home').then((m) => m.Home),
        data: { breadcrumb: [{ label: 'Overview' }] },
      },
      {
        path: 'assinar',
        loadComponent: () => import('./pages/assinar/assinar').then((m) => m.Assinar),
        data: {
          breadcrumb: [{ label: 'Home', link: '/web/home' }, { label: 'Assinar Plano' }],
        },
      },
      {
        path: 'cadastros/grp_c',
        loadComponent: () =>
          import('./pages/cadastros/grupo-clientes/grupo-clientes').then((m) => m.GrupoClientes),
        data: {
          breadcrumb: [
            { label: 'Home', link: '/web/home' },
            { label: 'Parceiros de Negócio' },
            { label: 'Grupo de Clientes' },
          ],
        },
      },
      {
        path: 'cadastros/depositos',
        loadComponent: () =>
          import('./pages/cadastros/deposito/depositos').then((m) => m.Depositos),
        data: {
          breadcrumb: [
            { label: 'Home', link: '/web/home' },
            { label: 'Estoque & Produtos' },
            { label: 'Depósitos' },
          ],
        },
      },
      {
        path: 'cadastros/cond-pagamento',
        loadComponent: () =>
          import('./pages/cadastros/cond-pagamento/cond-pagamentos').then((m) => m.CondPagamentos),
        data: {
          breadcrumb: [
            { label: 'Home', link: '/web/home' },
            { label: 'Comercial & Financeiro' },
            { label: 'Condições de Pagamento' },
          ],
        },
      },
      {
        path: 'vendas/pedidos',
        loadComponent: () => import('./pages/vendas/pedidos/pedidos').then((m) => m.Pedidos),
        data: {
          breadcrumb: [
            { label: 'Home', link: '/web/home' },
            { label: 'Comercial & Financeiro' },
            { label: 'Pedidos de Venda' },
          ],
        },
      },
      {
        path: 'vendas/pedidos/:id',
        loadComponent: () =>
          import('./pages/vendas/pedidos/pedido-detalhe/pedido-detalhe').then(
            (m) => m.PedidoDetalhe,
          ),
        data: {
          breadcrumb: [
            { label: 'Home', link: '/web/home' },
            { label: 'Comercial & Financeiro' },
            { label: 'Pedidos de Venda', link: '/web/vendas/pedidos' },
            { label: 'Detalhe do Pedido' },
          ],
        },
      },
      {
        path: 'cadastros/pessoas',
        loadComponent: () => import('./pages/cadastros/pessoas/pessoas').then((m) => m.Pessoas),
        data: {
          breadcrumb: [
            { label: 'Home', link: '/web/home' },
            { label: 'Parceiros de Negócio' },
            { label: 'Pessoas (Geral)' },
          ],
        },
      },
      {
        path: 'cadastros/pessoas/:pessoaId/estabelecimentos',
        loadComponent: () =>
          import('./pages/cadastros/estabelecimento/estabelecimentos').then(
            (m) => m.Estabelecimentos,
          ),
        data: {
          breadcrumb: [
            { label: 'Home', link: '/web/home' },
            { label: 'Parceiros de Negócio' },
            { label: 'Pessoas (Geral)', link: '/web/cadastros/pessoas' },
            { label: 'Filiais' },
          ],
        },
      },
      {
        path: 'cadastros/vendedores',
        loadComponent: () =>
          import('./pages/cadastros/vendedores/vendedores').then((m) => m.Vendedores),
        data: {
          breadcrumb: [
            { label: 'Home', link: '/web/home' },
            { label: 'Parceiros de Negócio' },
            { label: 'Vendedores' },
          ],
        },
      },
      {
        path: 'cadastros/clientes',
        loadComponent: () => import('./pages/cadastros/cliente/clientes').then((m) => m.Clientes),
        data: {
          breadcrumb: [
            { label: 'Home', link: '/web/home' },
            { label: 'Parceiros de Negócio' },
            { label: 'Clientes' },
          ],
        },
      },
      {
        path: 'cadastros/categorias',
        loadComponent: () =>
          import('./pages/cadastros/produto-categoria/produto-categoria').then(
            (m) => m.ProdutoCategoria,
          ),
        data: {
          breadcrumb: [
            { label: 'Home', link: '/web/home' },
            { label: 'Estoque & Produtos' },
            { label: 'Categorias' },
          ],
        },
      },
      {
        path: 'cadastros/fornecedores',
        loadComponent: () =>
          import('./pages/cadastros/fornecedores/fornecedores').then((m) => m.Fornecedores),
        data: {
          breadcrumb: [
            { label: 'Home', link: '/web/home' },
            { label: 'Parceiros de Negócio' },
            { label: 'Fornecedores' },
          ],
        },
      },
      {
        path: 'cadastros/transportadoras',
        loadComponent: () =>
          import('./pages/cadastros/transportadoras/transportadoras').then(
            (m) => m.Transportadoras,
          ),
        data: {
          breadcrumb: [
            { label: 'Home', link: '/web/home' },
            { label: 'Parceiros de Negócio' },
            { label: 'Transportadoras' },
          ],
        },
      },
      {
        path: 'cadastros/tabela-preco',
        loadComponent: () =>
          import('./pages/cadastros/tabela-precos/tabela-precos').then((m) => m.TabelaPrecos),
        data: {
          breadcrumb: [
            { label: 'Home', link: '/web/home' },
            { label: 'Estoque & Produtos' },
            { label: 'Tabelas de Preço' },
          ],
        },
      },
      {
        path: 'cadastros/tabela-preco-grupo',
        loadComponent: () =>
          import('./pages/cadastros/grupo-cliente-tabela-preco/grupo-cliente-tabela-preco').then(
            (m) => m.GrupoClienteTabelaPrecoComponent,
          ),
        data: {
          breadcrumb: [
            { label: 'Home', link: '/web/home' },
            { label: 'Estoque & Produtos' },
            { label: 'Preços por Grupo' },
          ],
        },
      },
      {
        path: 'cadastros/produtos',
        loadComponent: () => import('./pages/cadastros/produtos/produtos').then((m) => m.Produtos),
        data: {
          breadcrumb: [
            { label: 'Home', link: '/web/home' },
            { label: 'Estoque & Produtos' },
            { label: 'Produtos' },
          ],
        },
      },
      {
        path: 'security/users',
        loadComponent: () => import('./pages/security/users/users').then((m) => m.SecurityUsers),
        data: {
          breadcrumb: [
            { label: 'Home', link: '/web/home' },
            { label: 'Segurança' },
            { label: 'Usuários' },
          ],
        },
      },
      {
        path: 'security/roles',
        loadComponent: () => import('./pages/security/roles/roles').then((m) => m.SecurityRoles),
        data: {
          breadcrumb: [
            { label: 'Home', link: '/web/home' },
            { label: 'Segurança' },
            { label: 'Roles' },
          ],
        },
      },
      {
        path: 'security/role-permissions',
        loadComponent: () =>
          import('./pages/security/role-permissions/role-permissions').then(
            (m) => m.SecurityRolePermissions,
          ),
        data: {
          breadcrumb: [
            { label: 'Home', link: '/web/home' },
            { label: 'Segurança' },
            { label: 'Configurar Roles' },
          ],
        },
      },
      {
        path: 'security/user-roles',
        loadComponent: () =>
          import('./pages/security/user-roles/user-roles').then((m) => m.SecurityUserRoles),
        data: {
          breadcrumb: [
            { label: 'Home', link: '/web/home' },
            { label: 'Segurança' },
            { label: 'Atribuir Acessos' },
          ],
        },
      },
    ],
  },
  { path: '**', redirectTo: 'login' },
];
