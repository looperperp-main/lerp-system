
import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { MessageService } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { CheckboxModule } from 'primeng/checkbox';
import { DatePickerModule } from 'primeng/datepicker';

import { TabelaPreco } from '../tabela-preco.model';
import { TabelaPrecoService } from '../tabela-preco.service';
import { PrimaryButtonComponent } from '../../../../components/primary-button/primary-button';
import {HttpErrorResponse} from '@angular/common/http';

@Component({
  selector: 'app-tabela-precos-form',
  imports: [CommonModule, ReactiveFormsModule, ButtonModule, InputTextModule, CheckboxModule, DatePickerModule, PrimaryButtonComponent],
  templateUrl: './tabela-precos-form.html',
})
export class TabelaPrecosForm implements OnInit {
  @Input() tabelaPrecoData: TabelaPreco | null = null;
  @Output() saved = new EventEmitter<void>();
  @Output() canceled = new EventEmitter<void>();

  form!: FormGroup;
  loading = false;

  constructor(
    private fb: FormBuilder,
    private tabelaPrecoService: TabelaPrecoService,
    private messageService: MessageService
  ) {}

  ngOnInit(): void {
    this.initForm();
    if (this.tabelaPrecoData) {
      this.loadFormData();
    }
  }

  private initForm(): void {
    this.form = this.fb.group({
      nome: ['', [Validators.required, Validators.maxLength(100)]],
      moeda: ['BRL', [Validators.required, Validators.maxLength(3)]],
      ativa: [true],
      padrao: [false],
      inicioVigencia: [null, [Validators.required]],
      fimVigencia: [null]
    });
  }

  private loadFormData(): void {
    this.form.patchValue({
      ...this.tabelaPrecoData,
      inicioVigencia: this.tabelaPrecoData?.inicioVigencia ? new Date(this.tabelaPrecoData.inicioVigencia) : null,
      fimVigencia: this.tabelaPrecoData?.fimVigencia ? new Date(this.tabelaPrecoData.fimVigencia) : null
    });
  }

  onSubmit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.loading = true;
    const formValue = this.form.value;

    // Converte datas para ISO yyyy-MM-dd
    const payload: TabelaPreco = {
      ...formValue,
      inicioVigencia: formValue.inicioVigencia ? formValue.inicioVigencia.toISOString().split('T')[0] : null,
      fimVigencia: formValue.fimVigencia ? formValue.fimVigencia.toISOString().split('T')[0] : null,
    };

    const request$ = this.tabelaPrecoData?.id
      ? this.tabelaPrecoService.update(this.tabelaPrecoData.id, payload)
      : this.tabelaPrecoService.create(payload);

    request$.subscribe({
      next: () => {
        this.messageService.add({ severity: 'success', summary: 'Sucesso', detail: 'Tabela de Preço salva com sucesso!' });
        this.loading = false;
        this.saved.emit();
      },
      error: (errHttpE: HttpErrorResponse) => {
        console.error('Erro ao salvar', errHttpE);
        this.messageService.add({ severity: 'error', summary: 'Erro', detail: 'Falha ao salvar a Tabela de Preço: ' + errHttpE.error.message });
        this.loading = false;
      }
    });
  }

  onCancel(): void {
    this.canceled.emit();
  }
}
