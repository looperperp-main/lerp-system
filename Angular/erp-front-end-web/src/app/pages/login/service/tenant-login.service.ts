import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, of, tap } from 'rxjs';
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
    TENANT_CNPJ: 'tenant-cnpj',
  };

  constructor(private httpClient: HttpClient) {
    // "Manter conectado": rehidrata a sessão persistida no localStorage para o sessionStorage,
    // que é onde todos os getters/interceptor leem.
    if (
      localStorage.getItem(this.STORAGE_KEYS.TOKEN) &&
      !sessionStorage.getItem(this.STORAGE_KEYS.TOKEN)
    ) {
      for (const key of Object.values(this.STORAGE_KEYS)) {
        const value = localStorage.getItem(key);
        if (value) sessionStorage.setItem(key, value);
      }
    }
  }

  login(cnpj: string, email: string, password: string, rememberMe = false) {
    return this.httpClient
      .post<TenantLoginResponse>(`${environment.apiUrl}/auth/tenant/login`, {
        cnpj,
        email,
        password,
      })
      .pipe(tap((response) => this.storeSession(response, rememberMe)));
  }

  private storeSession(response: TenantLoginResponse, persist: boolean): void {
    const values: Record<string, string> = {
      [this.STORAGE_KEYS.TOKEN]: response.token,
      [this.STORAGE_KEYS.REFRESH_TOKEN]: response.refreshToken,
      [this.STORAGE_KEYS.USERNAME]: response.username,
      [this.STORAGE_KEYS.TENANT_ID]: String(response.tenantId),
      [this.STORAGE_KEYS.TENANT_NAME]: response.tenantName,
      [this.STORAGE_KEYS.TENANT_CNPJ]: response.tenantCnpj,
    };
    for (const [key, value] of Object.entries(values)) {
      sessionStorage.setItem(key, value);
      // Persiste a sessão (nunca a senha) só quando "manter conectado" está marcado.
      if (persist) localStorage.setItem(key, value);
      else localStorage.removeItem(key);
    }
  }

  logout() {
    const refreshToken = sessionStorage.getItem('refresh-token');
    // Encerra a sessão persistida ("manter conectado") para não rehidratar no próximo boot.
    for (const key of Object.values(this.STORAGE_KEYS)) localStorage.removeItem(key);
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
