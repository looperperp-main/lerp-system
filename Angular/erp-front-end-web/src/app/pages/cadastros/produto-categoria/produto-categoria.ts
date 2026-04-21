import {Component, inject, signal} from '@angular/core';
import {ColumnConfig, DataTableComponent} from '../../../components/table/data-table';
import {CommonModule} from '@angular/common';
import {RouterModule} from '@angular/router';
import {TableModule} from 'primeng/table';
import {ButtonModule} from 'primeng/button';
import {Dialog} from 'primeng/dialog';
import {HtmlDecodePipe} from '../../../util/pipe/html-decode.pipe';
import {PrimaryButtonComponent} from '../../../components/primary-button/primary-button';
import {Ripple} from 'primeng/ripple';
import {Toast} from 'primeng/toast';
import {Tooltip} from 'primeng/tooltip';
import { ProdutoCategoriaForm } from "./produto-categoria-form/produto-categoria-form";
import {MessageService} from 'primeng/api';
import {ProdutoCategoriaService} from './produto-categoria.service';
import {ProdutoCat} from './produto-categoria.model';

@Component({
  selector: 'app-produto-categoria',
  imports: [
    CommonModule, RouterModule, TableModule, ButtonModule, Dialog,
    HtmlDecodePipe, PrimaryButtonComponent, Ripple, Toast, Tooltip,
    ProdutoCategoriaForm, DataTableComponent
  ],
  templateUrl: './produto-categoria.html',
  styleUrl: './produto-categoria.scss',
})
export class ProdutoCategoria {
  private messageService = inject(MessageService);
  private categoriaService = inject(ProdutoCategoriaService);

  categorias = signal<ProdutoCategoria[]>([]);
  loading = signal<boolean>(true);
  totalRecords = signal<number>(0);
  page: number = 0;
  size: number = 10;

  displayForm = false;
  selectedCategoria: ProdutoCat | null = null;

  cols: ColumnConfig[] = [
    { field: 'id', header: 'ID', type: 'text' },
    { field: 'nome', header: 'Nome', type: 'text' },
    { field: 'descricao', header: 'Descrição', type: 'text' },
    { field: 'ativa', header: 'Ativa', type: 'status' }, // Reutilizando a prop 'ativo' -> 'ativa'
    { field: 'createdAt', header: 'Criado Em', type: 'date' },
    { field: 'createdBy', header: 'Criado Por', type: 'text' },
    { field: 'actions', header: 'Ações', type: 'actions' }
  ];

  ngOnInit(): void {
    this.loadCategorias();
  }

  loadCategorias(event?: any) {
    setTimeout(() => {
      this.loading.set(true);
    });

    if (event) {
      this.page = event.first / event.rows;
      this.size = event.rows;
    }

    this.categoriaService.getAll(this.page, this.size).subscribe({
      next: (response) => {
        // Ajuste a chave collectionRelation retornada pelo Hateoas se necessário (ex: "produto_categoria")
        if (response._embedded?.produto_categoria) {
          this.categorias.set(response._embedded.produto_categoria || []);
        } else {
          this.categorias.set([]);
        }
        this.totalRecords.set(response.page?.totalElements || 0);
        this.loading.set(false);
      },
      error: (err) => {
        console.error('Erro ao buscar categorias', err);
        this.messageService.add({ severity: 'error', summary: 'Erro', detail: 'Erro ao buscar categorias.' });
        this.loading.set(false);
      }
    });
  }

  openNew() {
    this.selectedCategoria = null;
    this.displayForm = true;
  }

  editCategoria(categoria: ProdutoCat) {
    this.selectedCategoria = categoria;
    this.displayForm = true;
  }

  inativarCategoria(categoria: ProdutoCat): void {
    if (!categoria.id) return;

    this.categoriaService.updateStatus(categoria.id).subscribe({
      next: () => {
        const acao = categoria.ativa ? 'inativada' : 'ativada';
        this.messageService.add({ severity: 'success', summary: 'Sucesso', detail: `Categoria ${acao} com sucesso.` });
        this.loadCategorias({ first: this.page * this.size, rows: this.size });
      },
      error: () => {
        this.messageService.add({ severity: 'error', summary: 'Erro', detail: 'Erro ao alterar o status da categoria.' });
      }
    });
  }

  exportData(): void {
    this.messageService.add({ severity: 'info', summary: 'Info', detail: 'Exportação em desenvolvimento.' });
  }

  onFormSaved() {
    this.displayForm = false;
    this.loadCategorias({ first: 0, rows: this.size });
  }

  onFormCanceled() {
    this.displayForm = false;
  }
}
