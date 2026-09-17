import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { TableModule } from 'primeng/table';
import { PrimeTemplate, MessageService } from 'primeng/api';
import { ButtonDirective } from 'primeng/button';
import { Select } from 'primeng/select';
import { ReactiveFormsModule, FormBuilder, FormGroup } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { Breadcrumb } from '../../../components/breadcrumb/breadcrumb';
import { EstoqueService } from '../estoque.service';
import { PENDENCIA_TIPO_LABEL, PendenciaEstoque, PendenciaTipoEstoque } from '../estoque.model';
import { ProdutoService } from '../../cadastros/produtos/produto.service';
import { DepositoService } from '../../cadastros/deposito/deposito.service';

/** Fila de pendências de estoque (item 5/E11, spec/modulos/estoque/estoque.md §12, RN-EST-12/13). */
@Component({
  selector: 'app-pendencias',
  imports: [
    CommonModule,
    RouterModule,
    TableModule,
    PrimeTemplate,
    ButtonDirective,
    Select,
    ReactiveFormsModule,
    Breadcrumb,
  ],
  templateUrl: './pendencias.html',
  styleUrl: './pendencias.scss',
})
export class Pendencias implements OnInit {
  private estoqueService = inject(EstoqueService);
  private produtoService = inject(ProdutoService);
  private depositoService = inject(DepositoService);
  private messageService = inject(MessageService);
  private fb = inject(FormBuilder);

  pendencias = signal<PendenciaEstoque[]>([]);
  loading = signal<boolean>(true);
  totalRecords = signal<number>(0);
  page = 0;
  size = 10;

  produtosMap = new Map<string, string>();
  depositosMap = new Map<string, string>();

  resolvidaOptions = [
    { label: 'Abertas', value: false },
    { label: 'Resolvidas', value: true },
  ];

  filtroForm: FormGroup = this.fb.group({
    resolvida: [false],
  });

  ngOnInit(): void {
    this.carregarMapas();
  }

  private carregarMapas(): void {
    this.produtoService.getAll(0, 1000).subscribe({
      next: (res: any) => {
        const content =
          res._embedded?.produtoResponseDTOList ||
          res._embedded?.produtoDTOList ||
          res._embedded?.produtos ||
          res.content ||
          [];
        content.forEach((p: any) => this.produtosMap.set(p.id, p.nome));
      },
    });
    this.depositoService.listar(0, 1000).subscribe({
      next: (res: any) => {
        const content =
          res._embedded?.depositoList || res._embedded?.depositos || res.content || [];
        content.forEach((d: any) => this.depositosMap.set(d.id, d.nome));
      },
    });
  }

  nomeProduto(id?: string): string {
    return (id && this.produtosMap.get(id)) || '-';
  }

  nomeDeposito(id?: string): string {
    return (id && this.depositosMap.get(id)) || '-';
  }

  labelTipo(tipo: PendenciaTipoEstoque): string {
    return PENDENCIA_TIPO_LABEL[tipo] || tipo;
  }

  loadPendencias(event?: any): void {
    this.loading.set(true);
    if (event) {
      this.page = event.first / event.rows;
      this.size = event.rows;
    }
    this.estoqueService.buscarPendencias(this.filtroForm.value, this.page, this.size).subscribe({
      next: (res: any) => {
        const content = res._embedded?.pendencias || res.content || [];
        this.pendencias.set(content);
        this.totalRecords.set(res.page?.totalElements ?? res.totalElements ?? 0);
        this.loading.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.handleError(err, 'Erro ao carregar pendências de estoque.');
        this.loading.set(false);
      },
    });
  }

  aplicarFiltro(): void {
    this.loadPendencias({ first: 0, rows: this.size });
  }

  resolver(pendencia: PendenciaEstoque): void {
    this.estoqueService.resolverPendencia(pendencia.id).subscribe({
      next: () => {
        this.messageService.add({
          severity: 'success',
          summary: 'Pendência resolvida',
          detail: 'A pendência foi marcada como resolvida.',
        });
        this.loadPendencias({ first: this.page * this.size, rows: this.size });
      },
      error: (err: HttpErrorResponse) => this.handleError(err, 'Erro ao resolver pendência'),
    });
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
