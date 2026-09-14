export type CodigoRegimeTributario =
  'SIMPLES_NACIONAL' | 'SIMPLES_EXCESSO' | 'REGIME_NORMAL' | 'MEI';

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
  crt: CodigoRegimeTributario;
  createdAt?: string;
  updatedAt?: string;
}
