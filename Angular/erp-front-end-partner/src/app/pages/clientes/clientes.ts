import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { CadastrarClienteModalService } from '../../services/cadastrar-cliente-modal.service';
import { ConviteService, ConviteDTO } from '../../services/convite.service';

export type ClienteStatus = 'ATIVO' | 'TRIAL' | 'CONVIDADO' | 'PERDIDO';
export type ClientePlano = 'Anual' | 'Mensal' | null;
export type ClienteAcao = 'documento' | 'atividade' | 'mensagem' | 'email' | 'reengajar';

export interface Cliente {
  id: string;
  nome: string;
  cnpj: string;
  status: ClienteStatus;
  engajamento: number | null;
  trialExpira: string | null;
  plano: ClientePlano;
  acoes: ClienteAcao[];
}

const STATUS_MAP: Record<ConviteDTO['status'], ClienteStatus> = {
  CONVIDADO: 'CONVIDADO',
  TRIAL: 'TRIAL',
  ATIVADO: 'ATIVO',
  CONVERTIDO: 'ATIVO',
  PERDIDO: 'PERDIDO',
};

const STATUS_LABEL: Record<ClienteStatus, string> = {
  CONVIDADO: 'Aguardando ativação',
  TRIAL: 'Trial',
  ATIVO: 'Ativo',
  PERDIDO: 'Perdido',
};

const ACOES_MAP: Record<ConviteDTO['status'], ClienteAcao[]> = {
  CONVIDADO: ['email'],
  TRIAL: ['documento', 'mensagem'],
  ATIVADO: ['documento', 'mensagem'],
  CONVERTIDO: ['documento'],
  PERDIDO: ['reengajar'],
};

@Component({
  selector: 'app-clientes',
  imports: [FormsModule],
  templateUrl: './clientes.html',
  styleUrl: './clientes.scss',
})
export class Clientes implements OnInit {
  readonly cadastrarModal = inject(CadastrarClienteModalService);
  private readonly conviteService = inject(ConviteService);
  private readonly destroyRef = inject(DestroyRef);

  readonly statusLabel = STATUS_LABEL;

  readonly busca = signal('');
  readonly statusFiltro = signal<ClienteStatus | 'TODOS'>('TODOS');
  readonly engajamentoFiltro = signal<'TODOS' | 'ALTO' | 'MEDIO' | 'BAIXO'>('TODOS');

  private readonly todosClientes = signal<Cliente[]>([]);
  readonly carregando = signal(false);
  readonly erro = signal('');
  readonly notificacao = signal('');
  private notificacaoTimer: ReturnType<typeof setTimeout> | null = null;
  totalClientes = 0;

  readonly clientes = computed(() => {
    const busca = this.busca().toLowerCase();
    const status = this.statusFiltro();
    const eng = this.engajamentoFiltro();

    return this.todosClientes().filter(c => {
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

  ngOnInit(): void {
    this.carregar();
    this.cadastrarModal.inviteSent$
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => {
        this.carregar();
        this.mostrarNotificacao('Convite enviado com sucesso!');
      });
  }

  private mostrarNotificacao(msg: string): void {
    if (this.notificacaoTimer) clearTimeout(this.notificacaoTimer);
    this.notificacao.set(msg);
    this.notificacaoTimer = setTimeout(() => this.notificacao.set(''), 4000);
  }

  carregar(): void {
    this.carregando.set(true);
    this.erro.set('');
    this.conviteService.listar().subscribe({
      next: page => {
        this.todosClientes.set(page.content.map(c => this.mapear(c)));
        this.totalClientes = page.totalElements;
        this.carregando.set(false);
      },
      error: () => {
        this.erro.set('Erro ao carregar clientes. Tente novamente.');
        this.carregando.set(false);
      },
    });
  }

  reenviarConvite(id: string): void {
    this.conviteService.reenviar(id).subscribe({
      next: () => this.carregar(),
      error: () => this.erro.set('Erro ao reenviar convite. Tente novamente.'),
    });
  }

  engajamentoCor(v: number | null): 'verde' | 'amarelo' | 'vermelho' {
    if (v === null) return 'vermelho';
    if (v >= 70) return 'verde';
    if (v >= 30) return 'amarelo';
    return 'vermelho';
  }

  trialUrgente(data: string | null): boolean {
    if (!data) return false;
    const [d, m, y] = data.split('/').map(Number);
    const expira = new Date(2000 + y, m - 1, d);
    const diff = (expira.getTime() - Date.now()) / 86_400_000;
    return diff <= 7;
  }

  private mapear(c: ConviteDTO): Cliente {
    return {
      id: c.referralId,
      nome: c.razaoSocial,
      cnpj: this.formatarCnpj(c.cnpj),
      status: STATUS_MAP[c.status],
      engajamento: null,
      trialExpira: c.trialExpiresAt
        ? this.formatarData(c.trialExpiresAt)
        : (c.status === 'CONVIDADO' && c.tokenExpiresAt ? this.formatarData(c.tokenExpiresAt) : null),
      plano: this.mapearPlano(c.planoSugerido),
      acoes: ACOES_MAP[c.status],
    };
  }

  private mapearPlano(plano: string | null): ClientePlano {
    if (plano === 'ANUAL') return 'Anual';
    if (plano === 'MENSAL') return 'Mensal';
    return null;
  }

  private formatarCnpj(cnpj: string): string {
    const d = cnpj.replaceAll(/\D/g, '');
    if (d.length !== 14) return cnpj;
    return `${d.slice(0, 2)}.${d.slice(2, 5)}.${d.slice(5, 8)}/${d.slice(8, 12)}-${d.slice(12)}`;
  }

  private formatarData(iso: string): string {
    const d = new Date(iso);
    const dd = String(d.getDate()).padStart(2, '0');
    const mm = String(d.getMonth() + 1).padStart(2, '0');
    const yy = String(d.getFullYear()).slice(2);
    return `${dd}/${mm}/${yy}`;
  }
}