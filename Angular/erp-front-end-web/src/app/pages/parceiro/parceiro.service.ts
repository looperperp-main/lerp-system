import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface PartnerRequest {
  name: string;
  crc: string | null;
  cnpj: string;
  email: string;
  phone: string | null;
}

@Injectable({ providedIn: 'root' })
export class ParceiroService {
  private readonly url = `${environment.apiUrl}/billing/api/v1/partners`;

  constructor(private http: HttpClient) {}

  cadastrar(payload: PartnerRequest): Observable<unknown> {
    return this.http.post(this.url, payload);
  }
}