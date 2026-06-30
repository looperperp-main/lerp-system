import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

@Injectable({ providedIn: 'root' })
export class PasswordResetService {
  private api = `${environment.apiUrl}/auth`;

  constructor(private http: HttpClient) {}

  /** Solicita o reset (portal do tenant). Resposta sempre genérica (anti-enumeração). */
  esqueciSenhaTenant(email: string, cnpj: string): Observable<{ message: string }> {
    return this.http.post<{ message: string }>(`${this.api}/tenant/esqueci-senha`, { email, cnpj });
  }

  /** Redefine a senha a partir do token recebido por e-mail. */
  redefinirSenha(
    token: string,
    novaSenha: string,
    confirmacaoSenha: string,
  ): Observable<{ message: string }> {
    return this.http.post<{ message: string }>(`${this.api}/redefinir-senha`, {
      token,
      novaSenha,
      confirmacaoSenha,
    });
  }
}
