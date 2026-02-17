export interface TenantModel {
  id?: number;
  name: string;
  cnpj: string;
  status: 'ATIVO' | 'PENDENTE' | 'SUSPENSO' | 'CANCELADO';
  createdBy?: string;
  creationDate?: string; // Date ISO string
  lastUpdatedBy?: string;
  updateDate?: string; // Date ISO string
}
