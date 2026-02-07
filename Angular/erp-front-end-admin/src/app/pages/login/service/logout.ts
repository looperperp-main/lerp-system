import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import {environment} from '../../../../environments/environment';
import { catchError, of } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class LogoutService {
  constructor(private http: HttpClient) {}

  logout() {
    const refreshToken = sessionStorage.getItem('refresh-token');
    if (!refreshToken) {
      return of(void 0);
    }
    return this.http.post<void>(`${environment.apiUrl}/auth/logout`, { refreshToken });
  }
}
