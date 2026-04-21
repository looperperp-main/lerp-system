import {Component, EventEmitter, inject, Input, OnInit, Output, signal} from '@angular/core';
import {InputNumber} from 'primeng/inputnumber';
import {Checkbox} from 'primeng/checkbox';
import {FormBuilder, FormGroup, FormsModule, ReactiveFormsModule, Validators} from '@angular/forms';
import {Select} from 'primeng/select';
import {ButtonDirective} from 'primeng/button';
import {InputText} from 'primeng/inputtext';
import {ClientesService} from '../clientes.service';
import {MessageService} from 'primeng/api';
import {Cliente} from '../clientes.model';
import {HttpErrorResponse} from '@angular/common/http';

@Component({
  selector: 'app-cliente-form',
  imports: [
    InputNumber, Checkbox, FormsModule, ReactiveFormsModule, Select, ButtonDirective, InputText
  ],
  templateUrl: './cliente-form.html',
  styleUrl: './cliente-form.scss',
})
export class ClienteForm implements OnInit{
  @Input() clienteData: Cliente | null = null;
  @Output() saved = new EventEmitter<void>();
  @Output() canceled = new EventEmitter<void>();

  private fb = inject(FormBuilder);
  private messageService = inject(MessageService);
  private clienteService = inject(ClientesService);

  // Sinais para Dropdowns
  pessoasOptions = signal<any[]>([]);
  condicoesOptions = signal<any[]>([]);
  gruposOptions = signal<any[]>([]);
  vendedoresOptions = signal<any[]>([]);

  form!: FormGroup;

  riscoOptions = [
    { label: 'BAIXO', value: 'BAIXO' },
    { label: 'MEDIO', value: 'MEDIO' },
    { label: 'ALTO', value: 'ALTO' }
  ];

  constructor() {
    this.form = this.fb.group({
      id: [null],
      pessoaId: [null, Validators.required],
      codigoInterno: ['', [Validators.maxLength(50)]],
      condicaoPagamentoId: [null],
      grupoClienteId: [null],
      vendedorId: [null],
      limiteCredito: [0, [Validators.min(0)]],
      classificacaoRisco: ['BAIXO'],
      prazoMedioPagamentoDias: [0, [Validators.min(0)]],
      ativo: [true]
    });
  }

  ngOnInit(): void {
    this.loadDropdowns();

    if (this.clienteData) {
      this.form.patchValue({
        id: this.clienteData.id,
        pessoaId: this.clienteData.pessoaId,
        codigoInterno: this.clienteData.codigoInterno,
        condicaoPagamentoId: this.clienteData.condicaoPagamentoId,
        grupoClienteId: this.clienteData.grupoClienteId,
        vendedorId: this.clienteData.vendedorId,
        limiteCredito: this.clienteData.limiteCredito,
        classificacaoRisco: this.clienteData.classificacaoRisco || 'BAIXO',
        prazoMedioPagamentoDias: this.clienteData.prazoMedioPagamentoDias,
        ativo: this.clienteData.ativo
      });
    }
  }

  loadDropdowns() {
    this.clienteService.getPessoasDropdown().subscribe(res => {
      const content = res._embedded ? res._embedded.pessoas : (res.content || []);
      this.pessoasOptions.set(content.map((p: any) => ({ label: p.nomeRazao, value: p.id })));
    });

    this.clienteService.getCondicoesPagamentoDropdown().subscribe(res => {
      const content = res._embedded ? res._embedded.condicaopagamento : (res.content || []);
      this.condicoesOptions.set(content.map((c: any) => ({ label: c.nome, value: c.id })));
    });

    this.clienteService.getGruposClienteDropdown().subscribe(res => {
      const content = res._embedded ? res._embedded.grupocliente : (res.content || []);
      this.gruposOptions.set(content.map((g: any) => ({ label: g.nome, value: g.id })));
    });

    this.clienteService.getVendedoresDropdown().subscribe(res => {
      const content = res._embedded ? res._embedded.vendedor : (res.content || []);
      this.vendedoresOptions.set(content.map((v: any) => ({ label: v.nome, value: v.id })));
    });
  }

  save() {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const payload = this.form.value;

    if (this.clienteData && this.clienteData.id) {
      this.clienteService.update(this.clienteData.id, payload).subscribe({
        next: () => {
          this.messageService.add({ severity: 'success', summary: 'Sucesso', detail: 'Cliente atualizado com sucesso' });
          this.saved.emit();
        },
        error: (err: HttpErrorResponse) => this.messageService.add({ severity: 'error', summary: 'Erro', detail: 'Erro ao atualizar cliente: ' + err.message })
      });
    } else {
      this.clienteService.create(payload).subscribe({
        next: () => {
          this.messageService.add({ severity: 'success', summary: 'Sucesso', detail: 'Cliente criado com sucesso' });
          this.saved.emit();
        },
        error: (err: HttpErrorResponse) => this.messageService.add({ severity: 'error', summary: 'Erro', detail: 'Erro ao criar cliente: ' + err.message })
      });
    }
  }

  onCancel(): void {
    this.canceled.emit();
  }
}
