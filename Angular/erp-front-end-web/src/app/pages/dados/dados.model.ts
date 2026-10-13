export interface Pagina<T> {
  itens: T[];
  total: number;
  pagina: number;
  tamanho: number;
}

// ───────────────────────── Exportação ─────────────────────────

export interface CatalogoTabela {
  id: string;
  rotulo: string;
  filtraPorPeriodo: boolean;
}

export interface CatalogoGrupo {
  grupo: string;
  rotulo: string;
  tabelas: CatalogoTabela[];
}

export interface ExportEvento {
  acao: string;
  ator: string | null;
  em: string;
}

export interface ExportLote {
  id: string;
  status: string;
  formato: string;
  tabelas: string[];
  dataInicial: string | null;
  dataFinal: string | null;
  motivo: string | null;
  tentativas: number;
  mensagemFalha: string | null;
  linhasPorTabela: Record<string, number> | null;
  criadoEm: string;
  prontoEm: string | null;
  expiraEm: string | null;
  baixadoEm: string | null;
  reaproveitado: boolean;
  eventos: ExportEvento[] | null;
}

export interface ExportRequest {
  tabelas: string[];
  dataInicial: string | null;
  dataFinal: string | null;
  formato: 'XLSX' | 'CSV';
  motivo: string | null;
}

export const EXPORT_MOTIVOS: { label: string; value: string }[] = [
  { label: 'Backup / auditoria', value: 'ROUTINE_BACKUP' },
  { label: 'Auditoria', value: 'AUDIT' },
  { label: 'Cancelamento do contrato', value: 'CANCELLATION' },
  { label: 'Outro', value: 'OTHER' },
];

// ───────────────────────── Importação ─────────────────────────

export interface ImportLote {
  id: string;
  entidade: string;
  status: string;
  trilha: string;
  arquivo: string | null;
  totalLinhas: number;
  linhasValidas: number;
  linhasComErro: number;
  linhasPromovidas: number;
  linhasIgnoradas: number;
  mensagemFalha: string | null;
  colunasIgnoradas: string[] | null;
  criadoEm: string;
  atualizadoEm: string;
  prazoConciergeEm: string | null;
  recusasConcierge: number;
  escalado: boolean;
}

export interface ErroLinha {
  linha: number;
  campo: string;
  mensagem: string;
  valorInformado: string | null;
}

export interface RelatorioErros {
  id: string;
  totalLinhas: number;
  linhasValidas: number;
  linhasComErro: number;
  erros: ErroLinha[];
  pagina: number;
  tamanho: number;
}

export interface CampoAlvo {
  campo: string;
  rotulo: string;
  tipo: string;
  obrigatorio: boolean;
}

export interface Mapeamento {
  id: string;
  entidade: string;
  colunasArquivo: string[];
  campos: CampoAlvo[];
  sugestao: Record<string, string>;
}

export const ENTIDADES_IMPORT: { value: string; label: string; plural: string }[] = [
  { value: 'CUSTOMER', label: 'Clientes', plural: 'clientes' },
  { value: 'PRODUCT', label: 'Produtos', plural: 'produtos' },
];

export const IMPORT_STATUS: Record<string, { rotulo: string; cor: string }> = {
  UPLOADED: { rotulo: 'Validando…', cor: 'azul' },
  VALIDATING: { rotulo: 'Validando…', cor: 'azul' },
  AWAITING_MAPPING: { rotulo: 'Mapear colunas', cor: 'ambar' },
  AWAITING_CONCIERGE: { rotulo: 'Na fila do concierge', cor: 'cinza' },
  AWAITING_FIX: { rotulo: 'Com erros', cor: 'ambar' },
  READY: { rotulo: 'Pronto para importar', cor: 'verde' },
  PROMOTING: { rotulo: 'Importando…', cor: 'azul' },
  DONE: { rotulo: 'Concluído', cor: 'verde' },
  FAILED: { rotulo: 'Falhou', cor: 'vermelho' },
};

export const IMPORT_TRILHA: Record<string, string> = {
  TEMPLATE: 'Modelo',
  SELF_MAPPED: 'Mapeamento',
  CONCIERGE: 'Concierge',
};

/** Estados em que o servidor ainda trabalha: a tela atualiza sozinha enquanto houver algum. */
export const IMPORT_TRANSITORIOS = ['UPLOADED', 'VALIDATING', 'PROMOTING'];
export const EXPORT_TRANSITORIOS = ['REQUESTED', 'PROCESSING'];
