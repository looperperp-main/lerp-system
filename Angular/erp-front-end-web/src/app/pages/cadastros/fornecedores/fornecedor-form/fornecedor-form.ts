import {Component, EventEmitter, inject, Input, OnInit, Output, signal} from '@angular/core';
import {Fornecedor} from '../fornecedor.model';
import {FormBuilder, FormGroup, FormsModule, ReactiveFormsModule, Validators} from '@angular/forms';
import {FornecedorService} from '../fornecedor.service';
import {Checkbox} from 'primeng/checkbox';
import {Select} from 'primeng/select';
import {ButtonDirective} from 'primeng/button';
import {MessageService} from 'primeng/api';
import {HttpErrorResponse} from '@angular/common/http';

@Component({
  selector: 'app-fornecedor-form',
  imports: [
    Checkbox,
    FormsModule,
    ReactiveFormsModule,
    Select,
    ButtonDirective
  ],
  templateUrl: './fornecedor-form.html',
  styleUrl: './fornecedor-form.scss',
})
export class FornecedorForm implements OnInit {
  @Input() FornecedorData: Fornecedor | null = null;
  @Output() saved = new EventEmitter<void>();
  @Output() canceled = new EventEmitter<void>();

  private fb = inject(FormBuilder);
  private messageService = inject(MessageService);
  private fornecedorService = inject(FornecedorService);
  pessoasOptions = signal<any[]>([]);

  form!: FormGroup;

  constructor() {
    this.form = this.fb.group({
      id: [this.FornecedorData?.id || null],
      ativo: [this.FornecedorData?.ativo || false],
      pessoaId: [this.FornecedorData?.pessoaId || null, Validators.required]
    });
  }

  ngOnInit(): void {
    this.loadPessoas();
    if (this.FornecedorData) {
      this.form.patchValue({
        ativo: this.FornecedorData.ativo !== false, // Default true
        pessoaId: this.FornecedorData.pessoaId
      });
    }
  }

  loadPessoas() {
    this.fornecedorService.getPessoasDropdown().subscribe({
      next: (res) => {
        const content = res._embedded ? res._embedded.pessoas : (res.content || []);
        this.pessoasOptions.set(content.map((p: any) => ({
          label: p.nomeRazao,
          value: p.id
        })));
      },
      error: (err) => this.messageService.add({ severity: 'error', summary: 'Erro', detail: 'Erro ao carregar pessoas:'+err.error?.message })
    });
  }

  save() {
    if (this.form.invalid) {
      return;
    }

    const fornecedorData = this.form.value;

    if (this.FornecedorData) {
      this.fornecedorService.update(this.FornecedorData.id, fornecedorData).subscribe({
        next: () => {
          this.saved.emit();
          this.messageService.add({ severity: 'success', summary: 'Sucesso', detail: 'Fornecedor atualizado.' });
        },
        error: (err: HttpErrorResponse) => this.messageService.add({ severity: 'error', summary: 'Erro', detail: 'Erro ao atualizar fornecedor:'+err.error?.message })
      });
    } else {
      this.fornecedorService.create(fornecedorData).subscribe({
        next: () => {
          this.saved.emit();
          this.messageService.add({ severity: 'success', summary: 'Sucesso', detail: 'Fornecedor criado.' });
        },
        error: (err: HttpErrorResponse) => this.messageService.add({ severity: 'error', summary: 'Erro', detail: 'Erro ao criar fornecedor:'+err.error?.message })
      });
    }
  }

  onCancel(): void {
    this.canceled.emit();
  }
}
