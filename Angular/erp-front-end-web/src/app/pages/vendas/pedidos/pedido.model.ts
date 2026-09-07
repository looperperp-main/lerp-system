export type StatusPedido =
  'ORCAMENTO' | 'BLOQUEADO_CREDITO' | 'CONFIRMADO' | 'EXPEDIDO' | 'FATURADO' | 'CANCELADO';

export type ModalidadeFrete = 'CIF' | 'FOB' | 'SEM_FRETE';

export type TipoItemPedido = 'MERCADORIA' | 'SERVICO';

export interface PedidoItemRequest {
  produtoId: string;
  quantidade: number;
  precoUnitario?: number | null;
  desconto?: number | null;
}

export interface PedidoItemResponse {
  id: string;
  produtoId: string;
  tipoItem: TipoItemPedido;
  quantidade: number;
  precoUnitario: number;
  desconto: number;
  valorTotal: number;
  precoTabela?: number;
  precoManual?: boolean;
  tabelaPrecoId?: string;
  origemPreco?: string;
}

export interface PedidoStatusHistorico {
  id: string;
  statusDe: StatusPedido | null;
  statusPara: StatusPedido;
  motivo?: string;
  createdAt: string;
  createdBy?: string;
}

export interface ParcelaFaturamento {
  numero: number;
  dataVencimento: string;
  valor: number;
  formaPagamento: string;
}

export interface PedidoRequest {
  clienteId: string;
  dataEmissao?: string | null;
  dataValidade?: string | null;
  vendedorId?: string | null;
  condicaoPagamentoId?: string | null;
  modalidadeFrete?: ModalidadeFrete | null;
  observacao?: string | null;
  itens: PedidoItemRequest[];
}

export interface Pedido {
  id?: string;
  tenantId?: number;
  numero?: number;
  status?: StatusPedido;
  clienteId: string;
  vendedorId?: string;
  condicaoPagamentoId?: string;
  transportadoraId?: string;
  depositoId?: string;
  modalidadeFrete?: ModalidadeFrete;
  valorFrete?: number;
  valorItens?: number;
  valorDesconto?: number;
  valorTotal?: number;
  valorTotalNf?: number;
  valorIbs?: number;
  valorCbs?: number;
  valorIs?: number;
  valorIss?: number;
  valorRetencoes?: number;
  dataEmissao?: string;
  dataValidade?: string;
  dataConfirmacao?: string;
  dataExpedicao?: string;
  dataFaturamento?: string;
  dataCancelamento?: string;
  motivoCancelamento?: string;
  observacao?: string;
  createdAt?: string;
  updatedAt?: string;
  createdBy?: string;
  lastUpdatedBy?: string;
  itens?: PedidoItemResponse[];
  historico?: PedidoStatusHistorico[];
  parcelas?: ParcelaFaturamento[];
  _links?: any;
}

export interface ExpedirPedidoRequest {
  depositoId: string;
  transportadoraId?: string | null;
  valorFrete?: number | null;
  modalidadeFrete?: ModalidadeFrete | null;
}

export interface CancelarPedidoRequest {
  motivo: string;
}

export const STATUS_PEDIDO_LABEL: Record<StatusPedido, string> = {
  ORCAMENTO: 'Orçamento',
  BLOQUEADO_CREDITO: 'Bloqueado (Crédito)',
  CONFIRMADO: 'Confirmado',
  EXPEDIDO: 'Expedido',
  FATURADO: 'Faturado',
  CANCELADO: 'Cancelado',
};
