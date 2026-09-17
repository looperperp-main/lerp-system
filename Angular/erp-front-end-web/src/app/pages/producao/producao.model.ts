export type StatusOrdemProducao = 'ABERTA' | 'CONCLUIDA' | 'CANCELADA';

export interface FichaTecnicaItem {
  produtoComponenteId: string;
  quantidade: number;
}

export interface FichaTecnica {
  id: string;
  produtoAcabadoId: string;
  ativo: boolean;
  itens: FichaTecnicaItem[];
}

export interface FichaTecnicaRequest {
  produtoAcabadoId: string;
  itens: FichaTecnicaItem[];
}

export interface OrdemProducao {
  id: string;
  produtoAcabadoId: string;
  quantidadePlanejada: number;
  depositoId: string;
  status: StatusOrdemProducao;
  criadaEm: string;
}

export interface OrdemProducaoRequest {
  produtoAcabadoId: string;
  quantidadePlanejada: number;
  depositoId: string;
}

export const STATUS_ORDEM_LABEL: Record<StatusOrdemProducao, string> = {
  ABERTA: 'Aberta',
  CONCLUIDA: 'Concluída',
  CANCELADA: 'Cancelada',
};
