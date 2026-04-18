import {Component, inject, OnInit, signal} from '@angular/core';
import {CommonModule} from '@angular/common';
import {Router, RouterModule} from '@angular/router';
import {TableModule} from 'primeng/table';
import {ButtonModule} from 'primeng/button';
import {InputTextModule} from 'primeng/inputtext';
import {Vendedor} from './vendedor.model';
import {VendedorService} from './vendedor.service';
import {Dialog} from 'primeng/dialog';
import {HtmlDecodePipe} from '../../../util/pipe/html-decode.pipe';
import {PrimaryButtonComponent} from '../../../components/primary-button/primary-button';
import {Ripple} from 'primeng/ripple';
import {Toast} from 'primeng/toast';
import {Tooltip} from 'primeng/tooltip';
import {MessageService} from 'primeng/api';
import {ColumnConfig} from '../../../components/table/data-table';
import {VendedorForm} from './vendedor-form/vendedor-form';


@Component({
  selector: 'app-vendedores',
  imports: [CommonModule, RouterModule, TableModule, ButtonModule, InputTextModule, Dialog, HtmlDecodePipe, PrimaryButtonComponent, Ripple, Toast, Tooltip, VendedorForm],
  templateUrl: './vendedores.html',
  styleUrl: './vendedores.scss',
})
export class Vendedores implements OnInit {
  private messageService = inject(MessageService);
  vendedores = signal<Vendedor[]>([]);
  loading = signal<boolean>(true);
  totalRecords = signal<number>(0);
  page: number = 0;
  size: number = 10;
  displayForm = false;
  selectedVendedor: Vendedor | null = null;

  cols: ColumnConfig[] = [
    { field: 'id', header: 'ID', type: 'text' },
    { field: 'nome', header: 'Nome', type: 'text' },
    { field: 'ativo', header: 'Ativo', type: 'status' },
    { field: 'comissaoPercentual', header: 'Comissão', type: 'percent' },
    { field: 'createdAt', header: 'Data de Criação', type: 'date' },
    { field: 'updatedAt', header: 'Data de Atualização', type: 'date' },
    { field: 'actions', header: 'Ações', type: 'actions' }
  ];

  constructor(private vendedorService: VendedorService, private router: Router) {}

  ngOnInit(): void {
    this.loadVendedores();
  }

  loadVendedores(event?: any) {
    setTimeout(() => {
      this.loading.set(true);
    });

    if (event) {
      this.page = event.first / event.rows;
      this.size = event.rows;
    }
    this.vendedorService.getAll(this.page, this.size).subscribe({
      next: (response) => {
        // Formato HATEOAS PagedModel
        if (response._embedded && response._embedded.vendedor) {
          this.vendedores.set(response._embedded.vendedor || []);
        }
        this.totalRecords.set(response.page?.totalElements || 0);
        this.loading.set(false);
      },
      error: (err) => {
        console.error('Erro ao buscar vendedores', err);
        this.messageService.add({ severity: 'error', summary: 'Erro', detail: 'Erro ao buscar vendedores. ' + err });
        this.loading.set(false);
      }
    });
  }

  exportData(): void {
    this.messageService.add({ severity: 'info', summary: 'Info', detail: 'Exportação em desenvolvimento.' });
  }

  editVendedor(vendedor: Vendedor) {
    this.displayForm = true;
    this.selectedVendedor = vendedor;
  }

  inativarVendedor(id: string): void {
    this.messageService.add({ severity: 'info', summary: 'Info', detail: 'Inativação em desenvolvimento.' });
  }

  onFormSaved() {
    this.displayForm = false;
    this.loadVendedores({ first: 0, rows: 10 });
  }

  onFormCanceled() {
    this.displayForm = false;
  }

  protected openNew() {
    this.selectedVendedor = null;
    this.displayForm = true;
  }
}
