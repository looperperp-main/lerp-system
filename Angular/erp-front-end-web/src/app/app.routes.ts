import { Routes } from '@angular/router';
import { Home } from './pages/home/home';
import { TenantLogin } from './pages/login/login';
import { AtivarConta } from './pages/ativar/ativar';
import { Landing } from './pages/landing/landing';
import { ParceiroCadastro } from './pages/parceiro/parceiro-cadastro';
import { authGuard } from './util/auth.guard';
import {WebLayout} from './components/web-layout/web-layout';
import {GrupoClientes} from './pages/cadastros/grupo-clientes/grupo-clientes';
import {Depositos} from './pages/cadastros/deposito/depositos';
import {CondPagamentos} from './pages/cadastros/cond-pagamento/cond-pagamentos';
import {Pessoas} from './pages/cadastros/pessoas/pessoas';
import {Vendedores} from './pages/cadastros/vendedores/vendedores';
import {Clientes} from './pages/cadastros/cliente/clientes';
import {ProdutoCategoria} from './pages/cadastros/produto-categoria/produto-categoria';
import {Fornecedores} from './pages/cadastros/fornecedores/fornecedores';
import {Transportadoras} from './pages/cadastros/transportadoras/transportadoras';
import {TabelaPrecos} from './pages/cadastros/tabela-precos/tabela-precos';
import {
  GrupoClienteTabelaPrecoComponent
} from './pages/cadastros/grupo-cliente-tabela-preco/grupo-cliente-tabela-preco';
import {Produtos} from './pages/cadastros/produtos/produtos';

export const routes: Routes = [
    { path: '', component: Landing },
    { path: 'login', component: TenantLogin },
    { path: 'ativar', component: AtivarConta },
    { path: 'cadastrar-parceiro', component: ParceiroCadastro },
    {
        path: 'web',
        component: WebLayout,
        children: [
          { path: '', redirectTo: 'home', pathMatch: 'full' },
          { path: 'home', component: Home },
          { path: 'cadastros/grp_c', component: GrupoClientes  },
          { path: 'cadastros/depositos', component: Depositos  },
          { path: 'cadastros/cond-pagamento', component: CondPagamentos  },
          { path: 'cadastros/pessoas', component: Pessoas  },
          { path: 'cadastros/vendedores', component: Vendedores  },
          { path: 'cadastros/clientes', component: Clientes  },
          { path: 'cadastros/categorias', component: ProdutoCategoria  },
          { path: 'cadastros/fornecedores', component: Fornecedores  },
          { path: 'cadastros/transportadoras', component: Transportadoras  },
          { path: 'cadastros/tabela-preco', component: TabelaPrecos  },
          { path: 'cadastros/tabela-preco-grupo', component: GrupoClienteTabelaPrecoComponent  },
          { path: 'cadastros/produtos', component: Produtos  },
        ]
        //canActivate: [authGuard]
    },
    { path: '**', redirectTo: 'login' }
];
