import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Vendedor } from './vendedor.model';
import { environment } from '../../../../environments/environment';

@Injectable({
  providedIn: 'root'
})
export class VendedorService {
  private apiUrl = `${environment.apiUrl}/api/v1/vendedores`;

  constructor(private http: HttpClient) {}

  getAll(page: number = 0, size: number = 10, sort: string = 'nome,asc'): Observable<any> {
    const params = new HttpParams()
      .set('page', page.toString())
      .set('size', size.toString())
      .set('sort', sort);
    return this.http.get<any>(this.apiUrl, { params });
  }

  getById(id: string): Observable<Vendedor> {
    return this.http.get<Vendedor>(`${this.apiUrl}/${id}`);
  }

  create(vendedor: Vendedor): Observable<Vendedor> {
    return this.http.post<Vendedor>(this.apiUrl, vendedor);
  }

  update(id: string, vendedor: Vendedor): Observable<Vendedor> {
    return this.http.put<Vendedor>(`${this.apiUrl}/${id}`, vendedor);
  }

  // Opcional se for carregar as pessoas pelo dropdown via pessoa.service.ts ou diretamente aqui:
  // Assumindo endpoint '/api/v1/pessoas'
  getPessoasDropdown(): Observable<any> {
    return this.http.get<any>(`${environment.apiUrl}/api/v1/pessoas?size=1000`);
  }
}
