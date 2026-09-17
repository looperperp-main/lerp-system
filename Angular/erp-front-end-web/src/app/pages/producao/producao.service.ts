import { inject, Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { FichaTecnicaRequest, OrdemProducaoRequest, StatusOrdemProducao } from './producao.model';

export interface OrdemFiltro {
  status?: StatusOrdemProducao | null;
}

/** Item 3 (Fase 2), spec/modulos/estoque/estoque.md §12 — ficha técnica + ordem de produção. */
@Injectable({ providedIn: 'root' })
export class ProducaoService {
  private http = inject(HttpClient);
  private apiUrl = `${environment.apiUrl}/api/v1/producao`;

  private paramsFrom(filtro: object, page: number, size: number): HttpParams {
    let params = new HttpParams().set('page', page.toString()).set('size', size.toString());
    Object.entries(filtro as Record<string, unknown>).forEach(([key, value]) => {
      if (value !== null && value !== undefined && value !== '') {
        params = params.set(key, String(value));
      }
    });
    return params;
  }

  buscarFichasTecnicas(page: number = 0, size: number = 10): Observable<any> {
    return this.http.get<any>(`${this.apiUrl}/fichas-tecnicas`, {
      params: this.paramsFrom({}, page, size),
    });
  }

  criarFichaTecnica(dto: FichaTecnicaRequest): Observable<void> {
    return this.http.post<void>(`${this.apiUrl}/fichas-tecnicas`, dto);
  }

  buscarOrdens(filtro: OrdemFiltro, page: number = 0, size: number = 10): Observable<any> {
    return this.http.get<any>(`${this.apiUrl}/ordens`, {
      params: this.paramsFrom(filtro, page, size),
    });
  }

  criarOrdem(dto: OrdemProducaoRequest): Observable<void> {
    return this.http.post<void>(`${this.apiUrl}/ordens`, dto);
  }

  apontarProducao(ordemId: string, quantidadeProduzida: number): Observable<void> {
    return this.http.post<void>(`${this.apiUrl}/ordens/${ordemId}/apontar`, {
      quantidadeProduzida,
    });
  }
}
