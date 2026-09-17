export type TipoMovimentoEstoque =
  | 'ENTRADA_COMPRA'
  | 'ESTORNO_ENTRADA_COMPRA'
  | 'SAIDA_VENDA'
  | 'ESTORNO_SAIDA_VENDA'
  | 'AJUSTE_ENTRADA'
  | 'AJUSTE_SAIDA'
  | 'SAIDA_CONSUMO'
  | 'SAIDA_PRODUCAO'
  | 'ENTRADA_PRODUCAO';

export type OrigemMovimentoEstoque =
  'PEDIDO_VENDA' | 'RECEBIMENTO' | 'AJUSTE' | 'INVENTARIO' | 'CONSUMO' | 'PRODUCAO';

export type TipoAjusteEstoque =
  | 'SALDO_INICIAL'
  | 'INVENTARIO'
  | 'AVARIA'
  | 'PERDA_QUEBRA'
  | 'FURTO_ROUBO'
  | 'BONIFICACAO_RECEBIDA'
  | 'AMOSTRA_BRINDE'
  | 'ERRO_LANCAMENTO';

export interface EstoqueSaldo {
  produtoId: string;
  depositoId: string;
  quantidade: number;
  custoMedio?: number | null;
  estoqueMinimo?: number | null;
  abaixoMinimo?: boolean | null;
  atualizadoEm?: string;
}

export interface MovimentoEstoque {
  id: string;
  produtoId: string;
  depositoId: string;
  tipo: TipoMovimentoEstoque;
  origemTipo: OrigemMovimentoEstoque;
  origemId?: string | null;
  quantidade: number;
  valorUnitario?: number | null;
  motivo?: string | null;
  tipoAjuste?: TipoAjusteEstoque | null;
  documentoReferencia?: string | null;
  centroCustoId?: string | null;
  usuarioId?: string;
  ocorridoEm: string;
}

export interface AjusteEstoqueRequest {
  produtoId: string;
  depositoId: string;
  quantidadeContada: number;
  origem: OrigemMovimentoEstoque;
  tipoAjuste?: TipoAjusteEstoque | null;
  motivo?: string | null;
  documentoReferencia?: string | null;
  valorUnitario?: number | null;
  permitirSaldoNegativo?: boolean;
}

export interface ConsumoEstoqueRequest {
  produtoId: string;
  depositoId: string;
  quantidade: number;
  centroCustoId: string;
  motivo?: string | null;
  permitirSaldoNegativo?: boolean;
}

export type PendenciaTipoEstoque = 'REGULARIZACAO' | 'APONTAR_PRODUCAO';

export interface PendenciaEstoque {
  id: string;
  produtoId: string;
  depositoId: string;
  tipo: PendenciaTipoEstoque;
  resolvida: boolean;
  criadaEm: string;
}

export const TIPO_MOVIMENTO_LABEL: Record<TipoMovimentoEstoque, string> = {
  ENTRADA_COMPRA: 'Entrada por compra',
  ESTORNO_ENTRADA_COMPRA: 'Estorno de entrada',
  SAIDA_VENDA: 'Saída por venda',
  ESTORNO_SAIDA_VENDA: 'Estorno de saída',
  AJUSTE_ENTRADA: 'Ajuste (entrada)',
  AJUSTE_SAIDA: 'Ajuste (saída)',
  SAIDA_CONSUMO: 'Saída por consumo',
  SAIDA_PRODUCAO: 'Saída por produção',
  ENTRADA_PRODUCAO: 'Entrada por produção',
};

export const ORIGEM_MOVIMENTO_LABEL: Record<OrigemMovimentoEstoque, string> = {
  PEDIDO_VENDA: 'Pedido de venda',
  RECEBIMENTO: 'Recebimento de mercadoria',
  AJUSTE: 'Ajuste manual',
  INVENTARIO: 'Inventário',
  CONSUMO: 'Requisição de consumo',
  PRODUCAO: 'Ordem de produção',
};

export const TIPO_AJUSTE_LABEL: Record<TipoAjusteEstoque, string> = {
  SALDO_INICIAL: 'Saldo inicial',
  INVENTARIO: 'Inventário',
  AVARIA: 'Avaria',
  PERDA_QUEBRA: 'Perda/quebra',
  FURTO_ROUBO: 'Furto/roubo',
  BONIFICACAO_RECEBIDA: 'Bonificação recebida',
  AMOSTRA_BRINDE: 'Amostra/brinde',
  ERRO_LANCAMENTO: 'Erro de lançamento',
};

export const PENDENCIA_TIPO_LABEL: Record<PendenciaTipoEstoque, string> = {
  REGULARIZACAO: 'Regularização de saldo',
  APONTAR_PRODUCAO: 'Apontar produção',
};
