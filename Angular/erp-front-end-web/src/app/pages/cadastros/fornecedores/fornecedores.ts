import {Component, inject, OnInit, signal} from '@angular/core';
import {CommonModule} from '@angular/common';
import {Router, RouterModule} from '@angular/router';
import {TableModule} from 'primeng/table';
import {ButtonModule} from 'primeng/button';
import {InputTextModule} from 'primeng/inputtext';
import {Fornecedor} from './fornecedor.model';
import {FornecedorService} from './fornecedor.service';
import {Dialog} from 'primeng/dialog';
import {HtmlDecodePipe} from '../../../util/pipe/html-decode.pipe';
import {PrimaryButtonComponent} from '../../../components/primary-button/primary-button';
import {Ripple} from 'primeng/ripple';
import {Toast} from 'primeng/toast';
import {Tooltip} from 'primeng/tooltip';
import {MessageService} from 'primeng/api';
import {ColumnConfig} from '../../../components/table/data-table';
import {FornecedorForm} from './fornecedor-form/fornecedor-form';

@Component({
  selector: 'app-fornecedores',
  imports: [CommonModule, RouterModule, TableModule, ButtonModule, InputTextModule, Dialog, HtmlDecodePipe, PrimaryButtonComponent, Ripple, Toast, Tooltip, FornecedorForm],
  templateUrl: './fornecedores.html',
  styleUrl: './fornecedores.scss',
})
export class Fornecedores implements OnInit {
  private messageService = inject(MessageService);
  fornecedores = signal<Fornecedor[]>([]);
  loading = signal<boolean>(true);
  totalRecords = signal<number>(0);
  page: number = 0;
  size: number = 10;
  displayForm = false;
  selectedFornecedor: Fornecedor | null = null;

  cols: ColumnConfig[] = [
    { field: 'id', header: 'ID', type: 'text' },
    { field: 'pessoaId', header: 'ID Pessoa', type: 'text' },
    { field: 'ativo', header: 'Ativo', type: 'status' },
    { field: 'createdAt', header: 'Data de Criação', type: 'date' },
    { field: 'updatedAt', header: 'Data de Atualização', type: 'date' },
    { field: 'actions', header: 'Ações', type: 'actions' }
  ];

  constructor(private fornecedorService: FornecedorService, private router: Router) {}

  ngOnInit(): void {
    this.loadFornecedores();
  }

  loadFornecedores(event?: any) {
    setTimeout(() => {
      this.loading.set(true);
    });

    if (event) {
      this.page = event.first / event.rows;
      this.size = event.rows;
    }
    this.fornecedorService.getAll(this.page, this.size).subscribe({
      next: (response) => {
        if (response._embedded && response._embedded.fornecedorResponseDTOList) { // O nome na lista retornada pelo hateoas
          this.fornecedores.set(response._embedded.fornecedorResponseDTOList || []);
        } else if (response._embedded && response._embedded.fornecedor) {
          this.fornecedores.set(response._embedded.fornecedor || []);
        }
        this.totalRecords.set(response.page?.totalElements || 0);
        this.loading.set(false);
      },
      error: (err) => {
        console.error('Erro ao buscar fornecedores', err);
        this.messageService.add({ severity: 'error', summary: 'Erro', detail: 'Erro ao buscar fornecedores. ' + err });
        this.loading.set(false);
      }
    });
  }

  exportData(): void {
    this.messageService.add({ severity: 'info', summary: 'Info', detail: 'Exportação em desenvolvimento.' });
  }

  editFornecedor(fornecedor: Fornecedor) {
    this.displayForm = true;
    this.selectedFornecedor = fornecedor;
  }

  inativarFornecedor(rowData: any): void {
    this.fornecedorService.updateStatus(rowData.id).subscribe({
      next: () => {
        this.messageService.add({ severity: 'success', summary: 'Sucesso', detail: 'Status atualizado.' });
        this.loadFornecedores({ first: this.page * this.size, rows: this.size });
      },
      error: (err) => {
        this.messageService.add({ severity: 'error', summary: 'Erro', detail: 'Erro ao inativar.' });
      }
    });
  }

  onFormSaved() {
    this.displayForm = false;
    this.loadFornecedores({ first: 0, rows: 10 });
  }

  onFormCanceled() {
    this.displayForm = false;
  }

  protected openNew() {
    this.selectedFornecedor = null;
    this.displayForm = true;
  }
}
