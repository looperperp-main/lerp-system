import { Injectable } from '@angular/core';
import { environment } from '../../../../environments/environment';
import { HttpClient, HttpParams } from '@angular/common/http';
import { ParceirosModel } from './parceiros.model';
import { Observable } from 'rxjs';

@Injectable({
  providedIn: 'root',
})
export class ParceirosService {
  private apiUrl = `${environment.apiUrl}/partner/api/v1/partners`;
  constructor(private http: HttpClient) {}

  getAll(
    page: number = 0,
    size: number = 10,
    sort: string = 'name,asc',
    status?: string,
  ): Observable<any> {
    let params = new HttpParams()
      .set('page', page.toString())
      .set('size', size.toString())
      .set('sort', sort);
    if (status) {
      params = params.set('status', status);
    }
    return this.http.get<any>(this.apiUrl, { params });
  }

  getById(id: string): Observable<ParceirosModel> {
    return this.http.get<ParceirosModel>(`${this.apiUrl}/${id}`);
  }

  update(id: string, parceiro: ParceirosModel): Observable<ParceirosModel> {
    return this.http.put<ParceirosModel>(`${this.apiUrl}/${id}`, parceiro);
  }

  getIndicacoes(): Observable<any[]> {
    return this.http.get<any[]>(`${this.apiUrl}/indicacoes`);
  }

  approve(id: string, payload: { notes?: string | null }): Observable<ParceirosModel> {
    return this.http.patch<ParceirosModel>(`${this.apiUrl}/${id}/approve`, payload);
  }

  reject(id: string, payload: { notes?: string | null }): Observable<ParceirosModel> {
    return this.http.patch<ParceirosModel>(`${this.apiUrl}/${id}/reject`, payload);
  }

  inactivate(id: string): Observable<ParceirosModel> {
    return this.http.patch<ParceirosModel>(`${this.apiUrl}/${id}/inactivate`, {});
  }
}
