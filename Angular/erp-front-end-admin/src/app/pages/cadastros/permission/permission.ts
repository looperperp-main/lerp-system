import {Component, signal} from '@angular/core';
import {ColumnConfig} from '../../../components/table/data-table';
import {PermissionModel} from './permission.model';
import {ConfirmationService, MessageService, PrimeTemplate} from 'primeng/api';
import {PermissionService} from './permission.service';
import {HttpErrorResponse} from '@angular/common/http';
import {Toast} from 'primeng/toast';
import {Dialog} from 'primeng/dialog';
import {Ripple} from 'primeng/ripple';
import {ButtonDirective} from 'primeng/button';
import {Tooltip} from 'primeng/tooltip';
import {HtmlDecodePipe} from '../../../util/pipe/html-decode.pipe';
import {DatePipe, NgForOf, NgIf} from '@angular/common';
import {TableModule} from 'primeng/table';
import {PermissionForm} from './permission-form/permission-form';

@Component({
  selector: 'app-permission',
  imports: [
    Toast,
    Dialog,
    PrimeTemplate,
    Ripple,
    ButtonDirective,
    Tooltip,
    HtmlDecodePipe,
    DatePipe,
    TableModule,
    PermissionForm,
    NgForOf,
    NgIf
  ],
  templateUrl: './permission.html',
  providers: [MessageService, ConfirmationService],
  styleUrl: './permission.scss',
})
export class Permission {
  // Configuração da Tela e Tabela
  permissionDialog: boolean = false;
  permissions = signal<PermissionModel[]>([]);
  permission: PermissionModel = { code: '', domain: '', description: '' };

  loading = signal<boolean>(true);
  totalRecords = signal<number>(0);

  // Variáveis para exclusão
  deleteDialogVisible: boolean = false;
  permissionToDelete: PermissionModel | null = null;

  // Colunas
  cols: ColumnConfig[] = [
    { field: 'id', header: 'ID', type: 'text' },
    { field: 'code', header: 'Código', type: 'text' },
    { field: 'domain', header: 'Domínio', type: 'text' },
    { field: 'description', header: 'Descrição', type: 'text' },
    { field: 'createdDate', header: 'Data Criação', type: 'date' },
    { field: 'createdBy', header: 'Data Criação', type: 'text' },
    { field: 'lastUpdateDate', header: 'Data da Ultima Atualização', type: 'date' },
    { field: 'lastUpdatedBy', header: 'Atualizado Por', type: 'text' },
    { field: 'actions', header: 'Ações', type: 'actions' }
  ];

  constructor(
    private messageService: MessageService,
    private permissionService: PermissionService
  ) {}

  ngOnInit() {}

  onLazyLoad(event: any) {
    const page = event.first / event.rows;
    const size = event.rows;
    this.loadPermissions(page, size);
  }

  loadPermissions(page: number = 0, size: number = 10) {
    this.loading.set(true);
    this.permissionService.getPermissions(page, size).subscribe({
      next: (response) => {
        this.permissions.set(response.content || []);
        this.totalRecords.set(response.totalElements || 0);
        this.loading.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.handleError(err, 'Erro ao carregar permissões');
        this.loading.set(false);
      }
    });
  }

  openNew() {
    this.permission = { code: '', domain: '', description: '' };
    this.permissionDialog = true;
  }

  editPermission(permission: PermissionModel) {
    this.permission = { ...permission };
    this.permissionDialog = true;
  }

  savePermission(savedData: PermissionModel) {
    if (savedData.id) {
      // Atualizar
      this.permissionService.updatePermission(savedData).subscribe({
        next: (updated) => {
          this.permissions.update(current => current.map(p => p.id === updated.id ? updated : p));
          this.messageService.add({ severity: 'success', summary: 'Sucesso', detail: 'Permissão atualizada.', life: 3000 });
        },
        error: (err: HttpErrorResponse) => this.handleError(err, 'Falha ao atualizar')
      });
    } else {
      // Criar Novo
      this.permissionService.createPermission(savedData).subscribe({
        next: () => {
          this.loadPermissions(0, 10); // Volta pra primeira página
          this.messageService.add({ severity: 'success', summary: 'Sucesso', detail: 'Permissão criada.', life: 3000 });
        },
        error: (err: HttpErrorResponse) => this.handleError(err, 'Falha ao criar permissão')
      });
    }
  }

  // --- Deleção ---
  deletePermission(permission: PermissionModel) {
    this.permissionToDelete = { ...permission };
    this.deleteDialogVisible = true;
  }

  hideDeleteDialog() {
    this.deleteDialogVisible = false;
    this.permissionToDelete = null;
  }

  confirmDelete() {
    if (this.permissionToDelete && this.permissionToDelete.id) {
      this.permissionService.deletePermission(this.permissionToDelete.id).subscribe({
        next: () => {
          this.permissions.update(current => current.filter(p => p.id !== this.permissionToDelete?.id));
          this.messageService.add({ severity: 'success', summary: 'Sucesso', detail: 'Permissão deletada.', life: 3000 });
          this.hideDeleteDialog();
          // Opcional: subtrair 1 do totalRecords ou recarregar a pagina
        },
        error: (err: HttpErrorResponse) => this.handleError(err, 'Erro ao deletar')
      });
    }
  }

  private handleError(err: HttpErrorResponse, defaultSummary: string) {
    if (err.error && err.error.message && err.error.error && err.error.status) {
      const detailMsg = `[${err.error.status}] ${err.error.error} - ${err.error.message}`;
      this.messageService.add({ severity: 'error', summary: defaultSummary, detail: detailMsg, life: 5000 });
    } else {
      this.messageService.add({ severity: 'error', summary: defaultSummary, detail: 'Erro inesperado.', life: 5000 });
    }
  }

  exportData() {
    this.messageService.add({ severity: 'info', summary: 'Info', detail: 'Funcionalidade de exportação aqui' });
  }
}
