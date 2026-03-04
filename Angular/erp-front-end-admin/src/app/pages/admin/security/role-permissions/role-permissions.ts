import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ToastModule } from 'primeng/toast';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { TooltipModule } from 'primeng/tooltip';
import { MessageService } from 'primeng/api';

import { ColumnConfig } from '../../../../components/table/data-table';
import { HttpErrorResponse } from '@angular/common/http';
import {RolePermissionsForm} from './role-permission-form/role-permission-form';
import {RoleModel} from '../../../cadastros/roles/roles/role.model';
import {RoleService} from '../../../cadastros/roles/roles/role.service';

@Component({
  selector: 'app-role-permissions',
  standalone: true,
  imports: [CommonModule, ToastModule, TableModule, ButtonModule, TooltipModule, RolePermissionsForm],
  providers: [MessageService],
  templateUrl: './role-permissions.html',
  styleUrl: './role-permissions.scss',
})
export class RolePermissions implements OnInit {
  roles = signal<RoleModel[]>([]);
  totalRecords = signal<number>(0);
  loading = signal<boolean>(true);

  // Variáveis para o Modal do Picklist
  dialogVisible: boolean = false;
  selectedRole!: RoleModel;

  cols: ColumnConfig[] = [
    { field: 'name', header: 'Nome da Role', type: 'text' },
    { field: 'tenantId', header: 'Tenant ID', type: 'text' },
    { field: 'actions', header: 'Configurar', type: 'actions' }
  ];

  constructor(
    private roleService: RoleService,
    private messageService: MessageService
  ) {}

  ngOnInit() {}

  onLazyLoad(event: any) {
    const page = event.first / event.rows;
    const size = event.rows;
    this.loadRoles(page, size);
  }

  loadRoles(page: number = 0, size: number = 10) {
    this.loading.set(true);
    this.roleService.getRolesbyPage(page, size).subscribe({
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
}
