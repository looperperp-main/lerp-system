import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { NavigationEnd, Router } from '@angular/router';
import { filter } from 'rxjs/operators';

const ROUTE_FEATURE_MAP: Record<string, string> = {
  // cadastros — módulo em produção hoje (rotas reais de app.routes.ts)
  '/web/cadastros/grp_c': 'grupo_clientes',
  '/web/cadastros/depositos': 'depositos',
  '/web/cadastros/cond-pagamento': 'cond_pagamento',
  '/web/cadastros/pessoas': 'pessoas',
  '/web/cadastros/vendedores': 'vendedores',
  '/web/cadastros/clientes': 'clientes',
  '/web/cadastros/categorias': 'categorias',
  '/web/cadastros/fornecedores': 'fornecedores',
  '/web/cadastros/transportadoras': 'transportadoras',
  '/web/cadastros/tabela-preco': 'tabela_preco',
  '/web/cadastros/tabela-preco-grupo': 'tabela_preco_grupo',
  '/web/cadastros/produtos': 'produtos',
  // ponytail: telas abaixo ainda não existem em app.routes.ts — mapeadas pra quando forem criadas
  '/web/nfe': 'nfe',
  '/web/financeiro': 'contas_pagar_receber',
  '/web/relatorios': 'relatorios',
  '/web/conciliacao': 'conciliacao',
  '/web/folha-pagamento': 'folha_pagamento',
  '/web/integracao-contabil': 'integracao_contabil',
};

@Injectable({ providedIn: 'root' })
export class FeatureTrackingService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  // ponytail: gateway roteia cadastro-service em Path=/api/** sem prefixo /cadastro (diferente
  // de partner/billing, que usam StripPrefix) — mesmo padrão de clientes.service.ts etc.
  private readonly API = 'http://localhost:8090/api/v1/engagement/feature';

  init(): void {
    this.router.events
      .pipe(filter((e): e is NavigationEnd => e instanceof NavigationEnd))
      .subscribe(({ urlAfterRedirects }) => {
        const featureKey = ROUTE_FEATURE_MAP[urlAfterRedirects];
        if (featureKey) {
          this.http.post(this.API, { featureKey }).subscribe({
            error: () => {
              /* falha silenciosa */
            },
          });
        }
      });
  }
}
