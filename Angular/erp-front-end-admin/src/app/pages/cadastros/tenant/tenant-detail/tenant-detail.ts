import { ChangeDetectorRef, Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule, CurrencyPipe, DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient, HttpParams } from '@angular/common/http';
import { ActivatedRoute, Router } from '@angular/router';
import { TableModule } from 'primeng/table';
import { Toast } from 'primeng/toast';
import { MessageService } from 'primeng/api';
import { ButtonDirective } from 'primeng/button';
import { Dialog } from 'primeng/dialog';
import { environment } from '../../../../../environments/environment';
import { CnpjPipe } from '../../../../util/pipe/cnpj.pipe';
import { TenantModel } from '../tenant/tenant.model';

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

interface TenantUser {
  id: string;
  email: string;
  displayName: string;
  active: boolean;
}

interface FeatureStat {
  featureKey: string;
  label: string;
  accessCount: number;
  lastAccessedAt: string | null;
}

interface EngajamentoModel {
  loginCount: number;
  lastLoginAt: string | null;
  daysActive: number;
  features: FeatureStat[];
  adoptionGaps: string[];
}

interface OrigemModel {
  contador: string;
  crc: string;
  referralCode: string;
  commissionRate: number;
  status: string;
  invitedAt: string | null;
  activatedAt: string | null;
  convertedAt: string | null;
}

interface AuditRow {
  id: string;
  actorUserId: string;
  action: string;
  targetType: string;
  targetId: string;
  result: string;
  eventDate: string;
}

/** Visão 360 do tenant: cabeçalho, assinatura (com ações), usuários, origem e auditoria. */
@Component({
  selector: 'app-tenant-detail',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    CurrencyPipe,
    DatePipe,
    CnpjPipe,
    TableModule,
    Toast,
    ButtonDirective,
    Dialog,
  ],
  providers: [MessageService],
  templateUrl: './tenant-detail.html',
  styleUrl: './tenant-detail.scss',
})
export class TenantDetail implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly messageService = inject(MessageService);
  // App é zoneless: mutação de campo comum em callback async não dispara CD sozinha.
  private readonly cdr = inject(ChangeDetectorRef);

  private readonly authBase = `${environment.apiUrl}/auth`;
  private readonly billingBase = `${environment.apiUrl}/billing/api/v1/subscriptions`;
  private readonly partnerBase = `${environment.apiUrl}/partner/api/v1/partners`;

  tenantId!: number;

  readonly tenant = signal<TenantModel | null>(null);
  readonly subscriptions = signal<SubscriptionRow[]>([]);
  readonly users = signal<TenantUser[]>([]);
  readonly origem = signal<OrigemModel | null>(null);
  readonly semOrigem = signal(false);
  readonly engajamento = signal<EngajamentoModel | null>(null);
  readonly audits = signal<AuditRow[]>([]);

  readonly loadingTenant = signal(true);
  readonly loadingSubs = signal(true);
  readonly loadingUsers = signal(true);
  readonly loadingOrigem = signal(true);
  readonly loadingEngajamento = signal(true);
  readonly loadingAudits = signal(false);

  // Drill-down de cobranças (assinatura)
  expandedRows: { [key: string]: boolean } = {};
  cobrancas: { [subscriptionId: string]: CobrancaModel[] } = {};
  cobrancasLoading: { [subscriptionId: string]: boolean } = {};
  reprocessLoading: { [subscriptionId: string]: boolean } = {};

  // Cancelamento
  cancelDialogVisible = false;
  cancelTarget: SubscriptionRow | null = null;
  cancelLoading = false;

  // Auditoria: usuário selecionado
  selectedAuditUserId = '';

  ngOnInit(): void {
    this.tenantId = Number(this.route.snapshot.paramMap.get('id'));
    if (!this.tenantId) {
      this.router.navigate(['/admin/cadastros/tenants']);
      return;
    }
    this.loadTenant();
    this.loadSubscriptions();
    this.loadUsers();
    this.loadOrigem();
    this.loadEngajamento();
  }

  voltar() {
    this.router.navigate(['/admin/cadastros/tenants']);
  }

  private loadTenant() {
    this.loadingTenant.set(true);
    this.http.get<TenantModel>(`${this.authBase}/tenants/${this.tenantId}`).subscribe({
      next: (t) => {
        this.tenant.set(t);
        this.loadingTenant.set(false);
      },
      error: () => {
        this.loadingTenant.set(false);
        this.messageService.add({
          severity: 'error',
          summary: 'Erro',
          detail: 'Erro ao carregar o tenant.',
        });
      },
    });
  }

  private loadSubscriptions() {
    this.loadingSubs.set(true);
    const params = new HttpParams()
      .set('tenantId', this.tenantId)
      .set('size', 50)
      .set('sort', 'createdAt,desc');
    this.http.get<any>(this.billingBase, { params }).subscribe({
      next: (res) => {
        this.subscriptions.set(res?.content ?? []);
        this.loadingSubs.set(false);
      },
      error: () => this.loadingSubs.set(false),
    });
  }

  private loadUsers() {
    this.loadingUsers.set(true);
    const params = new HttpParams().set('size', 100).set('sort', 'displayName,asc');
    this.http
      .post<any>(`${this.authBase}/users/search`, { tenantId: this.tenantId }, { params })
      .subscribe({
        next: (res) => {
          const list: TenantUser[] = res?.content ?? [];
          this.users.set(list);
          this.loadingUsers.set(false);
          if (list.length > 0) {
            this.selectedAuditUserId = list[0].id;
            this.loadAudits();
          }
        },
        error: () => this.loadingUsers.set(false),
      });
  }

  private loadOrigem() {
    this.loadingOrigem.set(true);
    this.http
      .get<OrigemModel>(`${this.partnerBase}/referrals/by-tenant/${this.tenantId}`)
      .subscribe({
        next: (o) => {
          // 200 sem corpo (ResponseEntity.of vazio) chega como null → cadastro direto
          if (o) {
            this.origem.set(o);
          } else {
            this.semOrigem.set(true);
          }
          this.loadingOrigem.set(false);
        },
        error: (err) => {
          this.loadingOrigem.set(false);
          if (err.status === 404) {
            this.semOrigem.set(true);
          } else {
            this.messageService.add({
              severity: 'error',
              summary: 'Erro',
              detail: 'Erro ao carregar a origem.',
            });
          }
        },
      });
  }

  loadEngajamento() {
    this.loadingEngajamento.set(true);
    this.http
      .get<EngajamentoModel>(`${this.partnerBase}/engajamento/by-tenant/${this.tenantId}`)
      .subscribe({
        next: (e) => {
          this.engajamento.set(e);
          this.loadingEngajamento.set(false);
        },
        error: () => {
          this.loadingEngajamento.set(false);
          this.messageService.add({
            severity: 'error',
            summary: 'Erro',
            detail: 'Erro ao carregar o engajamento.',
          });
        },
      });
  }

  /** % da barra de uso de uma feature, relativo à mais usada. */
  barraFeature(f: FeatureStat): number {
    const max = Math.max(1, ...this.engajamento()!.features.map((x) => x.accessCount));
    return Math.round((f.accessCount / max) * 100);
  }

  loadAudits() {
    if (!this.selectedAuditUserId) {
      return;
    }
    this.loadingAudits.set(true);
    const params = new HttpParams()
      .set('targetType', 'USER')
      .set('targetId', this.selectedAuditUserId)
      .set('size', 25)
      .set('sort', 'eventDate,desc');
    this.http.get<any>(`${this.authBase}/audits`, { params }).subscribe({
      next: (res) => {
        this.audits.set(res?.content ?? []);
        this.loadingAudits.set(false);
      },
      error: () => this.loadingAudits.set(false),
    });
  }

  // ── Assinatura: ações reaproveitadas da tela Assinaturas ──

  toggleRow(row: SubscriptionRow) {
    const abrir = !this.expandedRows[row.id];
    this.expandedRows = { ...this.expandedRows, [row.id]: abrir };
    if (abrir && !this.cobrancas[row.id]) {
      this.cobrancasLoading[row.id] = true;
      this.http.get<CobrancaModel[]>(`${this.billingBase}/${row.id}/cobrancas`).subscribe({
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
    this.http.post<any>(`${this.billingBase}/${row.id}/reprocess`, {}).subscribe({
      next: (res) => {
        this.reprocessLoading[row.id] = false;
        this.cdr.markForCheck();
        this.messageService.add({
          severity: res.ativada ? 'success' : 'info',
          summary: res.ativada ? 'Ativada' : 'Sem mudança',
          detail: res.mensagem,
        });
        if (res.ativada) {
          this.subscriptions.update((rows) =>
            rows.map((r) => (r.id === row.id ? { ...r, status: res.status } : r)),
          );
          delete this.cobrancas[row.id];
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
    this.http.post<any>(`${this.billingBase}/${row.id}/cancel`, {}).subscribe({
      next: (res) => {
        this.cancelLoading = false;
        this.cancelDialogVisible = false;
        this.subscriptions.update((rows) =>
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
      case 'ATIVO':
      case 'ATIVA':
        return base + 'bg-green-100 text-green-700';
      case 'TRIAL':
        return base + 'bg-blue-100 text-blue-700';
      case 'CONVIDADO':
        return base + 'bg-purple-100 text-purple-700';
      case 'AGUARDANDO_PAGAMENTO':
      case 'PENDENTE':
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
