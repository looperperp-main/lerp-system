import { Injectable } from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {LoginResponse} from '../../../util/types/login-response.type';
import {tap} from 'rxjs';
import {environment} from '../../../../environments/environment';

@Injectable({
  providedIn: 'root',
})
export class LoginService {
  constructor(private httpClient: HttpClient) {
  }

  login(email: string, password: string) {
    return this.httpClient.post<LoginResponse>(`${environment.apiUrl}/auth/login`, {email, password}).pipe(
      tap((value) => {
          sessionStorage.setItem("auth-token",value.token);
          sessionStorage.setItem("username",value.username);
          sessionStorage.setItem("refresh-token", value.refreshToken);
        }
      )
    );
  }
}
