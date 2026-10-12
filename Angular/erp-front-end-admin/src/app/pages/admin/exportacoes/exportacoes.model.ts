export interface ExportEvento {
  acao: string;
  ator: string | null;
  em: string;
}

export interface ExportResumo {
  id: string;
  status: string;
  formato: string;
  tabelas: string[];
  dataInicial: string | null;
  dataFinal: string | null;
  motivo: string | null;
  solicitadoPor: string;
  tentativas: number;
  mensagemFalha: string | null;
  linhasPorTabela: Record<string, number> | null;
  criadoEm: string;
  prontoEm: string | null;
  expiraEm: string | null;
  baixadoEm: string | null;
  eventos: ExportEvento[] | null;
}

/** Visão de suporte: só metadados. A API não devolve token nem caminho de arquivo, e não há download. */
export interface ExportAdmin {
  tenantId: number;
  exportacao: ExportResumo;
}

export interface ExportPagina {
  itens: ExportAdmin[];
  total: number;
  pagina: number;
  tamanho: number;
}
