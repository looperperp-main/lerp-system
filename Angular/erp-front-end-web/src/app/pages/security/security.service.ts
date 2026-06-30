import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}

export interface RoleModel {
  id?: string;
  name: string;
  tenantId?: number;
  createdDate?: string;
  createdBy?: string;
  lastUpdateDate?: string;
  lastUpdateBy?: string;
}

export interface PermissionModel {
  id?: string;
  code: string;
  domain: string;
  scope?: string;
  description: string;
}

export interface UserModel {
  id?: string;
  tenantId?: string | number;
  email: string;
  displayName: string;
  passwordHash?: string;
  active?: boolean;
  lockedUntil?: string;
  createdBy?: string;
  createdDate?: string;
  lastUpdatedBy?: string;
  lastUpdateDate?: string;
}

/**
 * Endpoints de segurança do portal do tenant (/auth/tenant/security/**).
 * O tenant é resolvido no backend pelo header X-Tenant-Id (injetado pelo gateway) —
 * nenhuma chamada aqui envia tenantId.
 */
@Injectable({ providedIn: 'root' })
export class SecurityService {
  private api = `${environment.apiUrl}/auth/tenant/security`;

  constructor(private http: HttpClient) {}

  // ----- Roles -----
  searchRoles(page: number, size: number, filters: any, sort = 'name,asc'): Observable<PageResponse<RoleModel>> {
    const params = new HttpParams().set('page', page).set('size', size).set('sort', sort);
    return this.http.post<PageResponse<RoleModel>>(`${this.api}/roles/search`, filters, { params });
  }

  listRoles(): Observable<RoleModel[]> {
    return this.http.get<RoleModel[]>(`${this.api}/roles`);
  }

  createRole(name: string): Observable<RoleModel> {
    return this.http.post<RoleModel>(`${this.api}/roles`, { name });
  }

  deleteRole(id: string): Observable<void> {
    return this.http.delete<void>(`${this.api}/roles/${id}`);
  }

  getRolePermissions(roleId: string): Observable<PermissionModel[]> {
    return this.http.get<PermissionModel[]>(`${this.api}/roles/${roleId}/permissions`);
  }

  assignPermissions(roleId: string, permissionIds: string[]): Observable<void> {
    return this.http.post<void>(`${this.api}/roles/${roleId}/permissions`, { roleId, permissionIds });
  }

  removePermission(roleId: string, permissionId: string): Observable<void> {
    return this.http.delete<void>(`${this.api}/roles/${roleId}/permissions/${permissionId}`);
  }

  // ----- Permissions (apenas scope TENANT) -----
  listPermissions(): Observable<PageResponse<PermissionModel>> {
    const params = new HttpParams().set('page', 0).set('size', 500).set('sort', 'domain,asc');
    return this.http.get<PageResponse<PermissionModel>>(`${this.api}/permissions`, { params });
  }

  // ----- Users -----
  searchUsers(page: number, size: number, filters: any, sort = 'displayName,asc'): Observable<PageResponse<UserModel>> {
    const params = new HttpParams().set('page', page).set('size', size).set('sort', sort);
    return this.http.post<PageResponse<UserModel>>(`${this.api}/users/search`, filters, { params });
  }

  createUser(user: { email: string; displayName: string; passwordHash: string }): Observable<UserModel> {
    return this.http.post<UserModel>(`${this.api}/users`, user);
  }

  updateUser(userId: string, user: { email: string; displayName: string; passwordHash?: string }): Observable<UserModel> {
    return this.http.put<UserModel>(`${this.api}/users/${userId}`, user);
  }

  updateUserStatus(userId: string): Observable<void> {
    return this.http.patch<void>(`${this.api}/users/${userId}/status`, null);
  }

  // ----- User <-> Roles -----
  getUserRoles(userId: string): Observable<RoleModel[]> {
    return this.http.get<RoleModel[]>(`${this.api}/users/${userId}/roles`);
  }

  assignRoles(userId: string, roleIds: string[]): Observable<void> {
    return this.http.post<void>(`${this.api}/users/${userId}/roles`, { userId, roleIds });
  }
}