export type StatusCotacaoCompra = 'ABERTA' | 'ENCERRADA' | 'CANCELADA';

export type StatusCotacaoCompraFornecedor = 'AGUARDANDO' | 'RESPONDIDA' | 'DECLINADA';

export interface CotacaoCompraItemRequest {
  produtoId: string;
  quantidade: number;
}

export interface CotacaoCompraItemResponse {
  id: string;
  produtoId: string;
  quantidade: number;
}

export interface CotacaoCompraFornecedorItemResponse {
  cotacaoItemId: string;
  produtoId: string;
  quantidade: number;
  precoUnitario: number;
  valorTotal: number;
}

export interface CotacaoCompraFornecedorResponse {
  id: string;
  fornecedorId: string;
  status: StatusCotacaoCompraFornecedor;
  condicaoPagamentoId?: string;
  prazoEntregaDias?: number;
  valorFrete?: number;
  observacao?: string;
  valorTotalOfertado?: number;
  ordemSugerida?: number;
  itens: CotacaoCompraFornecedorItemResponse[];
}

export interface CompraStatusHistorico {
  id: string;
  documentoTipo?: string;
  documentoId?: string;
  statusAnterior?: string | null;
  statusNovo: string;
  motivo?: string;
  usuarioId?: string;
  ocorridoEm: string;
}

export interface CotacaoCompraRequest {
  requisicaoId?: string | null;
  depositoId: string;
  dataLimiteResposta?: string | null;
  fornecedorIds: string[];
  itens?: CotacaoCompraItemRequest[];
}

export interface CotacaoCompra {
  id?: string;
  numero?: number;
  requisicaoId?: string;
  status?: StatusCotacaoCompra;
  dataLimiteResposta?: string;
  depositoId?: string;
  cotacaoFornecedorVencedorId?: string;
  quantidadeFornecedoresConvidados?: number;
  createdAt?: string;
  updatedAt?: string;
  createdBy?: string;
  lastUpdatedBy?: string;
  itens?: CotacaoCompraItemResponse[];
  fornecedores?: CotacaoCompraFornecedorResponse[];
  historico?: CompraStatusHistorico[];
}

export interface CotacaoCompraRespostaItemRequest {
  cotacaoItemId: string;
  precoUnitario: number;
}

export interface CotacaoCompraRespostaRequest {
  condicaoPagamentoId: string;
  prazoEntregaDias?: number | null;
  valorFrete?: number | null;
  observacao?: string | null;
  itens: CotacaoCompraRespostaItemRequest[];
}

export interface DeclinarCotacaoFornecedorRequest {
  motivo?: string | null;
}

export interface EncerrarCotacaoRequest {
  cotacaoFornecedorVencedorId: string;
}

export interface CancelarCotacaoRequest {
  motivo: string;
}

export const STATUS_COTACAO_LABEL: Record<StatusCotacaoCompra, string> = {
  ABERTA: 'Aberta',
  ENCERRADA: 'Encerrada',
  CANCELADA: 'Cancelada',
};

export const STATUS_COTACAO_FORNECEDOR_LABEL: Record<StatusCotacaoCompraFornecedor, string> = {
  AGUARDANDO: 'Aguardando Resposta',
  RESPONDIDA: 'Respondida',
  DECLINADA: 'Declinada',
};
