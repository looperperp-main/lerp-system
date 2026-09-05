import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { map, Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { Estabelecimento } from './estabelecimento.model';

@Injectable({ providedIn: 'root' })
export class EstabelecimentoService {
  private http = inject(HttpClient);

  private apiUrl(pessoaId: string): string {
    return `${environment.apiUrl}/api/v1/pessoas/${pessoaId}/estabelecimentos`;
  }

  buscarPessoaIdProprio(): Observable<string> {
    return this.http
      .get<{ pessoaId: string }>(`${environment.apiUrl}/api/v1/estabelecimentos/proprio`)
      .pipe(map((response) => response.pessoaId));
  }

  listar(pessoaId: string): Observable<Estabelecimento[]> {
    // Endpoint não é paginado (lista de filiais de uma pessoa é sempre pequena) — resposta HATEOAS simples.
    return this.http
      .get<any>(this.apiUrl(pessoaId))
      .pipe(map((response) => (response._embedded ? response._embedded.estabelecimentos : [])));
  }

  salvar(pessoaId: string, estabelecimento: Estabelecimento): Observable<Estabelecimento> {
    if (estabelecimento.id) {
      return this.http.put<Estabelecimento>(
        `${this.apiUrl(pessoaId)}/${estabelecimento.id}`,
        estabelecimento,
      );
    }
    return this.http.post<Estabelecimento>(this.apiUrl(pessoaId), estabelecimento);
  }

  alterarStatus(pessoaId: string, estabelecimento: Estabelecimento): Observable<Estabelecimento> {
    // Sem endpoint dedicado de status (diferente de Depósito) — reaproveita o PUT com ativo invertido.
    return this.http.put<Estabelecimento>(`${this.apiUrl(pessoaId)}/${estabelecimento.id}`, {
      ...estabelecimento,
      ativo: !estabelecimento.ativo,
    });
  }
}
