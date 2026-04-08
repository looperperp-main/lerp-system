import {Component, EventEmitter, inject, Input, OnInit, Output} from '@angular/core';
import {GrupoCliente} from '../../grupo-clientes/grupo-cliente.model';
import {FormBuilder, FormGroup, ReactiveFormsModule, Validators} from '@angular/forms';
import {ToastrService} from 'ngx-toastr';
import {DepositoService} from '../deposito.service';
import {Deposito} from '../deposito.model';
import {Button} from 'primeng/button';
import {Checkbox} from 'primeng/checkbox';
import {InputText} from 'primeng/inputtext';
import {NgClass, NgIf} from '@angular/common';
import {Textarea} from 'primeng/textarea';

@Component({
  selector: 'app-deposito-form',
  imports: [
    Button,
    Checkbox,
    InputText,
    NgIf,
    ReactiveFormsModule,
    Textarea,
    NgClass
  ],
  templateUrl: './deposito-form.html',
  styleUrl: './deposito-form.scss',
})
export class DepositoForm implements OnInit{
  @Input() depositoData: Deposito | null = null;
  @Output() saved = new EventEmitter<void>();
  @Output() canceled = new EventEmitter<void>();

  private fb = inject(FormBuilder);
  private depositoService = inject(DepositoService);
  private toastService = inject(ToastrService);

  form!: FormGroup;
  isSaving = false;

  ngOnInit(): void {
    this.form = this.fb.group({
      id: [this.depositoData?.id || null],
      nome: [this.depositoData?.nome || '', [Validators.required, Validators.maxLength(100)]],
      descricao: [this.depositoData?.descricao || '', [Validators.maxLength(500)]],
      tipo: [this.depositoData?.tipo || '', [Validators.maxLength(30)]],
      ativo: [this.depositoData ? this.depositoData.ativo : true]
    });
  }

  onSubmit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.isSaving = true;
    const formValue: Deposito = this.form.value;

    this.depositoService.salvar(formValue).subscribe({
      next: () => {
        this.toastService.success('Deposito salvo com sucesso!');
        this.isSaving = false;
        this.saved.emit();
      },
      error: (err) => {
        this.isSaving = false;
        if (err.error?.message === 'DEPOSITO_ALREADY_EXISTS') {
          this.toastService.error('Já existe um Deposito com este nome.');
        } else {
          this.toastService.error('Erro ao salvar o deposito.');
          this.canceled.emit();
        }
      }
    });
  }

  onCancel(): void {
    this.canceled.emit();
  }

  // Helpers para o template
  isFieldInvalid(field: string): boolean {
    const control = this.form.get(field);
    return !!(control && control.invalid && (control.dirty || control.touched));
  }
}
