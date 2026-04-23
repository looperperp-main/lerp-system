export interface GrupoClienteTabelaPrecoResponse {
  tabelaPrecoId: string;
  grupoClienteId: string;
  tabelaPrecoNome?: string;
  tenantId?: number;
  _links?: any;
}

export interface GrupoClienteTabelaPrecoRequest {
  tabelaPrecoIds: string[];
}
