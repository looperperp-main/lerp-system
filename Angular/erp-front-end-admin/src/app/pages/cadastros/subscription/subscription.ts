import { ChangeDetectorRef, Component, OnInit, inject, signal } from '@angular/core';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { TableModule } from 'primeng/table';
import { Toast } from 'primeng/toast';
import { MessageService } from 'primeng/api';
import { ButtonDirective } from 'primeng/button';
import { Dialog } from 'primeng/dialog';
import { environment } from '../../../../environments/environment';
import { ColumnConfig } from '../../../components/table/data-table';

interface SubscriptionRow {
  id: string;
  tenantId: number;
  planType: string;
  billingCycle: string;
  value: number;
  status: string;
  asaasSubscriptionId: string;
  nextDueDate: string | null;
  createdAt: string;
}

interface CobrancaModel {
  id: string;
  status: string;
  value: number;
  dueDate: string | null;
  invoiceUrl: string | null;
}

@Component({
  selector: 'app-subscription',
  standalone: true,
  imports: [CurrencyPipe, DatePipe, TableModule, Toast, ButtonDirective, Dialog],
  providers: [MessageService],
  templateUrl: './subscription.html',
  styleUrl: './subscription.scss',
})
export class Subscription implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly messageService = inject(MessageService);
  private readonly base = `${environment.apiUrl}/billing/api/v1/subscriptions`;
  // App é zoneless: mutação de campo comum em callback async não dispara CD sozinha.
  private readonly cdr = inject(ChangeDetectorRef);

  readonly carregando = signal(true);
  readonly rows = signal<SubscriptionRow[]>([]);

  expandedRows: { [key: string]: boolean } = {};
  cobrancas: { [subscriptionId: string]: CobrancaModel[] } = {};
  cobrancasLoading: { [subscriptionId: string]: boolean } = {};
  reprocessLoading: { [subscriptionId: string]: boolean } = {};

  cancelDialogVisible = false;
  cancelTarget: SubscriptionRow | null = null;
  cancelLoading = false;

  cols: ColumnConfig[] = [
    { field: 'tenantId', header: 'Tenant', type: 'text' },
    { field: 'planType', header: 'Plano', type: 'text' },
    { field: 'billingCycle', header: 'Ciclo', type: 'text' },
    { field: 'value', header: 'Valor', type: 'text' },
    { field: 'status', header: 'Status', type: 'status' },
    { field: 'nextDueDate', header: 'Próx. vencimento', type: 'text' },
    { field: 'asaasSubscriptionId', header: 'Asaas', type: 'text' },
  ];

  ngOnInit(): void {
    this.http.get<any>(`${this.base}?size=50&sort=createdAt,desc`).subscribe({
      next: (res) => {
        this.rows.set(res?.content ?? []);
        this.carregando.set(false);
      },
      error: () => this.carregando.set(false),
    });
  }

  toggleRow(row: SubscriptionRow) {
    const abrir = !this.expandedRows[row.id];
    this.expandedRows = { ...this.expandedRows, [row.id]: abrir };
    if (abrir && !this.cobrancas[row.id]) {
      this.cobrancasLoading[row.id] = true;
      this.http.get<CobrancaModel[]>(`${this.base}/${row.id}/cobrancas`).subscribe({
        next: (data) => {
          this.cobrancas[row.id] = data ?? [];
          this.cobrancasLoading[row.id] = false;
          this.cdr.markForCheck();
        },
        error: () => {
          this.cobrancasLoading[row.id] = false;
          this.cdr.markForCheck();
          this.messageService.add({
            severity: 'error',
            summary: 'Erro',
            detail: 'Erro ao buscar cobranças no Asaas.',
          });
        },
      });
    }
  }

  reprocessar(row: SubscriptionRow) {
    this.reprocessLoading[row.id] = true;
    this.http.post<any>(`${this.base}/${row.id}/reprocess`, {}).subscribe({
      next: (res) => {
        this.reprocessLoading[row.id] = false;
        this.cdr.markForCheck();
        this.messageService.add({
          severity: res.ativada ? 'success' : 'info',
          summary: res.ativada ? 'Ativada' : 'Sem mudança',
          detail: res.mensagem,
        });
        if (res.ativada) {
          this.rows.update((rows) =>
            rows.map((r) => (r.id === row.id ? { ...r, status: res.status } : r)),
          );
          delete this.cobrancas[row.id]; // força recarga do drill-down
        }
      },
      error: (err) => {
        this.reprocessLoading[row.id] = false;
        this.cdr.markForCheck();
        this.messageService.add({
          severity: 'error',
          summary: 'Erro',
          detail: err.error?.message || 'Erro ao reprocessar a assinatura.',
        });
      },
    });
  }

  abrirCancelDialog(row: SubscriptionRow) {
    this.cancelTarget = row;
    this.cancelDialogVisible = true;
  }

  confirmarCancel() {
    if (!this.cancelTarget) {
      return;
    }
    const row = this.cancelTarget;
    this.cancelLoading = true;
    this.http.post<any>(`${this.base}/${row.id}/cancel`, {}).subscribe({
      next: (res) => {
        this.cancelLoading = false;
        this.cancelDialogVisible = false;
        this.rows.update((rows) =>
          rows.map((r) => (r.id === row.id ? { ...r, status: res.status } : r)),
        );
        this.messageService.add({
          severity: 'success',
          summary: 'Cancelamento solicitado',
          detail: res.nextDueDate
            ? 'Acesso mantido até ' + new Date(res.nextDueDate).toLocaleDateString('pt-BR')
            : 'Renovação interrompida no Asaas.',
        });
      },
      error: (err) => {
        this.cancelLoading = false;
        this.cdr.markForCheck();
        this.messageService.add({
          severity: 'error',
          summary: 'Erro',
          detail: err.error?.message || 'Erro ao cancelar a assinatura.',
        });
      },
    });
  }

  statusClass(status: string): string {
    const base = 'px-2 py-1 rounded text-xs font-medium ';
    switch (status) {
      case 'ATIVA':
        return base + 'bg-green-100 text-green-700';
      case 'TRIAL':
        return base + 'bg-blue-100 text-blue-700';
      case 'AGUARDANDO_PAGAMENTO':
        return base + 'bg-orange-100 text-orange-700';
      case 'CANCELAMENTO_SOLICITADO':
        return base + 'bg-yellow-100 text-yellow-700';
      case 'SUSPENSO':
      case 'CANCELADO':
        return base + 'bg-red-100 text-red-700';
      default:
        return base + 'bg-gray-100 text-gray-600';
    }
  }

  cobrancaStatusClass(status: string): string {
    const base = 'px-2 py-1 rounded text-xs font-medium ';
    if (['RECEIVED', 'CONFIRMED', 'RECEIVED_IN_CASH'].includes(status)) {
      return base + 'bg-green-100 text-green-700';
    }
    if (status === 'PENDING') {
      return base + 'bg-orange-100 text-orange-700';
    }
    if (status === 'OVERDUE') {
      return base + 'bg-red-100 text-red-700';
    }
    return base + 'bg-gray-100 text-gray-600';
  }
}
