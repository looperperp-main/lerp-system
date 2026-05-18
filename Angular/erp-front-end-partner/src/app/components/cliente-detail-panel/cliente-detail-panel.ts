import { Component, Input, Output, EventEmitter, OnInit, signal, computed, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DashboardService, ClienteDetalheResponse, FeatureStatDTO } from '../../services/dashboard.service';

@Component({
  selector: 'app-cliente-detail-panel',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './cliente-detail-panel.html',
  styleUrl: './cliente-detail-panel.scss',
})
export class ClienteDetailPanelComponent implements OnInit {
  @Input({ required: true }) referralId!: string;
  @Output() closed = new EventEmitter<void>();

  private readonly dashboardService = inject(DashboardService);

  readonly carregando = signal(true);
  readonly detalhe = signal<ClienteDetalheResponse | null>(null);
  readonly followupAberto = signal(false);
  readonly followupMensagem = signal('');
  readonly enviando = signal(false);
  readonly enviado = signal(false);

  readonly daysLeft = computed(() => {
    const d = this.detalhe();
    if (!d?.referral.trialExpiresAt) return null;
    return Math.ceil(
      (new Date(d.referral.trialExpiresAt).getTime() - Date.now()) / 86_400_000
    );
  });

  readonly resumoTexto = computed(() => {
    const d = this.detalhe();
    if (!d) return '';
    const features = d.features
      .filter(f => f.accessCount > 0)
      .map(f => `- ${f.label}: ${f.accessCount}×`)
      .join('\n');
    const gaps = d.adoptionGaps.map(g => `- ${g}: nunca acessado`).join('\n');
    return `Relatório de engajamento — ${d.referral.razaoSocial}\n\n` +
           `Logins: ${d.loginCount}\n` +
           `Dias ativos: ${d.daysActive}\n\n` +
           `Features acessadas:\n${features || '(nenhuma)'}\n\n` +
           `GAPs de adoção:\n${gaps || '(nenhum)'}`;
  });

  ngOnInit(): void {
    this.dashboardService.getClienteDetalhe(this.referralId).subscribe({
      next: (data) => {
        this.detalhe.set(data);
        this.carregando.set(false);
      },
      error: () => this.carregando.set(false),
    });
  }

  enviarFollowup(): void {
    const msg = this.followupMensagem().trim();
    if (!msg || this.enviando()) return;
    this.enviando.set(true);
    this.dashboardService.iniciarFollowup(this.referralId, msg).subscribe({
      next: () => {
        this.enviando.set(false);
        this.enviado.set(true);
        this.followupAberto.set(false);
        this.followupMensagem.set('');
      },
      error: () => this.enviando.set(false),
    });
  }

  copiarResumo(): void {
    navigator.clipboard.writeText(this.resumoTexto());
  }

  barWidth(feature: FeatureStatDTO, features: FeatureStatDTO[]): number {
    const max = Math.max(...features.map(f => f.accessCount), 1);
    return Math.round((feature.accessCount / max) * 100);
  }

  barColor(feature: FeatureStatDTO): string {
    if (feature.accessCount === 0) return '#374151';
    if (feature.accessCount <= 2)  return '#f59e0b';
    return '#22c55e';
  }

  formatarCnpj(cnpj: string): string {
    return cnpj.replace(/^(\d{2})(\d{3})(\d{3})(\d{4})(\d{2})$/, '$1.$2.$3/$4-$5');
  }

  tempoRelativo(iso: string | null): string {
    if (!iso) return '—';
    const diff = Date.now() - new Date(iso).getTime();
    const dias = Math.floor(diff / 86_400_000);
    if (dias === 0) return 'hoje';
    if (dias === 1) return 'há 1 dia';
    return `há ${dias} dias`;
  }
}