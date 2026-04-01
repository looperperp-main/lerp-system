import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ToastModule } from 'primeng/toast';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { TooltipModule } from 'primeng/tooltip';
import { MessageService } from 'primeng/api';
import { HttpErrorResponse } from '@angular/common/http';

import { ColumnConfig } from '../../../../components/table/data-table';
import { UserRoleForm } from './user-role-form/user-role-form';
import {UserAccountModel, UsersPageModel} from '../../../cadastros/tenant/users/usersPage.model';
import {UserService} from '../../../cadastros/tenant/users/users.service';
import {Select} from 'primeng/select';
import {FormsModule} from '@angular/forms';
import {InputText} from 'primeng/inputtext';
import {PrimaryButtonComponent} from '../../../../components/primary-button/primary-button';
import {TenantModel} from '../../../cadastros/tenant/tenant/tenant.model';
import {TenantService} from '../../../cadastros/tenant/tenant/tenant.service';

@Component({
  selector: 'app-user-roles',
  standalone: true,
  imports: [CommonModule, ToastModule, TableModule, ButtonModule, TooltipModule, UserRoleForm, Select, FormsModule, InputText, PrimaryButtonComponent],
  providers: [MessageService],
  templateUrl: './user-roles.html'
})
export class UserRolesComponent implements OnInit {

  users = signal<UsersPageModel[]>([]);
  totalRecords = signal<number>(0);
  loading = signal<boolean>(true);

  // Variável para a lista de tenants no dropdown
  tenantsList = signal<TenantModel[]>([]);

  // DTO de Filtros específicos desta tela
  filters = {
    tenantId: null as number | null,
    displayName: null as string | null,
    email: null as string | null,
    active: true // Busca apenas ativos por padrão
  };

  dialogVisible: boolean = false;
  selectedUser!: UsersPageModel;

  cols: ColumnConfig[] = [
    { field: 'displayName', header: 'Nome', type: 'text' },
    { field: 'email', header: 'Email', type: 'text' },
    { field: 'tenantId', header: 'Tenant ID', type: 'text' },
    { field: 'actions', header: 'Configurar', type: 'actions' }
  ];

  constructor(
    private userService: UserService,
    private tenantService: TenantService,
    private messageService: MessageService
  ) {}

  ngOnInit() {
    this.loadTenantsForDropdown();
  }

  loadTenantsForDropdown() {
    this.tenantService.getTenantsActive(0, 100).subscribe({
      next: (res) => {
        this.tenantsList.set(res.content || []);
      },
      error: () => this.messageService.add({ severity: 'error', summary: 'Erro', detail: 'Falha ao carregar lista de Tenants' })
    });
  }

  onLazyLoad(event: any) {
    const page = event.first / event.rows;
    const size = event.rows;
    let sortStr = '';
    if (event.sortField) {
      // Se o campo for tenantId (como está no field do json de exibição),
      // nós traduzimos pro nome do atributo correto do Back-End (UserAccount -> tenant -> name)
      let fieldToOrder = event.sortField;
      if (fieldToOrder === 'tenantId') {
        fieldToOrder = 'tenant.name'; // Ou 'tenant.id' dependendo da sua preferência visual
      }

      const direction = event.sortOrder === 1 ? 'asc' : 'desc';
      sortStr = `${fieldToOrder},${direction}`;
    }
    this.loadUsers(page, size,sortStr);
  }

  loadUsers(page: number = 0, size: number = 10,sortStr: string = '') {
    this.loading.set(true);
    const payload = {
      tenantId: this.filters.tenantId,
      displayName: this.filters.displayName ? this.filters.displayName : null,
      email: this.filters.email ? this.filters.email : null,
      active: this.filters.active
    };

    this.userService.searchUsers(page, size,payload, sortStr).subscribe({
      next: (response) => {
        this.users.set(response.content || []);
        this.totalRecords.set(response.totalElements || 0);
        this.loading.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.messageService.add({ severity: 'error', summary: 'Erro', detail: 'Erro ao carregar Usuários' });
        this.loading.set(false);
      }
    });
  }

  applyFilters() {
    this.loadUsers(0, 10);
  }

  clearFilters() {
    this.filters = {
      tenantId: null,
      displayName: null,
      email: null,
      active: true
    };
    this.loadUsers(0, 10);
  }

  openConfig(user: UsersPageModel) {
    this.selectedUser = user;
    this.dialogVisible = true;
  }
}
