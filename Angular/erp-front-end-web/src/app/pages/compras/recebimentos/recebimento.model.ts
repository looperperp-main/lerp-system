export type StatusRecebimentoMercadoria = 'EM_CONFERENCIA' | 'CONFIRMADO' | 'FATURADO' | 'CANCELADO';

export type TipoDocumentoFiscal = 'NFE' | 'NFSE';

export interface RecebimentoItemRequest {
  pedidoItemId: string;
  quantidade: number;
  precoUnitarioNf: number;
}

export interface RecebimentoItemResponse {
  id: string;
  pedidoItemId: string;
  produtoId: string;
  quantidade: number;
  precoUnitarioNf: number;
}

export interface RecebimentoMercadoriaRequest {
  depositoId?: string | null;
  dataRecebimento: string;
  tipoDocumentoFiscal: TipoDocumentoFiscal;
  nfeNumero: string;
  nfeSerie?: string | null;
  nfeChave?: string | null;
  nfseCodigoVerificacao?: string | null;
  nfeDataEmissao: string;
  valorTotalNf: number;
  condicaoPagamentoId?: string | null;
  observacao?: string | null;
  itens: RecebimentoItemRequest[];
}

export interface RecebimentoMercadoria {
  id?: string;
  numero?: number;
  pedidoId: string;
  depositoId?: string;
  status?: StatusRecebimentoMercadoria;
  dataRecebimento?: string;
  tipoDocumentoFiscal?: TipoDocumentoFiscal;
  nfeNumero?: string;
  nfeSerie?: string;
  nfeChave?: string;
  nfseCodigoVerificacao?: string;
  nfeDataEmissao?: string;
  valorTotalNf?: number;
  condicaoPagamentoId?: string;
  impostosIbs?: number;
  impostosCbs?: number;
  impostosIs?: number;
  faturadoEm?: string;
  observacao?: string;
  motivoCancelamento?: string;
  createdAt?: string;
  updatedAt?: string;
  itens?: RecebimentoItemResponse[];
  _links?: any;
}

export interface CancelarRecebimentoRequest {
  motivo?: string | null;
}

export const STATUS_RECEBIMENTO_LABEL: Record<StatusRecebimentoMercadoria, string> = {
  EM_CONFERENCIA: 'Em Conferência',
  CONFIRMADO: 'Confirmado',
  FATURADO: 'Faturado',
  CANCELADO: 'Cancelado',
};
