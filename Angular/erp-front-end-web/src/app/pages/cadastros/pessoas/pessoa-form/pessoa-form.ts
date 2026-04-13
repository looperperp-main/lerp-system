import { Component, EventEmitter, Input, Output, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Pessoa } from '../pessoa.model';
import { PessoaService } from '../pessoa.service';
import { StepperModule } from 'primeng/stepper';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import {Select} from 'primeng/select';
import {DatePickerModule} from 'primeng/datepicker';
import {InputMaskDirective} from 'primeng/inputmask';
import {Checkbox} from 'primeng/checkbox';
import {TableModule} from 'primeng/table';

@Component({
  selector: 'app-pessoa-form',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, StepperModule, ButtonModule, InputTextModule, Select, DatePickerModule, InputMaskDirective, Checkbox, TableModule],
  templateUrl: './pessoa-form.html',
  styleUrl: './pessoa-form.scss',
})
export class PessoaForm implements OnInit {
  @Input() pessoaEdit: Pessoa | null = null;
  @Output() onSaved = new EventEmitter<void>();
  @Output() onCanceled = new EventEmitter<void>();

  formPessoa!: FormGroup;
  formEndereco!: FormGroup;
  formContato!: FormGroup;
  pessoaId: string | null = null;
  pessoaSalva: any = null;
  activeStep: number = 1;

  enderecos: any[] = [];
  contatos: any[] = [];

  tiposPessoa = [
    { label: 'Pessoa Física', value: 'PF' },
    { label: 'Pessoa Jurídica', value: 'PJ' }
  ];

  tiposEndereco = [
    { label: 'Fiscal', value: 'FISCAL' },
    { label: 'Cobrança', value: 'COBRANCA' },
    { label: 'Entrega', value: 'ENTREGA' },
    { label: 'Principal', value: 'PRINCIPAL' }
  ];

  tiposContato = [
    { label: 'Comercial', value: 'COMERCIAL' },
    { label: 'Financeiro', value: 'FINANCEIRO' },
    { label: 'Técnico', value: 'TECNICO' },
    { label: 'Administrativo', value: 'ADMINISTRATIVO' },
    { label: 'Outro', value: 'OUTRO' }
  ];

  constructor(private fb: FormBuilder, private pessoaService: PessoaService) {}

  ngOnInit() {
    this.formPessoa = this.fb.group({
      tipo: ['PJ', Validators.required],
      nomeRazao: ['', Validators.required],
      apelidoFantasia: ['', ],
      documento: ['', Validators.required],
      ie: ['', ],
      im: ['', ],
      dataNascimento: ['', ],
      ativo: [true]
    });

    this.formEndereco = this.fb.group({
      id: [null],
      tipo: ['', Validators.required],
      logradouro: ['', Validators.required],
      numero: [''],
      complemento: [''],
      bairro: [''],
      cidade: ['', Validators.required],
      uf: ['', Validators.required],
      cep: ['', Validators.required],
      ibgeCodigo: [''],
      pais: [''],
      principal: [false, Validators.required],
    });

    this.formContato = this.fb.group({
      id: [null],
      nome: ['', Validators.required],
      tipo: ['COMERCIAL', Validators.required],
      cargo: [''],
      email: [''],
      telefone: [''],
      ativo: [true]
    });

    if (this.pessoaEdit) {
      this.pessoaId = this.pessoaEdit.id!;
      this.pessoaSalva = this.pessoaEdit;
      this.formPessoa.patchValue(this.pessoaEdit);
      this.carregarEnderecos();
      this.carregarContatos();
    }
  }

  salvarPessoa() {
    if (this.formPessoa.invalid) return;

    const payload = this.formPessoa.value;

    if (this.pessoaId) {
      this.pessoaService.atualizar(this.pessoaId, payload).subscribe(res => {
        this.pessoaSalva = res;
        setTimeout(() => this.activeStep = 2);
        this.carregarEnderecos();
      });
    } else {
      this.pessoaService.criar(payload).subscribe(res => {
        this.pessoaId = res.id!;
        this.pessoaSalva = res;
        setTimeout(() => this.activeStep = 2);
        this.carregarEnderecos();
      });
    }
  }

  // --- ENDEREÇO ---
  carregarEnderecos() {
    const url = this.pessoaSalva?._links?.enderecos?.href;
    if (url) {
      this.pessoaService.listarEnderecos(url).subscribe(res => {
        this.enderecos = res._embedded?.enderecos || [];
      });
    }
  }

  editarEndereco(end: any) {
    this.formEndereco.patchValue(end);
  }

  salvarEndereco() {
    if (this.formEndereco.invalid) return;
    const url = this.pessoaSalva?._links?.enderecos?.href;
    if (!url) return;

    this.pessoaService.salvarEndereco(url, this.formEndereco.value).subscribe(() => {
      this.formEndereco.reset({ tipo: 'COMERCIAL', pais: 'Brasil', principal: false });
      this.carregarEnderecos();
    });
  }

  // --- CONTATO ---
  carregarContatos() {
    const url = this.pessoaSalva?._links?.contatos?.href;
    if (url) {
      this.pessoaService.listarContatos(url).subscribe(res => {
        this.contatos = res._embedded?.contatos || [];
      });
    }
  }

  editarContato(cont: any) {
    this.formContato.patchValue(cont);
  }

  salvarContato() {
    if (this.formContato.invalid) return;
    const url = this.pessoaSalva?._links?.contatos?.href;
    if (!url) return;

    this.pessoaService.salvarContato(url, this.formContato.value).subscribe(() => {
      this.formContato.reset({ tipo: 'COMERCIAL', ativo: true });
      this.carregarContatos();
    });
  }

  // Helpers para o template
  isFieldInvalid(field: string): boolean {
    const control = this.formPessoa.get(field);
    return !!(control && control.invalid && (control.dirty || control.touched));
  }
}
