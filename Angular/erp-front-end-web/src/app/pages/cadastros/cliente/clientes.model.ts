export interface Cliente {
  id?: string;
  tenantId?: number;
  pessoaId: string;
  nome?: string;
  codigoInterno?: string;
  condicaoPagamentoId?: string;
  grupoClienteId?: string;
  vendedorId?: string;
  limiteCredito?: number;
  classificacaoRisco?: string;
  prazoMedioPagamentoDias?: number;
  ativo: boolean;
  createdAt?: string;
  updatedAt?: string;
  createdBy?: string;
  lastUpdatedBy?: string;
  _links?: any;
}
