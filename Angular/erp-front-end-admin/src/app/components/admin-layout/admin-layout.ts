import { Component } from '@angular/core';
import {Router, RouterOutlet} from '@angular/router';
import {CommonModule} from '@angular/common';
import {LogoutService} from '../../pages/login/service/logout';
import {ToastrService} from 'ngx-toastr';
import {MenuItem} from 'primeng/api';
import {Toolbar} from 'primeng/toolbar';
import {Button} from 'primeng/button';
import {PanelMenu} from 'primeng/panelmenu';
import {InputTextModule} from 'primeng/inputtext';
import {Menu} from 'primeng/menu';

@Component({
  selector: 'app-admin-layout',
  standalone: true,
  imports: [RouterOutlet, CommonModule, PanelMenu, InputTextModule],
  templateUrl: './admin-layout.html',
  styleUrl: './admin-layout.scss',
})
export class AdminLayout {
  isCollapsed = false;
  isDropdownOpen = false;
  userEmail = sessionStorage.getItem('username') || 'dummy@gmail.com';
  logoutError = '';

  menuItems: MenuItem[] = [
    { label: 'Home', icon: 'pi pi-home', routerLink: '/admin/home' },
    { label: 'Cadastros', icon: 'pi pi-folder', path: '/admin/cadastros',items: [
      { label: 'Usuários', icon: 'pi pi-user', routerLink: '/users' },
      { label: 'Tenants', icon: 'pi pi-user', routerLink: '/admin/cadastros/tenants' }
      ] },
    { label: 'Relatórios', icon: 'pi pi-chart-bar', routerLink: '/admin/relatorios' },
    { label: 'Configurações', icon: 'pi pi-cog', routerLink: '/admin/config' },
  ];

  userMenuItems: MenuItem[] = [
    { label: 'Sair', icon: 'pi pi-sign-out', command: () => this.logout() }
  ];

  constructor(private router: Router,
              private logoutService: LogoutService,
              private toastService: ToastrService) {}

  toggleSidebar() {
    this.isCollapsed = !this.isCollapsed;
  }

  toggleDropdown() {
    this.isDropdownOpen = !this.isDropdownOpen;
  }

  logout() {
    this.logoutError = '';
    this.logoutService.logout().subscribe({
      next: () => {
        this.toastService.success('Logout realizado com sucesso');
        this.isDropdownOpen = false;
        sessionStorage.removeItem('auth-token');
        sessionStorage.removeItem('username');
        sessionStorage.removeItem('refresh-token');
        this.router.navigate(['/login-admin']);
      },
      error: () => {
        this.logoutError = 'Erro ao sair. Tente novamente.';
        this.toastService.error('Erro ao realizar logout');
      }
    });
  }

}
