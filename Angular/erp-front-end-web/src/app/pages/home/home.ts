import {Component} from '@angular/core';
import {CurrencyPipe, NgForOf, NgIf} from '@angular/common';
import {PrimeTemplate} from 'primeng/api';
import {TableModule} from 'primeng/table';


@Component({
  selector: 'app-home',
  standalone: true,
  imports: [
    NgIf,
    NgForOf,
    CurrencyPipe,
    NgForOf,
    NgIf,
    PrimeTemplate,
    TableModule
  ],
  templateUrl: './home.html',
  styleUrl: './home.scss',
})
export class Home {
  userEmail = sessionStorage.getItem('username') || 'dummy@gmail.com';

  cards = [
    {
      title: 'Pedidos',
      value: '13.465',
      description: '24 novos desde a última visita',
      icon: 'pi pi-box',
      bgIcon: 'bg-orange-100',
      textIcon: 'text-orange-500',
      isPositive: true
    },
    {
      title: 'Receita',
      value: 'R$2.135',
      description: '52%+ desde a última semana',
      icon: 'pi pi-dollar',
      bgIcon: 'bg-orange-100',
      textIcon: 'text-orange-500',
      isPositive: true
    },
    {
      title: 'Clientes',
      value: '16.841',
      description: '520 novos registros',
      icon: 'pi pi-users',
      bgIcon: 'bg-orange-100',
      textIcon: 'text-orange-500',
      isPositive: true
    },
    {
      title: 'Comentários',
      value: '152 não lidos',
      description: '85 respondidos',
      icon: 'pi pi-comments',
      bgIcon: 'bg-orange-100',
      textIcon: 'text-orange-500',
      isPositive: true
    }
  ];

  // Mock Data para Produtos Mais Vendidos
  products = [
    {
      name: 'T-shirt estampada azul',
      category: 'T-shirt',
      price: 60.00,
      sold: 520,
      sales: 31200,
      image: 'assets/images/tshirt.png' // Placeholder path
    },
    {
      name: 'Bermuda jeans escuro',
      category: 'Bermuda',
      price: 95.00,
      sold: 250,
      sales: 23750,
      image: 'assets/images/shorts.png'
    },
    {
      name: 'Camisa polo amarelo claro',
      category: 'Camisa Polo',
      price: 75.00,
      sold: 120,
      sales: 9000,
      image: 'assets/images/polo.png'
    },
    {
      name: 'Tênis casual branco',
      category: 'Tênis',
      price: 250.00,
      sold: 30,
      sales: 7500,
      image: 'assets/images/sneakers.png'
    },
    {
      name: 'Calça social preta',
      category: 'Calça',
      price: 150.00,
      sold: 15,
      sales: 2250,
      image: 'assets/images/pants.png'
    }
  ];

  // Mock Data para Notificações
  notifications = [
    { type: 'sale', message: 'Jonas comprou uma T-shirt estampada azul por R$60,00', date: 'Hoje', icon: 'pi pi-dollar', color: 'text-orange-500', bg: 'bg-orange-100' },
    { type: 'sale', message: 'Lucas comprou uma Calça social preta por R$150,00', date: 'Hoje', icon: 'pi pi-dollar', color: 'text-orange-500', bg: 'bg-orange-100' },
    { type: 'alert', message: 'Sua solicitação de saque no valor de R$2.500,00 foi iniciada.', date: 'Hoje', icon: 'pi pi-exclamation-triangle', color: 'text-orange-500', bg: 'bg-orange-100' },
    { type: 'sale', message: 'Renato comprou uma Bermuda jeans escuro por R$95,00', date: 'Ontem', icon: 'pi pi-dollar', color: 'text-orange-500', bg: 'bg-orange-100' },
    { type: 'comment', message: 'Maria postou um comentário sobre o seu produto', date: 'Ontem', icon: 'pi pi-comment', color: 'text-orange-500', bg: 'bg-orange-100' },
    { type: 'sale', message: 'Vanessa comprou 2 Tênis casual branco por R$500,00', date: 'Ontem', icon: 'pi pi-dollar', color: 'text-orange-500', bg: 'bg-orange-100' },
    { type: 'trend', message: 'Sua receita teve um aumento de 25%', date: 'Última Semana', icon: 'pi pi-chart-line', color: 'text-orange-500', bg: 'bg-orange-100' },
    { type: 'like', message: '20 usuários adicionaram seus produtos a lista de favoritos', date: 'Última Semana', icon: 'pi pi-heart', color: 'text-orange-500', bg: 'bg-orange-100' }
  ];

  constructor(  ) {  }
}
