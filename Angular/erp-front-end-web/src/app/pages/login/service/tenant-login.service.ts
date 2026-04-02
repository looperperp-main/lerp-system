import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import {Observable, of, tap} from 'rxjs';
import { environment } from '../../../../environments/environment';
import { TenantLoginResponse } from '../../../util/types/tenant-login-response.type';

@Injectable({
  providedIn: 'root',
})
export class TenantLoginService {

  private readonly STORAGE_KEYS = {
    TOKEN: 'auth-token',
    REFRESH_TOKEN: 'refresh-token',
    USERNAME: 'username',
    TENANT_ID: 'tenant-id',
    TENANT_NAME: 'tenant-name',
    TENANT_CNPJ: 'tenant-cnpj'
  };

  constructor(private httpClient: HttpClient) {}

  login(cnpj: string, email: string, password: string) {
    return this.httpClient.post<TenantLoginResponse>(
      `${environment.apiUrl}/auth/tenant/login`,
      { cnpj, email, password }
    ).pipe(
      tap((response) => this.storeSession(response))
    );
  }

  private storeSession(response: TenantLoginResponse): void {
    sessionStorage.setItem(this.STORAGE_KEYS.TOKEN, response.token);
    sessionStorage.setItem(this.STORAGE_KEYS.REFRESH_TOKEN, response.refreshToken);
    sessionStorage.setItem(this.STORAGE_KEYS.USERNAME, response.username);
    sessionStorage.setItem(this.STORAGE_KEYS.TENANT_ID, String(response.tenantId));
    sessionStorage.setItem(this.STORAGE_KEYS.TENANT_NAME, response.tenantName);
    sessionStorage.setItem(this.STORAGE_KEYS.TENANT_CNPJ, response.tenantCnpj);
  }

  logout() {
    const refreshToken = sessionStorage.getItem('refresh-token');
    if (!refreshToken) {
      return of(void 0);
    }
    return this.httpClient.post<void>(`${environment.apiUrl}/auth/logout`, { refreshToken });
  }

  isAuthenticated(): boolean {
    return !!sessionStorage.getItem(this.STORAGE_KEYS.TOKEN);
  }

  getToken(): string | null {
    return sessionStorage.getItem(this.STORAGE_KEYS.TOKEN);
  }

  getTenantName(): string | null {
    return sessionStorage.getItem(this.STORAGE_KEYS.TENANT_NAME);
  }

  getUsername(): string | null {
    return sessionStorage.getItem(this.STORAGE_KEYS.USERNAME);
  }
}
