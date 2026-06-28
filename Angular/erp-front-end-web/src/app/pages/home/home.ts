import { Component, OnInit, signal } from '@angular/core';
import { CurrencyPipe, NgForOf, NgIf } from '@angular/common';
import { MessageService, PrimeTemplate } from 'primeng/api';
import { TableModule } from 'primeng/table';
import { ToastModule } from 'primeng/toast';
import { Router } from '@angular/router';
import { TenantService } from '../../services/tenant.service';

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [NgIf, NgForOf, CurrencyPipe, NgForOf, NgIf, PrimeTemplate, TableModule, ToastModule],
  providers: [MessageService],
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

  // Mock Data para Produtos Mais Vendidos
  products = [
    {
      name: 'T-shirt estampada azul',
      category: 'T-shirt',
      price: 60.0,
      sold: 520,
      sales: 31200,
      image: 'assets/images/tshirt.png', // Placeholder path
    },
    {
      name: 'Bermuda jeans escuro',
      category: 'Bermuda',
      price: 95.0,
      sold: 250,
      sales: 23750,
      image: 'assets/images/shorts.png',
    },
    {
      name: 'Camisa polo amarelo claro',
      category: 'Camisa Polo',
      price: 75.0,
      sold: 120,
      sales: 9000,
      image: 'assets/images/polo.png',
    },
    {
      name: 'Tênis casual branco',
      category: 'Tênis',
      price: 250.0,
      sold: 30,
      sales: 7500,
      image: 'assets/images/sneakers.png',
    },
    {
      name: 'Calça social preta',
      category: 'Calça',
      price: 150.0,
      sold: 15,
      sales: 2250,
      image: 'assets/images/pants.png',
    },
  ];

  // Mock Data para Notificações
  notifications = [
    {
      type: 'sale',
      message: 'Jonas comprou uma T-shirt estampada azul por R$60,00',
      date: 'Hoje',
      icon: 'pi pi-dollar',
      color: 'text-orange-500',
      bg: 'bg-orange-100',
    },
    {
      type: 'sale',
      message: 'Lucas comprou uma Calça social preta por R$150,00',
      date: 'Hoje',
      icon: 'pi pi-dollar',
      color: 'text-orange-500',
      bg: 'bg-orange-100',
    },
    {
      type: 'alert',
      message: 'Sua solicitação de saque no valor de R$2.500,00 foi iniciada.',
      date: 'Hoje',
      icon: 'pi pi-exclamation-triangle',
      color: 'text-orange-500',
      bg: 'bg-orange-100',
    },
    {
      type: 'sale',
      message: 'Renato comprou uma Bermuda jeans escuro por R$95,00',
      date: 'Ontem',
      icon: 'pi pi-dollar',
      color: 'text-orange-500',
      bg: 'bg-orange-100',
    },
    {
      type: 'comment',
      message: 'Maria postou um comentário sobre o seu produto',
      date: 'Ontem',
      icon: 'pi pi-comment',
      color: 'text-orange-500',
      bg: 'bg-orange-100',
    },
    {
      type: 'sale',
      message: 'Vanessa comprou 2 Tênis casual branco por R$500,00',
      date: 'Ontem',
      icon: 'pi pi-dollar',
      color: 'text-orange-500',
      bg: 'bg-orange-100',
    },
    {
      type: 'trend',
      message: 'Sua receita teve um aumento de 25%',
      date: 'Última Semana',
      icon: 'pi pi-chart-line',
      color: 'text-orange-500',
      bg: 'bg-orange-100',
    },
    {
      type: 'like',
      message: '20 usuários adicionaram seus produtos a lista de favoritos',
      date: 'Última Semana',
      icon: 'pi pi-heart',
      color: 'text-orange-500',
      bg: 'bg-orange-100',
    },
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

  emDesenvolvimento(recurso: string) {
    this.messageService.add({
      severity: 'info',
      summary: 'Em Desenvolvimento',
      detail: `A funcionalidade '${recurso}' será liberada em breve!`,
      life: 3000,
    });
  }
}
