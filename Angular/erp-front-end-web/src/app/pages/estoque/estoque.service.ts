import { inject, Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  AjusteEstoqueRequest,
  OrigemMovimentoEstoque,
  TipoMovimentoEstoque,
} from './estoque.model';

export interface SaldoFiltro {
  produtoId?: string | null;
  depositoId?: string | null;
  comSaldo?: boolean | null;
}

export interface MovimentoFiltro {
  produtoId?: string | null;
  depositoId?: string | null;
  de?: string | null;
  ate?: string | null;
  tipo?: TipoMovimentoEstoque | null;
  origemTipo?: OrigemMovimentoEstoque | null;
}

@Injectable({ providedIn: 'root' })
export class EstoqueService {
  private http = inject(HttpClient);
  private apiUrl = `${environment.apiUrl}/api/v1/estoque`;

  private paramsFrom(filtro: object, page: number, size: number): HttpParams {
    let params = new HttpParams().set('page', page.toString()).set('size', size.toString());
    Object.entries(filtro as Record<string, unknown>).forEach(([key, value]) => {
      if (value !== null && value !== undefined && value !== '') {
        params = params.set(key, String(value));
      }
    });
    return params;
  }

  buscarSaldos(filtro: SaldoFiltro, page: number = 0, size: number = 10): Observable<any> {
    return this.http.get<any>(`${this.apiUrl}/saldos`, {
      params: this.paramsFrom(filtro, page, size),
    });
  }

  buscarMovimentos(filtro: MovimentoFiltro, page: number = 0, size: number = 10): Observable<any> {
    return this.http.get<any>(`${this.apiUrl}/movimentos`, {
      params: this.paramsFrom(filtro, page, size),
    });
  }

  ajustar(dto: AjusteEstoqueRequest): Observable<void> {
    return this.http.post<void>(`${this.apiUrl}/ajustes`, dto);
  }
}
