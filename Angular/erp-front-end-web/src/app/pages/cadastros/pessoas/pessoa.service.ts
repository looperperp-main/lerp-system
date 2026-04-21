import {inject, Injectable} from '@angular/core';
import {HttpClient, HttpParams} from '@angular/common/http';
import {environment} from '../../../../environments/environment';
import {map, Observable} from 'rxjs';
import {Contato, Endereco, Page, Pessoa} from './pessoa.model';

@Injectable({ providedIn: 'root'}) //Means it's a singleton app-wide
export class PessoaService {
  private http = inject(HttpClient);
  private readonly apiUrl = `${environment.apiUrl}/api/v1/pessoas`;
  listar(page: number = 0, size: number = 10): Observable<Page<Pessoa>> {
    let params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<any>(this.apiUrl, { params }).pipe(
      map(response => {
        // Mapeia o formato HATEOAS (_embedded) para um formato mais fácil de usar no componente
        return {
          content: response._embedded ? response._embedded.pessoas : [],
          totalElements: response.page?.totalElements || 0,
          totalPages: response.page?.totalPages || 0,
          size: response.page?.size || size,
          number: response.page?.number || page
        };
      })
    );
  }

  obterPorId(id: string): Observable<Pessoa> {
    return this.http.get<Pessoa>(`${this.apiUrl}/${id}`);
  }

  criar(pessoa: Pessoa): Observable<Pessoa> {
    return this.http.post<Pessoa>(this.apiUrl, pessoa);
  }

  atualizar(id: string, pessoa: Pessoa): Observable<Pessoa> {
    return this.http.put<Pessoa>(`${this.apiUrl}/${id}`, pessoa);
  }

  // Função auxiliar para reescrever o link HATEOAS passando pelo Gateway
  private fixHateoasUrl(url: string): string {
    const urlObj = new URL(url);
    // Retorna a URL base do seu environment (Gateway) + o path que veio do HATEOAS
    return `${environment.apiUrl}${urlObj.pathname}${urlObj.search}`;
  }

  // --- MÉTODOS PARA ENDEREÇO ---
  listarEnderecos(url: string): Observable<any> {
    return this.http.get<any>(this.fixHateoasUrl(url));
  }

  salvarEndereco(url: string, endereco: Endereco): Observable<Endereco> {
    const fixedUrl = this.fixHateoasUrl(url);
    if (endereco.id) {
      return this.http.put<Endereco>(`${fixedUrl}/${endereco.id}`, endereco);
    }
    return this.http.post<Endereco>(fixedUrl, endereco);
  }

  // --- MÉTODOS PARA CONTATO ---
  listarContatos(url: string): Observable<any> {
    return this.http.get<any>(this.fixHateoasUrl(url));
  }

  salvarContato(url: string, contato: Contato): Observable<Contato> {
    const fixedUrl = this.fixHateoasUrl(url);
    if (contato.id) {
      return this.http.put<Contato>(`${fixedUrl}/${contato.id}`, contato);
    }
    return this.http.post<Contato>(fixedUrl, contato);
  }

  // --- BUSCA DE CEP ---
  buscarCep(cep: string): Observable<any> {
    // Busca do endpoint criado no nosso backend (passa pelo Gateway/API)
    return this.http.get<any>(`${environment.apiUrl}/api/v1/cep/${cep}`);
  }
}
