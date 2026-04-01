import {Component, OnInit, signal} from '@angular/core';
import {CommonModule} from '@angular/common';
import {ToastModule} from 'primeng/toast';
import {TableModule} from 'primeng/table';
import {ButtonModule} from 'primeng/button';
import {TooltipModule} from 'primeng/tooltip';
import {MessageService} from 'primeng/api';

import {ColumnConfig} from '../../../../components/table/data-table';
import {HttpErrorResponse} from '@angular/common/http';
import {RolePermissionsForm} from './role-permission-form/role-permission-form';
import {RoleModel} from '../../../cadastros/roles/roles/role.model';
import {RoleService} from '../../../cadastros/roles/roles/role.service';
import {PrimaryButtonComponent} from '../../../../components/primary-button/primary-button';
import {FormsModule, ReactiveFormsModule} from '@angular/forms';
import {Select} from 'primeng/select';

@Component({
  selector: 'app-role-permissions',
  standalone: true,
  imports: [CommonModule, ToastModule, TableModule, ButtonModule, TooltipModule, RolePermissionsForm, PrimaryButtonComponent, ReactiveFormsModule, Select, FormsModule],
  providers: [MessageService],
  templateUrl: './role-permissions.html',
  styleUrl: './role-permissions.scss',
})
export class RolePermissions implements OnInit {
  roles = signal<RoleModel[]>([]);
  totalRecords = signal<number>(0);
  loading = signal<boolean>(true);

  // Lista de options do dropdown (todas as roles sem paginação)
  rolesList = signal<RoleModel[]>([]);

  // Variáveis para o Modal do Picklist
  dialogVisible: boolean = false;
  selectedRole!: RoleModel;

  filters = {
    name: null as string | null
  };

  cols: ColumnConfig[] = [
    { field: 'name', header: 'Nome da Role', type: 'text' },
    { field: 'tenantId', header: 'Tenant ID', type: 'text' },
    { field: 'actions', header: 'Configurar', type: 'actions' }
  ];

  constructor(
    private roleService: RoleService,
    private messageService: MessageService
  ) {}

  ngOnInit() {
    this.loadRolesForDropdown();
  }

  loadRolesForDropdown() {
    // Busca todas as roles disponíveis (ou as 100 primeiras) para preencher o combobox
    this.roleService.getRolesbyPage(0, 100).subscribe({
      next: (res) => {
        this.rolesList.set(res.content || []);
      },
      error: () => this.messageService.add({ severity: 'error', summary: 'Erro', detail: 'Falha ao carregar lista de Roles pro Dropdown' })
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
    this.loadRoles(page, size, sortStr);
  }

  loadRoles(page: number = 0, size: number = 10, sortStr: string = '') {
    this.loading.set(true);
    // Usando endpoint de search em vez do getAll se o filtro estiver preenchido
    const payload = {
      name: this.filters.name ? this.filters.name : null
    };
    this.roleService.searchRoles(page, size, payload, sortStr).subscribe({
      next: (response) => {
        this.roles.set(response.content || []);
        this.totalRecords.set(response.totalElements || 0);
        this.loading.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.messageService.add({ severity: 'error', summary: 'Erro', detail: 'Erro ao carregar Roles' });
        this.loading.set(false);
      }
    });
  }

  openConfig(role: RoleModel) {
    this.selectedRole = role;
    this.dialogVisible = true;
  }

  clearFilters() {
    this.filters = { name: null };
    this.loadRoles(0, 10);
  }

  applyFilters() {
    this.loadRoles(0, 10);
  }
}
