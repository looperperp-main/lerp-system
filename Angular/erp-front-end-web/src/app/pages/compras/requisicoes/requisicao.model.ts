export type StatusRequisicaoCompra =
  | 'RASCUNHO'
  | 'PENDENTE_APROVACAO'
  | 'APROVADA'
  | 'REPROVADA'
  | 'EM_COTACAO'
  | 'ATENDIDA'
  | 'CANCELADA';

export interface RequisicaoItemRequest {
  produtoId: string;
  quantidade: number;
  observacao?: string | null;
}

export interface RequisicaoItemResponse {
  id: string;
  produtoId: string;
  quantidade: number;
  observacao?: string;
}

export interface RequisicaoStatusHistorico {
  id: string;
  statusAnterior?: string | null;
  statusNovo: string;
  motivo?: string;
  usuarioId?: string;
  ocorridoEm: string;
}

export interface RequisicaoCompraRequest {
  solicitanteId: string;
  depositoId?: string | null;
  justificativa?: string | null;
  dataNecessidade?: string | null;
  itens: RequisicaoItemRequest[];
}

export interface RequisicaoCompra {
  id?: string;
  tenantId?: number;
  numero?: number;
  status?: StatusRequisicaoCompra;
  solicitanteId: string;
  depositoId?: string;
  justificativa?: string;
  dataNecessidade?: string;
  aprovadorId?: string;
  aprovadoEm?: string;
  motivoReprovacao?: string;
  itens?: RequisicaoItemResponse[];
  historico?: RequisicaoStatusHistorico[];
}

export interface ReprovarRequisicaoRequest {
  motivo: string;
}

export interface CancelarRequisicaoRequest {
  motivo: string;
}

export const STATUS_REQUISICAO_LABEL: Record<StatusRequisicaoCompra, string> = {
  RASCUNHO: 'Rascunho',
  PENDENTE_APROVACAO: 'Pendente de Aprovação',
  APROVADA: 'Aprovada',
  REPROVADA: 'Reprovada',
  EM_COTACAO: 'Em Cotação',
  ATENDIDA: 'Atendida',
  CANCELADA: 'Cancelada',
};
