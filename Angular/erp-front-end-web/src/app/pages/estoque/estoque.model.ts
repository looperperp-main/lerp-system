export type TipoMovimentoEstoque =
  | 'ENTRADA_COMPRA'
  | 'ESTORNO_ENTRADA_COMPRA'
  | 'SAIDA_VENDA'
  | 'ESTORNO_SAIDA_VENDA'
  | 'AJUSTE_ENTRADA'
  | 'AJUSTE_SAIDA';

export type OrigemMovimentoEstoque = 'PEDIDO_VENDA' | 'RECEBIMENTO' | 'AJUSTE' | 'INVENTARIO';

export interface EstoqueSaldo {
  produtoId: string;
  depositoId: string;
  quantidade: number;
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
  usuarioId?: string;
  ocorridoEm: string;
}

export interface AjusteEstoqueRequest {
  produtoId: string;
  depositoId: string;
  quantidadeContada: number;
  origem: OrigemMovimentoEstoque;
  motivo?: string | null;
  valorUnitario?: number | null;
}

export const TIPO_MOVIMENTO_LABEL: Record<TipoMovimentoEstoque, string> = {
  ENTRADA_COMPRA: 'Entrada por compra',
  ESTORNO_ENTRADA_COMPRA: 'Estorno de entrada',
  SAIDA_VENDA: 'Saída por venda',
  ESTORNO_SAIDA_VENDA: 'Estorno de saída',
  AJUSTE_ENTRADA: 'Ajuste (entrada)',
  AJUSTE_SAIDA: 'Ajuste (saída)',
};

export const ORIGEM_MOVIMENTO_LABEL: Record<OrigemMovimentoEstoque, string> = {
  PEDIDO_VENDA: 'Pedido de venda',
  RECEBIMENTO: 'Recebimento de mercadoria',
  AJUSTE: 'Ajuste manual',
  INVENTARIO: 'Inventário',
};
