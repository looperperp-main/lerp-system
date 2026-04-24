import { Component, EventEmitter, inject, Input, OnInit, Output, signal } from '@angular/core';
import { Produto, ProdutoEstoqueConfigDTO, ProdutoFornecedorDTO, ProdutoPrecoDTO } from '../produto.model';
import { FormArray, FormBuilder, FormGroup, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { ProdutoService } from '../produto.service';
import { Checkbox } from 'primeng/checkbox';
import { Select } from 'primeng/select';
import { ButtonDirective } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { DatePicker } from 'primeng/datepicker';
import { InputNumberModule } from 'primeng/inputnumber';
import { MessageService } from 'primeng/api';
import { HttpErrorResponse } from '@angular/common/http';
import { CommonModule } from '@angular/common';
import { Ripple } from 'primeng/ripple';
import { TabsModule } from 'primeng/tabs';

@Component({
  selector: 'app-produtos-form',
  standalone: true,
  imports: [
    CommonModule,
    Checkbox,
    FormsModule,
    ReactiveFormsModule,
    Select,
    ButtonDirective,
    InputTextModule,
    DatePicker,
    InputNumberModule,
    Ripple,
    TabsModule
  ],
  templateUrl: './produtos-form.html',
  styleUrl: './produtos-form.scss',
})
export class ProdutosForm implements OnInit {
  @Input() ProdutoData: Produto | null = null;
  @Output() saved = new EventEmitter<void>();
  @Output() canceled = new EventEmitter<void>();

  private fb = inject(FormBuilder);
  private messageService = inject(MessageService);
  private produtoService = inject(ProdutoService);

  categoriasOptions = signal<any[]>([]);
  fornecedoresOptions = signal<any[]>([]);
  tabelasPrecoOptions = signal<any[]>([]);
  depositosOptions = signal<any[]>([]);

  form!: FormGroup;

  constructor() {
    this.form = this.fb.group({
      id: [this.ProdutoData?.id || null],
      ativo: [this.ProdutoData?.ativo ?? true],
      sku: [this.ProdutoData?.sku || '', [Validators.required, Validators.maxLength(80)]],
      nome: [this.ProdutoData?.nome || '', [Validators.required, Validators.maxLength(200)]],
      categoriaId: [this.ProdutoData?.categoriaId || null],
      unidade: [this.ProdutoData?.unidade || '', [Validators.required, Validators.maxLength(10)]],
      unidadeSecundaria: [this.ProdutoData?.unidadeSecundaria || ''],
      fatorConversao: [this.ProdutoData?.fatorConversao || null],
      codigoExterno: [this.ProdutoData?.codigoExterno || ''],
      descricao: [this.ProdutoData?.descricao || ''],
      ncm: [this.ProdutoData?.ncm || ''],
      ean: [this.ProdutoData?.ean || ''],
      cest: [this.ProdutoData?.cest || ''],
      origem: [this.ProdutoData?.origem || ''],

      // Coleções Aninhadas
      precos: this.fb.array([]),
      fornecedores: this.fb.array([]),
      estoqueConfigs: this.fb.array([])
    });
  }

  ngOnInit(): void {
    this.loadCategorias();
    this.loadFornecedores();
    this.loadTabelasPreco();
    this.loadDepositos();

    // Limpar os arrays sempre que iniciar o formulário (útil ao fechar/abrir)
    this.precosFormArray.clear();
    this.fornecedoresFormArray.clear();
    this.estoqueConfigsFormArray.clear();

    if (this.ProdutoData) {
      this.form.patchValue({
        id: this.ProdutoData.id,
        ativo: this.ProdutoData.ativo !== false,
        sku: this.ProdutoData.sku,
        nome: this.ProdutoData.nome,
        categoriaId: this.ProdutoData.categoriaId,
        unidade: this.ProdutoData.unidade,
        unidadeSecundaria: this.ProdutoData.unidadeSecundaria,
        fatorConversao: this.ProdutoData.fatorConversao,
        codigoExterno: this.ProdutoData.codigoExterno,
        descricao: this.ProdutoData.descricao,
        ncm: this.ProdutoData.ncm,
        ean: this.ProdutoData.ean,
        cest: this.ProdutoData.cest,
        origem: this.ProdutoData.origem
      });

      // Preencher FormArrays se existirem dados (atenção: dependendo do mapper do Spring e como chega)
      if (this.ProdutoData.precos && Array.isArray(this.ProdutoData.precos)) {
        this.ProdutoData.precos.forEach(p => this.addPreco(p));
      }
      if (this.ProdutoData.fornecedores && Array.isArray(this.ProdutoData.fornecedores)) {
        this.ProdutoData.fornecedores.forEach(f => this.addFornecedor(f));
      }
      if (this.ProdutoData.estoqueConfigs && Array.isArray(this.ProdutoData.estoqueConfigs)) {
        this.ProdutoData.estoqueConfigs.forEach(e => this.addEstoqueConfig(e));
      }
    }
  }

  // --- Loads para os Selects ---
  loadCategorias() {
    this.produtoService.getCategoriasDropdown().subscribe({
      next: (res) => {
        const content = res._embedded ? (res._embedded.produtoCategoriaResponseDTOList || res._embedded.produto_categoria || []) : (res.content || []);
        this.categoriasOptions.set(content.map((c: any) => ({ label: c.nome, value: c.id })));
      }
    });
  }

  loadFornecedores() {
    this.produtoService.getFornecedoresDropdown().subscribe({
      next: (res) => {
        const content = res._embedded ? (res._embedded.fornecedorResponseDTOList || res._embedded.fornecedor || []) : (res.content || []);
        this.fornecedoresOptions.set(content.map((f: any) => ({ label: f.pessoaNomeRazao || f.id, value: f.id })));
      }
    });
  }

  loadTabelasPreco() {
    this.produtoService.getTabelasPrecoDropdown().subscribe({
      next: (res) => {
        const content = res._embedded ? (res._embedded.tabelaPrecoResponseDTOList || res._embedded.tabela_preco || []) : (res.content || []);
        this.tabelasPrecoOptions.set(content.map((t: any) => ({ label: t.nome, value: t.id })));
      }
    });
  }

  loadDepositos() {
    this.produtoService.getDepositosDropdown().subscribe({
      next: (res) => {
        const content = res.content || []; // Dependendo do HATEOAS: res._embedded?.depositos || res.content
        this.depositosOptions.set(content.map((d: any) => ({ label: d.nome, value: d.id })));
      }
    });
  }

  // --- Gestão dos FormArrays ---
  get precosFormArray() { return this.form.get('precos') as FormArray; }
  get fornecedoresFormArray() { return this.form.get('fornecedores') as FormArray; }
  get estoqueConfigsFormArray() { return this.form.get('estoqueConfigs') as FormArray; }

  addPreco(precoData?: ProdutoPrecoDTO) {
    this.precosFormArray.push(this.fb.group({
      id: [precoData?.id || null],
      tabelaPrecoId: [precoData?.tabelaPrecoId || null, Validators.required],
      preco: [precoData?.preco || null, Validators.required],
      inicioVigencia: [precoData?.inicioVigencia ? new Date(precoData.inicioVigencia) : null, Validators.required],
      fimVigencia: [precoData?.fimVigencia ? new Date(precoData.fimVigencia) : null]
    }));
  }

  removePreco(index: number) {
    this.precosFormArray.removeAt(index);
  }

  addFornecedor(fornecedorData?: ProdutoFornecedorDTO) {
    this.fornecedoresFormArray.push(this.fb.group({
      id: [fornecedorData?.id || null],
      fornecedorId: [fornecedorData?.fornecedorId || null, Validators.required],
      codigoProdutoFornecedor: [fornecedorData?.codigoProdutoFornecedor || ''],
      precoCusto: [fornecedorData?.precoCusto || null],
      leadTimeDias: [fornecedorData?.leadTimeDias || null],
      preferencial: [fornecedorData?.preferencial ?? false],
      ativo: [fornecedorData?.ativo ?? true]
    }));
  }

  removeFornecedor(index: number) {
    this.fornecedoresFormArray.removeAt(index);
  }

  addEstoqueConfig(estoqueData?: ProdutoEstoqueConfigDTO) {
    this.estoqueConfigsFormArray.push(this.fb.group({
      id: [estoqueData?.id || null],
      fornecedorPreferencialId: [estoqueData?.fornecedorPreferencialId || null],
      depositoId: [estoqueData?.depositoId || null, Validators.required],
      estoqueMinimo: [estoqueData?.estoqueMinimo || null],
      estoqueMaximo: [estoqueData?.estoqueMaximo || null],
      pontoReposicao: [estoqueData?.pontoReposicao || null],
      leadTimeDias: [estoqueData?.leadTimeDias || null]
    }));
  }

  removeEstoqueConfig(index: number) {
    this.estoqueConfigsFormArray.removeAt(index);
  }

  // --- Salvar ---
  save() {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const produtoData = this.form.value;

    // Ajustar datas para formato YYYY-MM-DD
    if (produtoData.precos) {
      produtoData.precos = produtoData.precos.map((p: any) => ({
        ...p,
        inicioVigencia: p.inicioVigencia ? new Date(p.inicioVigencia).toISOString().split('T')[0] : null,
        fimVigencia: p.fimVigencia ? new Date(p.fimVigencia).toISOString().split('T')[0] : null
      }));
    }

    if (this.ProdutoData?.id) {
      this.produtoService.update(this.ProdutoData.id, produtoData).subscribe({
        next: () => {
          this.saved.emit();
          this.messageService.add({ severity: 'success', summary: 'Sucesso', detail: 'Produto atualizado.' });
        },
        error: (err: HttpErrorResponse) => this.messageService.add({ severity: 'error', summary: 'Erro', detail: 'Erro ao atualizar produto!' })
      });
    } else {
      this.produtoService.create(produtoData).subscribe({
        next: () => {
          this.saved.emit();
          this.messageService.add({ severity: 'success', summary: 'Sucesso', detail: 'Produto criado.' });
        },
        error: (err: HttpErrorResponse) => this.messageService.add({ severity: 'error', summary: 'Erro', detail: 'Erro ao criar produto! ' })
      });
    }
  }

  onCancel(): void {
    this.canceled.emit();
  }
}
