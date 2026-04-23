import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule, Router } from '@angular/router';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { Dialog } from 'primeng/dialog';
import { Ripple } from 'primeng/ripple';
import { Toast } from 'primeng/toast';
import { Tooltip } from 'primeng/tooltip';
import { MessageService } from 'primeng/api';

import { TabelaPreco } from './tabela-preco.model';
import { TabelaPrecoService } from './tabela-preco.service';
import { HtmlDecodePipe } from '../../../util/pipe/html-decode.pipe';
import { PrimaryButtonComponent } from '../../../components/primary-button/primary-button';
import { TabelaPrecosForm } from './tabela-precos-form/tabela-precos-form';

interface ColumnConfig {
  field: string;
  header: string;
  type: string;
}

@Component({
  selector: 'app-tabela-precos',
  imports: [CommonModule, RouterModule, TableModule, ButtonModule, InputTextModule, Dialog, HtmlDecodePipe, PrimaryButtonComponent, Ripple, Toast, Tooltip, TabelaPrecosForm],
  templateUrl: './tabela-precos.html',
  styleUrl: './tabela-precos.scss',
})
export class TabelaPrecos implements OnInit {
  private messageService = inject(MessageService);
  private tabelaPrecoService = inject(TabelaPrecoService);

  tabelasPreco = signal<TabelaPreco[]>([]);
  loading = signal<boolean>(true);
  totalRecords = signal<number>(0);
  page: number = 0;
  size: number = 10;

  displayForm = false;
  selectedTabelaPreco: TabelaPreco | null = null;

  cols: ColumnConfig[] = [
    { field: 'id', header: 'ID', type: 'text' },
    { field: 'nome', header: 'Nome', type: 'text' },
    { field: 'moeda', header: 'Moeda', type: 'text' },
    { field: 'padrao', header: 'Padrão', type: 'boolean' },
    { field: 'inicioVigencia', header: 'Início', type: 'dateonly' },
    { field: 'fimVigencia', header: 'Fim', type: 'dateonly' },
    { field: 'ativa', header: 'Ativa', type: 'status' },
    { field: 'actions', header: 'Ações', type: 'actions' }
  ];

  ngOnInit(): void {
    this.loadTabelasPreco();
  }

  loadTabelasPreco(event?: any) {
    setTimeout(() => {
      this.loading.set(true);
    });

    if (event) {
      this.page = event.first / event.rows;
      this.size = event.rows;
    }

    this.tabelaPrecoService.getAll(this.page, this.size).subscribe({
      next: (response) => {
        if (response._embedded && response._embedded.tabelaPrecoResponseDTOList) {
          this.tabelasPreco.set(response._embedded.tabelaPrecoResponseDTOList || []);
        } else if (response._embedded && response._embedded.tabelasPreco) {
          this.tabelasPreco.set(response._embedded.tabelasPreco || []);
        } else {
          this.tabelasPreco.set([]);
        }
        this.totalRecords.set(response.page?.totalElements || 0);
        this.loading.set(false);
      },
      error: (err) => {
        console.error('Erro ao buscar tabelas de preço', err);
        this.messageService.add({ severity: 'error', summary: 'Erro', detail: 'Erro ao buscar tabelas de preço.' });
        this.loading.set(false);
      }
    });
  }

  exportData(): void {
    this.messageService.add({ severity: 'info', summary: 'Info', detail: 'Exportação em desenvolvimento.' });
  }

  editTabelaPreco(tabelaPreco: TabelaPreco) {
    this.displayForm = true;
    this.selectedTabelaPreco = tabelaPreco;
  }

  inativarTabelaPreco(rowData: any): void {
    this.tabelaPrecoService.updateStatus(rowData.id).subscribe({
      next: () => {
        this.messageService.add({ severity: 'success', summary: 'Sucesso', detail: 'Status atualizado com sucesso.' });
        this.loadTabelasPreco({ first: this.page * this.size, rows: this.size });
      },
      error: () => {
        this.messageService.add({ severity: 'error', summary: 'Erro', detail: 'Erro ao inativar/ativar tabela de preço.' });
      }
    });
  }

  onFormSaved() {
    this.displayForm = false;
    this.loadTabelasPreco({ first: 0, rows: this.size });
  }

  onFormCanceled() {
    this.displayForm = false;
  }

  openNew() {
    this.selectedTabelaPreco = null;
    this.displayForm = true;
  }
}
