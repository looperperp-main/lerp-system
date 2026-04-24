import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { Produto } from './produto.model';

@Injectable({
  providedIn: 'root'
})
export class ProdutoService {
  private readonly apiUrl = `${environment.apiUrl}/api/v1/produtos`;
  private readonly categoriasUrl = `${environment.apiUrl}/api/v1/produtos/categorias`;
  private readonly fornecedoresUrl = `${environment.apiUrl}/api/v1/fornecedores`;
  private readonly tabelasPrecoUrl = `${environment.apiUrl}/api/v1/tabelas-preco`;
  private readonly depositosUrl = `${environment.apiUrl}/api/v1/depositos`;

  constructor(private http: HttpClient) {}

  getAll(page: number, size: number): Observable<any> {
    const params = new HttpParams()
      .set('page', page.toString())
      .set('size', size.toString());
    return this.http.get<any>(this.apiUrl, { params });
  }

  getById(id: string): Observable<Produto> {
    return this.http.get<Produto>(`${this.apiUrl}/${id}`);
  }

  create(produto: Produto): Observable<Produto> {
    return this.http.post<Produto>(this.apiUrl, produto);
  }

  update(id: string, produto: Produto): Observable<Produto> {
    return this.http.put<Produto>(`${this.apiUrl}/${id}`, produto);
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`);
  }

  getDepositosDropdown(): Observable<any> {
    const params = new HttpParams().set('page', '0').set('size', '1000');
    return this.http.get<any>(this.depositosUrl, { params });
  }

  // Endpoints para alimentar os p-select
  getCategoriasDropdown(): Observable<any> {
    const params = new HttpParams().set('page', '0').set('size', '1000');
    return this.http.get<any>(`${this.categoriasUrl}/ativas`, { params });
  }

  getFornecedoresDropdown(): Observable<any> {
    const params = new HttpParams().set('page', '0').set('size', '1000');
    return this.http.get<any>(this.fornecedoresUrl, { params });
  }

  getTabelasPrecoDropdown(): Observable<any> {
    const params = new HttpParams().set('page', '0').set('size', '1000');
    return this.http.get<any>(this.tabelasPrecoUrl, { params });
  }
}
