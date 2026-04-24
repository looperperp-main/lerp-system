export interface ProdutoPrecoDTO {
  id?: string;
  tenantId?: number;
  tabelaPrecoId: string;
  preco: number;
  inicioVigencia: string;
  fimVigencia?: string;
  createdAt?: string;
  updatedAt?: string;
  createdBy?: string;
  lastUpdatedBy?: string;
}

export interface ProdutoFornecedorDTO {
  id?: string;
  tenantId?: number;
  fornecedorId: string;
  codigoProdutoFornecedor?: string;
  precoCusto?: number;
  leadTimeDias?: number;
  preferencial: boolean;
  ativo: boolean;
  createdAt?: string;
  updatedAt?: string;
  createdBy?: string;
  lastUpdatedBy?: string;
}

export interface ProdutoEstoqueConfigDTO {
  id?: string;
  tenantId?: number;
  depositoId: string;
  fornecedorPreferencialId?: string;
  estoqueMinimo?: number;
  estoqueMaximo?: number;
  pontoReposicao?: number;
  leadTimeDias?: number;
  createdAt?: string;
  updatedAt?: string;
  createdBy?: string;
  lastUpdatedBy?: string;
}

export interface Produto {
  id?: string;
  tenantId?: number;
  categoriaId?: string;
  sku: string;
  codigoExterno?: string;
  nome: string;
  descricao?: string;
  unidade: string;
  unidadeSecundaria?: string;
  fatorConversao?: number;
  ncm?: string;
  ean?: string;
  cest?: string;
  origem?: string;
  ativo: boolean;
  createdAt?: string;
  updatedAt?: string;
  createdBy?: string;
  lastUpdatedBy?: string;

  precos?: ProdutoPrecoDTO[];
  fornecedores?: ProdutoFornecedorDTO[];
  estoqueConfigs?: ProdutoEstoqueConfigDTO[];

  _links?: any;
}
