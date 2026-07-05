import { Injectable } from '@angular/core';
import { environment } from '../../../../environments/environment';
import { Observable } from 'rxjs';
import { HttpClient, HttpParams } from '@angular/common/http';
import { AuditLog } from './auditLog.model';

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}

@Injectable({
  providedIn: 'root',
})
export class AuditService {
  private apiUrl = `${environment.apiUrl}/auth/audits`;

  constructor(private http: HttpClient) {}

  getLogs(page: number, size: number, sort?: string): Observable<PageResponse<AuditLog>> {
    let params = new HttpParams().set('page', page.toString()).set('size', size.toString());
    if (sort) {
      params = params.set('sort', sort);
    }

    return this.http.get<PageResponse<AuditLog>>(this.apiUrl, { params });
  }
}
