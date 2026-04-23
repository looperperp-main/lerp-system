import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Fornecedor } from './fornecedor.model';
import { environment } from '../../../../environments/environment';

@Injectable({
  providedIn: 'root'
})
export class FornecedorService {
  private apiUrl = `${environment.apiUrl}/api/v1/fornecedores`;

  constructor(private http: HttpClient) {}

  getAll(page: number = 0, size: number = 10): Observable<any> {
    const params = new HttpParams()
      .set('page', page.toString())
      .set('size', size.toString());
    return this.http.get<any>(this.apiUrl, { params });
  }

  getById(id: string): Observable<Fornecedor> {
    return this.http.get<Fornecedor>(`${this.apiUrl}/${id}`);
  }

  create(fornecedor: Fornecedor): Observable<Fornecedor> {
    return this.http.post<Fornecedor>(this.apiUrl, fornecedor);
  }

  update(id: string, fornecedor: Fornecedor): Observable<Fornecedor> {
    return this.http.put<Fornecedor>(`${this.apiUrl}/${id}`, fornecedor);
  }

  updateStatus(id: string): Observable<void> {
    return this.http.patch<void>(`${this.apiUrl}/${id}/status`, {});
  }

  getPessoasDropdown(): Observable<any> {
    return this.http.get<any>(`${environment.apiUrl}/api/v1/pessoas?size=1000`);
  }
}
