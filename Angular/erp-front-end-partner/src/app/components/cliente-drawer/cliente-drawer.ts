import {
  Component,
  EventEmitter,
  Input,
  OnChanges,
  Output,
  SimpleChanges,
  inject,
  signal,
} from '@angular/core';
import {
  AssinaturaResumoDTO,
  DashboardService,
  ClienteDetalheResponse,
} from '../../services/dashboard.service';
import { Cliente } from '../../pages/clientes/clientes';

@Component({
  selector: 'app-cliente-drawer',
  standalone: true,
  templateUrl: './cliente-drawer.html',
  styleUrl: './cliente-drawer.scss',
})
export class ClienteDrawer implements OnChanges {
  private readonly dashboardService = inject(DashboardService);

  @Input() cliente: Cliente | null = null;
  @Input() mode: 'engajamento' | 'assinatura' | null = null;
  @Output() fechar = new EventEmitter<void>();

  readonly detalhe = signal<ClienteDetalheResponse | null>(null);
  readonly assinatura = signal<AssinaturaResumoDTO | null>(null);
  readonly carregando = signal(false);
  readonly followupEnviado = signal(false);

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['cliente'] || changes['mode']) {
      this.followupEnviado.set(false);
      this.detalhe.set(null);
      this.assinatura.set(null);
      if (this.cliente && (this.mode === 'engajamento' || this.mode === 'assinatura')) {
        this.carregarDetalhe(this.cliente.id);
      }
      if (this.mode === 'assinatura' && this.cliente) {
        this.carregarAssinatura(this.cliente.id);
      }
    }
  }

  private carregarDetalhe(referralId: string): void {
    this.carregando.set(true);
    this.dashboardService.getClienteDetalhe(referralId).subscribe({
      next: (d) => {
        this.detalhe.set(d);
        this.carregando.set(false);
      },
      error: () => this.carregando.set(false),
    });
  }

  private carregarAssinatura(referralId: string): void {
    this.dashboardService.getAssinatura(referralId).subscribe({
      next: (a) => this.assinatura.set(a),
    });
  }

  maiorAcesso(): number {
    return Math.max(1, ...(this.detalhe()?.features.map((f) => f.accessCount) ?? [1]));
  }

  engajamentoPercent(): number | null {
    const features = this.detalhe()?.features;
    if (!features || features.length === 0) return null;
    const acessadas = features.filter((f) => f.accessCount > 0).length;
    return Math.round((acessadas / features.length) * 100);
  }

  diasDesde(iso: string | null): string {
    if (!iso) return '—';
    const dias = Math.floor((Date.now() - new Date(iso).getTime()) / 86_400_000);
    return dias <= 0 ? 'hoje' : `há ${dias} dia${dias > 1 ? 's' : ''}`;
  }

  // ponytail: sem UI de composição de mensagem ainda — usa prompt nativo. Troca por modal se o fluxo crescer.
  iniciarFollowup(): void {
    const referralId = this.cliente?.id;
    if (!referralId) return;
    const mensagem = window.prompt(
      'Mensagem de follow-up:',
      'Vi que você ainda não usou algumas funcionalidades, posso ajudar?',
    );
    if (!mensagem) return;
    this.dashboardService.iniciarFollowup(referralId, mensagem).subscribe({
      next: () => this.followupEnviado.set(true),
    });
  }

  fecharDrawer(): void {
    this.fechar.emit();
  }

  formatarMoeda(valor: number | null | undefined): string {
    if (valor == null) return '—';
    return valor.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
  }

  formatarData(iso: string | null | undefined): string {
    if (!iso) return '—';
    return new Date(iso).toLocaleDateString('pt-BR');
  }

  formaPagamento(billingType: string | null | undefined): string {
    switch (billingType) {
      case 'CREDIT_CARD':
        return 'Cartão de crédito';
      case 'BOLETO':
        return 'Boleto';
      case 'PIX':
        return 'Pix';
      default:
        return '—';
    }
  }

  statusCobrancaClasse(status: string | null | undefined): string {
    switch (status) {
      case 'ATIVA':
        return 'badge--ativo';
      case 'AGUARDANDO_PAGAMENTO':
        return 'badge--trial';
      default:
        return 'badge--perdido';
    }
  }
}
