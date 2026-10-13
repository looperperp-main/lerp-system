import { Injectable } from '@angular/core';
import { HttpClient, HttpParams, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';

export interface LoteImport {
  id: string;
  entidade: string;
  status: string;
  trilha: string;
  arquivo: string | null;
  totalLinhas: number;
  linhasValidas: number;
  linhasComErro: number;
  mensagemFalha: string | null;
  criadoEm: string;
  prazoConciergeEm: string | null;
  recusasConcierge: number;
  escalado: boolean;
}

export interface ItemFila {
  tenantId: number;
  lote: LoteImport;
  prazoVencido: boolean;
}

export interface OnboardingSla {
  tenantId: number;
  iniciadoEm: string;
  concluidoEm: string | null;
  horasDecorridas: number;
  entidadesConcluidas: string[];
  atrasado: boolean;
}

export interface PainelSla {
  filaConcierge: number;
  prazosVencidos: number;
  escalados: number;
  fila: ItemFila[];
  onboardings: OnboardingSla[];
}

export interface PaginaFila {
  itens: ItemFila[];
  total: number;
  pagina: number;
  tamanho: number;
}

/** Suporte da plataforma na Trilha 3 (concierge): fila, painel de SLA, arquivo do cliente e envio da prévia. */
@Injectable({ providedIn: 'root' })
export class ImportacoesService {
  private readonly base = `${environment.apiUrl}/migracao/admin/imports`;

  constructor(private http: HttpClient) {}

  fila(pagina: number, tamanho: number): Observable<PaginaFila> {
    return this.http.get<PaginaFila>(this.base, {
      params: new HttpParams().set('pagina', pagina).set('tamanho', tamanho),
    });
  }

  sla(): Observable<PainelSla> {
    return this.http.get<PainelSla>(`${this.base}/sla`);
  }

  arquivoDoCliente(id: string): Observable<HttpResponse<Blob>> {
    return this.http.get(`${this.base}/${id}/arquivo`, { observe: 'response', responseType: 'blob' });
  }

  enviarPrevia(id: string, arquivo: File): Observable<ItemFila> {
    const f = new FormData();
    f.append('arquivo', arquivo);
    return this.http.post<ItemFila>(`${this.base}/${id}/previa`, f);
  }
}
