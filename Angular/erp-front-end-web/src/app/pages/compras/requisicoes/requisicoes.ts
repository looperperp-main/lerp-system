import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { TableModule } from 'primeng/table';
import { Dialog } from 'primeng/dialog';
import { PrimeTemplate, MessageService } from 'primeng/api';
import { ButtonDirective } from 'primeng/button';
import { Ripple } from 'primeng/ripple';
import { Tooltip } from 'primeng/tooltip';
import { Toast } from 'primeng/toast';
import { Select } from 'primeng/select';
import { InputText } from 'primeng/inputtext';
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { Textarea } from 'primeng/textarea';
import { HttpErrorResponse } from '@angular/common/http';
import { PrimaryButtonComponent } from '../../../components/primary-button/primary-button';
import { Breadcrumb } from '../../../components/breadcrumb/breadcrumb';
import { RequisicaoCompraService } from './requisicao.service';
import {
  RequisicaoCompra,
  STATUS_REQUISICAO_LABEL,
  StatusRequisicaoCompra,
} from './requisicao.model';
import { RequisicaoForm } from './requisicao-form/requisicao-form';
import { ProdutoService } from '../../cadastros/produtos/produto.service';

@Component({
  selector: 'app-requisicoes',
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
    InputText,
    Textarea,
    ReactiveFormsModule,
    PrimaryButtonComponent,
    Breadcrumb,
    RequisicaoForm,
  ],
  templateUrl: './requisicoes.html',
  styleUrl: './requisicoes.scss',
})
export class Requisicoes implements OnInit {
  private requisicaoService = inject(RequisicaoCompraService);
  private produtoService = inject(ProdutoService);
  private messageService = inject(MessageService);
  private fb = inject(FormBuilder);

  requisicoes = signal<RequisicaoCompra[]>([]);
  loading = signal<boolean>(true);
  totalRecords = signal<number>(0);
  page = 0;
  size = 10;

  produtosMap = new Map<string, string>();

  displayForm = false;
  viewMode = false;
  selectedRequisicao: RequisicaoCompra | null = null;

  displayReprovarDialog = false;
  requisicaoParaReprovar: RequisicaoCompra | null = null;
  processando = signal<boolean>(false);
  reprovarForm: FormGroup = this.fb.group({ motivo: ['', Validators.required] });

  statusLabel = STATUS_REQUISICAO_LABEL;
  statusOptions = [
    { label: 'Todos', value: null },
    ...Object.entries(STATUS_REQUISICAO_LABEL).map(([value, label]) => ({ label, value })),
  ];

  filtroForm: FormGroup = this.fb.group({
    status: [null],
  });

  ngOnInit(): void {
    this.carregarMapas();
  }

  private carregarMapas(): void {
    this.produtoService.getAll(0, 1000).subscribe((res: any) => {
      const content = res._embedded ? Object.values(res._embedded)[0] : res.content || [];
      (content as any[]).forEach((p) => this.produtosMap.set(p.id, p.nome));
    });
  }

  nomeProduto(id?: string): string {
    return (id && this.produtosMap.get(id)) || '-';
  }

  loadRequisicoes(event?: any): void {
    setTimeout(() => this.loading.set(true));

    if (event) {
      this.page = event.first / event.rows;
      this.size = event.rows;
    }

    const filtro = this.filtroForm.value;
    this.requisicaoService.listar(filtro, this.page, this.size).subscribe({
      next: (res) => {
        const content = res._embedded ? res._embedded.requisicoes : res.content || [];
        this.requisicoes.set(content);
        this.totalRecords.set(res.page?.totalElements ?? res.totalElements ?? 0);
        this.loading.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.handleError(err, 'Erro ao carregar requisições.');
        this.loading.set(false);
      },
    });
  }

  aplicarFiltro(): void {
    this.loadRequisicoes({ first: 0, rows: this.size });
  }

  limparFiltro(): void {
    this.filtroForm.reset();
    this.loadRequisicoes({ first: 0, rows: this.size });
  }

  openNew(): void {
    this.selectedRequisicao = null;
    this.viewMode = false;
    this.displayForm = true;
  }

  editarRequisicao(requisicao: RequisicaoCompra): void {
    this.selectedRequisicao = requisicao;
    this.viewMode = false;
    this.displayForm = true;

    this.requisicaoService.buscarPorId(requisicao.id!).subscribe({
      next: (r) => {
        this.selectedRequisicao = r;
      },
      error: (err: HttpErrorResponse) => {
        this.displayForm = false;
        this.handleError(err, 'Erro ao carregar a requisição para edição.');
      },
    });
  }

  visualizarRequisicao(requisicao: RequisicaoCompra): void {
    this.selectedRequisicao = requisicao;
    this.viewMode = true;
    this.displayForm = true;

    this.requisicaoService.buscarPorId(requisicao.id!).subscribe({
      next: (r) => {
        this.selectedRequisicao = r;
      },
      error: (err: HttpErrorResponse) => {
        this.displayForm = false;
        this.handleError(err, 'Erro ao carregar a requisição.');
      },
    });
  }

  podeEditar(requisicao: RequisicaoCompra): boolean {
    return requisicao.status === 'RASCUNHO';
  }

  enviarParaAprovacao(requisicao: RequisicaoCompra): void {
    this.requisicaoService.enviarParaAprovacao(requisicao.id!).subscribe({
      next: () => {
        this.messageService.add({
          severity: 'success',
          summary: 'Sucesso',
          detail: 'Requisição enviada para aprovação.',
        });
        this.loadRequisicoes();
      },
      error: (err: HttpErrorResponse) =>
        this.handleError(err, 'Erro ao enviar requisição para aprovação.'),
    });
  }

  podeAprovar(requisicao: RequisicaoCompra): boolean {
    return requisicao.status === 'PENDENTE_APROVACAO';
  }

  aprovar(requisicao: RequisicaoCompra): void {
    this.processando.set(true);
    this.requisicaoService.aprovar(requisicao.id!).subscribe({
      next: () => {
        this.processando.set(false);
        this.messageService.add({
          severity: 'success',
          summary: 'Sucesso',
          detail: 'Requisição aprovada.',
        });
        this.loadRequisicoes();
      },
      error: (err: HttpErrorResponse) => {
        this.processando.set(false);
        this.handleError(err, 'Erro ao aprovar requisição.');
      },
    });
  }

  abrirReprovar(requisicao: RequisicaoCompra): void {
    this.requisicaoParaReprovar = requisicao;
    this.reprovarForm.reset();
    this.displayReprovarDialog = true;
  }

  confirmarReprovacao(): void {
    if (this.reprovarForm.invalid || !this.requisicaoParaReprovar) return;
    this.processando.set(true);
    this.requisicaoService
      .reprovar(this.requisicaoParaReprovar.id!, this.reprovarForm.value)
      .subscribe({
        next: () => {
          this.processando.set(false);
          this.displayReprovarDialog = false;
          this.messageService.add({
            severity: 'success',
            summary: 'Sucesso',
            detail: 'Requisição reprovada.',
          });
          this.loadRequisicoes();
        },
        error: (err: HttpErrorResponse) => {
          this.processando.set(false);
          this.handleError(err, 'Erro ao reprovar requisição.');
        },
      });
  }

  onFormSaved(): void {
    this.displayForm = false;
    this.loadRequisicoes({ first: 0, rows: this.size });
  }

  onFormCanceled(): void {
    this.displayForm = false;
  }

  label(status: StatusRequisicaoCompra): string {
    return this.statusLabel[status];
  }

  statusBadgeClass(status?: StatusRequisicaoCompra): string {
    switch (status) {
      case 'PENDENTE_APROVACAO':
      case 'EM_COTACAO':
        return 'info';
      case 'APROVADA':
      case 'ATENDIDA':
        return 'ok';
      case 'REPROVADA':
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
