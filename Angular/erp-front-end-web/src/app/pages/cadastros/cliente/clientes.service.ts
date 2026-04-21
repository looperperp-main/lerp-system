import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Cliente } from './clientes.model';
import { environment } from '../../../../environments/environment';

@Injectable({
  providedIn: 'root'
})
export class ClientesService {
  private http = inject(HttpClient);
  private apiUrl = `${environment.apiUrl}/api/v1/clientes`;

  getAll(page: number = 0, size: number = 10, sort: string = 'createdAt,desc'): Observable<any> {
    const params = new HttpParams()
      .set('page', page.toString())
      .set('size', size.toString())
      .set('sort', sort);
    return this.http.get<any>(this.apiUrl, { params });
  }

  getById(id: string): Observable<Cliente> {
    return this.http.get<Cliente>(`${this.apiUrl}/${id}`);
  }

  create(cliente: Cliente): Observable<Cliente> {
    return this.http.post<Cliente>(this.apiUrl, cliente);
  }

  update(id: string, cliente: Cliente): Observable<Cliente> {
    return this.http.put<Cliente>(`${this.apiUrl}/${id}`, cliente);
  }

  // --- Endpoints de Apoio (Dropdowns) ---
  getPessoasDropdown(): Observable<any> {
    return this.http.get<any>(`${environment.apiUrl}/api/v1/pessoas?size=1000`);
  }

  getCondicoesPagamentoDropdown(): Observable<any> {
    return this.http.get<any>(`${environment.apiUrl}/api/v1/cond-pagamentos?size=1000`);
  }

  getGruposClienteDropdown(): Observable<any> {
    return this.http.get<any>(`${environment.apiUrl}/api/v1/grupos-clientes?size=1000`);
  }

  getVendedoresDropdown(): Observable<any> {
    return this.http.get<any>(`${environment.apiUrl}/api/v1/vendedores?size=1000`);
  }

  updateStatus(id: string): Observable<void> {
    return this.http.patch<void>(`${this.apiUrl}/${id}/status`, {});
  }
}
