export interface Fornecedor {
  id: string;
  tenantId?: number;
  pessoaId?: string;
  pessoaNomeRazao?: string;
  ativo?: boolean;
  createdAt?: string;
  updatedAt?: string;
  createdBy?: string;
  lastUpdatedBy?: string;
  _links?: any;
}
