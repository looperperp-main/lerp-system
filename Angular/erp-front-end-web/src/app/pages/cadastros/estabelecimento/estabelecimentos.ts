import { Component, inject, OnInit, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { ButtonDirective } from 'primeng/button';
import { DatePipe, NgForOf, NgIf } from '@angular/common';
import { Dialog } from 'primeng/dialog';
import { MessageService, PrimeTemplate } from 'primeng/api';
import { Ripple } from 'primeng/ripple';
import { TableModule } from 'primeng/table';
import { Toast } from 'primeng/toast';
import { Tooltip } from 'primeng/tooltip';
import { HttpErrorResponse } from '@angular/common/http';
import { Breadcrumb } from '../../../components/breadcrumb/breadcrumb';
import { PrimaryButtonComponent } from '../../../components/primary-button/primary-button';
import { CnpjPipe } from '../../../util/pipe/cnpj.pipe';
import { EstabelecimentoService } from './estabelecimento.service';
import { Estabelecimento } from './estabelecimento.model';
import { EstabelecimentoForm } from './estabelecimento-form/estabelecimento-form';
import { PessoaService } from '../pessoas/pessoa.service';
import { Pessoa } from '../pessoas/pessoa.model';

@Component({
  selector: 'app-estabelecimentos',
  imports: [
    ButtonDirective,
    DatePipe,
    Dialog,
    NgForOf,
    NgIf,
    PrimaryButtonComponent,
    PrimeTemplate,
    Ripple,
    TableModule,
    Toast,
    Tooltip,
    EstabelecimentoForm,
    Breadcrumb,
    CnpjPipe,
  ],
  templateUrl: './estabelecimentos.html',
  styleUrl: './estabelecimentos.scss',
})
export class Estabelecimentos implements OnInit {
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private estabelecimentoService = inject(EstabelecimentoService);
  private pessoaService = inject(PessoaService);
  private messageService = inject(MessageService);

  pessoaId = this.route.snapshot.paramMap.get('pessoaId')!;
  pessoa = signal<Pessoa | null>(null);
  loading = signal<boolean>(true);
  estabelecimentos = signal<Estabelecimento[]>([]);

  displayForm = false;
  selectedEstabelecimento: Estabelecimento | null = null;

  ngOnInit(): void {
    this.pessoaService.obterPorId(this.pessoaId).subscribe({
      next: (pessoa) => this.pessoa.set(pessoa),
      error: () => this.pessoa.set(null),
    });
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.estabelecimentoService.listar(this.pessoaId).subscribe({
      next: (data) => {
        this.estabelecimentos.set(data);
        this.loading.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.handleError(err, 'Erro ao carregar os Estabelecimentos.');
        this.loading.set(false);
      },
    });
  }

  voltar(): void {
    this.router.navigate(['/web/cadastros/pessoas']);
  }

  openNew(): void {
    this.selectedEstabelecimento = null;
    this.displayForm = true;
  }

  editEstabelecimento(estabelecimento: Estabelecimento): void {
    this.selectedEstabelecimento = { ...estabelecimento };
    this.displayForm = true;
  }

  onFormSaved(): void {
    this.displayForm = false;
    this.load();
  }

  onFormCanceled(): void {
    this.displayForm = false;
  }

  inativarAtivarEstabelecimento(rowData: Estabelecimento): void {
    this.estabelecimentoService.alterarStatus(this.pessoaId, rowData).subscribe({
      next: () => {
        this.messageService.add({
          severity: 'success',
          summary: 'Sucesso',
          detail: 'Status atualizado.',
        });
        this.load();
      },
      error: (err: HttpErrorResponse) => {
        this.handleError(err, 'Erro ao alterar status.');
      },
    });
  }

  private handleError(err: HttpErrorResponse, defaultSummary: string) {
    if (err.error && err.error.message && err.error.error && err.error.status) {
      const detailMsg = `[${err.error.status}] ${err.error.error} - ${err.error.message}`;
      this.messageService.add({
        severity: 'error',
        summary: defaultSummary,
        detail: detailMsg,
        life: 5000,
      });
    } else {
      this.messageService.add({
        severity: 'error',
        summary: defaultSummary,
        detail: 'Erro inesperado de comunicação com o servidor.',
        life: 5000,
      });
    }
  }
}
