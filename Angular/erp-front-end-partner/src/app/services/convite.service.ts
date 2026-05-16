import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface ConviteDTO {
  referralId: string;
  cnpj: string;
  razaoSocial: string;
  emailContato: string;
  status: 'CONVIDADO' | 'ATIVADO' | 'CONVERTIDO' | 'PERDIDO';
  followupAttempts: number;
  invitedAt: string;
  tokenExpiresAt: string | null;
}

export interface SpringPage<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}

@Injectable({ providedIn: 'root' })
export class ConviteService {
  private readonly http = inject(HttpClient);
  private readonly API = 'http://localhost:8090/partner/api/v1/partners/me/convites';

  listar(page = 0, size = 50): Observable<SpringPage<ConviteDTO>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<SpringPage<ConviteDTO>>(this.API, { params });
  }

  reenviar(referralId: string): Observable<ConviteDTO> {
    return this.http.post<ConviteDTO>(`${this.API}/${referralId}/reenviar`, {});
  }
}