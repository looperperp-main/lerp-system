import {Component} from '@angular/core';
import {Router, RouterModule, RouterOutlet} from '@angular/router';
import {CommonModule} from '@angular/common';
import {LogoutService} from '../../pages/login/service/logout';
import {ToastrService} from 'ngx-toastr';
import {MenuItem} from 'primeng/api';
import {InputTextModule} from 'primeng/inputtext';

@Component({
  selector: 'app-admin-layout',
  standalone: true,
  imports: [RouterOutlet, CommonModule, RouterModule, InputTextModule],
  templateUrl: './admin-layout.html',
  styleUrl: './admin-layout.scss',
})
export class AdminLayout {
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
      { label: 'Usuários', icon: 'pi pi-user', routerLink: '/users' },
      { label: 'Tenants', icon: 'pi pi-user', routerLink: '/admin/cadastros/tenants' }
      ] },
    { label: 'Auditoria', icon: 'pi pi-eye', routerLink: '/admin/audit' },
    { label: 'Relatórios', icon: 'pi pi-chart-bar', routerLink: '/admin/relatorios' },
    { label: 'Configurações', icon: 'pi pi-cog', routerLink: '/admin/config' }
  ];

  /*userMenuItems: MenuItem[] = [
    { label: 'Sair', icon: 'pi pi-sign-out', command: () => this.logout() }
  ];*/

  constructor(private router: Router,
              private logoutService: LogoutService,
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
