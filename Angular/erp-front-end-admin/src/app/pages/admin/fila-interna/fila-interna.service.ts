import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { FilaInternaItem, FilaPage, UpdateStatusRequest } from './fila-interna.model';

@Injectable({ providedIn: 'root' })
export class FilaInternaService {
  private readonly base = `${environment.apiUrl}/auth/api/v1/syax-queue`;

  constructor(private http: HttpClient) {}

  listar(status: string, page: number, size: number): Observable<FilaPage> {
    let params = new HttpParams().set('page', page).set('size', size).set('sort', 'createdAt,desc');
    if (status) params = params.set('status', status);
    return this.http.get<FilaPage>(this.base, { params });
  }

  atualizarStatus(id: number, req: UpdateStatusRequest): Observable<FilaInternaItem> {
    return this.http.patch<FilaInternaItem>(`${this.base}/${id}/status`, req);
  }
}