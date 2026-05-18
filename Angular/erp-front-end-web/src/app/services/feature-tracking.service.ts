import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { NavigationEnd, Router } from '@angular/router';
import { filter } from 'rxjs/operators';

const ROUTE_FEATURE_MAP: Record<string, string> = {
  '/web/nfe':                     'nfe',
  '/web/financeiro':              'contas_pagar_receber',
  '/web/relatorios':              'relatorios',
  '/web/conciliacao':             'conciliacao',
  '/web/folha-pagamento':         'folha_pagamento',
  '/web/integracao-contabil':     'integracao_contabil',
};

@Injectable({ providedIn: 'root' })
export class FeatureTrackingService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly API = 'http://localhost:8090/cadastro/api/v1/engagement/feature';

  init(): void {
    this.router.events
      .pipe(filter((e): e is NavigationEnd => e instanceof NavigationEnd))
      .subscribe(({ urlAfterRedirects }) => {
        const featureKey = ROUTE_FEATURE_MAP[urlAfterRedirects];
        if (featureKey) {
          this.http.post(this.API, { featureKey }).subscribe({
            error: () => { /* falha silenciosa */ },
          });
        }
      });
  }
}