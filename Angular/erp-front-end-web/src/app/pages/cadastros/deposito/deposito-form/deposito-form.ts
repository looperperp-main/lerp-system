import { Component, EventEmitter, inject, Input, OnInit, Output } from '@angular/core';
import { GrupoCliente } from '../../grupo-clientes/grupo-cliente.model';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MessageService } from 'primeng/api';
import { DepositoService } from '../deposito.service';
import { Deposito } from '../deposito.model';
import { EstabelecimentoService } from '../../estabelecimento/estabelecimento.service';
import { Estabelecimento } from '../../estabelecimento/estabelecimento.model';
import { Button } from 'primeng/button';
import { Checkbox } from 'primeng/checkbox';
import { InputText } from 'primeng/inputtext';
import { NgClass, NgIf } from '@angular/common';
import { Textarea } from 'primeng/textarea';
import { Select } from 'primeng/select';
import { CnpjPipe } from '../../../../util/pipe/cnpj.pipe';

type EstabelecimentoOption = Estabelecimento & { label: string };

@Component({
  selector: 'app-deposito-form',
  imports: [Button, Checkbox, InputText, NgIf, ReactiveFormsModule, Textarea, NgClass, Select],
  templateUrl: './deposito-form.html',
  styleUrl: './deposito-form.scss',
})
export class DepositoForm implements OnInit {
  @Input() depositoData: Deposito | null = null;
  @Output() saved = new EventEmitter<void>();
  @Output() canceled = new EventEmitter<void>();

  private fb = inject(FormBuilder);
  private depositoService = inject(DepositoService);
  private estabelecimentoService = inject(EstabelecimentoService);
  private messageService = inject(MessageService);

  form!: FormGroup;
  isSaving = false;
  estabelecimentos: EstabelecimentoOption[] = [];

  ngOnInit(): void {
    this.form = this.fb.group({
      id: [this.depositoData?.id || null],
      nome: [this.depositoData?.nome || '', [Validators.required, Validators.maxLength(100)]],
      descricao: [this.depositoData?.descricao || '', [Validators.maxLength(500)]],
      tipo: [this.depositoData?.tipo || '', [Validators.maxLength(30)]],
      ativo: [this.depositoData ? this.depositoData.ativo : true],
      estabelecimentoId: [this.depositoData?.estabelecimentoId || null],
    });

    this.carregarEstabelecimentos();
  }

  private carregarEstabelecimentos(): void {
    this.estabelecimentoService.buscarPessoaIdProprio().subscribe({
      next: (pessoaId) => {
        this.estabelecimentoService.listar(pessoaId).subscribe({
          next: (lista) => (this.estabelecimentos = lista.map((e) => this.toOption(e))),
          error: () => (this.estabelecimentos = []),
        });
      },
      error: () => (this.estabelecimentos = []),
    });
  }

  private toOption(e: Estabelecimento): EstabelecimentoOption {
    const tipo = e.matriz ? 'Matriz' : `Filial ${e.ordem || ''}`.trim();
    return { ...e, label: `${tipo} - ${new CnpjPipe().transform(e.cnpjCompleto)}` };
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
        this.messageService.add({
          severity: 'success',
          summary: 'Sucesso',
          detail: 'Depósito salvo com sucesso!',
        });
        this.isSaving = false;
        this.saved.emit();
      },
      error: (err) => {
        this.isSaving = false;
        if (err.error?.message === 'DEPOSITO_ALREADY_EXISTS') {
          this.messageService.add({
            severity: 'error',
            summary: 'Erro',
            detail: 'Já existe um Depósito com este nome.',
          });
          this.canceled.emit();
        } else {
          this.messageService.add({
            severity: 'error',
            summary: 'Erro',
            detail: 'Erro ao salvar o depósito.',
          });
          this.canceled.emit();
        }
      },
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
