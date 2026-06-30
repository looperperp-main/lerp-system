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
import { PermissionModel, RoleModel, SecurityService } from '../security.service';

@Component({
  selector: 'app-security-role-permissions',
  standalone: true,
  imports: [CommonModule, FormsModule, TableModule, ButtonModule, DialogModule, InputTextModule, PickListModule, TooltipModule, Ripple, ToastModule],
  providers: [MessageService],
  templateUrl: './role-permissions.html',
})
export class SecurityRolePermissions {
  roles = signal<RoleModel[]>([]);
  totalRecords = signal<number>(0);
  loading = signal<boolean>(true);
  filterName: string | null = null;

  dialog = false;
  saving = false;
  selectedRole: RoleModel | null = null;
  source: PermissionModel[] = []; // disponíveis (scope TENANT)
  target: PermissionModel[] = []; // atribuídas
  private originalIds = new Set<string>();

  constructor(private service: SecurityService, private messages: MessageService, private cdr: ChangeDetectorRef) {}

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

  openConfig(role: RoleModel) {
    this.selectedRole = role;
    this.dialog = true;
    forkJoin({
      all: this.service.listPermissions(),
      assigned: role.id ? this.service.getRolePermissions(role.id) : of([] as PermissionModel[]),
    }).subscribe({
      next: ({ all, assigned }) => {
        const allPerms = all.content || [];
        this.target = assigned;
        this.originalIds = new Set(assigned.map((p) => p.id!));
        this.source = allPerms.filter((p) => !this.originalIds.has(p.id!));
        this.cdr.detectChanges();
      },
      error: () => this.messages.add({ severity: 'error', summary: 'Erro', detail: 'Erro ao carregar permissões' }),
    });
  }

  save() {
    if (!this.selectedRole?.id) return;
    this.saving = true;
    const roleId = this.selectedRole.id;
    const targetIds = this.target.map((p) => p.id!).filter(Boolean);
    const removed = [...this.originalIds].filter((id) => !targetIds.includes(id));

    const removals = removed.map((id) => this.service.removePermission(roleId, id));
    const finalize = () => {
      this.service.assignPermissions(roleId, targetIds).subscribe({
        next: () => {
          this.messages.add({ severity: 'success', summary: 'Sucesso', detail: 'Permissões salvas!' });
          this.saving = false;
          this.dialog = false;
        },
        error: () => this.fail(),
      });
    };

    if (removals.length === 0) {
      finalize();
    } else {
      forkJoin(removals).subscribe({ next: finalize, error: () => this.fail() });
    }
  }

  private fail() {
    this.saving = false;
    this.messages.add({ severity: 'error', summary: 'Erro', detail: 'Erro ao salvar atribuições' });
  }
}