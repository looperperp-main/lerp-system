import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { TabelaPreco } from './tabela-preco.model';
import { environment } from '../../../../environments/environment';

@Injectable({
  providedIn: 'root'
})
export class TabelaPrecoService {
  private apiUrl = `${environment.apiUrl}/api/v1/tabelas-preco`;

  constructor(private http: HttpClient) {}

  getAll(page: number = 0, size: number = 10): Observable<any> {
    const params = new HttpParams()
      .set('page', page.toString())
      .set('size', size.toString());
    return this.http.get<any>(this.apiUrl, { params });
  }

  getById(id: string): Observable<TabelaPreco> {
    return this.http.get<TabelaPreco>(`${this.apiUrl}/${id}`);
  }

  create(tabelaPreco: TabelaPreco): Observable<TabelaPreco> {
    return this.http.post<TabelaPreco>(this.apiUrl, tabelaPreco);
  }

  update(id: string, tabelaPreco: TabelaPreco): Observable<TabelaPreco> {
    return this.http.put<TabelaPreco>(`${this.apiUrl}/${id}`, tabelaPreco);
  }

  updateStatus(id: string): Observable<void> {
    return this.http.patch<void>(`${this.apiUrl}/${id}/status`, {});
  }
}
