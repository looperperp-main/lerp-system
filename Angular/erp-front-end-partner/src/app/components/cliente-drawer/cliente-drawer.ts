import { Component, EventEmitter, Input, OnChanges, Output, SimpleChanges, inject, signal } from '@angular/core';
import { DashboardService, ClienteDetalheResponse } from '../../services/dashboard.service';
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
  readonly carregando = signal(false);
  readonly followupEnviado = signal(false);

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['cliente'] || changes['mode']) {
      this.followupEnviado.set(false);
      if (this.mode === 'engajamento' && this.cliente) {
        this.carregarDetalhe(this.cliente.id);
      } else {
        this.detalhe.set(null);
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

  maiorAcesso(): number {
    return Math.max(1, ...(this.detalhe()?.features.map((f) => f.accessCount) ?? [1]));
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
    const mensagem = window.prompt('Mensagem de follow-up:', 'Vi que você ainda não usou algumas funcionalidades, posso ajudar?');
    if (!mensagem) return;
    this.dashboardService.iniciarFollowup(referralId, mensagem).subscribe({
      next: () => this.followupEnviado.set(true),
    });
  }

  fecharDrawer(): void {
    this.fechar.emit();
  }
}
