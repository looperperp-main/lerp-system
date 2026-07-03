import { Component, OnInit, signal, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import {
  DashboardService,
  DashboardResponse,
  AtividadeItemDTO,
} from '../../services/dashboard.service';
import { ConviteService, ConviteDTO } from '../../services/convite.service';
import { ClienteDetailPanelComponent } from '../../components/cliente-detail-panel/cliente-detail-panel';
import { PartnerSessionService } from '../../services/partner-session.service';
import { CadastrarClienteModalService } from '../../services/cadastrar-cliente-modal.service';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [RouterLink, ClienteDetailPanelComponent],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.scss',
})
export class DashboardComponent implements OnInit {
  private readonly dashboardService = inject(DashboardService);
  private readonly conviteService = inject(ConviteService);
  private readonly partnerSession = inject(PartnerSessionService);
  private readonly cadastrarClienteModal = inject(CadastrarClienteModalService);

  // Lista real de convites — fonte da verdade do funil (mesma da tela /clientes).
  private readonly convites = signal<ConviteDTO[]>([]);
  private readonly convitesCarregados = signal(false);

  readonly primeiroNome = computed(() => (this.partnerSession.info()?.name ?? '').split(' ')[0]);
  readonly referralCode = computed(() => this.partnerSession.info()?.referralCode ?? '');
  readonly referralLink = computed(() => `syax.com.br/r/${this.referralCode()}`);

  readonly semClientes = computed(() => this.convitesCarregados() && this.convites().length === 0);

  readonly linkCopiado = signal(false);

  readonly carregando = signal(true);
  readonly atualizando = signal(false);
  readonly erro = signal<string | null>(null);
  readonly dashboard = signal<DashboardResponse | null>(null);

  readonly selectedReferralId = signal<string | null>(null);
  readonly panelOpen = signal(false);

  // Funil derivado da lista real de convites. Base 100% = Convidados (topo do funil).
  readonly funnelSteps = computed(() => {
    const list = this.convites();
    const convidados = list.length;
    const ativados = list.filter((c) => c.status !== 'CONVIDADO' && c.status !== 'PERDIDO').length;
    const trial = list.filter((c) => c.status === 'TRIAL').length;
    const convertidos = list.filter(
      (c) => c.status === 'CONVERTIDO' || c.status === 'ATIVADO',
    ).length;
    const pct = (v: number) => (convidados > 0 ? Math.round((v / convidados) * 100) : 0);
    return [
      { label: 'Convidados', value: convidados, color: '#3b82f6', pct: convidados > 0 ? 100 : 0 },
      { label: 'Ativados', value: ativados, color: '#f97316', pct: pct(ativados) },
      { label: 'Em Trial', value: trial, color: '#eab308', pct: pct(trial) },
      { label: 'Convertidos', value: convertidos, color: '#22c55e', pct: pct(convertidos) },
    ];
  });

  // Taxa de conversão do rodapé do funil (Convertidos / Convidados).
  readonly conversao = computed(() => {
    const s = this.funnelSteps();
    const total = s[0].value;
    const conv = s[3].value;
    return { conv, total, rate: total > 0 ? Math.round((conv / total) * 100) : 0 };
  });

  ngOnInit(): void {
    // 1ª visita carrega; visitas seguintes reusam o cache de sessão (sem mostrar "Carregando").
    this.partnerSession.load();
    this.carregarDashboard(false);
    this.conviteService.listar().subscribe((page) => {
      this.convites.set(page.content);
      this.convitesCarregados.set(true);
    });
  }

  abrirCadastroCliente(): void {
    this.cadastrarClienteModal.open();
  }

  copiarLink(): void {
    navigator.clipboard.writeText(`https://${this.referralLink()}`);
    this.linkCopiado.set(true);
    setTimeout(() => this.linkCopiado.set(false), 2000);
  }

  /** Refresh manual (botão pi-sync no card de Clientes Ativos) — força nova busca. */
  refresh(): void {
    if (this.atualizando()) return;
    this.atualizando.set(true);
    this.conviteService.listar().subscribe((page) => this.convites.set(page.content));
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
