import { Component, EventEmitter, inject, OnInit, Output, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormArray, FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MessageService } from 'primeng/api';
import { HttpErrorResponse } from '@angular/common/http';
import { Button, ButtonDirective } from 'primeng/button';
import { InputNumber } from 'primeng/inputnumber';
import { Select } from 'primeng/select';
import { MultiSelect } from 'primeng/multiselect';
import { DatePicker } from 'primeng/datepicker';
import { Tooltip } from 'primeng/tooltip';
import { Toast } from 'primeng/toast';
import { CotacaoCompraService } from '../cotacao.service';
import { CotacaoCompraRequest } from '../cotacao.model';
import { FornecedorService } from '../../../cadastros/fornecedores/fornecedor.service';
import { DepositoService } from '../../../cadastros/deposito/deposito.service';
import { ProdutoService } from '../../../cadastros/produtos/produto.service';
import { RequisicaoCompraService } from '../../requisicoes/requisicao.service';

@Component({
  selector: 'app-cotacao-form',
  imports: [
    CommonModule,
    ReactiveFormsModule,
    Button,
    ButtonDirective,
    InputNumber,
    Select,
    MultiSelect,
    DatePicker,
    Tooltip,
    Toast,
  ],
  templateUrl: './cotacao-form.html',
  styleUrl: './cotacao-form.scss',
})
export class CotacaoForm implements OnInit {
  @Output() saved = new EventEmitter<void>();
  @Output() canceled = new EventEmitter<void>();

  private fb = inject(FormBuilder);
  private cotacaoService = inject(CotacaoCompraService);
  private fornecedorService = inject(FornecedorService);
  private depositoService = inject(DepositoService);
  private produtoService = inject(ProdutoService);
  private requisicaoService = inject(RequisicaoCompraService);
  private messageService = inject(MessageService);

  form!: FormGroup;
  isSaving = false;

  fornecedoresOptions = signal<any[]>([]);
  depositosOptions = signal<any[]>([]);
  produtosOptions = signal<any[]>([]);
  requisicoesOptions = signal<any[]>([]);

  ngOnInit(): void {
    this.form = this.fb.group({
      depositoId: [null, Validators.required],
      dataLimiteResposta: [null],
      requisicaoId: [null],
      fornecedorIds: [[], [Validators.required, Validators.minLength(1)]],
      itens: this.fb.array([]),
    });

    this.addItem();
    this.loadDropdowns();

    this.form.get('requisicaoId')!.valueChanges.subscribe((requisicaoId) => {
      if (requisicaoId) {
        this.itensArray.disable();
      } else {
        this.itensArray.enable();
      }
    });
  }

  get itensArray(): FormArray {
    return this.form.get('itens') as FormArray;
  }

  private loadDropdowns(): void {
    this.fornecedorService.getAll(0, 1000).subscribe((res: any) => {
      const content = res._embedded ? Object.values(res._embedded)[0] : res.content || [];
      this.fornecedoresOptions.set(
        (content as any[])
          .filter((f: any) => f.ativo)
          .map((f: any) => ({ label: f.pessoaNomeRazao, value: f.id })),
      );
    });

    this.depositoService.listar(0, 1000).subscribe((res) => {
      this.depositosOptions.set(res.content.map((d) => ({ label: d.nome, value: d.id })));
    });

    this.produtoService.getAll(0, 1000).subscribe((res: any) => {
      const content = res._embedded ? Object.values(res._embedded)[0] : res.content || [];
      this.produtosOptions.set(
        (content as any[]).map((p: any) => ({ label: `${p.sku} - ${p.nome}`, value: p.id })),
      );
    });

    this.requisicaoService.listar({ status: 'APROVADA' }, 0, 1000).subscribe((res: any) => {
      const content = res._embedded ? res._embedded.requisicoes : res.content || [];
      this.requisicoesOptions.set(
        (content as any[]).map((r: any) => ({ label: `Requisição Nº ${r.numero}`, value: r.id })),
      );
    });
  }

  addItem(produtoId?: string, quantidade?: number): void {
    this.itensArray.push(
      this.fb.group({
        produtoId: [produtoId || null, Validators.required],
        quantidade: [quantidade ?? 1, [Validators.required, Validators.min(0.0001)]],
      }),
    );
  }

  removeItem(index: number): void {
    this.itensArray.removeAt(index);
  }

  onSubmit(): void {
    const semRequisicao = !this.form.value.requisicaoId;

    if (this.form.invalid || (semRequisicao && this.itensArray.length === 0)) {
      this.form.markAllAsTouched();
      if (semRequisicao && this.itensArray.length === 0) {
        this.messageService.add({
          severity: 'warning',
          summary: 'Aviso',
          detail: 'Adicione pelo menos um item ou selecione uma requisição de origem.',
        });
      }
      return;
    }

    this.isSaving = true;
    const formValue = this.form.value;
    const payload: CotacaoCompraRequest = {
      depositoId: formValue.depositoId,
      requisicaoId: formValue.requisicaoId,
      dataLimiteResposta: formValue.dataLimiteResposta
        ? new Date(formValue.dataLimiteResposta).toISOString().split('T')[0]
        : null,
      fornecedorIds: formValue.fornecedorIds,
      itens: semRequisicao ? formValue.itens : undefined,
    };

    this.cotacaoService.criar(payload).subscribe({
      next: () => {
        this.messageService.add({
          severity: 'success',
          summary: 'Sucesso',
          detail: 'Cotação criada e fornecedores convidados com sucesso!',
        });
        this.isSaving = false;
        this.saved.emit();
      },
      error: (err: HttpErrorResponse) => {
        this.isSaving = false;
        this.messageService.add({
          severity: 'error',
          summary: 'Erro',
          detail: err.error?.message || 'Erro ao criar a cotação.',
        });
      },
    });
  }

  onCancel(): void {
    this.canceled.emit();
  }

  isFieldInvalid(field: string): boolean {
    const control = this.form.get(field);
    return !!(control && control.invalid && (control.dirty || control.touched));
  }
}
