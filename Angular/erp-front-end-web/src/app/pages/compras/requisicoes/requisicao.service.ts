import { inject, Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import {
  CancelarRequisicaoRequest,
  RequisicaoCompra,
  RequisicaoCompraRequest,
  ReprovarRequisicaoRequest,
  StatusRequisicaoCompra,
} from './requisicao.model';

export interface RequisicaoFiltro {
  status?: StatusRequisicaoCompra | null;
  solicitanteId?: string | null;
  dataNecessidadeDe?: string | null;
  dataNecessidadeAte?: string | null;
}

@Injectable({ providedIn: 'root' })
export class RequisicaoCompraService {
  private http = inject(HttpClient);
  private apiUrl = `${environment.apiUrl}/api/v1/compras/requisicoes`;

  listar(filtro: RequisicaoFiltro, page: number = 0, size: number = 10): Observable<any> {
    let params = new HttpParams().set('page', page.toString()).set('size', size.toString());
    Object.entries(filtro).forEach(([key, value]) => {
      if (value !== null && value !== undefined && value !== '') {
        params = params.set(key, value.toString());
      }
    });
    return this.http.get<any>(this.apiUrl, { params });
  }

  buscarPorId(id: string): Observable<RequisicaoCompra> {
    return this.http.get<RequisicaoCompra>(`${this.apiUrl}/${id}`);
  }

  criar(dto: RequisicaoCompraRequest): Observable<RequisicaoCompra> {
    return this.http.post<RequisicaoCompra>(this.apiUrl, dto);
  }

  atualizar(id: string, dto: RequisicaoCompraRequest): Observable<RequisicaoCompra> {
    return this.http.put<RequisicaoCompra>(`${this.apiUrl}/${id}`, dto);
  }

  enviarParaAprovacao(id: string): Observable<RequisicaoCompra> {
    return this.http.post<RequisicaoCompra>(`${this.apiUrl}/${id}/enviar-aprovacao`, {});
  }

  aprovar(id: string): Observable<RequisicaoCompra> {
    return this.http.post<RequisicaoCompra>(`${this.apiUrl}/${id}/aprovar`, {});
  }

  reprovar(id: string, dto: ReprovarRequisicaoRequest): Observable<RequisicaoCompra> {
    return this.http.post<RequisicaoCompra>(`${this.apiUrl}/${id}/reprovar`, dto);
  }

  cancelar(id: string, dto: CancelarRequisicaoRequest): Observable<RequisicaoCompra> {
    return this.http.post<RequisicaoCompra>(`${this.apiUrl}/${id}/cancelar`, dto);
  }

  reabrir(id: string): Observable<RequisicaoCompra> {
    return this.http.post<RequisicaoCompra>(`${this.apiUrl}/${id}/reabrir`, {});
  }
}
