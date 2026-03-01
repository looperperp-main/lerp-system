import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { TenantModel } from './tenant.model';
import {environment} from '../../../../../environments/environment';

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}

@Injectable({
  providedIn: 'root'
})
export class TenantService {

  // Usando a URL base de environments. Se não usar, substitua por '/auth/tenants'
  private apiUrl = `${environment.apiUrl}/auth/tenants`;

  constructor(private http: HttpClient) {}

  /**
   * Obtém a lista paginada de tenants (GET /auth/tenants)
   */
  getTenants(page: number, size: number, sort: string = 'name,asc'): Observable<PageResponse<TenantModel>> {
    let params = new HttpParams()
      .set('page', page.toString())
      .set('size', size.toString())
      .set('sort', sort);

    return this.http.get<PageResponse<TenantModel>>(this.apiUrl, { params });
  }

  /**
   * Obtém a lista paginada de tenants Ativos (GET /auth/tenants)
   */
  getTenantsActive(page: number, size: number, sort: string = 'name,asc'): Observable<PageResponse<TenantModel>> {
    let params = new HttpParams()
      .set('page', page.toString())
      .set('size', size.toString())
      .set('sort', sort);

    return this.http.get<PageResponse<TenantModel>>(`${this.apiUrl}/active`, { params });
  }


  /**
   * Obtém um tenant específico pelo ID (GET /auth/tenants/{id})
   */
  getTenantById(tenantId: number): Observable<TenantModel> {
    return this.http.get<TenantModel>(`${this.apiUrl}/${tenantId}`);
  }

  /**
   * Cria um novo tenant (POST /auth/tenants)
   */
  createTenant(tenant: TenantModel): Observable<TenantModel> {
    return this.http.post<TenantModel>(this.apiUrl, tenant);
  }

  /**
   * Atualiza um tenant existente (PUT /auth/tenants)
   */
  updateTenant(tenant: TenantModel): Observable<TenantModel> {
    return this.http.put<TenantModel>(this.apiUrl, tenant);
  }

  /**
   * Atualiza apenas o status de um tenant (PATCH /auth/tenants/{id}/status)
   * O backend espera uma String pura no body.
   */
  updateTenantStatus(tenantId: number, status: string): Observable<void> {
    return this.http.patch<void>(`${this.apiUrl}/${tenantId}/status`, status);
  }
}
