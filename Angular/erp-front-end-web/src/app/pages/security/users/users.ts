import { Component, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
import { TooltipModule } from 'primeng/tooltip';
import { Ripple } from 'primeng/ripple';
import { ToastModule } from 'primeng/toast';
import { MessageService } from 'primeng/api';
import { SecurityService, UserModel } from '../security.service';
import { UserForm } from './user-form/user-form';
import { Breadcrumb } from '../../../components/breadcrumb/breadcrumb';

@Component({
  selector: 'app-security-users',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    TableModule,
    ButtonModule,
    DialogModule,
    InputTextModule,
    TooltipModule,
    Ripple,
    ToastModule,
    UserForm,
    Breadcrumb,
  ],
  templateUrl: './users.html',
  styleUrl: './users.scss',
})
export class SecurityUsers {
  users = signal<UserModel[]>([]);
  totalRecords = signal<number>(0);
  loading = signal<boolean>(true);

  filterName: string | null = null;

  dialog = false;
  selectedUser: UserModel | null = null;

  constructor(
    private service: SecurityService,
    private messages: MessageService,
  ) {}

  onLazyLoad(event: any) {
    const page = event.first / event.rows;
    let sort = '';
    if (event.sortField) sort = `${event.sortField},${event.sortOrder === 1 ? 'asc' : 'desc'}`;
    this.load(page, event.rows, sort);
  }

  load(page = 0, size = 10, sort = '') {
    this.loading.set(true);
    this.service
      .searchUsers(page, size, { displayName: this.filterName || null, active: null }, sort)
      .subscribe({
        next: (res) => {
          this.users.set(res.content || []);
          this.totalRecords.set(res.totalElements || 0);
          this.loading.set(false);
        },
        error: () => {
          this.messages.add({
            severity: 'error',
            summary: 'Erro',
            detail: 'Erro ao carregar usuários',
          });
          this.loading.set(false);
        },
      });
  }

  openNew() {
    this.selectedUser = null;
    this.dialog = true;
  }

  openEdit(user: UserModel) {
    this.selectedUser = user;
    this.dialog = true;
  }

  onFormSaved(): void {
    this.dialog = false;
    this.load();
  }

  onFormCanceled(): void {
    this.dialog = false;
  }

  isLocked(user: UserModel): boolean {
    return !!user.lockedUntil && new Date(user.lockedUntil).getTime() > Date.now();
  }

  unlockUser(user: UserModel) {
    if (!user.id) return;
    this.service.unlockUser(user.id).subscribe({
      next: () => {
        this.messages.add({
          severity: 'success',
          summary: 'Sucesso',
          detail: 'Usuário desbloqueado!',
        });
        this.load();
      },
      error: () => {
        this.messages.add({
          severity: 'error',
          summary: 'Erro',
          detail: 'Erro ao desbloquear usuário',
        });
      },
    });
  }

  toggleStatus(user: UserModel) {
    if (!user.id) return;
    this.service.updateUserStatus(user.id).subscribe({
      next: () => {
        this.messages.add({
          severity: 'success',
          summary: 'Sucesso',
          detail: 'Status atualizado!',
        });
        this.load();
      },
      error: () => {
        this.messages.add({
          severity: 'error',
          summary: 'Erro',
          detail: 'Erro ao atualizar status',
        });
      },
    });
  }
}
