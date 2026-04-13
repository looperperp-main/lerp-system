export interface CondPagamento {
  id?: string;
  nome: string;
  descricao?: string;
  ativo: boolean;
  createdAt?: string;
  updatedAt?: string;
}

export interface CondPagamentoParcela {
  id?: string;
  condicaoPagamentoId?: string;
  numeroParcela: number;
  diasPrazo: number;
  percentual: number;
  formaPagamento: string;
  _links?: any; // Para HATEOAS
}

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}
