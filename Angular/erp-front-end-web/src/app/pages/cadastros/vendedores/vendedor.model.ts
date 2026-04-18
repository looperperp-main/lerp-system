export interface Vendedor {
  id: string;
  tenantId?: number;
  pessoaId?: string; // UUID da pessoa vinculada
  nome: string;
  comissaoPercentual?: number;
  ativo?: boolean;
  createdAt?: string;
  updatedAt?: string;
  createdBy?: string;
  lastUpdatedBy?: string;
  _links?: any;
}
