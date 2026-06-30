import { ChangeDetectorRef, Component, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
import { PickListModule } from 'primeng/picklist';
import { TooltipModule } from 'primeng/tooltip';
import { Ripple } from 'primeng/ripple';
import { ToastModule } from 'primeng/toast';
import { MessageService } from 'primeng/api';
import { forkJoin, of } from 'rxjs';
import { RoleModel, SecurityService, UserModel } from '../security.service';

@Component({
  selector: 'app-security-user-roles',
  standalone: true,
  imports: [CommonModule, FormsModule, TableModule, ButtonModule, DialogModule, InputTextModule, PickListModule, TooltipModule, Ripple, ToastModule],
  providers: [MessageService],
  templateUrl: './user-roles.html',
})
export class SecurityUserRoles {
  users = signal<UserModel[]>([]);
  totalRecords = signal<number>(0);
  loading = signal<boolean>(true);
  filterName: string | null = null;

  dialog = false;
  saving = false;
  selectedUser: UserModel | null = null;
  source: RoleModel[] = []; // roles disponíveis
  target: RoleModel[] = []; // roles do usuário

  constructor(private service: SecurityService, private messages: MessageService, private cdr: ChangeDetectorRef) {}

  onLazyLoad(event: any) {
    const page = event.first / event.rows;
    let sort = '';
    if (event.sortField) sort = `${event.sortField},${event.sortOrder === 1 ? 'asc' : 'desc'}`;
    this.load(page, event.rows, sort);
  }

  load(page = 0, size = 10, sort = '') {
    this.loading.set(true);
    this.service.searchUsers(page, size, { displayName: this.filterName || null, active: true }, sort).subscribe({
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

  openConfig(user: UserModel) {
    this.selectedUser = user;
    this.dialog = true;
    forkJoin({
      all: this.service.listRoles(),
      assigned: user.id ? this.service.getUserRoles(user.id) : of([] as RoleModel[]),
    }).subscribe({
      next: ({ all, assigned }) => {
        this.target = assigned;
        const targetIds = new Set(assigned.map((r) => r.id));
        this.source = all.filter((r) => !targetIds.has(r.id));
        this.cdr.detectChanges();
      },
      error: () => this.messages.add({ severity: 'error', summary: 'Erro', detail: 'Erro ao carregar roles' }),
    });
  }

  save() {
    if (!this.selectedUser?.id) return;
    this.saving = true;
    // assignRoles faz sincronização completa no backend (adiciona e remove).
    const roleIds = this.target.map((r) => r.id!).filter(Boolean);
    this.service.assignRoles(this.selectedUser.id, roleIds).subscribe({
      next: () => {
        this.messages.add({ severity: 'success', summary: 'Sucesso', detail: 'Acessos salvos!' });
        this.saving = false;
        this.dialog = false;
      },
      error: () => {
        this.saving = false;
        this.messages.add({ severity: 'error', summary: 'Erro', detail: 'Erro ao salvar acessos' });
      },
    });
  }
}