export type TipoNovidade = 'novo' | 'melhoria' | 'correcao';

export interface Novidade {
  /** ISO yyyy-MM-dd */
  data: string;
  titulo: string;
  descricao: string;
  tipo: TipoNovidade;
}

/**
 * Fonte da verdade do changelog mostrado ao cliente final (página /web/novidades + card da sidebar).
 * Mais recente primeiro. Texto em PT-BR, sem jargão técnico — só o que o cliente percebe.
 * Toda PR com mudança visível ao cliente adiciona uma entrada aqui (o pre-push barra se faltar).
 */
export const CHANGELOG: Novidade[] = [
  {
    data: '2026-09-26',
    titulo: 'Compras: do pedido ao recebimento',
    descricao:
      'Agora você acompanha o ciclo completo de compras — requisição, cotação, pedido ao fornecedor e recebimento da mercadoria — em um só lugar.',
    tipo: 'novo',
  },
  {
    data: '2026-09-26',
    titulo: 'Impostos mais precisos nas vendas entre estados',
    descricao:
      'O cálculo de ICMS e do diferencial de alíquota (DIFAL) em vendas para outro estado agora segue as regras vigentes.',
    tipo: 'melhoria',
  },
  {
    data: '2026-09-16',
    titulo: 'Controle de estoque',
    descricao:
      'Movimentações, saldos por depósito e inventário passam a ser registrados e consultados direto no sistema.',
    tipo: 'novo',
  },
];
