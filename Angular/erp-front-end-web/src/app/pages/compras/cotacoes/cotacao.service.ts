import { inject, Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import {
  CancelarCotacaoRequest,
  CotacaoCompra,
  CotacaoCompraRequest,
  CotacaoCompraRespostaRequest,
  DeclinarCotacaoFornecedorRequest,
  EncerrarCotacaoRequest,
  StatusCotacaoCompra,
} from './cotacao.model';

export interface CotacaoFiltro {
  status?: StatusCotacaoCompra | null;
  requisicaoId?: string | null;
}

@Injectable({ providedIn: 'root' })
export class CotacaoCompraService {
  private http = inject(HttpClient);
  private apiUrl = `${environment.apiUrl}/api/v1/compras/cotacoes`;

  listar(filtro: CotacaoFiltro, page: number = 0, size: number = 10): Observable<any> {
    let params = new HttpParams().set('page', page.toString()).set('size', size.toString());
    Object.entries(filtro).forEach(([key, value]) => {
      if (value !== null && value !== undefined && value !== '') {
        params = params.set(key, value.toString());
      }
    });
    return this.http.get<any>(this.apiUrl, { params });
  }

  buscarPorId(id: string): Observable<CotacaoCompra> {
    return this.http.get<CotacaoCompra>(`${this.apiUrl}/${id}`);
  }

  criar(dto: CotacaoCompraRequest): Observable<CotacaoCompra> {
    return this.http.post<CotacaoCompra>(this.apiUrl, dto);
  }

  registrarResposta(
    id: string,
    cotacaoFornecedorId: string,
    dto: CotacaoCompraRespostaRequest,
  ): Observable<CotacaoCompra> {
    return this.http.post<CotacaoCompra>(
      `${this.apiUrl}/${id}/fornecedores/${cotacaoFornecedorId}/responder`,
      dto,
    );
  }

  declinar(
    id: string,
    cotacaoFornecedorId: string,
    dto: DeclinarCotacaoFornecedorRequest,
  ): Observable<CotacaoCompra> {
    return this.http.post<CotacaoCompra>(
      `${this.apiUrl}/${id}/fornecedores/${cotacaoFornecedorId}/declinar`,
      dto,
    );
  }

  encerrar(id: string, dto: EncerrarCotacaoRequest): Observable<CotacaoCompra> {
    return this.http.post<CotacaoCompra>(`${this.apiUrl}/${id}/encerrar`, dto);
  }

  cancelar(id: string, dto: CancelarCotacaoRequest): Observable<CotacaoCompra> {
    return this.http.post<CotacaoCompra>(`${this.apiUrl}/${id}/cancelar`, dto);
  }
}
