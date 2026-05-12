import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface PartnerLoginResponse {
  username: string;
  token: string;
  refreshToken: string;
}

@Injectable({ providedIn: 'root' })
export class PartnerLoginService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = 'http://localhost:8090';

  login(email: string, password: string): Observable<PartnerLoginResponse> {
    return this.http.post<PartnerLoginResponse>(`${this.apiUrl}/auth/partner/login`, { email, password });
  }
}