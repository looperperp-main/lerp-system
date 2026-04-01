import {Injectable} from '@angular/core';
import {environment} from '../../../../../environments/environment';
import {HttpClient, HttpParams} from '@angular/common/http';
import {Observable} from 'rxjs';
import {UserAccountModel, UsersPageModel} from './usersPage.model';

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
export class UserService {
  private apiUrl = `${environment.apiUrl}/auth/users`;

  constructor(private http: HttpClient) {
  }

  getUsers(page: number, size: number): Observable<PageResponse<UsersPageModel>> {
    let params = new HttpParams()
      .set('page', page.toString())
      .set('size', size.toString());

    return this.http.get<PageResponse<UsersPageModel>>(this.apiUrl, { params });
  }

  searchUsers(page: number, size: number, filters: any, sortStr?: string) {
    let params = new HttpParams()
      .set('page', page.toString())
      .set('size', size.toString());

    if(sortStr){
      params = params.set('sort', sortStr);
    }

    return this.http.post<any>(`${this.apiUrl}/search`, filters, { params });
  }

  getActiveUsers(page: number, size: number): Observable<PageResponse<UsersPageModel>> {
    let params = new HttpParams()
      .set('page', page.toString())
      .set('size', size.toString());

    return this.http.get<PageResponse<UsersPageModel>>(`${this.apiUrl}/active`, { params });
  }

  createUser(user: UserAccountModel): Observable<UserAccountModel> {
    return this.http.post<UserAccountModel>(this.apiUrl, user);
  }

  updateUser(userId: string, user: UserAccountModel): Observable<UserAccountModel> {
    return this.http.put<UserAccountModel>(`${this.apiUrl}/${userId}`, user);
  }

  updateStatus(userId: string): Observable<void> {
    return this.http.patch<void>(`${this.apiUrl}/${userId}/status`, null);
  }
}
