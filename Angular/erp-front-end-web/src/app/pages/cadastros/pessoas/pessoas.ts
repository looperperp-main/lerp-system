import {Component, inject, signal} from '@angular/core';
import {CommonModule} from '@angular/common';
import {TableModule} from 'primeng/table';
import {MessageService} from 'primeng/api';
import {ButtonDirective, ButtonModule} from 'primeng/button';
import {DialogModule} from 'primeng/dialog';
import {ToastModule} from 'primeng/toast';
import {PessoaService} from './pessoa.service';
import {Pessoa} from './pessoa.model';
import {PessoaForm} from './pessoa-form/pessoa-form';
import {PrimaryButtonComponent} from '../../../components/primary-button/primary-button';
import {ColumnConfig} from '../../../components/table/data-table';
import {HtmlDecodePipe} from '../../../util/pipe/html-decode.pipe';
import {Ripple} from 'primeng/ripple';
import {Tooltip} from 'primeng/tooltip';

@Component({
  selector: 'app-pessoas',
  imports: [CommonModule, TableModule, ButtonModule, DialogModule, ToastModule, PessoaForm, PrimaryButtonComponent, HtmlDecodePipe, Ripple, Tooltip, PrimaryButtonComponent, ButtonDirective, Ripple, Tooltip,/*, PessoaForm*/],
  providers: [MessageService],
  templateUrl: './pessoas.html',
  styleUrl: './pessoas.scss'
})
export class Pessoas {
  private pessoaService = inject(PessoaService);
  private messageService = inject(MessageService);

  loading = signal<boolean>(true);
  totalRecords = signal<number>(0);
  pessoas = signal<Pessoa[]>([]);
  displayForm = false;
  selectedPessoa: Pessoa | null = null;

  cols: ColumnConfig[] = [
    { field: 'id', header: 'ID', type: 'text' },
    { field: 'nomeRazao', header: 'Nome/Razão', type: 'text' },
    { field: 'apelidoFantasia', header: 'Nome Fantasia', type: 'text' },
    { field: 'documento', header: 'Documento', type: 'text' },
    { field: 'ativo', header: 'Ativo', type: 'status' },
    { field: 'createdAt', header: 'Data de Criação', type: 'date' },
    { field: 'updatedAt', header: 'Data de Atualização', type: 'date' },
    { field: 'actions', header: 'Ações', type: 'actions' }
  ];

  loadPessoas(event: any): void {
    setTimeout(() => {
      this.loading.set(true);
    });

    const page = (event.first ?? 0) / (event.rows ?? 10);
    this.pessoaService.listar(page, event.rows ?? 10).subscribe({
      next: (data) => {
        this.pessoas.set(data.content || []);
        this.totalRecords.set(data.totalElements || 0);
        this.loading.set(false);
      },
      error: () => this.loading.set(false)
    });
  }

  openNew(): void {
    this.selectedPessoa = null;
    this.displayForm = true;
  }

  editPessoa(pessoa: Pessoa): void {
    this.selectedPessoa = { ...pessoa };
    this.displayForm = true;
  }

  deletePessoa(pessoa: Pessoa): void {
    this.messageService.add({ severity: 'info', summary: 'Atenção', detail: 'Exclusão em desenvolvimento.' });
  }

  onFormSaved(): void {
    this.displayForm = false;
    this.loadPessoas({ first: 0, rows: 10 });
  }

  onFormCanceled(): void {
    this.displayForm = false;
  }

  exportData(): void {
    this.messageService.add({ severity: 'info', summary: 'Info', detail: 'Exportação em desenvolvimento.' });
  }

  protected inativarPessoa() {
    this.messageService.add({ severity: 'info', summary: 'Info', detail: 'Funcionalidade em desenvolvimento.' });
  }
}
