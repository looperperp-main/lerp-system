import {inject, Injectable} from '@angular/core';
import {HttpClient, HttpParams} from '@angular/common/http';
import {Observable} from 'rxjs';
import {CondPagamento, CondPagamentoParcela, Page} from './cond-pagamento.model';
import {environment} from '../../../../environments/environment';

@Injectable({
  providedIn: 'root'
})
export class CondPagamentoService {
  private http = inject(HttpClient);

  // O API Gateway (ou proxy) deve rotear /api/v1/cond-pagamentos para o cadastro-service
  private apiUrl = `${environment.apiUrl}/api/v1/cond-pagamentos`;

  listar(page: number = 0, size: number = 10): Observable<Page<CondPagamento>> {
    const params = new HttpParams()
      .set('page', page.toString())
      .set('size', size.toString());
    return this.http.get<Page<CondPagamento>>(this.apiUrl, { params });
  }

  buscarPorId(id: string): Observable<CondPagamento> {
    return this.http.get<CondPagamento>(`${this.apiUrl}/${id}`);
  }

  salvar(grupo: CondPagamento): Observable<CondPagamento> {
    if (grupo.id) {
      return this.http.put<CondPagamento>(`${this.apiUrl}/${grupo.id}`, grupo);
    }
    return this.http.post<CondPagamento>(this.apiUrl, grupo);
  }

  // --- Métodos de Parcelas ---

  buscarParcelas(condicaoId: string): Observable<any> {
    return this.http.get<any>(`${this.apiUrl}/${condicaoId}/parcelas`);
  }

  salvarParcelasEmLote(condicaoId: string, parcelas: CondPagamentoParcela[]): Observable<any> {
    return this.http.put<any>(`${this.apiUrl}/${condicaoId}/parcelas`, parcelas);
  }
}
