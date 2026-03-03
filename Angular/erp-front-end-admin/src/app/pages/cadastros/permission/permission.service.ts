import {environment} from '../../../../environments/environment';
import {Injectable} from '@angular/core';
import {HttpClient, HttpParams} from '@angular/common/http';
import {Observable} from 'rxjs';
import {PermissionModel} from './permission.model';

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
export class PermissionService {

  private apiUrl = `${environment.apiUrl}/auth/permissions`;

  constructor(private http: HttpClient) { }

  getPermissions(page: number, size: number): Observable<PageResponse<PermissionModel>> {
    let params = new HttpParams()
      .set('page', page.toString())
      .set('size', size.toString())
      .set('sort', 'domain,asc');

    return this.http.get<PageResponse<PermissionModel>>(this.apiUrl, { params });
  }

  createPermission(permission: PermissionModel): Observable<PermissionModel> {
    return this.http.post<PermissionModel>(this.apiUrl, permission);
  }

  updatePermission(permission: PermissionModel): Observable<PermissionModel> {
    return this.http.put<PermissionModel>(`${this.apiUrl}/${permission.id}`, permission);
  }

  deletePermission(id: string): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`);
  }
}
