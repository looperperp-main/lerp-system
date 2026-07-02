import {Component, inject, signal, OnInit} from '@angular/core';
import {ColumnConfig} from '../../../components/table/data-table';
import {MessageService} from "primeng/api";
import {ClientesService} from './clientes.service';
import {RouterModule} from '@angular/router';
import {TableModule} from 'primeng/table';
import {ButtonDirective, ButtonModule} from "primeng/button";
import {CommonModule} from '@angular/common';
import {Dialog} from 'primeng/dialog';
import {HtmlDecodePipe} from '../../../util/pipe/html-decode.pipe';
import {PrimaryButtonComponent} from '../../../components/primary-button/primary-button';
import {Ripple} from 'primeng/ripple';
import {Toast} from 'primeng/toast';
import {Tooltip} from 'primeng/tooltip';
import {ClienteForm} from './cliente-form/cliente-form';
import {Cliente} from './clientes.model';
import {HttpErrorResponse} from '@angular/common/http';

@Component({
  selector: 'app-cliente',
  imports: [CommonModule, RouterModule, TableModule, ButtonModule, Dialog, HtmlDecodePipe, PrimaryButtonComponent, Ripple, Toast, Tooltip, ClienteForm, ButtonDirective, Ripple, Tooltip],
  templateUrl: './clientes.html',
  styleUrl: './clientes.scss',
})
export class Clientes implements OnInit {
  private messageService = inject(MessageService);
  private clienteService = inject(ClientesService);

  clientes = signal<Cliente[]>([]);
  loading = signal<boolean>(true);
  totalRecords = signal<number>(0);
  page: number = 0;
  size: number = 10;

  displayForm = false;
  selectedCliente: Cliente | null = null;

  cols: ColumnConfig[] = [
    { field: 'id', header: 'ID', type: 'text' },
    { field: 'codigoInterno', header: 'Cód.', type: 'text' },
    { field: 'nome', header: 'Nome', type: 'text' },
    { field: 'classificacaoRisco', header: 'Risco', type: 'text' },
    { field: 'limiteCredito', header: 'L. Crédito', type: 'currency' },
    { field: 'ativo', header: 'Ativo', type: 'status' },
    { field: 'createdAt', header: 'Criado Em', type: 'date' },
    { field: 'createdBy', header: 'Criado Por', type: 'text' },
    { field: 'actions', header: 'Ações', type: 'actions' }
  ];

  ngOnInit(): void {
    this.loadClientes();
  }

  loadClientes(event?: any) {
    setTimeout(() => {
      this.loading.set(true);
    });

    if (event) {
      this.page = event.first / event.rows;
      this.size = event.rows;
    }

    this.clienteService.getAll(this.page, this.size).subscribe({
      next: (response) => {
        if (response._embedded?.clientes) {
          this.clientes.set(response._embedded.clientes || []);
        } else {
          this.clientes.set([]);
        }
        this.totalRecords.set(response.page?.totalElements || 0);
        this.loading.set(false);
      },
      error: (err) => {
        console.error('Erro ao buscar clientes', err);
        this.messageService.add({ severity: 'error', summary: 'Erro', detail: 'Erro ao buscar clientes.' });
        this.loading.set(false);
      }
    });
  }

  openNew() {
    this.selectedCliente = null;
    this.displayForm = true;
  }

  editCliente(cliente: Cliente) {
    this.selectedCliente = cliente;
    this.displayForm = true;
  }

  inativarCliente(cliente: Cliente): void {
    if (!cliente.id) return;

    this.clienteService.updateStatus(cliente.id).subscribe({
      next: () => {
        const acao = cliente.ativo ? 'inativado' : 'ativado';
        this.messageService.add({ severity: 'success', summary: 'Sucesso', detail: `Cliente ${acao} com sucesso.` });
        this.loadClientes({ first: this.page * this.size, rows: this.size }); // Recarrega a página atual
      },
      error: () => {
        this.messageService.add({ severity: 'error', summary: 'Erro', detail: 'Erro ao alterar o status do cliente.' });
      }
    });
  }

  deleteCliente(cliente: Cliente):void{
    if (!cliente.id) return;
    this.clienteService.delete(cliente.id).subscribe({
      next: () => {
        this.messageService.add({ severity: 'success', summary: 'Sucesso', detail: `Cliente removido com sucesso.` });
        this.loadClientes({ first: this.page * this.size, rows: this.size }); // Recarrega a página atual
      },
      error: (err: HttpErrorResponse) => {
        this.messageService.add({ severity: 'error', summary: 'Erro', detail: err.error.message || 'Erro ao remover cliente.' });
      }
    });
  }

  exportData(): void {
    this.messageService.add({ severity: 'info', summary: 'Info', detail: 'Exportação em desenvolvimento.' });
  }

  onFormSaved() {
    this.displayForm = false;
    this.loadClientes({ first: 0, rows: this.size });
  }

  onFormCanceled() {
    this.displayForm = false;
  }
}
