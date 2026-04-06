export interface GrupoCliente {
  id?: string;
  nome: string;
  descricao?: string;
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
