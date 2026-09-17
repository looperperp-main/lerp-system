import { Component, EventEmitter, inject, Input, OnInit, Output } from '@angular/core';
import { NgIf } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MessageService } from 'primeng/api';
import { Button } from 'primeng/button';
import { Checkbox } from 'primeng/checkbox';
import { InputNumber } from 'primeng/inputnumber';
import { InputText } from 'primeng/inputtext';
import { Select } from 'primeng/select';
import { Textarea } from 'primeng/textarea';
import { HttpErrorResponse } from '@angular/common/http';
import { EstoqueService } from '../../estoque.service';
import {
  EstoqueSaldo,
  OrigemMovimentoEstoque,
  TIPO_AJUSTE_LABEL,
  TipoAjusteEstoque,
} from '../../estoque.model';

/** Modal de ajuste por saldo contado (spec/modulos/estoque/estoque.md §5.3/E7, D5). */
@Component({
  selector: 'app-ajuste-form',
  imports: [NgIf, ReactiveFormsModule, Button, Checkbox, InputNumber, InputText, Select, Textarea],
  templateUrl: './ajuste-form.html',
  styleUrl: './ajuste-form.scss',
})
export class AjusteForm implements OnInit {
  @Input() saldo: EstoqueSaldo | null = null;
  @Input() nomeProduto = '';
  @Input() nomeDeposito = '';
  @Output() saved = new EventEmitter<void>();
  @Output() canceled = new EventEmitter<void>();

  private fb = inject(FormBuilder);
  private estoqueService = inject(EstoqueService);
  private messageService = inject(MessageService);

  form!: FormGroup;
  isSaving = false;

  origemOptions: { label: string; value: OrigemMovimentoEstoque }[] = [
    { label: 'Ajuste manual', value: 'AJUSTE' },
    { label: 'Inventário', value: 'INVENTARIO' },
  ];

  tipoAjusteOptions = Object.entries(TIPO_AJUSTE_LABEL).map(([value, label]) => ({
    label,
    value: value as TipoAjusteEstoque,
  }));

  ngOnInit(): void {
    this.form = this.fb.group({
      // D5 (spec/modulos/estoque/estoque.md §5.3): quantidadeContada é a contagem física da prateleira, nunca
      // negativa — mesmo que o saldo atual esteja negativo (venda sem bloqueio de estoque), a
      // correção é contar o valor real (ex.: 0) e deixar o sistema calcular o delta sozinho.
      // Backend valida a mesma regra (ESTOQUE_QUANTIDADE_CONTADA_INVALIDA); manter em sincronia.
      quantidadeContada: [
        Math.max(0, this.saldo?.quantidade ?? 0),
        [Validators.required, Validators.min(0)],
      ],
      origem: ['AJUSTE', Validators.required],
      tipoAjuste: [null, Validators.required],
      motivo: ['', [Validators.required, Validators.maxLength(500)]],
      documentoReferencia: ['', Validators.maxLength(200)],
      valorUnitario: [null],
      // RN-EST-12 (item 5): saída que zeraria o saldo abaixo de zero é bloqueada por padrão para
      // produtos REVENDA/USO_CONSUMO/MATERIA_PRIMA; marcar permite passar e abre uma pendência de
      // regularização em vez de travar o ajuste (nunca silencioso).
      permitirSaldoNegativo: [false],
    });

    // RN-EST-11 (§12): ajuste de entrada (contado > saldo atual) exige custo, exceto SALDO_INICIAL.
    // Backend valida a mesma regra (ESTOQUE_AJUSTE_ENTRADA_SEM_CUSTO); manter em sincronia.
    this.form
      .get('quantidadeContada')!
      .valueChanges.subscribe(() => this.atualizarValidacaoCusto());
    this.form.get('tipoAjuste')!.valueChanges.subscribe(() => this.atualizarValidacaoCusto());
  }

  get exigeCusto(): boolean {
    const quantidadeContada = this.form?.value?.quantidadeContada ?? 0;
    const saldoAtual = this.saldo?.quantidade ?? 0;
    return quantidadeContada > saldoAtual && this.form?.value?.tipoAjuste !== 'SALDO_INICIAL';
  }

  private atualizarValidacaoCusto(): void {
    const valorUnitario = this.form.get('valorUnitario')!;
    valorUnitario.setValidators(this.exigeCusto ? [Validators.required] : []);
    valorUnitario.updateValueAndValidity({ emitEvent: false });
  }

  onSubmit(): void {
    if (this.form.invalid || !this.saldo) {
      this.form.markAllAsTouched();
      return;
    }

    this.isSaving = true;
    const formValue = this.form.value;

    this.estoqueService
      .ajustar({
        produtoId: this.saldo.produtoId,
        depositoId: this.saldo.depositoId,
        quantidadeContada: formValue.quantidadeContada,
        origem: formValue.origem,
        tipoAjuste: formValue.tipoAjuste,
        motivo: formValue.motivo,
        documentoReferencia: formValue.documentoReferencia || null,
        valorUnitario: formValue.valorUnitario,
        permitirSaldoNegativo: formValue.permitirSaldoNegativo,
      })
      .subscribe({
        next: () => {
          this.messageService.add({
            severity: 'success',
            summary: 'Sucesso',
            detail: 'Ajuste de estoque registrado com sucesso!',
          });
          this.isSaving = false;
          this.saved.emit();
        },
        error: (err: HttpErrorResponse) => {
          this.isSaving = false;
          if (err.error?.message && err.error?.error && err.error?.status) {
            this.messageService.add({
              severity: 'error',
              summary: 'Erro ao ajustar estoque',
              detail: `[${err.error.status}] ${err.error.error} - ${err.error.message}`,
              life: 5000,
            });
          } else {
            this.messageService.add({
              severity: 'error',
              summary: 'Erro ao ajustar estoque',
              detail: 'Erro inesperado de comunicação com o servidor.',
              life: 5000,
            });
          }
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
