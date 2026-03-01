import {Component, signal} from '@angular/core';
import {Toast} from 'primeng/toast';
import {MessageService, PrimeTemplate} from 'primeng/api';
import {ButtonDirective} from 'primeng/button';
import {CnpjPipe} from '../../../util/pipe/cnpj.pipe';
import {NgForOf, NgIf} from '@angular/common';
import {HtmlDecodePipe} from '../../../util/pipe/html-decode.pipe';
import {Ripple} from 'primeng/ripple';
import {TableModule} from 'primeng/table';
import {Tooltip} from 'primeng/tooltip';
import {ColumnConfig} from '../../../components/table/data-table';
import {HttpErrorResponse} from '@angular/common/http';
import {AuditLog} from './auditLog.model';
import {AuditService} from './audit.service';

@Component({
  selector: 'app-audit',
  standalone: true,
  imports: [
    Toast,
    ButtonDirective,
    CnpjPipe,
    HtmlDecodePipe,
    NgForOf,
    NgIf,
    PrimeTemplate,
    Ripple,
    TableModule,
    Tooltip
  ],
  providers: [MessageService],
  templateUrl: './audit.html',
  styleUrl: './audit.scss',
})
export class Audit {

  cols: ColumnConfig[] = [
    { field: 'id', header: 'ID', type: 'text' },
    { field: 'actorUserId', header: 'ID do usuário', type: 'text' },
    { field: 'action', header: 'Ação', type: 'text' },
    { field: 'targetType', header: 'Alvo da Operação', type: 'status' },
    { field: 'targetId', header: 'ID do alvo', type: 'text' },
    { field: 'result', header: 'Resultado', type: 'date' },
    { field: 'detailsJson', header: 'Detalhes em JSON', type: 'text' },
    { field: 'correlationId', header: 'CorrelationID', type: 'date' }
  ];

  loading = signal<boolean>(true);
  totalRecords = signal<number>(0);
  logs = signal<AuditLog[]>([]);

  constructor(
    private messageService: MessageService,
    private auditService: AuditService
  ) {}

  ngOnInit() {
    // Carregamento feito via lazy load do datatable (onLazyLoad)
  }

  exportData() {
    this.messageService.add({ severity: 'info', summary: 'Info', detail: 'Funcionalidade de exportação aqui' });
  }

  onLazyLoad(event: any) {
    const page = event.first / event.rows;
    const size = event.rows;
    this.loadLogs(page, size);
  }

  loadLogs(page: number = 0, size: number = 10) {
    this.loading.set(true);
    this.auditService.getLogs(page, size).subscribe({
      next: (response) => {
        this.logs.set(response.content || []);
        this.totalRecords.set(response.totalElements || 0);
        this.loading.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.handleError(err, 'Erro ao carregar Logs de Auditoria');
        this.loading.set(false);
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
