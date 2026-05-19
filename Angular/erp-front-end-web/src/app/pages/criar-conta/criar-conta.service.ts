import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { tap } from 'rxjs';
import { environment } from '../../../environments/environment';
import { TenantLoginResponse } from '../../util/types/tenant-login-response.type';

interface CriarContaRequest {
  cnpj: string;
  razaoSocial: string;
  nomeFantasia: string | null;
  email: string;
  senha: string;
  telefone: string | null;
}

@Injectable({ providedIn: 'root' })
export class CriarContaService {
  private readonly STORAGE_KEYS = {
    TOKEN: 'auth-token',
    REFRESH_TOKEN: 'refresh-token',
    USERNAME: 'username',
    TENANT_ID: 'tenant-id',
    TENANT_NAME: 'tenant-name',
    TENANT_CNPJ: 'tenant-cnpj',
  };

  constructor(private http: HttpClient) {}

  criarConta(req: CriarContaRequest) {
    return this.http
      .post<TenantLoginResponse>(`${environment.apiUrl}/auth/criar-conta`, req)
      .pipe(tap(res => this.storeSession(res)));
  }

  private storeSession(res: TenantLoginResponse): void {
    sessionStorage.setItem(this.STORAGE_KEYS.TOKEN, res.token);
    sessionStorage.setItem(this.STORAGE_KEYS.REFRESH_TOKEN, res.refreshToken);
    sessionStorage.setItem(this.STORAGE_KEYS.USERNAME, res.username);
    sessionStorage.setItem(this.STORAGE_KEYS.TENANT_ID, String(res.tenantId));
    sessionStorage.setItem(this.STORAGE_KEYS.TENANT_NAME, res.tenantName);
    sessionStorage.setItem(this.STORAGE_KEYS.TENANT_CNPJ, res.tenantCnpj);
  }
}