export interface Fornecedor {
  id: string;
  tenantId?: number;
  pessoaId?: string; // UUID da pessoa vinculada
  ativo?: boolean;
  createdAt?: string;
  updatedAt?: string;
  createdBy?: string;
  lastUpdatedBy?: string;
  _links?: any;
}
