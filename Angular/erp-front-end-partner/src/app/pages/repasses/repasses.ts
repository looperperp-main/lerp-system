import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { LowerCasePipe } from '@angular/common';
import { DashboardService, ExtratoComissoesDTO } from '../../services/dashboard.service';
import { PayoutInfo, PayoutService } from '../../services/payout.service';

interface RepassePeriodo {
  period: string;
  valor: number;
  comissoes: number;
  pagoEm: string | null;
}

@Component({
  selector: 'app-repasses',
  standalone: true,
  imports: [LowerCasePipe],
  templateUrl: './repasses.html',
  styleUrl: './repasses.scss',
})
export class RepassesComponent implements OnInit {
  private readonly dashboardService = inject(DashboardService);
  private readonly payoutService = inject(PayoutService);

  readonly carregando = signal(true);
  readonly erro = signal<string | null>(null);
  readonly extrato = signal<ExtratoComissoesDTO | null>(null);
  readonly payoutInfo = signal<PayoutInfo | null>(null);

  readonly proximoRepasseValor = computed(() => this.extrato()?.comissaoMesAtual ?? 0);
  readonly diasParaRepasse = computed(() => this.extrato()?.diasParaRepasse ?? 0);
  readonly dataAgendada = computed(() => {
    const d = new Date();
    d.setDate(d.getDate() + this.diasParaRepasse());
    return d;
  });

  private readonly pendentes = computed(
    () => this.extrato()?.historico.filter((i) => i.status === 'PENDENTE') ?? [],
  );
  readonly proximoRepassePeriodo = computed(() => {
    const periods = this.pendentes().map((i) => i.period);
    return periods.length ? [...periods].sort().at(-1)! : '—';
  });
  readonly proximoRepasseComissoes = computed(() => this.pendentes().length);

  // Histórico de repasses = comissões PAGO agrupadas por período (dado real já disponível no extrato).
  readonly historicoRepasses = computed<RepassePeriodo[]>(() => {
    const pagos = this.extrato()?.historico.filter((i) => i.status === 'PAGO') ?? [];
    const porPeriodo = new Map<string, RepassePeriodo>();
    for (const item of pagos) {
      const atual = porPeriodo.get(item.period) ?? {
        period: item.period,
        valor: 0,
        comissoes: 0,
        pagoEm: null as string | null,
      };
      atual.valor += item.amount ?? 0;
      atual.comissoes += 1;
      if (item.paidAt && (!atual.pagoEm || item.paidAt > atual.pagoEm)) {
        atual.pagoEm = item.paidAt;
      }
      porPeriodo.set(item.period, atual);
    }
    return [...porPeriodo.values()].sort((a, b) => b.period.localeCompare(a.period));
  });

  ngOnInit(): void {
    this.dashboardService.getComissoes().subscribe({
      next: (data) => {
        this.extrato.set(data);
        this.carregando.set(false);
      },
      error: () => {
        this.erro.set('Erro ao carregar o histórico de repasses. Tente novamente.');
        this.carregando.set(false);
      },
    });
    this.payoutService.getPayoutInfo().subscribe({
      next: (info) => this.payoutInfo.set(info),
      error: () => this.payoutInfo.set({ pixKey: null, pixKeyType: null }),
    });
  }

  copiarChavePix(): void {
    const chave = this.payoutInfo()?.pixKey;
    if (chave) {
      navigator.clipboard.writeText(chave);
    }
  }

  // ponytail: exporta o histórico visível como CSV client-side. Troca por endpoint real se o back gerar relatório.
  exportarCsv(): void {
    const linhas = [
      ['Período', 'Valor', 'Comissões', 'Pago em'],
      ...this.historicoRepasses().map((r) => [
        r.period,
        this.formatarMoeda(r.valor),
        String(r.comissoes),
        this.formatarData(r.pagoEm),
      ]),
    ];
    const csv = linhas.map((l) => l.join(';')).join('\n');
    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'repasses.csv';
    a.click();
    URL.revokeObjectURL(url);
  }

  // ponytail: sem endpoint de comprovante ainda — usa print nativo do browser.
  baixarComprovante(): void {
    window.print();
  }

  formatarMoeda(valor: number | null | undefined): string {
    if (valor == null) return 'R$ 0,00';
    return valor.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
  }

  formatarData(iso: string | null): string {
    if (!iso) return '—';
    return new Date(iso).toLocaleDateString('pt-BR');
  }

  formatarDataAgendada(d: Date): { dia: string; mes: string; ano: string } {
    return {
      dia: String(d.getDate()).padStart(2, '0'),
      mes: String(d.getMonth() + 1).padStart(2, '0'),
      ano: String(d.getFullYear()),
    };
  }
}
