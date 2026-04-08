import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import {Deposito, Page} from './deposito.model';

@Injectable({
  providedIn: 'root'
})
export class DepositoService {
  private http = inject(HttpClient);

  // O API Gateway (ou proxy) deve rotear /api/v1/grupos-clientes para o cadastro-service
  private apiUrl = `${environment.apiUrl}/api/v1/depositos`;

  listar(page: number = 0, size: number = 10): Observable<Page<Deposito>> {
    const params = new HttpParams()
      .set('page', page.toString())
      .set('size', size.toString());
    return this.http.get<Page<Deposito>>(this.apiUrl, { params });
  }

  buscarPorId(id: string): Observable<Deposito> {
    return this.http.get<Deposito>(`${this.apiUrl}/${id}`);
  }

  salvar(grupo: Deposito): Observable<Deposito> {
    if (grupo.id) {
      return this.http.put<Deposito>(`${this.apiUrl}/${grupo.id}`, grupo);
    }
    return this.http.post<Deposito>(this.apiUrl, grupo);
  }
}
