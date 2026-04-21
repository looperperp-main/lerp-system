import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ProdutoCat } from './produto-categoria.model';
import {environment} from '../../../../environments/environment';

@Injectable({
  providedIn: 'root'
})
export class ProdutoCategoriaService {
  private apiUrl = `${environment.apiUrl}/api/v1/produtos/categorias`;
  private http = inject(HttpClient);

  getAll(page: number = 0, size: number = 10, sort: string = 'createdAt,desc'): Observable<any> {
    const params = new HttpParams()
      .set('page', page.toString())
      .set('size', size.toString())
      .set('sort', sort);
    return this.http.get<any>(this.apiUrl, { params });
  }

  getById(id: string): Observable<ProdutoCat> {
    return this.http.get<ProdutoCat>(`${this.apiUrl}/${id}`);
  }

  create(categoria: ProdutoCat): Observable<ProdutoCat> {
    return this.http.post<ProdutoCat>(this.apiUrl, categoria);
  }

  update(id: string, categoria: ProdutoCat): Observable<ProdutoCat> {
    return this.http.put<ProdutoCat>(`${this.apiUrl}/${id}`, categoria);
  }

  updateStatus(id: string): Observable<any> {
    return this.http.patch(`${this.apiUrl}/${id}/status`, {});
  }
}
