import { Component, EventEmitter, inject, Input, OnInit, Output } from '@angular/core';
import { ProdutoCat } from "../produto-categoria.model";
import {FormBuilder, FormGroup, FormsModule, ReactiveFormsModule, Validators} from "@angular/forms";
import { ProdutoCategoriaService } from "../produto-categoria.service";
import { MessageService } from "primeng/api";
import {HttpErrorResponse} from '@angular/common/http';
import {ButtonDirective} from 'primeng/button';
import {Checkbox} from 'primeng/checkbox';
import { TextareaModule } from "primeng/textarea";
import {CommonModule} from '@angular/common';
import {InputText} from 'primeng/inputtext';

@Component({
  selector: 'app-produto-categoria-form',
  imports: [
    CommonModule, FormsModule, ReactiveFormsModule,
    InputText, ButtonDirective, Checkbox, TextareaModule
  ],
  templateUrl: './produto-categoria-form.html',
  styleUrl: './produto-categoria-form.scss',
})
export class ProdutoCategoriaForm implements OnInit  {
  @Input() categoriaData: ProdutoCat | null = null;
  @Output() saved = new EventEmitter<void>();
  @Output() canceled = new EventEmitter<void>();

  private fb = inject(FormBuilder);
  private messageService = inject(MessageService);
  private categoriaService = inject(ProdutoCategoriaService);

  form!: FormGroup;

  constructor() {
    this.form = this.fb.group({
      id: [null],
      nome: ['', [Validators.required, Validators.maxLength(100)]],
      descricao: ['', [Validators.maxLength(500)]],
      ativa: [true, Validators.required]
    });
  }

  ngOnInit(): void {
    if (this.categoriaData) {
      this.form.patchValue({
        id: this.categoriaData.id,
        nome: this.categoriaData.nome,
        descricao: this.categoriaData.descricao,
        ativa: this.categoriaData.ativa
      });
    }
  }

  save() {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const payload = this.form.value;

    if (this.categoriaData && this.categoriaData.id) {
      this.categoriaService.update(this.categoriaData.id, payload).subscribe({
        next: () => {
          this.messageService.add({ severity: 'success', summary: 'Sucesso', detail: 'Categoria atualizada com sucesso' });
          this.saved.emit();
        },
        error: (err: HttpErrorResponse) => this.messageService.add({ severity: 'error', summary: 'Erro', detail: 'Erro ao atualizar categoria: ' + err.message })
      });
    } else {
      this.categoriaService.create(payload).subscribe({
        next: () => {
          this.messageService.add({ severity: 'success', summary: 'Sucesso', detail: 'Categoria criada com sucesso' });
          this.saved.emit();
        },
        error: (err: HttpErrorResponse) => this.messageService.add({ severity: 'error', summary: 'Erro', detail: 'Erro ao criar categoria: ' + err.message })
      });
    }
  }

  onCancel(): void {
    this.canceled.emit();
  }
}
