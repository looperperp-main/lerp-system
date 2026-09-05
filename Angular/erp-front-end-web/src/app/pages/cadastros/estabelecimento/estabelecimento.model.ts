export interface Estabelecimento {
  id?: string;
  tenantId?: number;
  cnpjCompleto: string;
  ordem?: string;
  matriz?: boolean;
  proprio?: boolean;
  ie?: string;
  im?: string;
  ativo: boolean;
  createdAt?: string;
  updatedAt?: string;
}
