import { Component, EventEmitter, inject, Input, OnInit, Output } from '@angular/core';
import { GrupoCliente } from '../grupo-cliente.model';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MessageService } from 'primeng/api';
import { GrupoClienteService } from '../grupo-cliente.service';
import { Checkbox } from 'primeng/checkbox';
import { Button } from 'primeng/button';
import { NgClass, NgIf } from '@angular/common';
import { Textarea } from 'primeng/textarea';
import { InputText } from 'primeng/inputtext';

@Component({
  selector: 'app-grupo-cliente-form',
  imports: [Checkbox, ReactiveFormsModule, Button, NgClass, Textarea, InputText, NgIf],
  templateUrl: './grupo-cliente-form.html',
  styleUrl: './grupo-cliente-form.scss',
})
export class GrupoClienteForm implements OnInit {
  @Input() grupoData: GrupoCliente | null = null;
  @Output() saved = new EventEmitter<void>();
  @Output() canceled = new EventEmitter<void>();

  private fb = inject(FormBuilder);
  private grupoService = inject(GrupoClienteService);
  private messageService = inject(MessageService);

  form!: FormGroup;
  isSaving = false;

  ngOnInit(): void {
    this.form = this.fb.group({
      id: [this.grupoData?.id || null],
      nome: [this.grupoData?.nome || '', [Validators.required, Validators.maxLength(100)]],
      descricao: [this.grupoData?.descricao || '', [Validators.maxLength(500)]],
      ativo: [this.grupoData ? this.grupoData.ativo : true],
    });
  }

  onSubmit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.isSaving = true;
    const formValue: GrupoCliente = this.form.value;

    this.grupoService.salvar(formValue).subscribe({
      next: () => {
        this.messageService.add({
          severity: 'success',
          summary: 'Sucesso',
          detail: 'Grupo de cliente salvo com sucesso!',
        });
        this.isSaving = false;
        this.saved.emit();
      },
      error: (err) => {
        this.isSaving = false;
        // O backend joga "GROUP_C_ALREADY_EXISTS" caso já exista
        if (err.error?.message === 'GROUP_C_ALREADY_EXISTS') {
          this.messageService.add({
            severity: 'error',
            summary: 'Erro',
            detail: 'Já existe um grupo com este nome.',
          });
        } else {
          this.messageService.add({
            severity: 'error',
            summary: 'Erro',
            detail: 'Erro ao salvar o grupo.',
          });
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
