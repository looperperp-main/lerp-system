import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../../environments/environment';
import {PermissionModel} from '../../../cadastros/permission/permission.model';

export interface RolePermissionRequest {
  roleId: string;
  permissionIds: string[];
}

@Injectable({
  providedIn: 'root'
})
export class RolePermissionsService {

  private apiUrl = `${environment.apiUrl}/auth/roles`;

  constructor(private http: HttpClient) { }

  // Busca as permissões que a Role JÁ POSSUI
  getPermissionsByRole(roleId: string): Observable<PermissionModel[]> {
    return this.http.get<PermissionModel[]>(`${this.apiUrl}/${roleId}/permissions`);
  }

  // Atribui múltiplas permissões a uma Role
  assignPermissionsToRole(roleId: string, request: RolePermissionRequest): Observable<void> {
    return this.http.post<void>(`${this.apiUrl}/${roleId}/permissions`, request);
  }

  // Remove uma permissão específica
  removePermissionFromRole(roleId: string, permissionId: string): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${roleId}/permissions/${permissionId}`);
  }
}
