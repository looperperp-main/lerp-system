export interface Pessoa {
  id?: string;
  tenantId?: number;
  tipo: 'PF' | 'PJ';
  nomeRazao: string;
  apelidoFantasia?: string;
  documento: string;
  ie?: string;
  im?: string;
  rg?: string;
  dataNascimento?: string;
  email?: string;
  telefone?: string;
  ativo: boolean;
  _links?: any; // HATEOAS Links
}

export interface Endereco {
  id?: string;
  tipo: string;
  logradouro: string;
  numero?: string;
  complemento?: string;
  bairro?: string;
  cidade: string;
  uf: string;
  cep: string;
  ibgeCodigo?: string;
  pais?: string;
  principal: boolean;
}

export interface Contato {
  id?: string;
  nome: string;
  tipo: string;
  cargo?: string;
  email?: string;
  telefone?: string;
  ativo: boolean;
  principal: boolean;
}

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}
