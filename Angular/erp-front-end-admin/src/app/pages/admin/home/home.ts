import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TableModule } from 'primeng/table';
import { ChartModule } from 'primeng/chart';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../../environments/environment';
import { TenantService } from '../../cadastros/tenant/tenant/tenant.service';
import { UserService } from '../../cadastros/tenant/users/users.service';
import { ParceirosService } from '../../cadastros/parceiros/parceiros.service';
import { AuditService } from '../audit/audit.service';
import { AuditLog } from '../audit/auditLog.model';
import { HtmlDecodePipe } from '../../../util/pipe/html-decode.pipe';

interface DashboardCard {
  title: string;
  value: string;
  icon: string;
  bgIcon: string;
  textIcon: string;
}

interface FunilEtapa {
  label: string;
  count: number;
  percent: number;
  barClass: string;
}

interface TopContador {
  contador: string;
  crc?: string;
  totalIndicacoes: number;
  convertidas: number;
}

@Component({
  selector: 'app-home',
  imports: [CommonModule, TableModule, ChartModule, HtmlDecodePipe],
  templateUrl: './home.html',
  styleUrl: './home.scss',
})
export class Home implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly tenantService = inject(TenantService);
  private readonly userService = inject(UserService);
  private readonly parceirosService = inject(ParceirosService);
  private readonly auditService = inject(AuditService);

  userEmail = sessionStorage.getItem('username') || 'dummy@gmail.com';

  cards = signal<DashboardCard[]>([
    {
      title: 'Tenants',
      value: '—',
      icon: 'pi pi-receipt',
      bgIcon: 'bg-orange-100',
      textIcon: 'text-orange-500',
    },
    {
      title: 'Usuários',
      value: '—',
      icon: 'pi pi-users',
      bgIcon: 'bg-orange-100',
      textIcon: 'text-orange-500',
    },
    {
      title: 'Parceiros',
      value: '—',
      icon: 'pi pi-calculator',
      bgIcon: 'bg-orange-100',
      textIcon: 'text-orange-500',
    },
    {
      title: 'Assinaturas Ativas',
      value: '—',
      icon: 'pi pi-dollar',
      bgIcon: 'bg-orange-100',
      textIcon: 'text-orange-500',
    },
  ]);

  funil = signal<FunilEtapa[]>([]);
  topContadores = signal<TopContador[]>([]);
  maxIndicacoes = signal(1);

  tenantChartData = signal<any>(null);
  mrrChartData = signal<any>(null);
  notificacoes = signal<AuditLog[]>([]);

  doughnutOptions = {
    cutout: '65%',
    plugins: {
      legend: {
        position: 'bottom',
        labels: {
          usePointStyle: true,
          pointStyle: 'circle',
          boxWidth: 8,
          boxHeight: 8,
          padding: 16,
          font: { size: 12 },
        },
      },
    },
    maintainAspectRatio: false,
  };
  lineOptions = {
    plugins: { legend: { display: false } },
    maintainAspectRatio: false,
    scales: { y: { beginAtZero: true } },
  };

  ngOnInit() {
    // ponytail: contagens e gráficos agregados no front lendo até 200 registros; endpoint agregado no backend só se a base crescer
    this.tenantService.getTenants(0, 200).subscribe((res) => {
      this.setCard(0, String(res.totalElements));
      this.buildTenantChart(res.content ?? []);
    });
    // ponytail: só TENANT_USER conta como "usuário"; owners e parceiros têm card próprio
    this.userService
      .searchUsers(0, 1, { userType: 'TENANT_USER' })
      .subscribe((res) => this.setCard(1, String(res.totalElements)));
    this.parceirosService
      .getAll(0, 1, 'name,asc', 'ATIVO')
      .subscribe((res) => this.setCard(2, String(res.page?.totalElements ?? 0)));

    this.http
      .get<any>(`${environment.apiUrl}/billing/api/v1/subscriptions?size=200&sort=createdAt,desc`)
      .subscribe((res) => {
        const subs = res?.content ?? [];
        const ativas = subs.filter((s: any) => s.status === 'ATIVA');
        const total = ativas.reduce((sum: number, s: any) => sum + Number(s.value || 0), 0);
        this.setCard(3, total.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' }));
      });

    this.http
      .get<any[]>(`${environment.apiUrl}/billing/api/v1/subscriptions/mrr`)
      .subscribe((meses) => this.buildMrrChart(meses ?? []));

    // ponytail: curadoria no front (busca 100, filtra, mostra 8); filtro no backend se o feed ficar pobre
    const rotina = ['LOGIN_SUCCESS', 'TENANT_LOGIN_SUCCESS', 'PARTNER_LOGIN_SUCCESS', 'LOGOUT'];
    this.auditService.getLogs(0, 100, 'eventDate,desc').subscribe((res) => {
      const relevantes = (res.content ?? [])
        .filter((log) => !!log.eventDate)
        .filter((log) => log.result !== 'SUCCESS' || !rotina.includes(log.action))
        .slice(0, 20);
      this.notificacoes.set(relevantes);
    });

    this.parceirosService.getIndicacoes().subscribe((contadores) => {
      const indicacoes = contadores.flatMap((c: any) => c.indicacoes ?? []);
      this.buildFunil(indicacoes);

      const top = [...contadores]
        .sort(
          (a: any, b: any) =>
            b.convertidas - a.convertidas || b.totalIndicacoes - a.totalIndicacoes,
        )
        .slice(0, 5);
      this.topContadores.set(top);
      this.maxIndicacoes.set(Math.max(1, ...top.map((c: any) => c.totalIndicacoes)));
    });
  }

  private setCard(index: number, value: string) {
    this.cards.update((cards) => cards.map((c, i) => (i === index ? { ...c, value } : c)));
  }

  private buildTenantChart(tenants: any[]) {
    const cores: Record<string, string> = {
      ATIVO: '#10b981',
      TRIAL: '#3b82f6',
      CONVIDADO: '#8b5cf6',
      PENDENTE: '#f59e0b',
      SUSPENSO: '#ef4444',
      CANCELADO: '#9ca3af',
    };
    const porStatus = new Map<string, number>();
    tenants.forEach((t) => porStatus.set(t.status, (porStatus.get(t.status) ?? 0) + 1));
    // ordem fixa (mapa de cores) pra legenda não embaralhar a cada carga; desconhecidos vão pro fim
    const ordem = (s: string) => {
      const i = Object.keys(cores).indexOf(s);
      return i === -1 ? 99 : i;
    };
    const labels = [...porStatus.keys()].sort((a, b) => ordem(a) - ordem(b));
    this.tenantChartData.set({
      labels: labels.map((l) => `${l} (${porStatus.get(l)})`),
      datasets: [
        {
          data: labels.map((l) => porStatus.get(l)),
          backgroundColor: labels.map((l) => cores[l] ?? '#d1d5db'),
          borderWidth: 2,
          borderColor: '#ffffff',
          hoverOffset: 6,
        },
      ],
    });
  }

  private buildMrrChart(meses: { mes: string; valor: number }[]) {
    const labels = meses.map((m) => {
      const [ano, mesNum] = m.mes.split('-');
      return new Date(Number(ano), Number(mesNum) - 1, 1).toLocaleDateString('pt-BR', {
        month: 'short',
        year: '2-digit',
      });
    });
    this.mrrChartData.set({
      labels,
      datasets: [
        {
          label: 'MRR (R$)',
          data: meses.map((m) => Number(m.valor)),
          fill: true,
          tension: 0.4,
          borderColor: '#f97316',
          backgroundColor: 'rgba(249, 115, 22, 0.12)',
        },
      ],
    });
  }

  notificacaoTexto(log: AuditLog): string {
    const substantivos: Record<string, string> = {
      PARCEIRO: 'Parceiro',
      TENANT: 'Tenant',
      USER: 'Usuário',
      USER_ROLE: 'Role de usuário',
      ROLE_PERMISSION: 'Permissão de role',
      ROLE: 'Role',
      PERMISSION: 'Permissão',
      PLAN: 'Plano',
      CONVITE: 'Convite',
      PESSOA: 'Pessoa',
      CLIENTE: 'Cliente',
      CONTATO: 'Contato',
      ENDERECO: 'Endereço',
      TABELA_PRECO: 'Tabela de preço',
      CONDICAO_PAGAMENTO: 'Condição de pagamento',
      CONDICAO_PAGAMENTO_PARCELA: 'Parcela de condição de pagamento',
      TENANT_SUBSCRIPTION: 'Assinatura do tenant',
    };
    const verbos: Record<string, string> = {
      INSERT: 'cadastrado(a)',
      UPDATE: 'atualizado(a)',
      DELETE: 'excluído(a)',
      APPROVE: 'aprovado(a)',
      REJECT: 'rejeitado(a)',
      INACTIVATE: 'inativado(a)',
      ACTIVATED: 'ativado(a)',
      RESEND: 'reenviado(a)',
      ASSIGN: 'atribuído(a)',
      STATUS: 'com status alterado',
      FAILED: 'falhou',
    };
    const action = log.action ?? '';
    const idx = action.lastIndexOf('_');
    const sujeito = idx > 0 ? action.slice(0, idx) : action;
    const verbo = idx > 0 ? action.slice(idx + 1) : '';
    if (substantivos[sujeito] && verbos[verbo]) {
      return `${substantivos[sujeito]} ${verbos[verbo]}`;
    }
    // ponytail: fallback legível pra actions fora do mapa
    const texto = action.replaceAll('_', ' ').toLowerCase();
    return texto.charAt(0).toUpperCase() + texto.slice(1);
  }

  notificacaoDetalhe(log: AuditLog): string | null {
    const d = (log.detailsJson ?? '').trim();
    // ponytail: detailsJson é mensagem fixa livre; só escondo o vazio/placeholder
    if (!d || d === '{}' || d === 'null') {
      return null;
    }
    return d;
  }

  notificacaoIcone(log: AuditLog): { icon: string; classes: string } {
    if (log.result === 'FAILED' || log.result === 'ERROR') {
      return { icon: 'pi pi-exclamation-triangle', classes: 'bg-red-100 text-red-500' };
    }
    if ((log.action ?? '').startsWith('LOGIN') || (log.action ?? '').startsWith('LOGOUT')) {
      return { icon: 'pi pi-sign-in', classes: 'bg-blue-100 text-blue-500' };
    }
    return { icon: 'pi pi-bell', classes: 'bg-orange-100 text-orange-500' };
  }

  private buildFunil(indicacoes: any[]) {
    const total = Math.max(1, indicacoes.length);
    const count = (statuses: string[]) =>
      indicacoes.filter((i) => statuses.includes(i.status)).length;
    // ponytail: funil pelo status atual de cada indicação; sem histórico de transições
    const etapas: FunilEtapa[] = [
      { label: 'Convidados', count: indicacoes.length, barClass: 'bg-orange-400', percent: 0 },
      {
        label: 'Ativados / Trial',
        count: count(['ATIVADO', 'TRIAL', 'FOLLOWUP', 'CONVERTIDO']),
        barClass: 'bg-blue-400',
        percent: 0,
      },
      { label: 'Em follow-up', count: count(['FOLLOWUP']), barClass: 'bg-purple-400', percent: 0 },
      {
        label: 'Convertidos',
        count: count(['CONVERTIDO']),
        barClass: 'bg-emerald-500',
        percent: 0,
      },
    ];
    etapas.forEach((e) => (e.percent = Math.round((e.count / total) * 100)));
    this.funil.set(etapas);
  }
}
