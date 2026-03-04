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

@Component({
  selector: 'app-user-roles',
  standalone: true,
  imports: [CommonModule, ToastModule, TableModule, ButtonModule, TooltipModule, UserRoleForm],
  providers: [MessageService],
  templateUrl: './user-roles.html'
})
export class UserRolesComponent implements OnInit {

  users = signal<UsersPageModel[]>([]);
  totalRecords = signal<number>(0);
  loading = signal<boolean>(true);

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
    private messageService: MessageService
  ) {}

  ngOnInit() {}

  onLazyLoad(event: any) {
    const page = event.first / event.rows;
    const size = event.rows;
    this.loadUsers(page, size);
  }

  loadUsers(page: number = 0, size: number = 10) {
    this.loading.set(true);
    this.userService.getActiveUsers(page, size).subscribe({
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

  openConfig(user: UsersPageModel) {
    this.selectedUser = user;
    this.dialogVisible = true;
  }
}
