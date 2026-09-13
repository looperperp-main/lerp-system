import { inject, Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import {
  CancelarPedidoCompraRequest,
  EncerrarSaldoPedidoCompraRequest,
  PedidoCompra,
  PedidoCompraRequest,
  ReprovarPedidoCompraRequest,
  StatusPedidoCompra,
} from './pedido-compra.model';

export interface PedidoCompraFiltro {
  status?: StatusPedidoCompra | null;
  fornecedorId?: string | null;
  dataEmissaoDe?: string | null;
  dataEmissaoAte?: string | null;
}

@Injectable({ providedIn: 'root' })
export class PedidoCompraService {
  private http = inject(HttpClient);
  private apiUrl = `${environment.apiUrl}/api/v1/compras/pedidos`;

  listar(filtro: PedidoCompraFiltro, page: number = 0, size: number = 10): Observable<any> {
    let params = new HttpParams().set('page', page.toString()).set('size', size.toString());
    Object.entries(filtro).forEach(([key, value]) => {
      if (value !== null && value !== undefined && value !== '') {
        params = params.set(key, value.toString());
      }
    });
    return this.http.get<any>(this.apiUrl, { params });
  }

  buscarPorId(id: string): Observable<PedidoCompra> {
    return this.http.get<PedidoCompra>(`${this.apiUrl}/${id}`);
  }

  criar(dto: PedidoCompraRequest): Observable<PedidoCompra> {
    return this.http.post<PedidoCompra>(this.apiUrl, dto);
  }

  atualizar(id: string, dto: PedidoCompraRequest): Observable<PedidoCompra> {
    return this.http.put<PedidoCompra>(`${this.apiUrl}/${id}`, dto);
  }

  enviarParaAprovacao(id: string): Observable<PedidoCompra> {
    return this.http.post<PedidoCompra>(`${this.apiUrl}/${id}/enviar-aprovacao`, {});
  }

  aprovar(id: string): Observable<PedidoCompra> {
    return this.http.post<PedidoCompra>(`${this.apiUrl}/${id}/aprovar`, {});
  }

  reprovar(id: string, dto: ReprovarPedidoCompraRequest): Observable<PedidoCompra> {
    return this.http.post<PedidoCompra>(`${this.apiUrl}/${id}/reprovar`, dto);
  }

  reabrir(id: string): Observable<PedidoCompra> {
    return this.http.post<PedidoCompra>(`${this.apiUrl}/${id}/reabrir`, {});
  }

  enviar(id: string): Observable<PedidoCompra> {
    return this.http.post<PedidoCompra>(`${this.apiUrl}/${id}/enviar`, {});
  }

  cancelar(id: string, dto: CancelarPedidoCompraRequest): Observable<PedidoCompra> {
    return this.http.post<PedidoCompra>(`${this.apiUrl}/${id}/cancelar`, dto);
  }

  encerrarSaldo(id: string, dto: EncerrarSaldoPedidoCompraRequest): Observable<PedidoCompra> {
    return this.http.post<PedidoCompra>(`${this.apiUrl}/${id}/encerrar-saldo`, dto);
  }
}
