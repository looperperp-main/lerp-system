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
// Ajuste o path se necessário

import { Transportadora } from './transportadora.model';
import { TransportadoraService } from './transportadora.service';
import {HtmlDecodePipe} from '../../../util/pipe/html-decode.pipe';
import {PrimaryButtonComponent} from '../../../components/primary-button/primary-button';
import {TransportadoraForm} from './transportadoras-form/transportadoras-form';

interface ColumnConfig {
  field: string;
  header: string;
  type: string;
}

@Component({
  selector: 'app-transportadoras',
  imports: [CommonModule, RouterModule, TableModule, ButtonModule, InputTextModule, Dialog, HtmlDecodePipe, PrimaryButtonComponent, Ripple, Toast, Tooltip, TransportadoraForm],
  templateUrl: './transportadoras.html',
  styleUrl: './transportadoras.scss',
})
export class Transportadoras implements OnInit {
  private messageService = inject(MessageService);
  transportadoras = signal<Transportadora[]>([]);
  loading = signal<boolean>(true);
  totalRecords = signal<number>(0);
  page: number = 0;
  size: number = 10;
  displayForm = false;
  selectedTransportadora: Transportadora | null = null;

  cols: ColumnConfig[] = [
    { field: 'id', header: 'ID', type: 'text' },
    { field: 'pessoaId', header: 'ID Pessoa', type: 'text' },
    { field: 'rntrc', header: 'RNTRC', type: 'text' },
    { field: 'modal', header: 'Modal', type: 'text' },
    { field: 'ativo', header: 'Ativo', type: 'status' },
    { field: 'createdAt', header: 'Data de Criação', type: 'date' },
    { field: 'actions', header: 'Ações', type: 'actions' }
  ];

  constructor(private transportadoraService: TransportadoraService, private router: Router) {}

  ngOnInit(): void {
    this.loadTransportadoras();
  }

  loadTransportadoras(event?: any) {
    setTimeout(() => {
      this.loading.set(true);
    });

    if (event) {
      this.page = event.first / event.rows;
      this.size = event.rows;
    }
    this.transportadoraService.getAll(this.page, this.size).subscribe({
      next: (response) => {
        if (response._embedded && response._embedded.transportadoraResponseDTOList) {
          this.transportadoras.set(response._embedded.transportadoraResponseDTOList || []);
        } else if (response._embedded && response._embedded.transportadora) {
          this.transportadoras.set(response._embedded.transportadora || []);
        }
        this.totalRecords.set(response.page?.totalElements || 0);
        this.loading.set(false);
      },
      error: (err) => {
        console.error('Erro ao buscar transportadoras', err);
        this.messageService.add({ severity: 'error', summary: 'Erro', detail: 'Erro ao buscar transportadoras. ' + err });
        this.loading.set(false);
      }
    });
  }

  exportData(): void {
    this.messageService.add({ severity: 'info', summary: 'Info', detail: 'Exportação em desenvolvimento.' });
  }

  editTransportadora(transportadora: Transportadora) {
    this.displayForm = true;
    this.selectedTransportadora = transportadora;
  }

  inativarTransportadora(rowData: any): void {
    this.transportadoraService.updateStatus(rowData.id).subscribe({
      next: () => {
        this.messageService.add({ severity: 'success', summary: 'Sucesso', detail: 'Status atualizado.' });
        this.loadTransportadoras({ first: this.page * this.size, rows: this.size });
      },
      error: (err) => {
        this.messageService.add({ severity: 'error', summary: 'Erro', detail: 'Erro ao inativar.' });
      }
    });
  }

  onFormSaved() {
    this.displayForm = false;
    this.loadTransportadoras({ first: 0, rows: 10 });
  }

  onFormCanceled() {
    this.displayForm = false;
  }

  protected openNew() {
    this.selectedTransportadora = null;
    this.displayForm = true;
  }
}
