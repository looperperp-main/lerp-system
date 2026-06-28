import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export interface TenantProfile {
  id: number;
  name: string;
  cnpj: string;
  email: string;
  status: string;
  trialStartedAt: string | null;
  trialExpiresAt: string | null;
}

@Injectable({ providedIn: 'root' })
export class TenantService {
  private readonly http = inject(HttpClient);

  /** Perfil do tenant logado. O gateway injeta X-Tenant-Id a partir do JWT. */
  getMe(): Observable<TenantProfile> {
    return this.http.get<TenantProfile>(`${environment.apiUrl}/auth/tenant/me`);
  }
}
