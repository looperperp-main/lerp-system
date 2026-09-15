export type StatusPedidoCompra =
  | 'RASCUNHO'
  | 'PENDENTE_APROVACAO'
  | 'APROVADO'
  | 'REPROVADO'
  | 'ENVIADO'
  | 'RECEBIDO_PARCIAL'
  | 'RECEBIDO_TOTAL'
  | 'ENCERRADO'
  | 'CANCELADO';

export interface PedidoCompraItemRequest {
  produtoId: string;
  quantidade: number;
  precoUnitario: number;
}

export interface PedidoCompraItemResponse {
  id: string;
  produtoId: string;
  quantidade: number;
  precoUnitario: number;
  quantidadeRecebida?: number;
  valorTotal?: number;
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

export interface PedidoCompraRequest {
  fornecedorId: string;
  condicaoPagamentoId: string;
  depositoId: string;
  requisicaoId?: string | null;
  dataPrevisaoEntrega?: string | null;
  valorFrete?: number | null;
  observacao?: string | null;
  itens: PedidoCompraItemRequest[];
}

export interface PedidoCompra {
  id?: string;
  tenantId?: number;
  numero?: number;
  fornecedorId: string;
  condicaoPagamentoId?: string;
  depositoId?: string;
  requisicaoId?: string;
  cotacaoFornecedorId?: string;
  status?: StatusPedidoCompra;
  dataEmissao?: string;
  dataPrevisaoEntrega?: string;
  valorFrete?: number;
  valorTotal?: number;
  aprovadorId?: string;
  aprovadoEm?: string;
  motivoCancelamento?: string;
  observacao?: string;
  createdAt?: string;
  updatedAt?: string;
  createdBy?: string;
  lastUpdatedBy?: string;
  itens?: PedidoCompraItemResponse[];
  historico?: CompraStatusHistorico[];
  _links?: any;
}

export interface ReprovarPedidoCompraRequest {
  motivo: string;
}

export interface CancelarPedidoCompraRequest {
  motivo: string;
}

export interface EncerrarSaldoPedidoCompraRequest {
  motivo: string;
}

export const STATUS_PEDIDO_COMPRA_LABEL: Record<StatusPedidoCompra, string> = {
  RASCUNHO: 'Rascunho',
  PENDENTE_APROVACAO: 'Pendente de Aprovação',
  APROVADO: 'Aprovado',
  REPROVADO: 'Reprovado',
  ENVIADO: 'Enviado ao Fornecedor',
  RECEBIDO_PARCIAL: 'Recebido Parcialmente',
  RECEBIDO_TOTAL: 'Recebido Totalmente',
  ENCERRADO: 'Encerrado',
  CANCELADO: 'Cancelado',
};
