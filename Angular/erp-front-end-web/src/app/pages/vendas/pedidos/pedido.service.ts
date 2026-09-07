import { inject, Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import {
  CancelarPedidoRequest,
  ExpedirPedidoRequest,
  Pedido,
  PedidoRequest,
  StatusPedido,
} from './pedido.model';

export interface PedidoFiltro {
  status?: StatusPedido | null;
  clienteId?: string | null;
  vendedorId?: string | null;
  numero?: number | null;
  dataEmissaoDe?: string | null;
  dataEmissaoAte?: string | null;
}

@Injectable({ providedIn: 'root' })
export class PedidoService {
  private http = inject(HttpClient);
  private apiUrl = `${environment.apiUrl}/api/v1/pedidos`;

  listar(filtro: PedidoFiltro, page: number = 0, size: number = 10): Observable<any> {
    let params = new HttpParams().set('page', page.toString()).set('size', size.toString());
    Object.entries(filtro).forEach(([key, value]) => {
      if (value !== null && value !== undefined && value !== '') {
        params = params.set(key, value.toString());
      }
    });
    return this.http.get<any>(this.apiUrl, { params });
  }

  buscarPorId(id: string): Observable<Pedido> {
    return this.http.get<Pedido>(`${this.apiUrl}/${id}`);
  }

  criar(dto: PedidoRequest): Observable<Pedido> {
    return this.http.post<Pedido>(this.apiUrl, dto);
  }

  atualizar(id: string, dto: PedidoRequest): Observable<Pedido> {
    return this.http.put<Pedido>(`${this.apiUrl}/${id}`, dto);
  }

  confirmar(id: string): Observable<Pedido> {
    return this.http.post<Pedido>(`${this.apiUrl}/${id}/confirmar`, {});
  }

  expedir(id: string, dto: ExpedirPedidoRequest): Observable<Pedido> {
    return this.http.post<Pedido>(`${this.apiUrl}/${id}/expedir`, dto);
  }

  faturar(id: string): Observable<Pedido> {
    return this.http.post<Pedido>(`${this.apiUrl}/${id}/faturar`, {});
  }

  cancelar(id: string, dto: CancelarPedidoRequest): Observable<Pedido> {
    return this.http.post<Pedido>(`${this.apiUrl}/${id}/cancelar`, dto);
  }

  recalcularPrecos(id: string): Observable<Pedido> {
    return this.http.post<Pedido>(`${this.apiUrl}/${id}/recalcular-precos`, {});
  }

  reabrir(id: string): Observable<Pedido> {
    return this.http.post<Pedido>(`${this.apiUrl}/${id}/reabrir`, {});
  }
}
