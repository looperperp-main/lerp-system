import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Transportadora } from './transportadora.model';
import { environment } from '../../../../environments/environment';

@Injectable({
  providedIn: 'root'
})
export class TransportadoraService {
  private apiUrl = `${environment.apiUrl}/api/v1/transportadoras`;

  constructor(private http: HttpClient) {}

  getAll(page: number = 0, size: number = 10): Observable<any> {
    const params = new HttpParams()
      .set('page', page.toString())
      .set('size', size.toString());
    return this.http.get<any>(this.apiUrl, { params });
  }

  getById(id: string): Observable<Transportadora> {
    return this.http.get<Transportadora>(`${this.apiUrl}/${id}`);
  }

  create(transportadora: Transportadora): Observable<Transportadora> {
    return this.http.post<Transportadora>(this.apiUrl, transportadora);
  }

  update(id: string, transportadora: Transportadora): Observable<Transportadora> {
    return this.http.put<Transportadora>(`${this.apiUrl}/${id}`, transportadora);
  }

  updateStatus(id: string): Observable<void> {
    return this.http.patch<void>(`${this.apiUrl}/${id}/status`, {});
  }

  getPessoasDropdown(): Observable<any> {
    return this.http.get<any>(`${environment.apiUrl}/api/v1/pessoas?size=1000`);
  }
}
