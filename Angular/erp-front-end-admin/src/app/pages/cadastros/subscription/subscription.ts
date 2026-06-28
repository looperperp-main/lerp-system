import { Component, OnInit, inject, signal } from '@angular/core';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { TableModule } from 'primeng/table';
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

@Component({
  selector: 'app-subscription',
  standalone: true,
  imports: [CurrencyPipe, DatePipe, TableModule],
  templateUrl: './subscription.html',
  styleUrl: './subscription.scss',
})
export class Subscription implements OnInit {
  private readonly http = inject(HttpClient);

  readonly carregando = signal(true);
  readonly rows = signal<SubscriptionRow[]>([]);

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
    this.http
      .get<any>(`${environment.apiUrl}/billing/api/v1/subscriptions?size=50&sort=createdAt,desc`)
      .subscribe({
        next: (res) => {
          this.rows.set(res?.content ?? []);
          this.carregando.set(false);
        },
        error: () => this.carregando.set(false),
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
      case 'SUSPENSO':
      case 'CANCELADO':
        return base + 'bg-red-100 text-red-700';
      default:
        return base + 'bg-gray-100 text-gray-600';
    }
  }
}
