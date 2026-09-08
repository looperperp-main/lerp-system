import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { TableModule } from 'primeng/table';
import { PrimeTemplate, MessageService } from 'primeng/api';
import { ButtonDirective } from 'primeng/button';
import { Select } from 'primeng/select';
import { DatePicker } from 'primeng/datepicker';
import { ReactiveFormsModule, FormBuilder, FormGroup } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { Breadcrumb } from '../../../components/breadcrumb/breadcrumb';
import { EstoqueService } from '../estoque.service';
import {
  MovimentoEstoque,
  ORIGEM_MOVIMENTO_LABEL,
  TIPO_MOVIMENTO_LABEL,
  TipoMovimentoEstoque,
  OrigemMovimentoEstoque,
} from '../estoque.model';
import { ProdutoService } from '../../cadastros/produtos/produto.service';
import { DepositoService } from '../../cadastros/deposito/deposito.service';

/** Extrato de movimentos de estoque (spec/estoque.md §5.2/E7). */
@Component({
  selector: 'app-movimentos',
  imports: [
    CommonModule,
    RouterModule,
    TableModule,
    PrimeTemplate,
    ButtonDirective,
    Select,
    DatePicker,
    ReactiveFormsModule,
    Breadcrumb,
  ],
  templateUrl: './movimentos.html',
  styleUrl: './movimentos.scss',
})
export class Movimentos implements OnInit {
  private estoqueService = inject(EstoqueService);
  private produtoService = inject(ProdutoService);
  private depositoService = inject(DepositoService);
  private messageService = inject(MessageService);
  private fb = inject(FormBuilder);

  movimentos = signal<MovimentoEstoque[]>([]);
  loading = signal<boolean>(true);
  totalRecords = signal<number>(0);
  page = 0;
  size = 10;

  produtosMap = new Map<string, string>();
  depositosMap = new Map<string, string>();
  produtoOptions: { label: string; value: string }[] = [];
  depositoOptions: { label: string; value: string }[] = [];

  tipoOptions = Object.entries(TIPO_MOVIMENTO_LABEL).map(([value, label]) => ({ label, value }));
  origemOptions = Object.entries(ORIGEM_MOVIMENTO_LABEL).map(([value, label]) => ({ label, value }));

  filtroForm: FormGroup = this.fb.group({
    produtoId: [null],
    depositoId: [null],
    de: [null],
    ate: [null],
    tipo: [null],
    origemTipo: [null],
  });

  ngOnInit(): void {
    this.carregarMapas();
  }

  private carregarMapas(): void {
    this.produtoService.getAll(0, 1000).subscribe({
      next: (res: any) => {
        const content = res._embedded?.produtoResponseDTOList || res._embedded?.produtoDTOList
          || res._embedded?.produtos || res.content || [];
        content.forEach((p: any) => {
          this.produtosMap.set(p.id, p.nome);
          this.produtoOptions.push({ label: p.nome, value: p.id });
        });
      },
    });
    this.depositoService.listar(0, 1000).subscribe({
      next: (res: any) => {
        const content = res._embedded?.depositoList || res._embedded?.depositos || res.content || [];
        content.forEach((d: any) => {
          this.depositosMap.set(d.id, d.nome);
          this.depositoOptions.push({ label: d.nome, value: d.id });
        });
      },
    });
  }

  nomeProduto(id?: string): string {
    return (id && this.produtosMap.get(id)) || '-';
  }

  nomeDeposito(id?: string): string {
    return (id && this.depositosMap.get(id)) || '-';
  }

  labelTipo(tipo: TipoMovimentoEstoque): string {
    return TIPO_MOVIMENTO_LABEL[tipo] || tipo;
  }

  labelOrigem(origem: OrigemMovimentoEstoque): string {
    return ORIGEM_MOVIMENTO_LABEL[origem] || origem;
  }

  loadMovimentos(event?: any): void {
    this.loading.set(true);
    if (event) {
      this.page = event.first / event.rows;
      this.size = event.rows;
    }
    const formValue = this.filtroForm.value;
    const filtro = {
      ...formValue,
      de: formValue.de ? new Date(formValue.de).toISOString() : null,
      ate: formValue.ate ? new Date(formValue.ate).toISOString() : null,
    };
    this.estoqueService.buscarMovimentos(filtro, this.page, this.size).subscribe({
      next: (res: any) => {
        const content = res._embedded?.movimentos || res.content || [];
        this.movimentos.set(content);
        this.totalRecords.set(res.page?.totalElements ?? res.totalElements ?? 0);
        this.loading.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.handleError(err, 'Erro ao carregar movimentos de estoque.');
        this.loading.set(false);
      },
    });
  }

  aplicarFiltro(): void {
    this.loadMovimentos({ first: 0, rows: this.size });
  }

  limparFiltro(): void {
    this.filtroForm.reset();
    this.loadMovimentos({ first: 0, rows: this.size });
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
