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
import { RoleModel, SecurityService } from '../security.service';

@Component({
  selector: 'app-security-roles',
  standalone: true,
  imports: [CommonModule, FormsModule, TableModule, ButtonModule, DialogModule, InputTextModule, TooltipModule, Ripple, ToastModule],
  providers: [MessageService],
  templateUrl: './roles.html',
})
export class SecurityRoles {
  roles = signal<RoleModel[]>([]);
  totalRecords = signal<number>(0);
  loading = signal<boolean>(true);

  filterName: string | null = null;

  createDialog = false;
  newName = '';
  submitted = false;

  deleteDialog = false;
  roleToDelete: RoleModel | null = null;

  constructor(private service: SecurityService, private messages: MessageService) {}

  onLazyLoad(event: any) {
    const page = event.first / event.rows;
    let sort = '';
    if (event.sortField) sort = `${event.sortField},${event.sortOrder === 1 ? 'asc' : 'desc'}`;
    this.load(page, event.rows, sort);
  }

  load(page = 0, size = 10, sort = '') {
    this.loading.set(true);
    this.service.searchRoles(page, size, { name: this.filterName || null }, sort).subscribe({
      next: (res) => {
        this.roles.set(res.content || []);
        this.totalRecords.set(res.totalElements || 0);
        this.loading.set(false);
      },
      error: () => {
        this.messages.add({ severity: 'error', summary: 'Erro', detail: 'Erro ao carregar roles' });
        this.loading.set(false);
      },
    });
  }

  openNew() {
    this.newName = '';
    this.submitted = false;
    this.createDialog = true;
  }

  save() {
    this.submitted = true;
    const name = this.newName?.trim();
    if (!name) return;
    this.service.createRole(name.toUpperCase().replace(/\s/g, '_')).subscribe({
      next: () => {
        this.messages.add({ severity: 'success', summary: 'Sucesso', detail: 'Role criada!' });
        this.createDialog = false;
        this.load();
      },
      error: (err: HttpErrorResponse) => this.handleError(err, 'Falha ao criar a Role'),
    });
  }

  askDelete(role: RoleModel) {
    this.roleToDelete = role;
    this.deleteDialog = true;
  }

  confirmDelete() {
    if (!this.roleToDelete?.id) return;
    this.service.deleteRole(this.roleToDelete.id).subscribe({
      next: () => {
        this.messages.add({ severity: 'success', summary: 'Sucesso', detail: 'Role deletada.' });
        this.deleteDialog = false;
        this.roleToDelete = null;
        this.load();
      },
      error: (err: HttpErrorResponse) => this.handleError(err, 'Erro ao deletar Role'),
    });
  }

  private handleError(err: HttpErrorResponse, summary: string) {
    const detail = err.error?.message ? `[${err.error.status}] ${err.error.error} - ${err.error.message}` : 'Erro de comunicação com o servidor.';
    this.messages.add({ severity: 'error', summary, detail, life: 5000 });
  }
}