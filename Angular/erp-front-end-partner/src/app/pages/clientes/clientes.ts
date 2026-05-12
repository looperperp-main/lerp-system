import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CadastrarClienteModalService } from '../../services/cadastrar-cliente-modal.service';

export type ClienteStatus = 'ATIVO' | 'TRIAL' | 'CONVIDADO' | 'PERDIDO';
export type ClientePlano = 'Anual' | 'Mensal' | null;
export type ClienteAcao = 'documento' | 'atividade' | 'mensagem' | 'email' | 'reengajar';

export interface Cliente {
  id: number;
  nome: string;
  cnpj: string;
  status: ClienteStatus;
  engajamento: number | null;
  trialExpira: string | null;
  plano: ClientePlano;
  acoes: ClienteAcao[];
}

const MOCK_CLIENTES: Cliente[] = [
  {
    id: 1,
    nome: 'Padaria Vila Nova',
    cnpj: '12.345.678/0001-90',
    status: 'ATIVO',
    engajamento: 88,
    trialExpira: null,
    plano: 'Anual',
    acoes: ['documento'],
  },
  {
    id: 2,
    nome: 'Auto Peças Sul',
    cnpj: '23.456.789/0001-12',
    status: 'TRIAL',
    engajamento: 18,
    trialExpira: '03/05/26',
    plano: null,
    acoes: ['atividade', 'mensagem'],
  },
  {
    id: 3,
    nome: 'Mercado Bom Preço',
    cnpj: '18.234.567/0001-43',
    status: 'TRIAL',
    engajamento: 52,
    trialExpira: '06/05/26',
    plano: null,
    acoes: ['mensagem'],
  },
  {
    id: 4,
    nome: 'Restaurante Sabor & Cia',
    cnpj: '45.678.901/0001-22',
    status: 'CONVIDADO',
    engajamento: null,
    trialExpira: null,
    plano: null,
    acoes: ['email'],
  },
  {
    id: 5,
    nome: 'Pet Shop Amigo Fiel',
    cnpj: '31.987.654/0001-09',
    status: 'TRIAL',
    engajamento: 74,
    trialExpira: '10/05/26',
    plano: null,
    acoes: ['mensagem'],
  },
  {
    id: 6,
    nome: 'Salão Beleza Pura',
    cnpj: '52.111.222/0001-33',
    status: 'ATIVO',
    engajamento: 65,
    trialExpira: null,
    plano: 'Mensal',
    acoes: ['documento'],
  },
  {
    id: 7,
    nome: 'Loja do Tio Zé',
    cnpj: '67.333.444/0001-55',
    status: 'PERDIDO',
    engajamento: 8,
    trialExpira: null,
    plano: null,
    acoes: ['reengajar'],
  },
];

@Component({
  selector: 'app-clientes',
  imports: [FormsModule],
  templateUrl: './clientes.html',
  styleUrl: './clientes.scss',
})
export class Clientes {
  readonly cadastrarModal = inject(CadastrarClienteModalService);
  readonly busca = signal('');
  readonly statusFiltro = signal<ClienteStatus | 'TODOS'>('TODOS');
  readonly engajamentoFiltro = signal<'TODOS' | 'ALTO' | 'MEDIO' | 'BAIXO'>('TODOS');

  readonly clientes = computed(() => {
    const busca = this.busca().toLowerCase();
    const status = this.statusFiltro();
    const eng = this.engajamentoFiltro();

    return MOCK_CLIENTES.filter(c => {
      const matchBusca = !busca || c.nome.toLowerCase().includes(busca) || c.cnpj.includes(busca);
      const matchStatus = status === 'TODOS' || c.status === status;
      const matchEng =
        eng === 'TODOS' ||
        (eng === 'ALTO' && c.engajamento !== null && c.engajamento >= 70) ||
        (eng === 'MEDIO' && c.engajamento !== null && c.engajamento >= 30 && c.engajamento < 70) ||
        (eng === 'BAIXO' && (c.engajamento === null || c.engajamento < 30));
      return matchBusca && matchStatus && matchEng;
    });
  });

  readonly totalClientes = MOCK_CLIENTES.length;

  engajamentoCor(v: number | null): 'verde' | 'amarelo' | 'vermelho' {
    if (v === null) return 'vermelho';
    if (v >= 70) return 'verde';
    if (v >= 30) return 'amarelo';
    return 'vermelho';
  }

  trialUrgente(data: string | null): boolean {
    return data !== null;
  }
}