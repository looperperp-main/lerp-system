import { Component } from '@angular/core';
import {NgForOf, NgIf} from "@angular/common";
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
    RouterLinkActive
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
    { label: 'Overview', icon: 'pi pi-home', routerLink: '/admin/home' },
    { label: 'Cadastros', icon: 'pi pi-folder', path: '/admin/cadastros',items: [
        { label: 'Usuários', icon: 'pi pi-user', routerLink: '/admin/cadastros/users' },
        { label: 'Tenants', icon: 'pi pi-user', routerLink: '/admin/cadastros/tenants' },
        { label: 'Roles', icon: 'pi pi-id-card', routerLink: '/admin/cadastros/roles' },
        { label: 'Permissões', icon: 'pi pi-shield', routerLink: '/admin/cadastros/permission' }
      ] },
    { label: 'Segurança', icon: 'pi pi-server', routerLink: '/admin/security', items: [
        { label: 'Configurar Roles', icon: 'pi pi-sitemap', routerLink: '/admin/security/role-permissions' },
        { label: 'Atribuir Acessos', icon: 'pi pi-key', routerLink: '/admin/security/user-roles' }
      ] },
    { label: 'Auditoria', icon: 'pi pi-eye', routerLink: '/admin/audit' },
    { label: 'Subscrições', icon: 'pi pi-barcode', path: '/admin/subscriptions',items: [
        { label: 'Planos', icon: 'pi pi-book', routerLink: '/admin/cadastros/plans' },
        { label: 'Assinaturas', icon: 'pi pi-wallet', routerLink: '/admin/cadastros/subscription' },
        { label: 'Pagamentos', icon: 'pi pi-receipt', routerLink: '/admin/cadastros/invoices' },
      ] },
    { label: 'Relatórios', icon: 'pi pi-chart-bar', routerLink: '/admin/relatorios' },
    { label: 'Configurações', icon: 'pi pi-cog', routerLink: '/admin/config' }
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
