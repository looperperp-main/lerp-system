import { Component, EventEmitter, inject, Input, OnInit, Output } from '@angular/core';
import { NgIf } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MessageService } from 'primeng/api';
import { Button } from 'primeng/button';
import { InputNumber } from 'primeng/inputnumber';
import { Select } from 'primeng/select';
import { Textarea } from 'primeng/textarea';
import { HttpErrorResponse } from '@angular/common/http';
import { EstoqueService } from '../../estoque.service';
import { EstoqueSaldo, OrigemMovimentoEstoque } from '../../estoque.model';

/** Modal de ajuste por saldo contado (spec/estoque.md §5.3/E7, D5). */
@Component({
  selector: 'app-ajuste-form',
  imports: [NgIf, ReactiveFormsModule, Button, InputNumber, Select, Textarea],
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

  ngOnInit(): void {
    this.form = this.fb.group({
      // D5 (spec/estoque.md §5.3): quantidadeContada é a contagem física da prateleira, nunca
      // negativa — mesmo que o saldo atual esteja negativo (venda sem bloqueio de estoque), a
      // correção é contar o valor real (ex.: 0) e deixar o sistema calcular o delta sozinho.
      // Backend valida a mesma regra (ESTOQUE_QUANTIDADE_CONTADA_INVALIDA); manter em sincronia.
      quantidadeContada: [
        Math.max(0, this.saldo?.quantidade ?? 0),
        [Validators.required, Validators.min(0)],
      ],
      origem: ['AJUSTE', Validators.required],
      motivo: ['', [Validators.required, Validators.maxLength(500)]],
      valorUnitario: [null],
    });
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
        motivo: formValue.motivo,
        valorUnitario: formValue.valorUnitario,
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
