import { Component, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
import { TooltipModule } from 'primeng/tooltip';
import { Ripple } from 'primeng/ripple';
import { ToastModule } from 'primeng/toast';
import { MessageService } from 'primeng/api';
import { SecurityService, UserModel } from '../security.service';

@Component({
  selector: 'app-security-users',
  standalone: true,
  imports: [CommonModule, FormsModule, TableModule, ButtonModule, DialogModule, InputTextModule, TooltipModule, Ripple, ToastModule],
  providers: [MessageService],
  templateUrl: './users.html',
})
export class SecurityUsers {
  users = signal<UserModel[]>([]);
  totalRecords = signal<number>(0);
  loading = signal<boolean>(true);

  filterName: string | null = null;

  dialog = false;
  submitted = false;
  editing = false;
  form: { id?: string; email: string; displayName: string; passwordHash: string } = { email: '', displayName: '', passwordHash: '' };

  constructor(private service: SecurityService, private messages: MessageService) {}

  onLazyLoad(event: any) {
    const page = event.first / event.rows;
    let sort = '';
    if (event.sortField) sort = `${event.sortField},${event.sortOrder === 1 ? 'asc' : 'desc'}`;
    this.load(page, event.rows, sort);
  }

  load(page = 0, size = 10, sort = '') {
    this.loading.set(true);
    this.service.searchUsers(page, size, { displayName: this.filterName || null, active: null }, sort).subscribe({
      next: (res) => {
        this.users.set(res.content || []);
        this.totalRecords.set(res.totalElements || 0);
        this.loading.set(false);
      },
      error: () => {
        this.messages.add({ severity: 'error', summary: 'Erro', detail: 'Erro ao carregar usuários' });
        this.loading.set(false);
      },
    });
  }

  openNew() {
    this.editing = false;
    this.submitted = false;
    this.form = { email: '', displayName: '', passwordHash: '' };
    this.dialog = true;
  }

  openEdit(user: UserModel) {
    this.editing = true;
    this.submitted = false;
    this.form = { id: user.id, email: user.email, displayName: user.displayName, passwordHash: '' };
    this.dialog = true;
  }

  save() {
    this.submitted = true;
    if (!this.form.displayName?.trim()) return;
    if (!this.editing && (!this.form.email?.trim() || !this.form.passwordHash?.trim())) return;

    const done = (msg: string) => {
      this.messages.add({ severity: 'success', summary: 'Sucesso', detail: msg });
      this.dialog = false;
      this.load();
    };

    if (this.editing && this.form.id) {
      const body: any = { email: this.form.email, displayName: this.form.displayName };
      if (this.form.passwordHash?.trim()) body.passwordHash = this.form.passwordHash;
      this.service.updateUser(this.form.id, body).subscribe({
        next: () => done('Usuário atualizado!'),
        error: (err: HttpErrorResponse) => this.handleError(err, 'Erro ao atualizar usuário'),
      });
    } else {
      this.service.createUser({ email: this.form.email, displayName: this.form.displayName, passwordHash: this.form.passwordHash }).subscribe({
        next: () => done('Usuário criado!'),
        error: (err: HttpErrorResponse) => this.handleError(err, 'Erro ao criar usuário'),
      });
    }
  }

  toggleStatus(user: UserModel) {
    if (!user.id) return;
    this.service.updateUserStatus(user.id).subscribe({
      next: () => {
        this.messages.add({ severity: 'success', summary: 'Sucesso', detail: 'Status atualizado!' });
        this.load();
      },
      error: (err: HttpErrorResponse) => this.handleError(err, 'Erro ao atualizar status'),
    });
  }

  private handleError(err: HttpErrorResponse, summary: string) {
    const detail = err.error?.message ? `[${err.error.status}] ${err.error.error} - ${err.error.message}` : 'Erro de comunicação com o servidor.';
    this.messages.add({ severity: 'error', summary, detail, life: 5000 });
  }
}