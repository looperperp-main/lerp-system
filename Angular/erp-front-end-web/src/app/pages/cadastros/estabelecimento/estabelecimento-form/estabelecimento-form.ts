import { Component, EventEmitter, inject, Input, OnInit, Output } from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MessageService } from 'primeng/api';
import { Button } from 'primeng/button';
import { Checkbox } from 'primeng/checkbox';
import { InputText } from 'primeng/inputtext';
import { NgClass, NgIf } from '@angular/common';
import { EstabelecimentoService } from '../estabelecimento.service';
import { Estabelecimento } from '../estabelecimento.model';
import { CnpjService } from '../../../../services/cnpj.service';

@Component({
  selector: 'app-estabelecimento-form',
  imports: [Button, Checkbox, InputText, NgClass, NgIf, ReactiveFormsModule],
  templateUrl: './estabelecimento-form.html',
  styleUrl: './estabelecimento-form.scss',
})
export class EstabelecimentoForm implements OnInit {
  @Input() pessoaId!: string;
  @Input() estabelecimentoData: Estabelecimento | null = null;
  @Output() saved = new EventEmitter<void>();
  @Output() canceled = new EventEmitter<void>();

  private fb = inject(FormBuilder);
  private estabelecimentoService = inject(EstabelecimentoService);
  private messageService = inject(MessageService);
  private cnpjService = inject(CnpjService);

  form!: FormGroup;
  isSaving = false;

  get isMatriz(): boolean {
    return !!this.estabelecimentoData?.matriz;
  }

  ngOnInit(): void {
    this.form = this.fb.group({
      id: [this.estabelecimentoData?.id || null],
      // Matriz: cnpjCompleto é espelhado do documento da Pessoa por PessoaService — não editável aqui.
      cnpjCompleto: [
        { value: this.estabelecimentoData?.cnpjCompleto || '', disabled: this.isMatriz },
        [Validators.required, Validators.maxLength(18)],
      ],
      ie: [this.estabelecimentoData?.ie || '', [Validators.maxLength(20)]],
      im: [this.estabelecimentoData?.im || '', [Validators.maxLength(20)]],
      ativo: [this.estabelecimentoData ? this.estabelecimentoData.ativo : true],
    });
  }

  onSubmit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.isSaving = true;
    const formValue: Estabelecimento = this.form.getRawValue();

    this.estabelecimentoService.salvar(this.pessoaId, formValue).subscribe({
      next: () => {
        this.messageService.add({
          severity: 'success',
          summary: 'Sucesso',
          detail: 'Estabelecimento salvo com sucesso!',
        });
        this.isSaving = false;
        this.saved.emit();
      },
      error: () => {
        this.isSaving = false;
        this.messageService.add({
          severity: 'error',
          summary: 'Erro',
          detail: 'Erro ao salvar o estabelecimento.',
        });
      },
    });
  }

  onCnpjInput(value: string): void {
    this.form.get('cnpjCompleto')?.setValue(this.cnpjService.aplicarMascara(value));
  }

  onCancel(): void {
    this.canceled.emit();
  }

  isFieldInvalid(field: string): boolean {
    const control = this.form.get(field);
    return !!(control && control.invalid && (control.dirty || control.touched));
  }
}
