import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { Toast } from 'primeng/toast';
import { Tooltip } from 'primeng/tooltip';
import { MessageService } from 'primeng/api';
import { GrupoCliente } from '../grupo-clientes/grupo-cliente.model';
import { GrupoClienteService } from '../grupo-clientes/grupo-cliente.service';
import { ColumnConfig } from '../../../components/table/data-table';
import { GrupoClienteTabelaPrecoFormComponent } from './grupo-cliente-tabela-preco-form/grupo-cliente-tabela-preco-form';
import {PrimaryButtonComponent} from '../../../components/primary-button/primary-button';
import {HtmlDecodePipe} from '../../../util/pipe/html-decode.pipe';

@Component({
  selector: 'app-grupo-cliente-tabela-preco',
  standalone: true,
  imports: [
    CommonModule,
    RouterModule,
    TableModule,
    ButtonModule,
    Toast,
    Tooltip,
    GrupoClienteTabelaPrecoFormComponent,
    PrimaryButtonComponent,
    HtmlDecodePipe
  ],
  templateUrl: './grupo-cliente-tabela-preco.html',
  styleUrl: './grupo-cliente-tabela-preco.scss',
  providers: [MessageService]
})
export class GrupoClienteTabelaPrecoComponent implements OnInit {
  private messageService = inject(MessageService);
  private grupoClienteService = inject(GrupoClienteService);

  grupos = signal<GrupoCliente[]>([]);
  loading = signal<boolean>(true);
  totalRecords = signal<number>(0);
  page: number = 0;
  size: number = 10;

  displayForm = false;
  selectedGrupoId: string = '';
  selectedGrupoNome: string = '';

  cols: ColumnConfig[] = [
    { field: 'id', header: 'ID', type: 'text' },
    { field: 'nome', header: 'Nome do Grupo', type: 'text' },
    { field: 'ativo', header: 'Ativo', type: 'status' },
    { field: 'actions', header: 'Ações', type: 'actions' }
  ];

  ngOnInit(): void {
    this.loadGrupos();
  }

  loadGrupos(event?: any) {
    setTimeout(() => {
      this.loading.set(true);
    });

    if (event) {
      this.page = event.first / event.rows;
      this.size = event.rows;
    }

    this.grupoClienteService.listar(this.page, this.size).subscribe({
      next: (response: any) => {
        // Ajuste de acordo com o nome retornado pelo seu HATEOAS do grupo de cliente
        if (response.content) {
          this.grupos.set(response.content || []);
        } else {
          this.grupos.set([]);
        }
        this.totalRecords.set(response.page?.totalElements || 0);
        this.loading.set(false);
      },
      error: (err) => {
        console.error('Erro ao buscar grupos de cliente', err);
        this.messageService.add({ severity: 'error', summary: 'Erro', detail: 'Erro ao buscar grupos de cliente.' });
        this.loading.set(false);
      }
    });
  }

  abrirAssociacao(grupo: GrupoCliente): void {
    if (grupo.id) {
      this.selectedGrupoId = grupo.id;
      this.selectedGrupoNome = grupo.nome;
      this.displayForm = true;
    }
  }

  onFormSaved(): void {
    // Ação após salvar, se necessário (ex: exibir toast)
  }

  exportData(): void {
    this.messageService.add({ severity: 'info', summary: 'Info', detail: 'Exportação em desenvolvimento.' });
  }
}
