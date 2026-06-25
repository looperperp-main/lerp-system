import { Component } from '@angular/core';
import {NgOptimizedImage} from "@angular/common";
import {Router, RouterLink, RouterLinkActive, RouterOutlet} from "@angular/router";
import {MenuItem, MessageService} from 'primeng/api';
import {TenantLoginService} from '../../pages/login/service/tenant-login.service';
import {ToastrService} from 'ngx-toastr';
import {Toast} from 'primeng/toast';

@Component({
  selector: 'app-web-layout',
  standalone: true,
  imports: [
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    NgOptimizedImage,
    Toast
  ],
  templateUrl: './web-layout.html',
  styleUrl: './web-layout.scss',
})
export class WebLayout {
  isCollapsed = true;
  isDropdownOpen = false;
  isMobile = false;
  userEmail = sessionStorage.getItem('username') || 'dummy@gmail.com';
  logoutError = '';

  user = {
    name: this.userEmail,
    role: 'Admin',
    avatar: 'https://primefaces.org/cdn/primeng/images/demo/avatar/amyelsner.png' // Placeholder
  };

  menuItems: MenuItem[] = [
    { label: 'Overview', icon: 'pi pi-home', routerLink: '/web/home' },
    {
      label: 'Parceiros de Negócio',
      icon: 'pi pi-users',
      path: '/web/cadastros/parceiros',
      items: [
        { label: 'Pessoas (Geral)', icon: 'pi pi-user', routerLink: '/web/cadastros/pessoas' },
        { label: 'Clientes', icon: 'pi pi-shopping-bag', routerLink: '/web/cadastros/clientes' },
        { label: 'Fornecedores', icon: 'pi pi-truck', routerLink: '/web/cadastros/fornecedores' },
        { label: 'Vendedores', icon: 'pi pi-briefcase', routerLink: '/web/cadastros/vendedores' },
        { label: 'Transportadoras', icon: 'pi pi-map', routerLink: '/web/cadastros/transportadoras' },
        { label: 'Grupo de Clientes', icon: 'pi pi-sitemap', routerLink: '/web/cadastros/grp_c' }
      ]
    },
    {
      label: 'Estoque & Produtos',
      icon: 'pi pi-box',
      path: '/web/cadastros/produtos',
      items: [
        { label: 'Produtos', icon: 'pi pi-tags', routerLink: '/web/cadastros/produtos' },
        { label: 'Categorias', icon: 'pi pi-bookmark', routerLink: '/web/cadastros/categorias' },
        { label: 'Depósitos', icon: 'pi pi-building', routerLink: '/web/cadastros/depositos' },
        { label: 'Tabelas de Preço', icon: 'pi pi-dollar', routerLink: '/web/cadastros/tabela-preco' },
        { label: 'Preços por Grupo', icon: 'pi pi-users', routerLink: '/web/cadastros/tabela-preco-grupo' }
      ]
    },
    {
      label: 'Comercial & Financeiro',
      icon: 'pi pi-wallet',
      path: '/web/financeiro',
      items: [
        { label: 'Condições de Pagamento', icon: 'pi pi-id-card', routerLink: '/web/cadastros/cond-pagamento' }
        // A tabela de "condicao_pagamento_parcela" é gerenciada DENTRO da tela de Condição de Pagamento.
      ]
    },
    {
      label: 'Contas a Pagar',
      icon: 'pi pi-arrow-left',
      path: '/web/financeiro/pagaveis',
      items: [
        { label: 'Títulos a Pagar', icon: 'pi pi-file', routerLink: '/web/financeiro/pagaveis' },
        { label: 'Pagamentos / Baixas', icon: 'pi pi-money-bill', routerLink: '/web/financeiro/pagaveis/baixas' },
        { label: 'Adiantamentos a Fornecedores', icon: 'pi pi-arrow-up-right', routerLink: '/web/financeiro/pagaveis/adiantamentos' },
        { label: 'Empréstimos / Financiamentos', icon: 'pi pi-percentage', routerLink: '/web/financeiro/emprestimos' }
      ]
    },
    {
      label: 'Contas a Receber',
      icon: 'pi pi-arrow-right',
      path: '/web/financeiro/recebiveis',
      items: [
        { label: 'Títulos a Receber', icon: 'pi pi-file', routerLink: '/web/financeiro/recebiveis' },
        { label: 'Recebimentos / Baixas', icon: 'pi pi-wallet', routerLink: '/web/financeiro/recebiveis/baixas' },
        { label: 'Cobrança', icon: 'pi pi-megaphone', routerLink: '/web/financeiro/recebiveis/cobranca' },
        { label: 'Adiantamentos de Clientes', icon: 'pi pi-arrow-down-left', routerLink: '/web/financeiro/recebiveis/adiantamentos' },
        { label: 'Antecipação / Desconto', icon: 'pi pi-bolt', routerLink: '/web/financeiro/recebiveis/antecipacao' }
      ]
    },
    { label: 'Compensações', icon: 'pi pi-arrow-right-arrow-left', routerLink: '/web/financeiro/compensacoes' },

    { label: 'Segurança', icon: 'pi pi-server', routerLink: '/web/security', items: [
        { label: 'Configurar Roles', icon: 'pi pi-sitemap', routerLink: '/web/security/role-permissions' },
        { label: 'Atribuir Acessos', icon: 'pi pi-key', routerLink: '/web/security/user-roles' }
      ] },
    { label: 'Subscrições', icon: 'pi pi-barcode', path: '/web/subscriptions',items: [
        { label: 'Planos', icon: 'pi pi-book', routerLink: '/web/cadastros/plans' },
        { label: 'Assinaturas', icon: 'pi pi-wallet', routerLink: '/web/cadastros/subscription' },
        { label: 'Pagamentos', icon: 'pi pi-receipt', routerLink: '/web/cadastros/invoices' },
      ] },
    { label: 'Relatórios', icon: 'pi pi-chart-bar', routerLink: '/web/relatorios' },
    { label: 'Configurações', icon: 'pi pi-cog', routerLink: '/web/config' }
  ];

  /*userMenuItems: MenuItem[] = [
    { label: 'Sair', icon: 'pi pi-sign-out', command: () => this.logout() }
  ];*/

  constructor(private router: Router,
              private logoutService: TenantLoginService,
              private messageService: MessageService,
              private toastService: ToastrService) {

    this.checkScreenSize();
    window.addEventListener('resize', () => {
      this.checkScreenSize();
    });
  }

  checkScreenSize() {
    this.isMobile = window.innerWidth < 1024;

    this.isCollapsed = this.isMobile;
  }

  toggleSidebar() {
    this.isCollapsed = !this.isCollapsed;
  }

  toggleDropdown() {
    this.isDropdownOpen = !this.isDropdownOpen;
  }

  toggleSubmenu(item: any) {
    if (item.items) {
      item.expanded = !item.expanded;
    }
  }

  logout() {
    this.logoutError = '';
    this.logoutService.logout().subscribe({
      next: () => {
        this.messageService.add({ severity: 'success', summary: 'Sucesso', detail: 'Logout realizado com sucesso!' });
        this.isDropdownOpen = false;
        sessionStorage.clear();
        /*sessionStorage.removeItem('auth-token');
        sessionStorage.removeItem('username');
        sessionStorage.removeItem('refresh-token');*/
        this.router.navigate(['/login-admin']);
      },
      error: () => {
        this.logoutError = 'Erro ao sair. Tente novamente.';
        this.messageService.add({ severity: 'error', summary: 'Erro', detail: 'Erro ao realizar logout!' });
      }
    });
  }
}
