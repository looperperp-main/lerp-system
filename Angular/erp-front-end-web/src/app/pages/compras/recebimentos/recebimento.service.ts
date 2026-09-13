import { inject, Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import {
  CancelarRecebimentoRequest,
  RecebimentoMercadoria,
  RecebimentoMercadoriaRequest,
  StatusRecebimentoMercadoria,
} from './recebimento.model';

export interface RecebimentoFiltro {
  status?: StatusRecebimentoMercadoria | null;
  pedidoId?: string | null;
  dataRecebimentoDe?: string | null;
  dataRecebimentoAte?: string | null;
}

@Injectable({ providedIn: 'root' })
export class RecebimentoMercadoriaService {
  private http = inject(HttpClient);
  private apiUrl = `${environment.apiUrl}/api/v1/compras`;

  listar(filtro: RecebimentoFiltro, page: number = 0, size: number = 100): Observable<any> {
    let params = new HttpParams().set('page', page.toString()).set('size', size.toString());
    Object.entries(filtro).forEach(([key, value]) => {
      if (value !== null && value !== undefined && value !== '') {
        params = params.set(key, value.toString());
      }
    });
    return this.http.get<any>(`${this.apiUrl}/recebimentos`, { params });
  }

  buscarPorId(id: string): Observable<RecebimentoMercadoria> {
    return this.http.get<RecebimentoMercadoria>(`${this.apiUrl}/recebimentos/${id}`);
  }

  criar(pedidoId: string, dto: RecebimentoMercadoriaRequest): Observable<RecebimentoMercadoria> {
    return this.http.post<RecebimentoMercadoria>(`${this.apiUrl}/pedidos/${pedidoId}/recebimentos`, dto);
  }

  atualizar(id: string, dto: RecebimentoMercadoriaRequest): Observable<RecebimentoMercadoria> {
    return this.http.put<RecebimentoMercadoria>(`${this.apiUrl}/recebimentos/${id}`, dto);
  }

  confirmar(id: string): Observable<RecebimentoMercadoria> {
    return this.http.post<RecebimentoMercadoria>(`${this.apiUrl}/recebimentos/${id}/confirmar`, {});
  }

  cancelar(id: string, dto: CancelarRecebimentoRequest): Observable<RecebimentoMercadoria> {
    return this.http.post<RecebimentoMercadoria>(`${this.apiUrl}/recebimentos/${id}/cancelar`, dto);
  }

  faturar(id: string): Observable<RecebimentoMercadoria> {
    return this.http.post<RecebimentoMercadoria>(`${this.apiUrl}/recebimentos/${id}/faturar`, {});
  }
}
