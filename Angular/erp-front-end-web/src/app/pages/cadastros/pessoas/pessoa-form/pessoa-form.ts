import {ChangeDetectorRef, Component, EventEmitter, Input, OnInit, Output} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FormBuilder, FormGroup, ReactiveFormsModule, Validators} from '@angular/forms';
import {Pessoa} from '../pessoa.model';
import {PessoaService} from '../pessoa.service';
import {StepperModule} from 'primeng/stepper';
import {ButtonModule} from 'primeng/button';
import {InputTextModule} from 'primeng/inputtext';
import {Select} from 'primeng/select';
import {DatePickerModule} from 'primeng/datepicker';
import {InputMask, InputMaskDirective} from 'primeng/inputmask';
import {Checkbox} from 'primeng/checkbox';
import {TableModule} from 'primeng/table';
import {KeyFilter} from 'primeng/keyfilter';
import {distinctUntilChanged, filter, finalize} from 'rxjs';
import {MessageService} from 'primeng/api';

@Component({
  selector: 'app-pessoa-form',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, StepperModule, ButtonModule, InputTextModule, Select, DatePickerModule, InputMaskDirective, Checkbox, TableModule, InputMask, KeyFilter],
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
  loadingCep: boolean = false;

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

  constructor(private fb: FormBuilder, private pessoaService: PessoaService, private messageService: MessageService, private cdr: ChangeDetectorRef) {}

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
      uf: ['', [Validators.required, Validators.maxLength(2)]],
      cep: ['', Validators.required],
      ibgeCodigo: [''],
      pais: [''],
      principal: [false, Validators.required],
    });

    // Escutando mudanças no campo CEP
    this.formEndereco.get('cep')?.valueChanges
      .pipe(
        filter(valor => valor != null), // Apenas valores não nulos
        distinctUntilChanged() // Só emite se o valor for diferente do último
      )
      .subscribe(valor => {
        const cepLimpo = valor ? valor.replace(/\D/g, '') : '';
        // Somente busca se tiver exatamente 8 dígitos e não estivermos já carregando (opcional)
        if (cepLimpo.length === 8 && !this.loadingCep) {
          this.buscarCep(cepLimpo);
        }
      });

    this.formContato = this.fb.group({
      id: [null],
      nome: ['', Validators.required],
      tipo: ['COMERCIAL', Validators.required],
      cargo: [''],
      email: ['', Validators.email],
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

    Promise.resolve().then(() => {
        this.activeStep = 1;
        this.cdr.detectChanges();
    });
  }

  salvarPessoa() {
    if (this.formPessoa.invalid) return;

    const payload = this.formPessoa.value;

    if (this.pessoaId) {
      this.pessoaService.atualizar(this.pessoaId, payload).subscribe(res => {
        this.pessoaSalva = res;
        setTimeout(() => this.activeStep = 2);
        this.messageService.add({severity: 'success', summary: 'Sucesso', detail: 'Pessoa alterada com sucesso'});
        this.carregarEnderecos();
      });
    } else {
      this.pessoaService.criar(payload).subscribe(res => {
        this.pessoaId = res.id!;
        this.pessoaSalva = res;
        this.messageService.add({severity: 'success', summary: 'Sucesso', detail: 'Pessoa salva com sucesso'});
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
        this.cdr.detectChanges();
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
      this.messageService.add({severity: 'success', summary: 'Sucesso', detail: 'Endereço salvo com sucesso'});
      this.carregarEnderecos();
    });
  }

  // --- CONTATO ---
  carregarContatos() {
    const url = this.pessoaSalva?._links?.contatos?.href;
    if (url) {
      this.pessoaService.listarContatos(url).subscribe(res => {
        this.contatos = res._embedded?.contatos || [];
        this.cdr.detectChanges();
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

    // Usando getRawValue() para incluir os campos que foram desabilitados pelo CEP
    const payload = this.formEndereco.getRawValue();

    this.pessoaService.salvarContato(url, this.formContato.value).subscribe(() => {
      this.formContato.reset({ tipo: 'COMERCIAL', ativo: true });
      this.formEndereco.enable({ emitEvent: false });
      this.messageService.add({severity: 'success', summary: 'Sucesso', detail: 'Contato salvo com sucesso'});
      this.carregarContatos();
    });
  }

  // Helpers para o template
  isFieldInvalid(field: string): boolean {
    const control = this.formPessoa.get(field);
    return !!(control && control.invalid && (control.dirty || control.touched));
  }

  buscarCep(cep: string) {
    if (this.loadingCep) return; // Evita múltiplas chamadas simultâneas
    this.loadingCep = true;
    this.formEndereco.disable({ emitEvent: false }); // Desabilita sem disparar valueChanges

    this.pessoaService.buscarCep(cep)
      .pipe(
        finalize(() => {
          this.loadingCep = false;
          // Reabilita todos (menos os que vamos bloquear a seguir)
          this.formEndereco.enable({ emitEvent: false });
        })
      )
      .subscribe({
        next: (dados) => {
          if (dados && !dados.erro) {
            this.formEndereco.patchValue({
              logradouro: dados.logradouro,
              complemento: dados.complemento,
              bairro: dados.bairro,
              cidade: dados.localidade,
              uf: dados.uf,
              ibgeCodigo: dados.ibge,
              pais: 'Brasil'
            }, { emitEvent: false }); // Não dispara valueChanges ao preencher

            // Desabilita os campos preenchidos automaticamente (se houver valor retornado)
            if(dados.logradouro) this.formEndereco.get('logradouro')?.disable({ emitEvent: false });
            if(dados.bairro) this.formEndereco.get('bairro')?.disable({ emitEvent: false });
            if(dados.localidade) this.formEndereco.get('cidade')?.disable({ emitEvent: false });
            if(dados.uf) this.formEndereco.get('uf')?.disable({ emitEvent: false });
            if(dados.ibge) this.formEndereco.get('ibgeCodigo')?.disable({ emitEvent: false });
            this.formEndereco.get('pais')?.disable({ emitEvent: false });

            this.cdr.detectChanges();
          }
        },
        error: (err) => this.messageService.add({ severity: 'error', summary: 'Erro', detail: 'Erro ao buscar CEP: '+err })
      });
  }
}
