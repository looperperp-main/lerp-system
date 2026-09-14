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
import { PedidoCompraService } from '../pedido-compra.service';
import { PedidoCompra, PedidoCompraRequest } from '../pedido-compra.model';
import { FornecedorService } from '../../../cadastros/fornecedores/fornecedor.service';
import { CondPagamentoService } from '../../../cadastros/cond-pagamento/cond-pagamento.service';
import { DepositoService } from '../../../cadastros/deposito/deposito.service';
import { ProdutoService } from '../../../cadastros/produtos/produto.service';
import { RequisicaoCompraService } from '../../requisicoes/requisicao.service';

@Component({
  selector: 'app-pedido-form',
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
  templateUrl: './pedido-form.html',
  styleUrl: './pedido-form.scss',
})
export class PedidoForm implements OnInit, OnChanges {
  @Input() pedidoData: PedidoCompra | null = null;
  @Output() saved = new EventEmitter<void>();
  @Output() canceled = new EventEmitter<void>();

  private fb = inject(FormBuilder);
  private pedidoService = inject(PedidoCompraService);
  private fornecedorService = inject(FornecedorService);
  private condPagamentoService = inject(CondPagamentoService);
  private depositoService = inject(DepositoService);
  private produtoService = inject(ProdutoService);
  private requisicaoService = inject(RequisicaoCompraService);
  private messageService = inject(MessageService);

  form!: FormGroup;
  isSaving = false;

  fornecedoresOptions = signal<any[]>([]);
  condicoesOptions = signal<any[]>([]);
  depositosOptions = signal<any[]>([]);
  produtosOptions = signal<any[]>([]);
  requisicoesOptions = signal<any[]>([]);

  ngOnInit(): void {
    this.form = this.fb.group({
      fornecedorId: [null, Validators.required],
      condicaoPagamentoId: [null, Validators.required],
      depositoId: [null, Validators.required],
      requisicaoId: [null],
      dataPrevisaoEntrega: [null],
      valorFrete: [null],
      observacao: ['', [Validators.maxLength(500)]],
      itens: this.fb.array([]),
    });

    this.populateForm();
    this.loadDropdowns();

    this.form.get('requisicaoId')!.valueChanges.subscribe((requisicaoId) => {
      if (requisicaoId) {
        this.aoSelecionarRequisicao(requisicaoId);
      }
    });
  }

  private aoSelecionarRequisicao(requisicaoId: string): void {
    this.requisicaoService.buscarPorId(requisicaoId).subscribe({
      next: (requisicao) => {
        this.form.patchValue({ depositoId: requisicao.depositoId || null });
        this.itensArray.clear();
        (requisicao.itens || []).forEach((item) =>
          this.addItem(item.produtoId, item.quantidade, 0),
        );
        if (this.itensArray.length === 0) {
          this.addItem();
        }
      },
      error: (err: HttpErrorResponse) => {
        this.messageService.add({
          severity: 'error',
          summary: 'Erro',
          detail: err.error?.message || 'Erro ao carregar itens da requisição.',
        });
      },
    });
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['pedidoData'] && !changes['pedidoData'].firstChange && this.form) {
      this.populateForm();
    }
  }

  private populateForm(): void {
    this.itensArray.clear();

    this.form.patchValue(
      {
        fornecedorId: this.pedidoData?.fornecedorId || null,
        condicaoPagamentoId: this.pedidoData?.condicaoPagamentoId || null,
        depositoId: this.pedidoData?.depositoId || null,
        requisicaoId: this.pedidoData?.requisicaoId || null,
        dataPrevisaoEntrega: this.pedidoData?.dataPrevisaoEntrega
          ? new Date(this.pedidoData.dataPrevisaoEntrega)
          : null,
        valorFrete: this.pedidoData?.valorFrete ?? null,
        observacao: this.pedidoData?.observacao || '',
      },
      { emitEvent: false },
    );

    if (this.pedidoData?.itens?.length) {
      this.pedidoData.itens.forEach((item) =>
        this.addItem(item.produtoId, item.quantidade, item.precoUnitario),
      );
    } else {
      this.addItem();
    }
  }

  get itensArray(): FormArray {
    return this.form.get('itens') as FormArray;
  }

  private loadDropdowns(): void {
    this.fornecedorService.getAll(0, 1000).subscribe((res: any) => {
      const content = res._embedded ? Object.values(res._embedded)[0] : res.content || [];
      this.fornecedoresOptions.set(
        (content as any[]).map((f: any) => ({ label: f.pessoaNomeRazao, value: f.id })),
      );
    });

    this.condPagamentoService.listar(0, 1000).subscribe((res: any) => {
      const content = res._embedded ? res._embedded.condicaopagamento : res.content || [];
      this.condicoesOptions.set(content.map((c: any) => ({ label: c.nome, value: c.id })));
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

  addItem(produtoId?: string, quantidade?: number, precoUnitario?: number | null): void {
    this.itensArray.push(
      this.fb.group({
        produtoId: [produtoId || null, Validators.required],
        quantidade: [quantidade ?? 1, [Validators.required, Validators.min(0.0001)]],
        precoUnitario: [precoUnitario ?? 0, [Validators.required, Validators.min(0)]],
      }),
    );
  }

  removeItem(index: number): void {
    this.itensArray.removeAt(index);
  }

  totalItem(index: number): number {
    const item = this.itensArray.at(index).value;
    return (item.quantidade || 0) * (item.precoUnitario || 0);
  }

  totalGeral(): number {
    const itens = this.itensArray.controls.reduce((sum, _, i) => sum + this.totalItem(i), 0);
    return itens + (this.form.value.valorFrete || 0);
  }

  onSubmit(): void {
    if (this.form.invalid || this.itensArray.length === 0) {
      this.form.markAllAsTouched();
      if (this.itensArray.length === 0) {
        this.messageService.add({
          severity: 'warning',
          summary: 'Aviso',
          detail: 'Adicione pelo menos um item ao pedido.',
        });
      }
      return;
    }

    this.isSaving = true;
    const formValue = this.form.value;
    const payload: PedidoCompraRequest = {
      fornecedorId: formValue.fornecedorId,
      condicaoPagamentoId: formValue.condicaoPagamentoId,
      depositoId: formValue.depositoId,
      requisicaoId: formValue.requisicaoId,
      valorFrete: formValue.valorFrete,
      observacao: formValue.observacao,
      dataPrevisaoEntrega: formValue.dataPrevisaoEntrega
        ? new Date(formValue.dataPrevisaoEntrega).toISOString().split('T')[0]
        : null,
      itens: formValue.itens,
    };

    const request$ = this.pedidoData?.id
      ? this.pedidoService.atualizar(this.pedidoData.id, payload)
      : this.pedidoService.criar(payload);

    request$.subscribe({
      next: () => {
        this.messageService.add({
          severity: 'success',
          summary: 'Sucesso',
          detail: `Pedido ${this.pedidoData?.id ? 'atualizado' : 'criado'} com sucesso!`,
        });
        this.isSaving = false;
        this.saved.emit();
      },
      error: (err: HttpErrorResponse) => {
        this.isSaving = false;
        this.messageService.add({
          severity: 'error',
          summary: 'Erro',
          detail: err.error?.message || 'Erro ao salvar o pedido.',
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
