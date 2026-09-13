import {
  Component,
  EventEmitter,
  inject,
  Input,
  OnChanges,
  OnInit,
  Output,
  signal,
  SimpleChanges,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormArray, FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MessageService } from 'primeng/api';
import { HttpErrorResponse } from '@angular/common/http';
import { Button, ButtonDirective } from 'primeng/button';
import { InputText } from 'primeng/inputtext';
import { InputNumber } from 'primeng/inputnumber';
import { Select } from 'primeng/select';
import { DatePicker } from 'primeng/datepicker';
import { Textarea } from 'primeng/textarea';
import { Tooltip } from 'primeng/tooltip';
import { Toast } from 'primeng/toast';
import { RequisicaoCompraService } from '../requisicao.service';
import { RequisicaoCompra, RequisicaoCompraRequest } from '../requisicao.model';
import { ProdutoService } from '../../../cadastros/produtos/produto.service';
import { DepositoService } from '../../../cadastros/deposito/deposito.service';
import { TenantLoginService } from '../../../login/service/tenant-login.service';

@Component({
  selector: 'app-requisicao-form',
  imports: [
    CommonModule,
    ReactiveFormsModule,
    Button,
    ButtonDirective,
    InputText,
    InputNumber,
    Select,
    DatePicker,
    Textarea,
    Tooltip,
    Toast,
  ],
  templateUrl: './requisicao-form.html',
  styleUrl: './requisicao-form.scss',
})
export class RequisicaoForm implements OnInit, OnChanges {
  @Input() requisicaoData: RequisicaoCompra | null = null;
  @Output() saved = new EventEmitter<void>();
  @Output() canceled = new EventEmitter<void>();

  private fb = inject(FormBuilder);
  private requisicaoService = inject(RequisicaoCompraService);
  private produtoService = inject(ProdutoService);
  private depositoService = inject(DepositoService);
  private tenantLoginService = inject(TenantLoginService);
  private messageService = inject(MessageService);

  form!: FormGroup;
  isSaving = false;

  depositosOptions = signal<any[]>([]);
  produtosOptions = signal<any[]>([]);

  ngOnInit(): void {
    this.form = this.fb.group({
      depositoId: [null],
      justificativa: ['', [Validators.maxLength(500)]],
      dataNecessidade: [null],
      itens: this.fb.array([]),
    });

    this.populateForm();
    this.loadDropdowns();
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['requisicaoData'] && !changes['requisicaoData'].firstChange && this.form) {
      this.populateForm();
    }
  }

  private populateForm(): void {
    this.itensArray.clear();

    this.form.patchValue({
      depositoId: this.requisicaoData?.depositoId || null,
      justificativa: this.requisicaoData?.justificativa || '',
      dataNecessidade: this.requisicaoData?.dataNecessidade
        ? new Date(this.requisicaoData.dataNecessidade)
        : null,
    });

    if (this.requisicaoData?.itens?.length) {
      this.requisicaoData.itens.forEach((item) =>
        this.addItem(item.produtoId, item.quantidade, item.observacao),
      );
    } else {
      this.addItem();
    }
  }

  get itensArray(): FormArray {
    return this.form.get('itens') as FormArray;
  }

  private loadDropdowns(): void {
    this.depositoService.listar(0, 1000).subscribe((res) => {
      this.depositosOptions.set(res.content.map((d) => ({ label: d.nome, value: d.id })));
    });

    this.produtoService.getAll(0, 1000).subscribe((res: any) => {
      const content = res._embedded ? Object.values(res._embedded)[0] : res.content || [];
      this.produtosOptions.set(
        (content as any[]).map((p: any) => ({ label: `${p.sku} - ${p.nome}`, value: p.id })),
      );
    });
  }

  addItem(produtoId?: string, quantidade?: number, observacao?: string | null): void {
    this.itensArray.push(
      this.fb.group({
        produtoId: [produtoId || null, Validators.required],
        quantidade: [quantidade ?? 1, [Validators.required, Validators.min(0.001)]],
        observacao: [observacao || ''],
      }),
    );
  }

  removeItem(index: number): void {
    this.itensArray.removeAt(index);
  }

  onSubmit(): void {
    if (this.form.invalid || this.itensArray.length === 0) {
      this.form.markAllAsTouched();
      if (this.itensArray.length === 0) {
        this.messageService.add({
          severity: 'warning',
          summary: 'Aviso',
          detail: 'Adicione pelo menos um item à requisição.',
        });
      }
      return;
    }

    const solicitanteId = this.requisicaoData?.solicitanteId || this.tenantLoginService.getUserId();
    if (!solicitanteId) {
      this.messageService.add({
        severity: 'error',
        summary: 'Erro',
        detail: 'Não foi possível identificar o usuário solicitante. Faça login novamente.',
      });
      return;
    }

    this.isSaving = true;
    const formValue = this.form.value;
    const payload: RequisicaoCompraRequest = {
      solicitanteId,
      depositoId: formValue.depositoId,
      justificativa: formValue.justificativa,
      dataNecessidade: formValue.dataNecessidade
        ? new Date(formValue.dataNecessidade).toISOString().split('T')[0]
        : null,
      itens: formValue.itens,
    };

    const request$ = this.requisicaoData?.id
      ? this.requisicaoService.atualizar(this.requisicaoData.id, payload)
      : this.requisicaoService.criar(payload);

    request$.subscribe({
      next: () => {
        this.messageService.add({
          severity: 'success',
          summary: 'Sucesso',
          detail: `Requisição ${this.requisicaoData?.id ? 'atualizada' : 'criada'} com sucesso!`,
        });
        this.isSaving = false;
        this.saved.emit();
      },
      error: (err: HttpErrorResponse) => {
        this.isSaving = false;
        this.messageService.add({
          severity: 'error',
          summary: 'Erro',
          detail: err.error?.message || 'Erro ao salvar a requisição.',
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
