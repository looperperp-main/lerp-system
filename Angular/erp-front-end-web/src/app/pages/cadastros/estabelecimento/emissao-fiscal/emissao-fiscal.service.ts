import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../../environments/environment';
import { CertificadoDigital, CredenciamentoSefaz } from './emissao-fiscal.model';

@Injectable({ providedIn: 'root' })
export class EmissaoFiscalService {
  private http = inject(HttpClient);

  // emitenteId/cnpjEmitente: identificador neutro exigido pelo contrato do emissao-fiscal-service
  // (spec §2, "vendável separadamente") — aqui é sempre o estabelecimentoId do erp-vsd, mas o
  // serviço fiscal não sabe disso.
  uploadCertificado(
    emitenteId: string,
    cnpjEmitente: string,
    senha: string,
    arquivo: File,
  ): Observable<CertificadoDigital> {
    const formData = new FormData();
    formData.append('cnpjEmitente', cnpjEmitente);
    formData.append('senha', senha);
    formData.append('arquivo', arquivo);
    return this.http.post<CertificadoDigital>(
      `${environment.apiUrl}/emissao/certificados/${emitenteId}`,
      formData,
    );
  }

  listarCredenciamentos(emitenteId: string): Observable<CredenciamentoSefaz[]> {
    return this.http.get<CredenciamentoSefaz[]>(
      `${environment.apiUrl}/emissao/credenciamentos/${emitenteId}`,
    );
  }

  salvarCredenciamento(
    emitenteId: string,
    credenciamento: CredenciamentoSefaz,
  ): Observable<CredenciamentoSefaz> {
    return this.http.post<CredenciamentoSefaz>(
      `${environment.apiUrl}/emissao/credenciamentos/${emitenteId}`,
      credenciamento,
    );
  }
}
