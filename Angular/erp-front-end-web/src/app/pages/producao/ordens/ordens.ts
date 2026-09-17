import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { TableModule } from 'primeng/table';
import { PrimeTemplate, MessageService } from 'primeng/api';
import { ButtonDirective } from 'primeng/button';
import { Select } from 'primeng/select';
import { InputNumber } from 'primeng/inputnumber';
import { Dialog } from 'primeng/dialog';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { Breadcrumb } from '../../../components/breadcrumb/breadcrumb';
import { PrimaryButtonComponent } from '../../../components/primary-button/primary-button';
import { ProducaoService } from '../producao.service';
import { OrdemProducao, STATUS_ORDEM_LABEL, StatusOrdemProducao } from '../producao.model';
import { ProdutoService } from '../../cadastros/produtos/produto.service';
import { DepositoService } from '../../cadastros/deposito/deposito.service';

/** Item 3 (Fase 2) — ordens de produção e apontamento (N SAIDA_PRODUCAO + 1 ENTRADA_PRODUCAO). */
@Component({
  selector: 'app-ordens',
  imports: [
    CommonModule,
    RouterModule,
    TableModule,
    PrimeTemplate,
    ButtonDirective,
    Select,
    InputNumber,
    Dialog,
    ReactiveFormsModule,
    Breadcrumb,
    PrimaryButtonComponent,
  ],
  templateUrl: './ordens.html',
  styleUrl: './ordens.scss',
})
export class Ordens implements OnInit {
  private producaoService = inject(ProducaoService);
  private produtoService = inject(ProdutoService);
  private depositoService = inject(DepositoService);
  private messageService = inject(MessageService);
  private fb = inject(FormBuilder);

  ordens = signal<OrdemProducao[]>([]);
  loading = signal<boolean>(true);
  totalRecords = signal<number>(0);
  page = 0;
  size = 10;

  produtosMap = new Map<string, string>();
  produtoOptions: { label: string; value: string }[] = [];
  depositosMap = new Map<string, string>();
  depositoOptions: { label: string; value: string }[] = [];

  statusOptions = Object.entries(STATUS_ORDEM_LABEL).map(([value, label]) => ({ label, value }));

  filtroForm: FormGroup = this.fb.group({ status: [null] });

  displayForm = false;
  displayApontar = false;
  isSaving = false;
  ordemSelecionada: OrdemProducao | null = null;

  form: FormGroup = this.fb.group({
    produtoAcabadoId: [null, Validators.required],
    quantidadePlanejada: [null, [Validators.required, Validators.min(0.0001)]],
    depositoId: [null, Validators.required],
  });

  apontarForm: FormGroup = this.fb.group({
    quantidadeProduzida: [null, [Validators.required, Validators.min(0.0001)]],
  });

  ngOnInit(): void {
    this.carregarMapas();
    this.loadOrdens();
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
        content.forEach((p: any) => {
          this.produtosMap.set(p.id, p.nome);
          this.produtoOptions.push({ label: p.nome, value: p.id });
        });
      },
    });
    this.depositoService.listar(0, 1000).subscribe({
      next: (res: any) => {
        const content =
          res._embedded?.depositoList || res._embedded?.depositos || res.content || [];
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

  labelStatus(status: StatusOrdemProducao): string {
    return STATUS_ORDEM_LABEL[status] || status;
  }

  loadOrdens(event?: any): void {
    this.loading.set(true);
    if (event) {
      this.page = event.first / event.rows;
      this.size = event.rows;
    }
    this.producaoService.buscarOrdens(this.filtroForm.value, this.page, this.size).subscribe({
      next: (res: any) => {
        const content = res._embedded?.ordens || res.content || [];
        this.ordens.set(content);
        this.totalRecords.set(res.page?.totalElements ?? res.totalElements ?? 0);
        this.loading.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.handleError(err, 'Erro ao carregar ordens de produção.');
        this.loading.set(false);
      },
    });
  }

  aplicarFiltro(): void {
    this.loadOrdens({ first: 0, rows: this.size });
  }

  abrirNovaOrdem(): void {
    this.form.reset();
    this.displayForm = true;
  }

  onSubmit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.isSaving = true;
    this.producaoService.criarOrdem(this.form.value).subscribe({
      next: () => {
        this.isSaving = false;
        this.displayForm = false;
        this.messageService.add({
          severity: 'success',
          summary: 'Sucesso',
          detail: 'Ordem de produção criada com sucesso!',
        });
        this.loadOrdens({ first: 0, rows: this.size });
      },
      error: (err: HttpErrorResponse) => {
        this.isSaving = false;
        this.handleError(err, 'Erro ao criar ordem de produção');
      },
    });
  }

  abrirApontar(ordem: OrdemProducao): void {
    this.ordemSelecionada = ordem;
    this.apontarForm.reset();
    this.displayApontar = true;
  }

  onApontar(): void {
    if (this.apontarForm.invalid || !this.ordemSelecionada) {
      this.apontarForm.markAllAsTouched();
      return;
    }
    this.isSaving = true;
    this.producaoService
      .apontarProducao(this.ordemSelecionada.id, this.apontarForm.value.quantidadeProduzida)
      .subscribe({
        next: () => {
          this.isSaving = false;
          this.displayApontar = false;
          this.messageService.add({
            severity: 'success',
            summary: 'Produção apontada',
            detail: 'Consumo dos componentes e entrada do produto acabado registrados.',
          });
          this.loadOrdens({ first: this.page * this.size, rows: this.size });
        },
        error: (err: HttpErrorResponse) => {
          this.isSaving = false;
          this.handleError(err, 'Erro ao apontar produção');
        },
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
