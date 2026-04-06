import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { GrupoCliente, Page } from './grupo-cliente.model';
import { environment } from '../../../../environments/environment';

@Injectable({
  providedIn: 'root'
})
export class GrupoClienteService {
  private http = inject(HttpClient);

  // O API Gateway (ou proxy) deve rotear /api/v1/grupos-clientes para o cadastro-service
  private apiUrl = `${environment.apiUrl}/api/v1/grupos-clientes`;

  listar(page: number = 0, size: number = 10): Observable<Page<GrupoCliente>> {
    const params = new HttpParams()
      .set('page', page.toString())
      .set('size', size.toString());
    return this.http.get<Page<GrupoCliente>>(this.apiUrl, { params });
  }

  buscarPorId(id: string): Observable<GrupoCliente> {
    return this.http.get<GrupoCliente>(`${this.apiUrl}/${id}`);
  }

  salvar(grupo: GrupoCliente): Observable<GrupoCliente> {
    if (grupo.id) {
      return this.http.put<GrupoCliente>(`${this.apiUrl}/${grupo.id}`, grupo);
    }
    return this.http.post<GrupoCliente>(this.apiUrl, grupo);
  }
}
