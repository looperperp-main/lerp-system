import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterModule } from '@angular/router';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { Dialog } from 'primeng/dialog';
import { HtmlDecodePipe } from '../../../util/pipe/html-decode.pipe';
import { PrimaryButtonComponent } from '../../../components/primary-button/primary-button';
import { Ripple } from 'primeng/ripple';
import { Toast } from 'primeng/toast';
import { Tooltip } from 'primeng/tooltip';
import { MessageService } from 'primeng/api';
import { ColumnConfig } from '../../../components/table/data-table';
import { Produto } from './produto.model';
import { ProdutoService } from './produto.service';
import { ProdutosForm } from './produtos-form/produtos-form';
import { Breadcrumb } from '../../../components/breadcrumb/breadcrumb';

@Component({
  selector: 'app-produtos',
  standalone: true,
  imports: [
    CommonModule,
    RouterModule,
    TableModule,
    ButtonModule,
    InputTextModule,
    Dialog,
    HtmlDecodePipe,
    PrimaryButtonComponent,
    Ripple,
    Toast,
    Tooltip,
    ProdutosForm,
    Breadcrumb,
  ],
  templateUrl: './produtos.html',
  styleUrl: './produtos.scss',
})
export class Produtos implements OnInit {
  private messageService = inject(MessageService);
  produtos = signal<Produto[]>([]);
  loading = signal<boolean>(true);
  totalRecords = signal<number>(0);
  page: number = 0;
  size: number = 10;
  displayForm = false;
  selectedProduto: Produto | null = null;

  cols: ColumnConfig[] = [
    { field: 'sku', header: 'SKU', type: 'text' },
    { field: 'nome', header: 'Nome do Produto', type: 'text' },
    { field: 'unidade', header: 'UN', type: 'text' },
    { field: 'ativo', header: 'Status', type: 'status' },
    { field: 'createdAt', header: 'Criado Em', type: 'date' },
    { field: 'updatedAt', header: 'Atualizado Em', type: 'date' },
    { field: 'lastUpdatedBy', header: 'Atualizado Por', type: 'text' },
    { field: 'actions', header: 'Ações', type: 'actions' },
  ];

  constructor(
    private readonly produtoService: ProdutoService,
    private router: Router,
  ) {}

  ngOnInit(): void {
    this.loadProdutos();
  }

  loadProdutos(event?: any) {
    setTimeout(() => {
      this.loading.set(true);
    });

    if (event) {
      this.page = event.first / event.rows;
      this.size = event.rows;
    }
    this.produtoService.getAll(this.page, this.size).subscribe({
      next: (response) => {
        if (response._embedded?.produtoResponseDTOList) {
          this.produtos.set(response._embedded.produtoResponseDTOList || []);
        } else if (response._embedded?.produtoDTOList) {
          this.produtos.set(response._embedded.produtoDTOList || []);
        } else if (response._embedded?.produtos) {
          this.produtos.set(response._embedded.produtos || []);
        }
        this.totalRecords.set(response.page?.totalElements || 0);
        this.loading.set(false);
      },
      error: (err) => {
        console.error('Erro ao buscar produtos', err);
        this.messageService.add({
          severity: 'error',
          summary: 'Erro',
          detail: 'Erro ao buscar produtos. ' + err,
        });
        this.loading.set(false);
      },
    });
  }

  exportData(): void {
    this.messageService.add({
      severity: 'info',
      summary: 'Info',
      detail: 'Exportação em desenvolvimento.',
    });
  }

  editProduto(produto: Produto) {
    if (!produto.id) return;

    // Abre o diálogo já no clique (síncrono, como openNew()) com os dados da linha —
    // abrir só dentro do subscribe do HTTP causava NG0100 no [(visible)] do p-dialog
    // (o setter de visible do PrimeNG grava signals internos durante o ciclo de CD,
    // e isso só quebra quando a mudança vem de um callback assíncrono, não de um clique).
    this.selectedProduto = produto;
    this.displayForm = true;

    // Complementa com os relacionamentos (fornecedores, preços, estoque) que a linha da tabela não traz
    this.produtoService.getById(produto.id).subscribe({
      next: (dadosCompletos) => {
        this.selectedProduto = dadosCompletos;
      },
      error: () => {
        this.displayForm = false;
        this.messageService.add({
          severity: 'error',
          summary: 'Erro',
          detail: 'Não foi possível carregar os detalhes do produto.',
        });
      },
    });
  }

  inativarAtivarProduto(rowData: Produto): void {
    // Para mudar o status apenas alteramos e atualizamos. Depende de como é feito no backend
    const updated = { ...rowData, ativo: !rowData.ativo };
    this.produtoService.updateStatus(rowData.id!).subscribe({
      next: () => {
        this.messageService.add({
          severity: 'success',
          summary: 'Sucesso',
          detail: 'Status atualizado.',
        });
        this.loadProdutos({ first: this.page * this.size, rows: this.size });
      },
      error: (err) => {
        this.messageService.add({
          severity: 'error',
          summary: 'Erro',
          detail: 'Erro ao alterar status.',
        });
      },
    });
  }

  onFormSaved() {
    this.displayForm = false;
    this.loadProdutos({ first: 0, rows: 10 });
  }

  onFormCanceled() {
    this.displayForm = false;
  }

  protected openNew() {
    this.selectedProduto = null;
    this.displayForm = true;
  }
}
