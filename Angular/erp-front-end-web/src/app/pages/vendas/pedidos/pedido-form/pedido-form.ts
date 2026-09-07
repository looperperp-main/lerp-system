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
import { PedidoService } from '../pedido.service';
import { ModalidadeFrete, Pedido, PedidoRequest } from '../pedido.model';
import { ClientesService } from '../../../cadastros/cliente/clientes.service';
import { VendedorService } from '../../../cadastros/vendedores/vendedor.service';
import { CondPagamentoService } from '../../../cadastros/cond-pagamento/cond-pagamento.service';
import { ProdutoService } from '../../../cadastros/produtos/produto.service';

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
  @Input() pedidoData: Pedido | null = null;
  @Output() saved = new EventEmitter<void>();
  @Output() canceled = new EventEmitter<void>();

  private fb = inject(FormBuilder);
  private pedidoService = inject(PedidoService);
  private clientesService = inject(ClientesService);
  private vendedorService = inject(VendedorService);
  private condPagamentoService = inject(CondPagamentoService);
  private produtoService = inject(ProdutoService);
  private messageService = inject(MessageService);

  form!: FormGroup;
  isSaving = false;

  clientesOptions = signal<any[]>([]);
  vendedoresOptions = signal<any[]>([]);
  condicoesOptions = signal<any[]>([]);
  produtosOptions = signal<any[]>([]);

  modalidadeFreteOptions: { label: string; value: ModalidadeFrete }[] = [
    { label: 'CIF (frete por conta do emitente)', value: 'CIF' },
    { label: 'FOB (frete por conta do destinatário)', value: 'FOB' },
    { label: 'Sem Frete', value: 'SEM_FRETE' },
  ];

  ngOnInit(): void {
    this.form = this.fb.group({
      clienteId: [null, Validators.required],
      vendedorId: [null],
      condicaoPagamentoId: [null],
      modalidadeFrete: [null],
      dataEmissao: [new Date()],
      dataValidade: [null],
      observacao: ['', [Validators.maxLength(1000)]],
      itens: this.fb.array([]),
    });

    this.populateForm();
    this.loadDropdowns();
  }

  ngOnChanges(changes: SimpleChanges): void {
    // O diálogo de edição abre com os dados parciais da linha da tabela (evita o NG0100 do
    // p-dialog ao abrir de dentro de um subscribe assíncrono, ver pedidos.ts#editarPedido);
    // quando o pedido completo chega do backend, pedidoData troca de referência e o
    // formulário precisa ser repopulado. Mesmo padrão de produtos-form.ts.
    if (changes['pedidoData'] && !changes['pedidoData'].firstChange && this.form) {
      this.populateForm();
    }
  }

  private populateForm(): void {
    this.itensArray.clear();

    this.form.patchValue({
      clienteId: this.pedidoData?.clienteId || null,
      vendedorId: this.pedidoData?.vendedorId || null,
      condicaoPagamentoId: this.pedidoData?.condicaoPagamentoId || null,
      modalidadeFrete: this.pedidoData?.modalidadeFrete || null,
      dataEmissao: this.pedidoData?.dataEmissao
        ? new Date(this.pedidoData.dataEmissao)
        : new Date(),
      dataValidade: this.pedidoData?.dataValidade ? new Date(this.pedidoData.dataValidade) : null,
      observacao: this.pedidoData?.observacao || '',
    });

    if (this.pedidoData?.itens?.length) {
      this.pedidoData.itens.forEach((item) =>
        this.addItem(item.produtoId, item.quantidade, item.precoUnitario, item.desconto),
      );
    } else {
      this.addItem();
    }
  }

  get itensArray(): FormArray {
    return this.form.get('itens') as FormArray;
  }

  private loadDropdowns(): void {
    this.clientesService.getAll(0, 1000).subscribe((res) => {
      const content = res._embedded ? res._embedded.clientes : res.content || [];
      this.clientesOptions.set(content.map((c: any) => ({ label: c.nome, value: c.id })));
    });

    this.vendedorService.getAll(0, 1000).subscribe((res) => {
      const content = res._embedded ? res._embedded.vendedor : res.content || [];
      this.vendedoresOptions.set(content.map((v: any) => ({ label: v.nome, value: v.id })));
    });

    this.condPagamentoService.listar(0, 1000).subscribe((res: any) => {
      const content = res._embedded ? res._embedded.condicaopagamento : res.content || [];
      this.condicoesOptions.set(content.map((c: any) => ({ label: c.nome, value: c.id })));
    });

    this.produtoService.getAll(0, 1000).subscribe((res) => {
      const content = res._embedded ? Object.values(res._embedded)[0] : res.content || [];
      this.produtosOptions.set(
        (content as any[]).map((p: any) => ({ label: `${p.sku} - ${p.nome}`, value: p.id })),
      );
    });
  }

  addItem(
    produtoId?: string,
    quantidade?: number,
    precoUnitario?: number | null,
    desconto?: number | null,
  ): void {
    this.itensArray.push(
      this.fb.group({
        produtoId: [produtoId || null, Validators.required],
        quantidade: [quantidade ?? 1, [Validators.required, Validators.min(0.001)]],
        precoUnitario: [precoUnitario ?? null],
        desconto: [desconto ?? 0],
      }),
    );
  }

  removeItem(index: number): void {
    this.itensArray.removeAt(index);
  }

  totalItem(index: number): number {
    const item = this.itensArray.at(index).value;
    const bruto = (item.quantidade || 0) * (item.precoUnitario || 0);
    return bruto - (item.desconto || 0);
  }

  totalGeral(): number {
    return this.itensArray.controls.reduce((sum, _, i) => sum + this.totalItem(i), 0);
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
    const payload: PedidoRequest = {
      clienteId: formValue.clienteId,
      vendedorId: formValue.vendedorId,
      condicaoPagamentoId: formValue.condicaoPagamentoId,
      modalidadeFrete: formValue.modalidadeFrete,
      observacao: formValue.observacao,
      dataEmissao: formValue.dataEmissao
        ? new Date(formValue.dataEmissao).toISOString().split('T')[0]
        : null,
      dataValidade: formValue.dataValidade
        ? new Date(formValue.dataValidade).toISOString().split('T')[0]
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
          detail: `Orçamento ${this.pedidoData?.id ? 'atualizado' : 'criado'} com sucesso!`,
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
