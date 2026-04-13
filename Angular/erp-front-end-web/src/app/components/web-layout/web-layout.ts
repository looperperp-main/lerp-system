import { Component } from '@angular/core';
import {NgForOf, NgIf, NgOptimizedImage} from "@angular/common";
import {Router, RouterLink, RouterLinkActive, RouterOutlet} from "@angular/router";
import {MenuItem} from 'primeng/api';
import {TenantLoginService} from '../../pages/login/service/tenant-login.service';
import {ToastrService} from 'ngx-toastr';

@Component({
  selector: 'app-web-layout',
  standalone: true,
  imports: [
    NgForOf,
    NgIf,
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    NgOptimizedImage
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
    { label: 'Cadastros', icon: 'pi pi-folder', path: '/web/cadastros',items: [
        { label: 'Grupo de Clientes', icon: 'pi pi-user', routerLink: '/web/cadastros/grp_c' },
        { label: 'Depositos', icon: 'pi pi-box', routerLink: '/web/cadastros/depositos' },
        { label: 'Condições de Pagamento', icon: 'pi pi-id-card', routerLink: '/web/cadastros/cond-pagamento' },
        { label: 'Pessoas', icon: 'pi pi-user', routerLink: '/web/cadastros/pessoas' }
      ] },
    { label: 'Segurança', icon: 'pi pi-server', routerLink: '/web/security', items: [
        { label: 'Configurar Roles', icon: 'pi pi-sitemap', routerLink: '/web/security/role-permissions' },
        { label: 'Atribuir Acessos', icon: 'pi pi-key', routerLink: '/web/security/user-roles' }
      ] },
    { label: 'Auditoria', icon: 'pi pi-eye', routerLink: '/web/audit' },
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
        this.toastService.success('Logout realizado com sucesso');
        this.isDropdownOpen = false;
        sessionStorage.clear();
        /*sessionStorage.removeItem('auth-token');
        sessionStorage.removeItem('username');
        sessionStorage.removeItem('refresh-token');*/
        this.router.navigate(['/login-admin']);
      },
      error: () => {
        this.logoutError = 'Erro ao sair. Tente novamente.';
        this.toastService.error('Erro ao realizar logout');
      }
    });
  }
}
