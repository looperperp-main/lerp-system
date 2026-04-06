import {Component, inject, OnInit, signal} from '@angular/core';
import {GrupoClienteService} from './grupo-cliente.service';
import {ToastrService} from 'ngx-toastr';
import {GrupoCliente} from './grupo-cliente.model';
import {Dialog} from 'primeng/dialog';
import {MessageService, PrimeTemplate} from 'primeng/api';
import {DatePipe, NgClass, NgForOf, NgIf} from '@angular/common';
import {Button, ButtonDirective} from 'primeng/button';
import {TableModule} from 'primeng/table';
import {GrupoClienteForm} from './grupo-cliente-form/grupo-cliente-form';
import {ColumnConfig} from '../../../components/table/data-table';
import {Ripple} from 'primeng/ripple';
import {Tooltip} from 'primeng/tooltip';
import {HtmlDecodePipe} from '../../../util/pipe/html-decode.pipe';
import {Toast} from 'primeng/toast';
import {HttpErrorResponse} from '@angular/common/http';
import {PrimaryButtonComponent} from '../../../components/primary-button/primary-button';

@Component({
  selector: 'app-grupo-clientes',
  imports: [
    Dialog,
    PrimeTemplate,
    NgClass,
    Button,
    TableModule,
    GrupoClienteForm,
    NgIf,
    NgForOf,
    ButtonDirective,
    DatePipe,
    HtmlDecodePipe,
    Ripple,
    Tooltip,
    Toast,
    PrimaryButtonComponent
  ],
  providers: [MessageService],
  templateUrl: './grupo-clientes.html',
  styleUrl: './grupo-clientes.scss',
})
export class GrupoClientes implements OnInit {

  private grupoService = inject(GrupoClienteService);
  private messageService = inject(MessageService);

  loading = signal<boolean>(true);
  totalRecords = signal<number>(0);
  grupos = signal<GrupoCliente[]>([]);

  cols: ColumnConfig[] = [
    { field: 'nome', header: 'Nome', type: 'text' },
    { field: 'descricao', header: 'Descrição', type: 'text' },
    { field: 'ativo', header: 'Ativo', type: 'status' },
    { field: 'createdAt', header: 'Data Criação', type: 'date' },
    { field: 'updatedAt', header: 'Data Atualização', type: 'date' },
    { field: 'actions', header: 'Ações', type: 'actions' }
  ];

  // Controle do Modal de Formulário
  displayForm = false;
  selectedGrupo: GrupoCliente | null = null;

  ngOnInit(): void {
    // PrimeNG lazy load inicializa automaticamente
  }

  loadGrupos(event: any): void {
    setTimeout(() => {
      this.loading.set(true);
    });

    const first = event.first ?? 0;
    const rows = event.rows ?? 10;
    const page = first / rows;
    const size = rows;

    this.grupoService.listar(page, size).subscribe({
      next: (data) => {
        this.grupos.set(data.content || []);
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
    this.selectedGrupo = null;
    this.displayForm = true;
  }

  editGrupo(grupo: GrupoCliente): void {
    this.selectedGrupo = { ...grupo };
    this.displayForm = true;
  }

  onFormSaved(): void {
    this.displayForm = false;
    // Força recarga da primeira página
    this.loadGrupos({ first: 0, rows: 10 });
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
