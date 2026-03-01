import {Component,signal} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {ConfirmationService, MessageService} from 'primeng/api';
import {ToastModule} from 'primeng/toast';
import {ToolbarModule} from 'primeng/toolbar';
import {ButtonModule} from 'primeng/button';
import {DialogModule} from 'primeng/dialog';
import {ConfirmDialogModule} from 'primeng/confirmdialog';
import {InputTextModule} from 'primeng/inputtext';
import {ColumnConfig} from '../../../../components/table/data-table';
import {TenantModel} from './tenant.model';
import {Ripple} from 'primeng/ripple';
import {TableModule} from 'primeng/table';
import {TenantForm} from './tenant-form/tenant-form';
import {TenantService} from './tenant.service';
import {CnpjPipe} from '../../../../util/pipe/cnpj.pipe';
import {HtmlDecodePipe} from '../../../../util/pipe/html-decode.pipe';
import {HttpErrorResponse} from '@angular/common/http';
import {Tooltip} from 'primeng/tooltip';

@Component({
  selector: 'app-tenant',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    ToastModule,
    ToolbarModule,
    ButtonModule,
    DialogModule,
    ConfirmDialogModule,
    InputTextModule,
    Ripple,
    TableModule,
    TenantForm,
    CnpjPipe,
    HtmlDecodePipe,
    Tooltip
  ],
  providers: [MessageService, ConfirmationService],
  templateUrl: './tenant.html',
  styleUrl: './tenant.scss'
})
export class Tenant {

  // Configuração da Tabela
  tenantDialog: boolean = false;
  tenants =  signal<TenantModel[]>([]);
  tenant: TenantModel = { name: '', cnpj: '', status: 'ATIVO' }; // Objeto vazio inicial
  selectedTenants: Tenant[] = [];
  submitted: boolean = false;
  loading = signal<boolean>(true);
  totalRecords = signal<number>(0);

  // Variáveis para o Dialog de Exclusão
  deleteDialogVisible: boolean = false;
  tenantToDelete: TenantModel | null = null;
  deleteReason: string = '';
  deleteSubmitted: boolean = false;

  // Opções de Status
  statuses = [
    { label: 'Ativo', value: 'ATIVO' },
    { label: 'Pendente', value: 'PENDENTE' },
    { label: 'Suspenso', value: 'SUSPENSO' },
    { label: 'Cancelado', value: 'CANCELADO' }
  ];

  // Definição das Colunas
  cols: ColumnConfig[] = [
    { field: 'id', header: 'ID', type: 'text' },
    { field: 'name', header: 'Nome', type: 'text' },
    { field: 'cnpj', header: 'CNPJ', type: 'text' },
    { field: 'status', header: 'Status', type: 'status' },
    { field: 'createdBy', header: 'Criado Por', type: 'text' },
    { field: 'creationDate', header: 'Data Criação', type: 'date' },
    { field: 'lastUpdatedBy', header: 'Atualizado Por', type: 'text' },
    { field: 'updateDate', header: 'Data Atualização', type: 'date' },
    { field: 'actions', header: 'Ações', type: 'actions' }
  ];

  constructor(
    private messageService: MessageService,
    private confirmationService: ConfirmationService,
    private tenantService: TenantService
  ) {}

  ngOnInit() {
    // Carregamento feito via lazy load do datatable (onLazyLoad)
  }

  onLazyLoad(event: any) {
    const page = event.first / event.rows;
    const size = event.rows;
    this.loadTenants(page, size);
  }

  loadTenants(page: number = 0, size: number = 10) {
    this.loading.set(true);
    this.tenantService.getTenants(page, size).subscribe({
      next: (response) => {
        this.tenants.set(response.content || []);
        this.totalRecords.set(response.totalElements || 0);
        this.loading.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.handleError(err, 'Erro ao carregar tenants');
        this.loading.set(false);
      }
    });
  }

  openNew() {
    this.tenant = { name: '', cnpj: '', status: 'PENDENTE' };
    this.tenantDialog = true;
  }

  editTenant(tenant: TenantModel) {
    this.tenant = { ...tenant };
    this.tenantDialog = true;
  }

  // 1. Abre o dialog de exclusão
  deleteTenant(tenant: TenantModel) {
    this.tenantToDelete = { ...tenant };
    this.deleteReason = '';
    this.deleteSubmitted = false;
    this.deleteDialogVisible = true;
  }

  // 2. Fecha o dialog sem fazer nada
  hideDeleteDialog() {
    this.deleteDialogVisible = false;
    this.tenantToDelete = null;
    this.deleteReason = '';
    this.deleteSubmitted = false;
  }

  // 3. Valida e executa a exclusão
  confirmDelete() {
    this.deleteSubmitted = true;

    // Validação
    if (!this.deleteReason || this.deleteReason.trim() === '') {
      this.messageService.add({ severity: 'warn', summary: 'Atenção', detail: 'O motivo é obrigatório' });
      return;
    }

    if (this.tenantToDelete && this.tenantToDelete.id) {
      const tenantId = this.tenantToDelete.id;

      this.tenantService.updateTenantStatus(tenantId, 'CANCELADO').subscribe({
        next: () => {
          this.tenants.update(currentTenants =>
            currentTenants.map(t => t.id === tenantId ? { ...t, status: 'CANCELADO' } : t)
          );
          this.messageService.add({ severity: 'success', summary: 'Sucesso', detail: 'Tenant excluído (Cancelado)', life: 3000 });
          this.hideDeleteDialog(); // Sucesso: fecha o dialog
        },
        error: (err: HttpErrorResponse) => {
          this.handleError(err, 'Erro ao excluir o Tenant');
          // Não fecha o dialog em caso de erro, para o usuário tentar novamente se quiser
        }
      });
    }
  }

  toggleTenantStatus(tenant: any) {
    const newStatus = tenant.status === 'ATIVO' ? 'SUSPENSO' : 'ATIVO';

    if(tenant.id) {
      this.tenantService.updateTenantStatus(tenant.id, newStatus).subscribe({
        next: () => {
          // Atualiza o estado da lista via Signal
          this.tenants.update(currentTenants =>
            currentTenants.map(t => t.id === tenant.id ? { ...t, status: newStatus } : t)
          );
          this.messageService.add({ severity: 'success', summary: 'Sucesso', detail: `Status alterado para ${newStatus}`, life: 3000 });
        },
        error: (err: HttpErrorResponse) => this.handleError(err, 'Erro ao alterar o status do Tenant')
      });
    }
  }

  saveTenant(savedTenant: any) {
    this.submitted = true;

    if (savedTenant.id) {
      // Atualizar Existente
      this.tenantService.updateTenant(savedTenant).subscribe({
        next: (updatedTenant) => {
          // Substitui o tenant editado na lista reativamente via Signal
          this.tenants.update(currentTenants =>
            currentTenants.map(t => t.id === updatedTenant.id ? updatedTenant : t)
          );
          this.messageService.add({ severity: 'success', summary: 'Sucesso', detail: 'Tenant Atualizado', life: 3000 });
        },
        error: (err: HttpErrorResponse) => this.handleError(err, 'Falha ao atualizar o Tenant')
      });
    } else {
      // Criar Novo
      this.tenantService.createTenant(savedTenant).subscribe({
        next: () => {
          this.loadTenants(0, 10); // Recarrega a primeira página
          this.messageService.add({ severity: 'success', summary: 'Sucesso', detail: 'Tenant Criado', life: 3000 });
        },
        error: (err: HttpErrorResponse) => this.handleError(err, 'Falha ao criar o Tenant')
      });
    }
  }

  exportData() {
    this.messageService.add({ severity: 'info', summary: 'Info', detail: 'Funcionalidade de exportação aqui' });
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
