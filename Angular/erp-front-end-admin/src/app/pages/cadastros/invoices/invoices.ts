import { Component, OnInit, inject, signal } from '@angular/core';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { MessageService } from 'primeng/api';
import { ToastModule } from 'primeng/toast';
import { environment } from '../../../../environments/environment';
import { ButtonDirective } from 'primeng/button';
import { Ripple } from 'primeng/ripple';
import { TableModule } from 'primeng/table';
import { ColumnConfig } from '../../../components/table/data-table';

interface CommissionRow {
  id: string;
  partnerId: string;
  tenantId: number;
  amount: number;
  period: string;
  status: string;
  asaasTransferId: string | null;
  calculatedAt: string;
  paidAt: string | null;
}

@Component({
  selector: 'app-invoices',
  standalone: true,
  imports: [CurrencyPipe, DatePipe, ToastModule, ButtonDirective, Ripple, TableModule],
  providers: [MessageService],
  templateUrl: './invoices.html',
  styleUrl: './invoices.scss',
})
export class Invoices implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly messageService = inject(MessageService);
  private readonly base = `${environment.apiUrl}/billing/api/v1/commissions`;

  readonly carregando = signal(true);
  readonly processando = signal(false);
  readonly rows = signal<CommissionRow[]>([]);

  cols: ColumnConfig[] = [
    { field: 'partnerId', header: 'Parceiro', type: 'text' },
    { field: 'tenantId', header: 'Tenant', type: 'text' },
    { field: 'amount', header: 'Valor', type: 'text' },
    { field: 'period', header: 'Período', type: 'text' },
    { field: 'status', header: 'Status', type: 'status' },
    { field: 'asaasTransferId', header: 'Transfer Asaas', type: 'text' },
    { field: 'paidAt', header: 'Pago em', type: 'text' },
  ];

  ngOnInit(): void {
    this.carregar();
  }

  carregar(): void {
    this.carregando.set(true);
    this.http.get<any>(`${this.base}?size=50&sort=calculatedAt,desc`).subscribe({
      next: (res) => {
        this.rows.set(res?.content ?? []);
        this.carregando.set(false);
      },
      error: () => this.carregando.set(false),
    });
  }

  /** Money-out — exige REPASSE_EXECUTE no JWT (gateway injeta X-Authorities). */
  processarRepasses(): void {
    if (this.processando()) return;
    this.processando.set(true);
    this.http.post(`${this.base}/admin/trigger-repasse`, {}, { responseType: 'text' }).subscribe({
      next: (msg) => {
        this.messageService.add({ severity: 'success', summary: 'Repasse', detail: msg });
        this.processando.set(false);
        this.carregar();
      },
      error: (err) => {
        const detail =
          err?.status === 403
            ? (err?.error?.message ??
              'Operação não permitida. Fale com um administrador responsável pelos repasses.')
            : 'Falha ao processar repasses.';
        this.messageService.add({ severity: 'error', summary: 'Erro', detail });
        this.processando.set(false);
      },
    });
  }

  statusClass(status: string): string {
    const base = 'px-2 py-1 rounded text-xs font-medium ';
    switch (status) {
      case 'PAGO':
        return base + 'bg-green-100 text-green-700';
      case 'EM_TRANSFERENCIA':
        return base + 'bg-blue-100 text-blue-700';
      case 'PENDENTE':
        return base + 'bg-orange-100 text-orange-700';
      default:
        return base + 'bg-gray-100 text-gray-600';
    }
  }
}
