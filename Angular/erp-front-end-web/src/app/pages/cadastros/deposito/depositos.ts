import {Component, inject, OnInit, signal} from '@angular/core';
import {ButtonDirective} from 'primeng/button';
import {DatePipe, NgForOf, NgIf} from '@angular/common';
import {Dialog} from 'primeng/dialog';
import {HtmlDecodePipe} from '../../../util/pipe/html-decode.pipe';
import {PrimaryButtonComponent} from '../../../components/primary-button/primary-button';
import {MessageService, PrimeTemplate} from 'primeng/api';
import {Ripple} from 'primeng/ripple';
import {TableModule} from 'primeng/table';
import {Toast} from 'primeng/toast';
import {Tooltip} from 'primeng/tooltip';
import {DepositoService} from './deposito.service';
import {ColumnConfig} from '../../../components/table/data-table';
import {HttpErrorResponse} from '@angular/common/http';
import {GrupoCliente} from '../grupo-clientes/grupo-cliente.model';
import {Deposito} from './deposito.model';
import {DepositoForm} from './deposito-form/deposito-form';

@Component({
  selector: 'app-deposito',
  imports: [
    ButtonDirective,
    DatePipe,
    Dialog,
    HtmlDecodePipe,
    NgForOf,
    NgIf,
    PrimaryButtonComponent,
    PrimeTemplate,
    Ripple,
    TableModule,
    Toast,
    Tooltip,
    DepositoForm
  ],
  providers: [MessageService],
  templateUrl: './depositos.html',
  styleUrl: './depositos.scss',
})
export class Depositos implements OnInit{
  private depositoService = inject(DepositoService);
  private messageService = inject(MessageService);

  loading = signal<boolean>(true);
  totalRecords = signal<number>(0);
  depositos = signal<Deposito[]>([]);

  cols: ColumnConfig[] = [
    { field: 'nome', header: 'Nome', type: 'text' },
    { field: 'descricao', header: 'Descrição', type: 'text' },
    { field: 'tipo', header: 'Tipo', type: 'text' },
    { field: 'ativo', header: 'Ativo', type: 'status' },
    { field: 'createdAt', header: 'Data Criação', type: 'date' },
    { field: 'updatedAt', header: 'Data Atualização', type: 'date' },
    { field: 'actions', header: 'Ações', type: 'actions' }
  ];

  displayForm = false;
  selectedDeposito: Deposito | null = null;

  ngOnInit(): void {
    // PrimeNG lazy load inicializa automaticamente
  }

  loadDepositos(event: any): void {
    setTimeout(() => {
      this.loading.set(true);
    });

    const first = event.first ?? 0;
    const rows = event.rows ?? 10;
    const page = first / rows;
    const size = rows;

    this.depositoService.listar(page, size).subscribe({
      next: (data) => {
        this.depositos.set(data.content || []);
        this.totalRecords.set(data.totalElements || 0);
        this.loading.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.handleError(err, 'Erro ao carregar os grupos de clientes.');
        this.loading.set(false);
      }
    });
  }

  openNew(): void {
    this.selectedDeposito = null;
    this.displayForm = true;
  }

  editGrupo(deposito: Deposito): void {
    this.selectedDeposito = { ...deposito };
    this.displayForm = true;
  }

  onFormSaved(): void {
    this.displayForm = false;
    // Força recarga da primeira página
    this.loadDepositos({ first: 0, rows: 10 });
  }

  onFormCanceled(): void {
    this.displayForm = false;
  }

  exportData(): void {
    this.messageService.add({ severity: 'info', summary: 'Info', detail: 'Exportação em desenvolvimento.' });
  }

  private handleError(err: HttpErrorResponse, defaultSummary: string) {
    if (err.error && err.error.message && err.error.error && err.error.status) {
      const detailMsg = `[${err.error.status}] ${err.error.error} - ${err.error.message}`;
      this.messageService.add({
        severity: 'error',
        summary: defaultSummary,
        detail: detailMsg,
        life: 5000
      });
    } else {
      this.messageService.add({
        severity: 'error',
        summary: defaultSummary,
        detail: 'Erro inesperado de comunicação com o servidor.',
        life: 5000
      });
    }
  }
}
