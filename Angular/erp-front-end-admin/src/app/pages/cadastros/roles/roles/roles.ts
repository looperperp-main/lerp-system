import {Component, OnInit, signal} from '@angular/core';
import {Dialog} from 'primeng/dialog';
import {DatePipe, NgClass, NgForOf, NgIf} from '@angular/common';
import {Toast} from 'primeng/toast';
import {TableModule} from 'primeng/table';
import {ButtonDirective} from 'primeng/button';
import {Ripple} from 'primeng/ripple';
import {ColumnConfig} from '../../../../components/table/data-table';
import {RoleModel} from './role.model';
import {ConfirmationService, MessageService} from 'primeng/api';
import {RoleService} from './role.service';
import {HttpErrorResponse} from '@angular/common/http';
import {FormsModule} from '@angular/forms';
import {Tooltip} from 'primeng/tooltip';
import {InputText} from 'primeng/inputtext';
import {HtmlDecodePipe} from '../../../../util/pipe/html-decode.pipe';
import {CnpjPipe} from '../../../../util/pipe/cnpj.pipe';

@Component({
  selector: 'app-roles',
  imports: [
    Dialog,
    NgClass,
    Toast,
    TableModule,
    ButtonDirective,
    Ripple,
    FormsModule,
    DatePipe,
    Tooltip,
    InputText,
    NgForOf,
    NgIf,
    HtmlDecodePipe,
    CnpjPipe
  ],
  providers: [MessageService, ConfirmationService],
  templateUrl: './roles.html',
  styleUrl: './roles.scss',
})
export class Roles implements OnInit{

  roleDialog: boolean = false;
  roles = signal<RoleModel[]>([]);
  totalRecords = signal<number>(0);
  role: RoleModel = { name: '', tenantId: 0 };
  submitted: boolean = false;
  loading = signal<boolean>(true);

  cols: ColumnConfig[] = [
    { field: 'id', header: 'ID', type: 'text' },
    { field: 'name', header: 'Nome da Role', type: 'text' },
    { field: 'tenantId', header: 'Tenant ID', type: 'text' },
    { field: 'createdDate', header: 'Data de Criação', type: 'date' },
    { field: 'createdBy', header: 'Criado Por', type: 'text' },
    { field: 'lastUpdateDate', header: 'Data da Última Atualização', type: 'date' },
    { field: 'lastUpdateBy', header: 'Atualizado Por', type: 'text' },
    { field: 'actions', header: 'Ações', type: 'actions' }
  ];

  constructor(
    private messageService: MessageService,
    private roleService: RoleService
  ) {}
  ngOnInit() {
    this.loadRoles();
  }

  loadRoles() {
    this.loading.set(true);
    this.roleService.getRoles().subscribe({
      next: (response) => {
        this.roles.set(response || []);
        this.loading.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.handleError(err, 'Erro ao carregar roles');
        this.loading.set(false);
      }
    });
  }

  openNew() {
    this.role = { name: '', tenantId: 1 }; // Coloque o TenantID default ou busque do contexto logado
    this.submitted = false;
    this.roleDialog = true;
  }

  hideDialog() {
    this.roleDialog = false;
    this.submitted = false;
  }

  saveRole() {
    this.submitted = true;

    if (this.role.name?.trim() && this.role.tenantId) {
      this.roleService.createRole(this.role).subscribe({
        next: (newRole) => {
          this.roles.update(current => [...current, newRole]);
          this.messageService.add({ severity: 'success', summary: 'Sucesso', detail: 'Role Criada com sucesso!', life: 3000 });
          this.hideDialog();
        },
        error: (err: HttpErrorResponse) => this.handleError(err, 'Falha ao criar a Role')
      });
    }
  }

  private handleError(err: HttpErrorResponse, defaultSummary: string) {
    if (err.error && err.error.message && err.error.error && err.error.status) {
      const detailMsg = `[${err.error.status}] ${err.error.error} - ${err.error.message}`;
      this.messageService.add({ severity: 'error', summary: defaultSummary, detail: detailMsg, life: 5000 });
    } else {
      this.messageService.add({ severity: 'error', summary: defaultSummary, detail: 'Erro inesperado de comunicação com o servidor.', life: 5000 });
    }
  }

  exportData() {
    this.messageService.add({ severity: 'info', summary: 'Info', detail: 'Funcionalidade de exportação aqui' });
  }

  onLazyLoad(event: any) {
    const page = event.first / event.rows;
    const size = event.rows;
  }
}
