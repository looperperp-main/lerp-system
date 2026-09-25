export interface CertificadoDigital {
  id: string;
  emitenteId: string;
  cnpjSubject: string;
  certificadoValidoAte: string;
  ativo: boolean;
}

export type ModeloDocumentoFiscal = 'NFE' | 'NFCE' | 'CTE' | 'NFSE' | 'NFCOM' | 'NF3E';
export type StatusCredenciamento = 'PENDENTE' | 'CREDENCIADO' | 'BLOQUEADO';

export interface CredenciamentoSefaz {
  id?: string;
  emitenteId?: string;
  uf: string;
  modelo: ModeloDocumentoFiscal;
  status: StatusCredenciamento;
  dataCredenciamento?: string | null;
}
