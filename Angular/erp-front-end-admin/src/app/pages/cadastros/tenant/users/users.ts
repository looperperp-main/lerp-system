import {Component, OnInit, signal} from '@angular/core';
import {ButtonDirective} from "primeng/button";
import {CnpjPipe} from "../../../../util/pipe/cnpj.pipe";
import {HtmlDecodePipe} from "../../../../util/pipe/html-decode.pipe";
import {DatePipe, NgForOf, NgIf} from "@angular/common";
import {MessageService, PrimeTemplate} from "primeng/api";
import {Ripple} from "primeng/ripple";
import {TableModule} from "primeng/table";
import {Toast} from "primeng/toast";
import {Tooltip} from "primeng/tooltip";
import {UserAccountModel, UsersPageModel} from './usersPage.model';
import {ColumnConfig} from '../../../../components/table/data-table';
import {HttpErrorResponse} from '@angular/common/http';
import {UserService} from './users.service';
import {UserForm} from './user-form/user-form';
import {TenantService} from '../tenant/tenant.service';
import {TenantModel} from '../tenant/tenant.model';

@Component({
  selector: 'app-users',
  imports: [
    ButtonDirective,
    CnpjPipe,
    HtmlDecodePipe,
    NgForOf,
    NgIf,
    PrimeTemplate,
    Ripple,
    TableModule,
    Toast,
    Tooltip,
    DatePipe,
    UserForm
  ],
  providers: [MessageService],
  templateUrl: './users.html',
  styleUrl: './users.scss',
})
export class Users implements OnInit  {

  userDialogVisible: boolean = false;
  userToSave: UserAccountModel = { displayName: '', email: '', passwordHash: '', tenantId: undefined };


  cols: ColumnConfig[] = [
    { field: 'id', header: 'ID', type: 'text' },
    { field: 'tenantId', header: 'Tenant', type: 'text' },
    { field: 'email', header: 'Email', type: 'text' },
    { field: 'displayName', header: 'Nome do Usuário', type: 'text' },
    { field: 'active', header: 'Ativo', type: 'status' },
    { field: 'lockedUntil', header: 'Bloqueado Até', type: 'date' },
    { field: 'createdBy', header: 'Criado Por', type: 'text' },
    { field: 'createdDate', header: 'Data Criação', type: 'date' },
    { field: 'lastUpdatedBy', header: 'Atualizado Por', type: 'text' },
    { field: 'lastUpdateDate', header: 'Data Atualização', type: 'date' },
    { field: 'actions', header: 'Ações', type: 'actions' }
  ];

  loading = signal<boolean>(true);
  totalRecords = signal<number>(0);
  users = signal<UsersPageModel[]>([]);

  // Lista de tenants para o dropdown
  tenantsList = signal<TenantModel[]>([]);

  constructor(
    private messageService: MessageService,
    private userService: UserService,
    private tenantService: TenantService
  ) {}

  ngOnInit() {
    this.loadTenantsForDropdown();
  }

  loadTenantsForDropdown() {
    // Buscando os 100 primeiros (ou crie um endpoint no backend que retorna uma lista simples sem paginação)
    this.tenantService.getTenantsActive(0, 100).subscribe({
      next: (res) => {
        this.tenantsList.set(res.content || []);
      },
      error: () => this.messageService.add({ severity: 'error', summary: 'Erro', detail: 'Falha ao carregar lista de Tenants' })
    });
  }

  openNew() {
    this.userToSave = { displayName: '', email: '', passwordHash: '', tenantId: undefined };
    this.userDialogVisible = true;
  }

  onLazyLoad(event: any) {
    const page = event.first / event.rows;
    const size = event.rows;
    this.loadUsers(page, size);
  }

  loadUsers(page: number = 0, size: number = 10) {
    this.loading.set(true);
    this.userService.getUsers(page, size).subscribe({
      next: (response) => {
        this.users.set(response.content || []);
        this.totalRecords.set(response.totalElements || 0);
        this.loading.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.handleError(err, 'Erro ao carregar Logs de Auditoria');
        this.loading.set(false);
      }
    });
  }

  exportData() {
    this.messageService.add({ severity: 'info', summary: 'Info', detail: 'Funcionalidade de exportação aqui' });
  }

  saveUser(user: UserAccountModel) {
    this.userService.createUser(user).subscribe({
      next: () => {
        this.loadUsers();
        this.messageService.add({ severity: 'success', summary: 'Sucesso', detail: 'Usuário criado com sucesso!', life: 3000 });
      },
      error: (err: HttpErrorResponse) => {
        this.handleError(err, 'Erro ao criar usuário');
      }
    });
  }

  updateStatus(id: string){
    this.userService.updateStatus(id).subscribe({
      next: () => {
        this.loadUsers();
        this.messageService.add({ severity: 'success', summary: 'Sucesso', detail: 'Status atualizado com sucesso!', life: 3000 });
      },
      error: (err: HttpErrorResponse) => {
        this.handleError(err, 'Erro ao atualizar status do usuário');
      }
    });
  }

  private handleError(err: HttpErrorResponse, defaultSummary: string) {
    // Verifica se o erro possui o corpo do seu StandardError.java
    if (err.error && err.error.message && err.error.error && err.error.status) {

      // Formata a mensagem para mostrar o Status, Erro e a Mensagem detalhada
      const detailMsg = `[${err.error.status}] ${err.error.error} - ${err.error.message}`;

      this.messageService.add({
        severity: 'error',
        summary: defaultSummary,
        detail: detailMsg,
        life: 5000 // Aumentei o tempo de vida para 5 segundos para dar tempo de ler
      });

    } else {
      // Fallback caso seja um erro genérico (ex: API offline, timeout, etc)
      this.messageService.add({
        severity: 'error',
        summary: defaultSummary,
        detail: 'Erro inesperado de comunicação com o servidor.',
        life: 5000
      });
    }
  }
}
