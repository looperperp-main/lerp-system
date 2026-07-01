import { Component, OnInit, signal, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { DashboardService, DashboardResponse, AtividadeItemDTO } from '../../services/dashboard.service';
import { ClienteDetailPanelComponent } from '../../components/cliente-detail-panel/cliente-detail-panel';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [RouterLink, ClienteDetailPanelComponent],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.scss',
})
export class DashboardComponent implements OnInit {
  private readonly dashboardService = inject(DashboardService);

  readonly carregando = signal(true);
  readonly atualizando = signal(false);
  readonly erro = signal<string | null>(null);
  readonly dashboard = signal<DashboardResponse | null>(null);

  readonly selectedReferralId = signal<string | null>(null);
  readonly panelOpen = signal(false);

  // Funil derivado dos stats reais do dashboard. Base 100% = Convidados (topo do funil).
  readonly funnelSteps = computed(() => {
    const d = this.dashboard();
    const convidados = d?.statsConvidados ?? 0;
    const pct = (v: number) => (convidados > 0 ? Math.round((v / convidados) * 100) : 0);
    return [
      { label: 'Convidados', value: convidados, color: '#3b82f6', pct: convidados > 0 ? 100 : 0 },
      { label: 'Ativados', value: d?.statsAtivos ?? 0, color: '#f97316', pct: pct(d?.statsAtivos ?? 0) },
      { label: 'Em Trial', value: d?.statsTrial ?? 0, color: '#eab308', pct: pct(d?.statsTrial ?? 0) },
      { label: 'Convertidos', value: d?.statsConvertidos ?? 0, color: '#22c55e', pct: pct(d?.statsConvertidos ?? 0) },
    ];
  });

  ngOnInit(): void {
    // 1ª visita carrega; visitas seguintes reusam o cache de sessão (sem mostrar "Carregando").
    this.carregarDashboard(false);
  }

  /** Refresh manual (botão pi-sync no card de Clientes Ativos) — força nova busca. */
  refresh(): void {
    if (this.atualizando()) return;
    this.atualizando.set(true);
    this.dashboardService.getDashboard(true).subscribe({
      next: (data) => {
        this.dashboard.set(data);
        this.atualizando.set(false);
      },
      error: () => {
        this.erro.set('Erro ao atualizar dashboard. Tente novamente.');
        this.atualizando.set(false);
      },
    });
  }

  private carregarDashboard(force: boolean): void {
    // Só mostra o estado "Carregando…" cheio quando ainda não há dados em tela.
    if (!this.dashboard()) {
      this.carregando.set(true);
    }
    this.erro.set(null);
    this.dashboardService.getDashboard(force).subscribe({
      next: (data) => {
        this.dashboard.set(data);
        this.carregando.set(false);
      },
      error: () => {
        this.erro.set('Erro ao carregar dashboard. Tente novamente.');
        this.carregando.set(false);
      },
    });
  }

  abrirPainel(referralId: string): void {
    this.selectedReferralId.set(referralId);
    this.panelOpen.set(true);
  }

  fecharPainel(): void {
    this.panelOpen.set(false);
    this.selectedReferralId.set(null);
  }

  atividadeIcon(tipo: AtividadeItemDTO['tipo']): string {
    return tipo === 'ATIVADO' ? 'blue' : 'gray';
  }

  atividadeDesc(item: AtividadeItemDTO): string {
    if (item.tipo === 'ATIVADO') {
      return `ativou a conta e iniciou o trial`;
    }
    return `convite enviado`;
  }

  tempoRelativo(iso: string): string {
    const diff = Date.now() - new Date(iso).getTime();
    const dias = Math.floor(diff / 86_400_000);
    if (dias === 0) return 'hoje';
    if (dias === 1) return 'há 1 dia';
    return `há ${dias} dias`;
  }

  formatarCnpj(cnpj: string): string {
    return cnpj.replace(/^(\d{2})(\d{3})(\d{3})(\d{4})(\d{2})$/, '$1.$2.$3/$4-$5');
  }

  formatarMoeda(valor: number | null | undefined): string {
    if (valor == null) return 'R$ 0,00';
    return valor.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
  }
}