import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { ExportAdmin, ExportPagina } from './exportacoes.model';

@Injectable({ providedIn: 'root' })
export class ExportacoesService {
  private readonly base = `${environment.apiUrl}/migracao/admin/exports`;

  constructor(private http: HttpClient) {}

  listar(
    tenantId: number | null,
    status: string,
    pagina: number,
    tamanho: number,
  ): Observable<ExportPagina> {
    let params = new HttpParams().set('pagina', pagina).set('tamanho', tamanho);
    if (tenantId !== null) params = params.set('tenantId', tenantId);
    if (status) params = params.set('status', status);
    return this.http.get<ExportPagina>(this.base, { params });
  }

  detalhar(id: string): Observable<ExportAdmin> {
    return this.http.get<ExportAdmin>(`${this.base}/${id}`);
  }
}
