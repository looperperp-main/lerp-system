import {environment} from '../../../../../environments/environment';
import {HttpClient, HttpParams} from '@angular/common/http';
import {RoleModel} from './role.model';
import {Injectable} from '@angular/core';
import {Observable} from 'rxjs';

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
export class RoleService {

  private apiUrl = `${environment.apiUrl}/auth/roles`;

  constructor(private http: HttpClient) {
  }

  getRoles(): Observable<RoleModel[]> {
    return this.http.get<RoleModel[]>(this.apiUrl);
  }

  getRolesbyPage(page: number, size: number, sort: string = 'name,asc') : Observable<PageResponse<RoleModel>> {
    let params = new HttpParams()
      .set('page', page.toString())
      .set('size', size.toString())
      .set('sort', sort);
    return this.http.get<PageResponse<RoleModel>>(`${this.apiUrl}/pages`, { params });
  }

  searchRoles(page: number, size: number, filters: any, sort: string = 'name,asc') : Observable<PageResponse<RoleModel>> {
    let params = new HttpParams()
      .set('page', page.toString())
      .set('size', size.toString())
      .set('sort', sort);
    return this.http.post<PageResponse<RoleModel>>(`${this.apiUrl}/search`, filters, { params });
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

  deleteRole(id: string): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}/delete`);
  }
}
