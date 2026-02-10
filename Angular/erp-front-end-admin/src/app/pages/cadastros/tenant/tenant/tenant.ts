import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MessageService, ConfirmationService } from 'primeng/api';
import { ToastModule } from 'primeng/toast';
import { ToolbarModule } from 'primeng/toolbar';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { ConfirmDialogModule } from 'primeng/confirmdialog';
import { InputTextModule } from 'primeng/inputtext';
import { DataTableComponent, ColumnConfig } from '../../../../components/table/data-table';
import { TenantModel } from './tenant.model';
import {Select} from 'primeng/select';
import {Ripple} from 'primeng/ripple';

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
    DataTableComponent,
    Select,
    Ripple
  ],
  providers: [MessageService, ConfirmationService],
  templateUrl: './tenant.html',
  styleUrl: './tenant.scss'
})
export class Tenant {

  // Configuração da Tabela
  tenantDialog: boolean = false;
  tenants: TenantModel[] = [];
  tenant: TenantModel = { name: '', cnpj: '', status: 'ATIVO' }; // Objeto vazio inicial
  selectedTenants: Tenant[] = [];
  submitted: boolean = false;
  loading: boolean = true;

  // Opções de Status
  statuses = [
    { label: 'Ativo', value: 'ATIVO' },
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
    private confirmationService: ConfirmationService
  ) {}

  ngOnInit() {
    // Simulação de carga de dados (substituir por chamada HTTP depois)
    setTimeout(() => {
      this.tenants = [
        {
          id: 1001,
          name: 'Empresa Alpha Ltda',
          cnpj: '12.345.678/0001-90',
          status: 'ATIVO',
          createdBy: 'admin',
          creationDate: new Date().toISOString(),
          lastUpdatedBy: 'admin',
          updateDate: new Date().toISOString()
        },
        {
          id: 1002,
          name: 'Beta Serviços S.A.',
          cnpj: '98.765.432/0001-10',
          status: 'SUSPENSO',
          createdBy: 'system',
          creationDate: new Date('2023-01-15').toISOString(),
          lastUpdatedBy: 'manager',
          updateDate: new Date().toISOString()
        }
      ];
      this.loading = false;
    }, 1000);
  }

  openNew() {
    this.tenant = { name: '', cnpj: '', status: 'ATIVO' };
    this.submitted = false;
    this.tenantDialog = true;
  }

  editTenant(tenant: TenantModel) {
    this.tenant = { ...tenant };
    this.tenantDialog = true;
  }

  deleteTenant(tenant: TenantModel) {
    this.confirmationService.confirm({
      message: 'Tem certeza que deseja excluir ' + tenant.name + '?',
      header: 'Confirmar',
      icon: 'pi pi-exclamation-triangle',
      accept: () => {
        this.tenants = this.tenants.filter(val => val.id !== tenant.id);
        this.tenant = { name: '', cnpj: '', status: 'ATIVO' };
        this.messageService.add({ severity: 'success', summary: 'Sucesso', detail: 'Tenant excluído', life: 3000 });
      }
    });
  }

  hideDialog() {
    this.tenantDialog = false;
    this.submitted = false;
  }

  saveTenant() {
    this.submitted = true;

    if (this.tenant.name?.trim() && this.tenant.cnpj?.trim()) {
      if (this.tenant.id) {
        // Atualizar Existente
        const index = this.tenants.findIndex(t => t.id === this.tenant.id);
        this.tenants[index] = this.tenant;
        this.messageService.add({ severity: 'success', summary: 'Sucesso', detail: 'Tenant Atualizado', life: 3000 });
      } else {
        // Criar Novo
        this.tenant.id = Math.floor(Math.random() * 10000); // Gerar ID fake
        this.tenant.creationDate = new Date().toISOString();
        this.tenant.createdBy = 'usuario_atual';
        this.tenants.push(this.tenant);
        this.messageService.add({ severity: 'success', summary: 'Sucesso', detail: 'Tenant Criado', life: 3000 });
      }

      this.tenants = [...this.tenants];
      this.tenantDialog = false;
      this.tenant = { name: '', cnpj: '', status: 'ATIVO' };
    }
  }

  exportData() {
    this.messageService.add({ severity: 'info', summary: 'Info', detail: 'Funcionalidade de exportação aqui' });
  }
}
