import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { TableModule } from 'primeng/table';
import { PrimeTemplate, MessageService } from 'primeng/api';
import { ButtonDirective } from 'primeng/button';
import { Select } from 'primeng/select';
import { InputNumber } from 'primeng/inputnumber';
import { Dialog } from 'primeng/dialog';
import { FormBuilder, FormGroup, FormArray, ReactiveFormsModule, Validators } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { Breadcrumb } from '../../../components/breadcrumb/breadcrumb';
import { PrimaryButtonComponent } from '../../../components/primary-button/primary-button';
import { ProducaoService } from '../producao.service';
import { FichaTecnica } from '../producao.model';
import { ProdutoService } from '../../cadastros/produtos/produto.service';

/** Item 3 (Fase 2) — CRUD de ficha técnica (produto acabado + componentes). */
@Component({
  selector: 'app-fichas-tecnicas',
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
  templateUrl: './fichas-tecnicas.html',
  styleUrl: './fichas-tecnicas.scss',
})
export class FichasTecnicas implements OnInit {
  private producaoService = inject(ProducaoService);
  private produtoService = inject(ProdutoService);
  private messageService = inject(MessageService);
  private fb = inject(FormBuilder);

  fichas = signal<FichaTecnica[]>([]);
  loading = signal<boolean>(true);
  totalRecords = signal<number>(0);
  page = 0;
  size = 10;

  produtosMap = new Map<string, string>();
  produtoOptions: { label: string; value: string }[] = [];

  displayForm = false;
  isSaving = false;

  form: FormGroup = this.fb.group({
    produtoAcabadoId: [null, Validators.required],
    itens: this.fb.array([]),
  });

  get itens(): FormArray {
    return this.form.get('itens') as FormArray;
  }

  ngOnInit(): void {
    this.carregarProdutos();
    this.loadFichas();
  }

  private carregarProdutos(): void {
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
  }

  nomeProduto(id?: string): string {
    return (id && this.produtosMap.get(id)) || '-';
  }

  loadFichas(event?: any): void {
    this.loading.set(true);
    if (event) {
      this.page = event.first / event.rows;
      this.size = event.rows;
    }
    this.producaoService.buscarFichasTecnicas(this.page, this.size).subscribe({
      next: (res: any) => {
        const content = res._embedded?.fichasTecnicas || res.content || [];
        this.fichas.set(content);
        this.totalRecords.set(res.page?.totalElements ?? res.totalElements ?? 0);
        this.loading.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.handleError(err, 'Erro ao carregar fichas técnicas.');
        this.loading.set(false);
      },
    });
  }

  abrirNovaFicha(): void {
    this.itens.clear();
    this.form.reset();
    this.adicionarItem();
    this.displayForm = true;
  }

  adicionarItem(): void {
    this.itens.push(
      this.fb.group({
        produtoComponenteId: [null, Validators.required],
        quantidade: [null, [Validators.required, Validators.min(0.0001)]],
      }),
    );
  }

  removerItem(index: number): void {
    this.itens.removeAt(index);
  }

  onSubmit(): void {
    if (this.form.invalid || this.itens.length === 0) {
      this.form.markAllAsTouched();
      return;
    }
    this.isSaving = true;
    this.producaoService.criarFichaTecnica(this.form.value).subscribe({
      next: () => {
        this.isSaving = false;
        this.displayForm = false;
        this.messageService.add({
          severity: 'success',
          summary: 'Sucesso',
          detail: 'Ficha técnica criada com sucesso!',
        });
        this.loadFichas({ first: 0, rows: this.size });
      },
      error: (err: HttpErrorResponse) => {
        this.isSaving = false;
        this.handleError(err, 'Erro ao criar ficha técnica');
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
