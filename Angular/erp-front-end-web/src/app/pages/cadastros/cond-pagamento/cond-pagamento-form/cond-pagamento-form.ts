import {Component, EventEmitter, inject, Input, OnInit, Output} from '@angular/core';
import {CondPagamento} from '../cond-pagamento.model';
import {FormBuilder, FormGroup, ReactiveFormsModule, Validators} from '@angular/forms';
import {ToastrService} from 'ngx-toastr';
import {CondPagamentoService} from '../cond-pagamento.service';
import {Checkbox} from 'primeng/checkbox';
import {Button} from 'primeng/button';
import {NgClass, NgIf} from '@angular/common';
import {Textarea} from 'primeng/textarea';
import {InputText} from 'primeng/inputtext';

@Component({
  selector: 'app-cond-pagamento-form',
  imports: [
    Checkbox,
    ReactiveFormsModule,
    Button,
    NgClass,
    Textarea,
    InputText,
    NgIf
  ],
  templateUrl: './cond-pagamento-form.html',
  styleUrl: './cond-pagamento-form.scss',
})
export class CondPagamentoForm implements OnInit {
  @Input() cPagData: CondPagamento | null = null;
  @Output() saved = new EventEmitter<void>();
  @Output() canceled = new EventEmitter<void>();

  private fb = inject(FormBuilder);
  private grupoService = inject(CondPagamentoService);
  private toastService = inject(ToastrService);

  form!: FormGroup;
  isSaving = false;

  ngOnInit(): void {
    this.form = this.fb.group({
      id: [this.cPagData?.id || null],
      nome: [this.cPagData?.nome || '', [Validators.required, Validators.maxLength(100)]],
      descricao: [this.cPagData?.descricao || '', [Validators.maxLength(500)]],
      ativo: [this.cPagData ? this.cPagData.ativo : true]
    });
  }

  onSubmit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.isSaving = true;
    const formValue: CondPagamento = this.form.value;

    this.grupoService.salvar(formValue).subscribe({
      next: () => {
        this.toastService.success('Condição de Pagamento salva com sucesso!');
        this.isSaving = false;
        this.saved.emit();
      },
      error: (err) => {
        this.isSaving = false;
        // O backend joga "GROUP_C_ALREADY_EXISTS" caso já exista
        if (err.error?.message === 'COND_PAG_ALREADY_EXISTS') {
          this.toastService.error('Já existe uma condição com este nome.');
          this.canceled.emit();
        } else {
          this.toastService.error('Erro ao salvar a Condição de Pagamento.');
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
