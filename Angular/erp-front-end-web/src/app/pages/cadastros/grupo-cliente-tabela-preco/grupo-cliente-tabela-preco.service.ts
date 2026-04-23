import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { GrupoClienteTabelaPrecoRequest, GrupoClienteTabelaPrecoResponse } from './grupo-cliente-tabela-preco.model';

@Injectable({
  providedIn: 'root'
})
export class GrupoClienteTabelaPrecoService {
  private http = inject(HttpClient);
  private apiUrl = `${environment.apiUrl}/api/v1/grupos-clientes`;

  getAssociacoes(grupoClienteId: string): Observable<any> {
    return this.http.get<any>(`${this.apiUrl}/${grupoClienteId}/tabelas-preco`);
  }

  sincronizarAssociacoes(grupoClienteId: string, request: GrupoClienteTabelaPrecoRequest): Observable<void> {
    return this.http.put<void>(`${this.apiUrl}/${grupoClienteId}/tabelas-preco`, request);
  }
}
