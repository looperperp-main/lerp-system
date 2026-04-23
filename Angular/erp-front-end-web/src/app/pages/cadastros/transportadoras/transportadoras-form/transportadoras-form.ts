import { Component, EventEmitter, inject, Input, OnInit, Output, signal } from '@angular/core';
import { Transportadora } from '../transportadora.model';
import { FormBuilder, FormGroup, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { TransportadoraService } from '../transportadora.service';
import { Checkbox } from 'primeng/checkbox';
import { Select } from 'primeng/select';
import { ButtonDirective } from 'primeng/button';
import { MessageService } from 'primeng/api';
import { HttpErrorResponse } from '@angular/common/http';
import { InputText } from 'primeng/inputtext';
import {NgIf} from '@angular/common';

@Component({
  selector: 'app-transportadora-form',
  imports: [
    Checkbox,
    FormsModule,
    ReactiveFormsModule,
    Select,
    ButtonDirective,
    InputText,
    NgIf
  ],
  templateUrl: './transportadoras-form.html',
  styleUrl: './transportadoras-form.scss',
})
export class TransportadoraForm implements OnInit {
  @Input() TransportadoraData: Transportadora | null = null;
  @Output() saved = new EventEmitter<void>();
  @Output() canceled = new EventEmitter<void>();

  private fb = inject(FormBuilder);
  private messageService = inject(MessageService);
  private transportadoraService = inject(TransportadoraService);
  pessoasOptions = signal<any[]>([]);

  modalOptions = [
    { label: 'Rodoviário', value: 'RODOVIARIO' },
    { label: 'Aquaviário', value: 'AQUAVIARIO' },
    { label: 'Ferroviário', value: 'FERROVIARIO' },
    { label: 'Aéreo', value: 'AEREO' },
    { label: 'Dutoviário', value: 'DUTOVIARIO' }
  ];

  form!: FormGroup;

  constructor() {
    this.form = this.fb.group({
      id: [this.TransportadoraData?.id || null],
      ativo: [this.TransportadoraData?.ativo || false],
      pessoaId: [this.TransportadoraData?.pessoaId || null, Validators.required],
      rntrc: [this.TransportadoraData?.rntrc || null, [
        Validators.minLength(8),
        Validators.maxLength(8),
        Validators.pattern('^[0-9]{8}$')
      ]],
      modal: [this.TransportadoraData?.modal || null]
    });
  }

  ngOnInit(): void {
    this.loadPessoas();
    if (this.TransportadoraData) {
      this.form.patchValue({
        ativo: this.TransportadoraData.ativo !== false,
        pessoaId: this.TransportadoraData.pessoaId,
        rntrc: this.TransportadoraData.rntrc,
        modal: this.TransportadoraData.modal
      });
    }
  }

  loadPessoas() {
    this.transportadoraService.getPessoasDropdown().subscribe({
      next: (res) => {
        const content = res._embedded ? res._embedded.pessoas : (res.content || []);
        this.pessoasOptions.set(content.map((p: any) => ({
          label: p.nomeRazao,
          value: p.id
        })));
      },
      error: (err) => this.messageService.add({ severity: 'error', summary: 'Erro', detail: 'Erro ao carregar pessoas: ' + err.error?.message })
    });
  }

  save() {
    if (this.form.invalid) {
      return;
    }

    const data = this.form.value;

    if (this.TransportadoraData) {
      this.transportadoraService.update(this.TransportadoraData.id, data).subscribe({
        next: () => {
          this.saved.emit();
          this.messageService.add({ severity: 'success', summary: 'Sucesso', detail: 'Transportadora atualizada.' });
        },
        error: (err: HttpErrorResponse) => this.messageService.add({ severity: 'error', summary: 'Erro', detail: 'Erro ao atualizar: ' + err.error?.message })
      });
    } else {
      this.transportadoraService.create(data).subscribe({
        next: () => {
          this.saved.emit();
          this.messageService.add({ severity: 'success', summary: 'Sucesso', detail: 'Transportadora criada.' });
        },
        error: (err: HttpErrorResponse) => this.messageService.add({ severity: 'error', summary: 'Erro', detail: 'Erro ao criar: ' + err.error?.message })
      });
    }
  }

  onCancel(): void {
    this.canceled.emit();
  }
}
