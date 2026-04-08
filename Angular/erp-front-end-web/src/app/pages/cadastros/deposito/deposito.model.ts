export interface Deposito {
  id?: string;
  nome: string;
  descricao?: string;
  tipo?: string;
  ativo: boolean;
  createdAt?: string;
  updatedAt?: string;
}

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}
