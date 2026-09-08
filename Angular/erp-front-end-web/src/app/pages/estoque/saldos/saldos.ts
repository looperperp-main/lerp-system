import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TableModule } from 'primeng/table';
import { Dialog } from 'primeng/dialog';
import { PrimeTemplate, MessageService } from 'primeng/api';
import { ButtonDirective } from 'primeng/button';
import { Ripple } from 'primeng/ripple';
import { Tooltip } from 'primeng/tooltip';
import { Select } from 'primeng/select';
import { ReactiveFormsModule, FormBuilder, FormGroup } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { Breadcrumb } from '../../../components/breadcrumb/breadcrumb';
import { EstoqueService } from '../estoque.service';
import { EstoqueSaldo } from '../estoque.model';
import { AjusteForm } from './ajuste-form/ajuste-form';
import { ProdutoService } from '../../cadastros/produtos/produto.service';
import { DepositoService } from '../../cadastros/deposito/deposito.service';

/** Saldos de estoque por produto/depósito, com badge "abaixo do mínimo" (spec/estoque.md §5.1/E7). */
@Component({
  selector: 'app-saldos',
  imports: [
    CommonModule,
    TableModule,
    Dialog,
    PrimeTemplate,
    ButtonDirective,
    Ripple,
    Tooltip,
    Select,
    ReactiveFormsModule,
    Breadcrumb,
    AjusteForm,
  ],
  templateUrl: './saldos.html',
  styleUrl: './saldos.scss',
})
export class Saldos implements OnInit {
  private estoqueService = inject(EstoqueService);
  private produtoService = inject(ProdutoService);
  private depositoService = inject(DepositoService);
  private messageService = inject(MessageService);
  private fb = inject(FormBuilder);

  saldos = signal<EstoqueSaldo[]>([]);
  loading = signal<boolean>(true);
  totalRecords = signal<number>(0);
  page = 0;
  size = 10;

  produtosMap = new Map<string, string>();
  depositosMap = new Map<string, string>();
  produtoOptions: { label: string; value: string }[] = [];
  depositoOptions: { label: string; value: string }[] = [];

  displayAjuste = false;
  selectedSaldo: EstoqueSaldo | null = null;

  filtroForm: FormGroup = this.fb.group({
    produtoId: [null],
    depositoId: [null],
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

  loadSaldos(event?: any): void {
    this.loading.set(true);
    if (event) {
      this.page = event.first / event.rows;
      this.size = event.rows;
    }
    const filtro = this.filtroForm.value;
    this.estoqueService.buscarSaldos(filtro, this.page, this.size).subscribe({
      next: (res: any) => {
        const content = res._embedded?.saldos || res.content || [];
        this.saldos.set(content);
        this.totalRecords.set(res.page?.totalElements ?? res.totalElements ?? 0);
        this.loading.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.handleError(err, 'Erro ao carregar saldos de estoque.');
        this.loading.set(false);
      },
    });
  }

  aplicarFiltro(): void {
    this.loadSaldos({ first: 0, rows: this.size });
  }

  limparFiltro(): void {
    this.filtroForm.reset();
    this.loadSaldos({ first: 0, rows: this.size });
  }

  abrirAjuste(saldo: EstoqueSaldo): void {
    this.selectedSaldo = saldo;
    this.displayAjuste = true;
  }

  onAjusteSaved(): void {
    // toast de sucesso já é exibido pelo AjusteForm; aqui só fecha o modal e recarrega a lista.
    this.displayAjuste = false;
    this.loadSaldos({ first: this.page * this.size, rows: this.size });
  }

  onAjusteCanceled(): void {
    this.displayAjuste = false;
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
