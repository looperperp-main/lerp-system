import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterModule } from '@angular/router';
import { TableModule } from 'primeng/table';
import { Dialog } from 'primeng/dialog';
import { PrimeTemplate, MessageService } from 'primeng/api';
import { ButtonDirective } from 'primeng/button';
import { Ripple } from 'primeng/ripple';
import { Tooltip } from 'primeng/tooltip';
import { Toast } from 'primeng/toast';
import { Select } from 'primeng/select';
import { ReactiveFormsModule, FormBuilder, FormGroup } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { PrimaryButtonComponent } from '../../../components/primary-button/primary-button';
import { Breadcrumb } from '../../../components/breadcrumb/breadcrumb';
import { CotacaoCompraService } from './cotacao.service';
import { CotacaoCompra, STATUS_COTACAO_LABEL, StatusCotacaoCompra } from './cotacao.model';
import { CotacaoForm } from './cotacao-form/cotacao-form';
import { RequisicaoCompraService } from '../requisicoes/requisicao.service';

@Component({
  selector: 'app-cotacoes',
  imports: [
    CommonModule,
    RouterModule,
    TableModule,
    Dialog,
    PrimeTemplate,
    ButtonDirective,
    Ripple,
    Tooltip,
    Toast,
    Select,
    ReactiveFormsModule,
    PrimaryButtonComponent,
    Breadcrumb,
    CotacaoForm,
  ],
  templateUrl: './cotacoes.html',
  styleUrl: './cotacoes.scss',
})
export class Cotacoes implements OnInit {
  private cotacaoService = inject(CotacaoCompraService);
  private requisicaoService = inject(RequisicaoCompraService);
  private messageService = inject(MessageService);
  private fb = inject(FormBuilder);
  private router = inject(Router);

  cotacoes = signal<CotacaoCompra[]>([]);
  loading = signal<boolean>(true);
  totalRecords = signal<number>(0);
  page = 0;
  size = 10;

  requisicoesMap = new Map<string, number>();

  displayForm = false;

  statusLabel = STATUS_COTACAO_LABEL;
  statusOptions = [
    { label: 'Todos', value: null },
    ...Object.entries(STATUS_COTACAO_LABEL).map(([value, label]) => ({ label, value })),
  ];

  filtroForm: FormGroup = this.fb.group({
    status: [null],
  });

  ngOnInit(): void {
    this.carregarMapas();
  }

  private carregarMapas(): void {
    this.requisicaoService.listar({}, 0, 1000).subscribe((res: any) => {
      const content = res._embedded ? res._embedded.requisicoes : res.content || [];
      (content as any[]).forEach((r) => this.requisicoesMap.set(r.id, r.numero));
    });
  }

  numeroRequisicao(id?: string): string {
    return id && this.requisicoesMap.has(id) ? `Nº ${this.requisicoesMap.get(id)}` : 'Avulsa';
  }

  loadCotacoes(event?: any): void {
    setTimeout(() => this.loading.set(true));

    if (event) {
      this.page = event.first / event.rows;
      this.size = event.rows;
    }

    const filtro = this.filtroForm.value;
    this.cotacaoService.listar(filtro, this.page, this.size).subscribe({
      next: (res) => {
        const content = res._embedded ? res._embedded.cotacoes : res.content || [];
        this.cotacoes.set(content);
        this.totalRecords.set(res.page?.totalElements ?? res.totalElements ?? 0);
        this.loading.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.handleError(err, 'Erro ao carregar cotações.');
        this.loading.set(false);
      },
    });
  }

  aplicarFiltro(): void {
    this.loadCotacoes({ first: 0, rows: this.size });
  }

  limparFiltro(): void {
    this.filtroForm.reset();
    this.loadCotacoes({ first: 0, rows: this.size });
  }

  openNew(): void {
    this.displayForm = true;
  }

  verCotacao(cotacao: CotacaoCompra): void {
    this.router.navigate(['/web/compras/cotacoes', cotacao.id]);
  }

  onFormSaved(): void {
    this.displayForm = false;
    this.loadCotacoes({ first: 0, rows: this.size });
  }

  onFormCanceled(): void {
    this.displayForm = false;
  }

  label(status: StatusCotacaoCompra): string {
    return this.statusLabel[status];
  }

  statusBadgeClass(status?: StatusCotacaoCompra): string {
    switch (status) {
      case 'ABERTA':
        return 'info';
      case 'ENCERRADA':
        return 'ok';
      case 'CANCELADA':
        return 'bad';
      default:
        return 'neutral';
    }
  }

  private handleError(err: HttpErrorResponse, defaultSummary: string): void {
    if (err.error?.message && err.error?.error && err.error?.status) {
      this.messageService.add({
        severity: 'error',
        summary: defaultSummary,
        detail: `[${err.error.status}] ${err.error.error} - ${err.error.message}`,
        life: 5000,
      });
    } else {
      this.messageService.add({
        severity: 'error',
        summary: defaultSummary,
        detail: 'Erro inesperado de comunicação com o servidor.',
        life: 5000,
      });
    }
  }
}
