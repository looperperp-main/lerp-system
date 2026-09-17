import { Component, EventEmitter, inject, Input, OnInit, Output } from '@angular/core';
import { NgIf } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MessageService } from 'primeng/api';
import { Button } from 'primeng/button';
import { Checkbox } from 'primeng/checkbox';
import { InputNumber } from 'primeng/inputnumber';
import { InputText } from 'primeng/inputtext';
import { Textarea } from 'primeng/textarea';
import { HttpErrorResponse } from '@angular/common/http';
import { EstoqueService } from '../../estoque.service';
import { EstoqueSaldo } from '../../estoque.model';

/** Modal de registro de consumo (requisição de almoxarifado, D7/E9, RN-EST-10). */
@Component({
  selector: 'app-consumo-form',
  imports: [NgIf, ReactiveFormsModule, Button, Checkbox, InputNumber, InputText, Textarea],
  templateUrl: './consumo-form.html',
  styleUrl: './consumo-form.scss',
})
export class ConsumoForm implements OnInit {
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

  ngOnInit(): void {
    this.form = this.fb.group({
      quantidade: [null, [Validators.required, Validators.min(0.0001)]],
      // ponytail: sem cadastro de centro de custo no projeto ainda — id informado à mão até existir
      // uma tela própria (financeiro-service, ver spec/modulos/estoque/estoque.md §12 RN-EST-10/11).
      centroCustoId: ['', Validators.required],
      motivo: ['', Validators.maxLength(500)],
      permitirSaldoNegativo: [false],
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
      .consumir({
        produtoId: this.saldo.produtoId,
        depositoId: this.saldo.depositoId,
        quantidade: formValue.quantidade,
        centroCustoId: formValue.centroCustoId,
        motivo: formValue.motivo || null,
        permitirSaldoNegativo: formValue.permitirSaldoNegativo,
      })
      .subscribe({
        next: () => {
          this.messageService.add({
            severity: 'success',
            summary: 'Sucesso',
            detail: 'Consumo registrado com sucesso!',
          });
          this.isSaving = false;
          this.saved.emit();
        },
        error: (err: HttpErrorResponse) => {
          this.isSaving = false;
          if (err.error?.message && err.error?.error && err.error?.status) {
            this.messageService.add({
              severity: 'error',
              summary: 'Erro ao registrar consumo',
              detail: `[${err.error.status}] ${err.error.error} - ${err.error.message}`,
              life: 5000,
            });
          } else {
            this.messageService.add({
              severity: 'error',
              summary: 'Erro ao registrar consumo',
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
