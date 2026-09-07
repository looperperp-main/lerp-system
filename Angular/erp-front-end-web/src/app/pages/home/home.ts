import { Component, OnInit, signal } from '@angular/core';
import { CurrencyPipe, NgForOf, NgIf } from '@angular/common';
import { MessageService } from 'primeng/api';
import { ToastModule } from 'primeng/toast';
import { Router } from '@angular/router';
import { TenantService } from '../../services/tenant.service';
import { Breadcrumb } from '../../components/breadcrumb/breadcrumb';

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [NgIf, NgForOf, CurrencyPipe, ToastModule, Breadcrumb],
  templateUrl: './home.html',
  styleUrl: './home.scss',
})
export class Home implements OnInit {
  userEmail = sessionStorage.getItem('username') || 'dummy@gmail.com';

  readonly trialAtivo = signal(false);
  readonly trialDiasRestantes = signal<number | null>(null);

  cards = [
    {
      title: 'Total de Clientes',
      value: '1.245',
      description: '24 novos desde a semana passada',
      icon: 'pi pi-users',
      bgIcon: 'bg-blue-100',
      textIcon: 'text-blue-500',
      isPositive: true,
    },
    {
      title: 'Produtos Ativos',
      value: '356',
      description: 'Cadastrados no sistema',
      icon: 'pi pi-box',
      bgIcon: 'bg-orange-100',
      textIcon: 'text-orange-500',
      isPositive: true,
    },
    {
      title: 'Estoque Crítico',
      value: '12',
      description: 'Produtos precisando de reposição',
      icon: 'pi pi-exclamation-triangle',
      bgIcon: 'bg-red-100',
      textIcon: 'text-red-500',
      isPositive: false,
    },
    {
      title: 'Recebimentos (Hoje)',
      value: 'R$ 5.430,50',
      description: '85% já liquidado',
      icon: 'pi pi-wallet',
      bgIcon: 'bg-green-100',
      textIcon: 'text-green-500',
      isPositive: true,
    },
  ];

  // Mock Data: Faturamento mês atual vs anterior
  faturamento = {
    atual: 96534.2,
    anterior: 78200.5,
    variacao: 23.4,
  };

  // Mock Data: Pedidos de venda por status (funil)
  pedidosPorStatus = [
    { label: 'Orçamento', qty: 142, pct: 100 },
    { label: 'Pedido', qty: 98, pct: 69 },
    { label: 'Expedição', qty: 61, pct: 43 },
    { label: 'Faturado', qty: 47, pct: 33 },
  ];

  // Mock Data: contas a receber/pagar vencendo em 7 dias
  contasVencendo = [
    { tipo: 'Receber', pessoa: 'Comercial Alvorada Ltda', vencimento: '13/07/2026', valor: 4230.0 },
    {
      tipo: 'Pagar',
      pessoa: 'Distribuidora Rio Fornecimentos',
      vencimento: '14/07/2026',
      valor: 1890.5,
    },
    { tipo: 'Receber', pessoa: 'Mercado Boa Vista', vencimento: '15/07/2026', valor: 2650.0 },
    { tipo: 'Pagar', pessoa: 'Transportes Rápido SA', vencimento: '17/07/2026', valor: 980.0 },
    { tipo: 'Receber', pessoa: 'Loja Center Norte', vencimento: '18/07/2026', valor: 3120.75 },
  ];

  // Mock Data: compras pendentes de recebimento
  comprasPendentes = [
    {
      pedido: 'PC-1042',
      fornecedor: 'Insumos Sul Ltda',
      previsao: '14/07/2026',
      status: 'Aguardando entrega',
    },
    {
      pedido: 'PC-1047',
      fornecedor: 'Embalagens Prime',
      previsao: '16/07/2026',
      status: 'Em trânsito',
    },
    {
      pedido: 'PC-1051',
      fornecedor: 'Matéria-Prima Central',
      previsao: '20/07/2026',
      status: 'Aguardando entrega',
    },
  ];

  // Mock Data: alertas de cadastro incompleto
  alertasCadastro = [
    { message: '8 clientes sem e-mail cadastrado', icon: 'pi pi-exclamation-triangle' },
    { message: '15 produtos sem NCM informado', icon: 'pi pi-exclamation-triangle' },
    { message: '3 fornecedores sem CNPJ validado', icon: 'pi pi-exclamation-triangle' },
  ];

  // Mock Data: contagem de cadastros ativos
  cadastrosAtivos = {
    clientes: 1245,
    produtos: 356,
    fornecedores: 87,
  };

  // Mock Data: últimos logins / usuários ativos
  usuariosAtivos = [
    { nome: 'vitorff1234@gmail.com', ultimoAcesso: 'Hoje, 09:12', online: true },
    { nome: 'financeiro@empresa.com', ultimoAcesso: 'Hoje, 08:47', online: true },
    { nome: 'vendas1@empresa.com', ultimoAcesso: 'Ontem, 18:30', online: false },
    { nome: 'estoque@empresa.com', ultimoAcesso: 'Ontem, 14:05', online: false },
  ];

  // Mock Data: notificações do sistema
  notificacoesSistema = [
    { message: 'Sua assinatura vence em 5 dias.', date: 'Hoje', icon: 'pi pi-credit-card' },
    { message: 'Trial de um novo módulo expira em 2 dias.', date: 'Hoje', icon: 'pi pi-clock' },
    {
      message: 'Backup automático concluído com sucesso.',
      date: 'Ontem',
      icon: 'pi pi-check-circle',
    },
  ];

  // Mock Data: atividades recentes (auditoria resumida)
  atividadesRecentes = [
    {
      message: 'Vitor criou o pedido de venda PV-2201.',
      date: 'Há 12 min',
      icon: 'pi pi-plus-circle',
    },
    {
      message: 'Renato alterou o cadastro do cliente Mercado Boa Vista.',
      date: 'Há 40 min',
      icon: 'pi pi-pencil',
    },
    {
      message: 'Lucas aprovou o pedido de compra PC-1047.',
      date: 'Hoje, 08:15',
      icon: 'pi pi-check',
    },
    { message: 'Maria fez login no sistema.', date: 'Hoje, 08:02', icon: 'pi pi-sign-in' },
  ];

  constructor(
    private router: Router,
    private messageService: MessageService,
    private tenantService: TenantService,
  ) {}

  ngOnInit(): void {
    this.tenantService.getMe().subscribe({
      next: (t) => {
        this.trialAtivo.set(t.status === 'TRIAL');
        if (this.trialAtivo() && t.trialExpiresAt) {
          const diff = new Date(t.trialExpiresAt).getTime() - Date.now();
          this.trialDiasRestantes.set(Math.max(0, Math.ceil(diff / 86_400_000)));
        }
      },
      error: () => {},
    });
  }

  irParaAssinar(): void {
    this.router.navigate(['/web/assinar']);
  }

  // Métodos de Acesso Rápido
  novoCliente() {
    this.router.navigate(['/web/cadastros/clientes']);
  }

  cadastrarFornecedor() {
    this.router.navigate(['/web/cadastros/fornecedores']);
  }

  cadastrarProduto() {
    this.router.navigate(['/web/cadastros/produtos']);
  }

  novaVenda() {
    this.router.navigate(['/web/vendas/pedidos']);
  }

  emDesenvolvimento(recurso: string) {
    this.messageService.add({
      severity: 'info',
      summary: 'Em Desenvolvimento',
      detail: `A funcionalidade '${recurso}' será liberada em breve!`,
      life: 3000,
    });
  }
}
