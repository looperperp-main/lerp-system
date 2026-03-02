import {environment} from '../../../../../environments/environment';
import {HttpClient} from '@angular/common/http';
import {RoleModel} from './role.model';
import {Injectable} from '@angular/core';
import {Observable} from 'rxjs';

@Injectable({
  providedIn: 'root'
})
export class RoleService {

  private apiUrl = `${environment.apiUrl}/auth/roles`;

  constructor(private http: HttpClient) {
  }

  getRoles(): Observable<RoleModel[]> {
    return this.http.get<RoleModel[]>(this.apiUrl);
  }

  createRole(role: RoleModel): Observable<RoleModel> {
    return this.http.post<RoleModel>(this.apiUrl, role);
  }

  // Endpoints para gerenciar permissões da Role
  addPermissionToRole(roleId: string, permissionId: string): Observable<void> {
    return this.http.post<void>(`${this.apiUrl}/${roleId}/permissions/${permissionId}`, {});
  }

  getRolePermissions(roleId: string): Observable<any[]> {
    return this.http.get<any[]>(`${this.apiUrl}/${roleId}/permissions`);
  }
}
