import { Component, OnInit, inject, signal } from '@angular/core';
import { DashboardService, ExtratoComissoesDTO, ComissaoItemDTO } from '../../services/dashboard.service';

@Component({
  selector: 'app-comissoes',
  standalone: true,
  templateUrl: './comissoes.html',
  styleUrl: './comissoes.scss',
})
export class ComissoesComponent implements OnInit {
  private readonly dashboardService = inject(DashboardService);

  readonly carregando = signal(true);
  readonly erro = signal<string | null>(null);
  readonly extrato = signal<ExtratoComissoesDTO | null>(null);

  ngOnInit(): void {
    this.dashboardService.getComissoes().subscribe({
      next: (data) => {
        this.extrato.set(data);
        this.carregando.set(false);
      },
      error: () => {
        this.erro.set('Erro ao carregar o extrato de comissões. Tente novamente.');
        this.carregando.set(false);
      },
    });
  }

  statusClasse(status: string): string {
    switch (status) {
      case 'PAGO':
        return 'badge badge--green';
      case 'CANCELADO':
        return 'badge badge--red';
      default:
        return 'badge badge--amber'; // PENDENTE
    }
  }

  formatarCnpj(cnpj: string): string {
    return cnpj?.replace(/^(\d{2})(\d{3})(\d{3})(\d{4})(\d{2})$/, '$1.$2.$3/$4-$5') ?? '—';
  }

  formatarMoeda(valor: number | null | undefined): string {
    if (valor == null) return 'R$ 0,00';
    return valor.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
  }

  formatarData(iso: string | null): string {
    if (!iso) return '—';
    return new Date(iso).toLocaleDateString('pt-BR');
  }

  trackId(_: number, item: ComissaoItemDTO): string {
    return item.id;
  }
}
