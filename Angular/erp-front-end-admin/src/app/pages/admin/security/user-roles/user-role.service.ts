import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../../environments/environment';
import {RoleModel} from '../../../cadastros/roles/roles/role.model';

export interface UserRoleRequest {
  userId: string;
  roleIds: string[];
}

@Injectable({
  providedIn: 'root'
})
export class UserRoleService {

  private apiUrl = `${environment.apiUrl}/auth/users`;

  constructor(private http: HttpClient) { }

  // Busca as Roles que o Usuário JÁ POSSUI
  getRolesByUser(userId: string): Observable<RoleModel[]> {
    return this.http.get<RoleModel[]>(`${this.apiUrl}/${userId}/roles`);
  }

  // Atribui múltiplas Roles a um Usuário
  assignRolesToUser(userId: string, request: UserRoleRequest): Observable<void> {
    return this.http.post<void>(`${this.apiUrl}/${userId}/roles`, request);
  }

  // Remove uma Role específica de um Usuário
  removeRoleFromUser(userId: string, roleId: string): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${userId}/roles/${roleId}`);
  }
}
