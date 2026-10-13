import { inject, Injectable } from '@angular/core';
import { HttpClient, HttpParams, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  CatalogoGrupo,
  ExportLote,
  ExportRequest,
  ImportLote,
  Mapeamento,
  Pagina,
  RelatorioErros,
} from './dados.model';

/** Export e import de dados do tenant (migracao-service, via gateway em /migracao). */
@Injectable({ providedIn: 'root' })
export class DadosService {
  private http = inject(HttpClient);
  private base = `${environment.apiUrl}/migracao`;

  // ── Exportação ──
  catalogo(): Observable<CatalogoGrupo[]> {
    return this.http.get<CatalogoGrupo[]>(`${this.base}/exports/catalogo`);
  }

  solicitarExport(req: ExportRequest): Observable<HttpResponse<ExportLote>> {
    return this.http.post<ExportLote>(`${this.base}/exports`, req, { observe: 'response' });
  }

  listarExports(pagina: number, tamanho: number): Observable<Pagina<ExportLote>> {
    return this.http.get<Pagina<ExportLote>>(`${this.base}/exports`, { params: this.pag(pagina, tamanho) });
  }

  detalharExport(id: string): Observable<ExportLote> {
    return this.http.get<ExportLote>(`${this.base}/exports/${id}`);
  }

  baixarExport(id: string): Observable<HttpResponse<Blob>> {
    return this.http.get(`${this.base}/exports/${id}/arquivo`, { observe: 'response', responseType: 'blob' });
  }

  // ── Importação ──
  listarImports(pagina: number, tamanho: number): Observable<Pagina<ImportLote>> {
    return this.http.get<Pagina<ImportLote>>(`${this.base}/imports`, { params: this.pag(pagina, tamanho) });
  }

  detalharImport(id: string): Observable<ImportLote> {
    return this.http.get<ImportLote>(`${this.base}/imports/${id}`);
  }

  baixarModelo(entidade: string, formato: 'xlsx' | 'csv'): Observable<HttpResponse<Blob>> {
    return this.http.get(`${this.base}/imports/modelo/${entidade}`, {
      params: new HttpParams().set('formato', formato),
      observe: 'response',
      responseType: 'blob',
    });
  }

  enviarArquivo(entidade: string, arquivo: File): Observable<ImportLote> {
    return this.http.post<ImportLote>(`${this.base}/imports`, this.formulario(entidade, arquivo));
  }

  pedirAjuda(entidade: string, arquivo: File | null): Observable<ImportLote> {
    return this.http.post<ImportLote>(`${this.base}/imports/concierge`, this.formulario(entidade, arquivo));
  }

  recusarPrevia(id: string): Observable<ImportLote> {
    return this.http.post<ImportLote>(`${this.base}/imports/${id}/concierge/recusar`, {});
  }

  mapeamento(id: string): Observable<Mapeamento> {
    return this.http.get<Mapeamento>(`${this.base}/imports/${id}/mapeamento`);
  }

  aplicarMapeamento(id: string, mapeamento: Record<string, string>): Observable<ImportLote> {
    return this.http.post<ImportLote>(`${this.base}/imports/${id}/mapeamento`, { mapeamento });
  }

  erros(id: string, pagina: number, tamanho: number): Observable<RelatorioErros> {
    return this.http.get<RelatorioErros>(`${this.base}/imports/${id}/erros`, { params: this.pag(pagina, tamanho) });
  }

  promover(id: string): Observable<ImportLote> {
    return this.http.post<ImportLote>(`${this.base}/imports/${id}/promover`, {});
  }

  /** Entrega o blob ao navegador usando o nome que o servidor mandou em Content-Disposition. */
  salvar(resposta: HttpResponse<Blob>, nomePadrao: string): void {
    const cabecalho = resposta.headers.get('Content-Disposition') ?? '';
    const nome = /filename\*?=(?:UTF-8'')?"?([^";]+)"?/i.exec(cabecalho)?.[1];
    const url = URL.createObjectURL(resposta.body as Blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = nome ? decodeURIComponent(nome) : nomePadrao;
    a.click();
    URL.revokeObjectURL(url);
  }

  private pag(pagina: number, tamanho: number): HttpParams {
    return new HttpParams().set('pagina', pagina).set('tamanho', tamanho);
  }

  private formulario(entidade: string, arquivo: File | null): FormData {
    const f = new FormData();
    f.append('entidade', entidade);
    if (arquivo) f.append('arquivo', arquivo);
    return f;
  }
}
