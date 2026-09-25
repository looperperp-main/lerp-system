import { Component, inject, OnInit, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { MessageService, PrimeTemplate } from 'primeng/api';
import { Button } from 'primeng/button';
import { InputText } from 'primeng/inputtext';
import { Select } from 'primeng/select';
import { FileUpload, FileSelectEvent } from 'primeng/fileupload';
import { TableModule } from 'primeng/table';
import { Dialog } from 'primeng/dialog';
import { DatePipe, NgIf } from '@angular/common';
import { Breadcrumb } from '../../../../components/breadcrumb/breadcrumb';
import { PrimaryButtonComponent } from '../../../../components/primary-button/primary-button';
import { EmissaoFiscalService } from './emissao-fiscal.service';
import {
  CredenciamentoSefaz,
  ModeloDocumentoFiscal,
  StatusCredenciamento,
} from './emissao-fiscal.model';

@Component({
  selector: 'app-emissao-fiscal-config',
  imports: [
    Breadcrumb,
    Button,
    DatePipe,
    Dialog,
    FileUpload,
    InputText,
    NgIf,
    PrimaryButtonComponent,
    PrimeTemplate,
    ReactiveFormsModule,
    Select,
    TableModule,
  ],
  templateUrl: './emissao-fiscal.html',
  styleUrl: './emissao-fiscal.scss',
})
export class EmissaoFiscalConfig implements OnInit {
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private service = inject(EmissaoFiscalService);
  private messageService = inject(MessageService);
  private fb = inject(FormBuilder);

  pessoaId = this.route.snapshot.paramMap.get('pessoaId')!;
  estabelecimentoId = this.route.snapshot.paramMap.get('estabelecimentoId')!;

  loadingCredenciamentos = signal<boolean>(true);
  credenciamentos = signal<CredenciamentoSefaz[]>([]);
  uploadingCertificado = false;
  arquivoCertificado: File | null = null;

  certificadoForm!: FormGroup;
  credenciamentoForm!: FormGroup;
  displayCredenciamentoForm = false;

  opcoesModelo: { label: string; value: ModeloDocumentoFiscal }[] = [
    { label: 'NF-e', value: 'NFE' },
    { label: 'NFC-e', value: 'NFCE' },
    { label: 'CT-e', value: 'CTE' },
  ];

  opcoesStatus: { label: string; value: StatusCredenciamento }[] = [
    { label: 'Pendente', value: 'PENDENTE' },
    { label: 'Credenciado', value: 'CREDENCIADO' },
    { label: 'Bloqueado', value: 'BLOQUEADO' },
  ];

  ngOnInit(): void {
    this.certificadoForm = this.fb.group({
      cnpjEstabelecimento: ['', [Validators.required]],
      senha: ['', [Validators.required]],
    });
    this.credenciamentoForm = this.fb.group({
      uf: ['', [Validators.required, Validators.maxLength(2)]],
      modelo: ['NFE', [Validators.required]],
      status: ['PENDENTE', [Validators.required]],
    });
    this.carregarCredenciamentos();
  }

  carregarCredenciamentos(): void {
    this.loadingCredenciamentos.set(true);
    this.service.listarCredenciamentos(this.estabelecimentoId).subscribe({
      next: (dados) => {
        this.credenciamentos.set(dados);
        this.loadingCredenciamentos.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.handleError(err, 'Erro ao carregar os credenciamentos.');
        this.loadingCredenciamentos.set(false);
      },
    });
  }

  onArquivoSelecionado(event: FileSelectEvent): void {
    const arquivos = event.currentFiles ?? event.files ?? [];
    this.arquivoCertificado = arquivos.length > 0 ? arquivos[0] : null;
  }

  enviarCertificado(): void {
    if (this.certificadoForm.invalid) {
      this.certificadoForm.markAllAsTouched();
      return;
    }
    if (!this.arquivoCertificado) {
      this.messageService.add({
        severity: 'warn',
        summary: 'Atenção',
        detail: 'Selecione o arquivo .pfx do certificado antes de enviar.',
      });
      return;
    }

    this.uploadingCertificado = true;
    const { cnpjEstabelecimento, senha } = this.certificadoForm.value;
    this.service
      .uploadCertificado(
        this.estabelecimentoId,
        cnpjEstabelecimento,
        senha,
        this.arquivoCertificado,
      )
      .subscribe({
        next: () => {
          this.uploadingCertificado = false;
          this.arquivoCertificado = null;
          this.certificadoForm.patchValue({ senha: '' });
          this.messageService.add({
            severity: 'success',
            summary: 'Sucesso',
            detail: 'Certificado digital enviado com sucesso.',
          });
        },
        error: (err: HttpErrorResponse) => {
          this.uploadingCertificado = false;
          this.handleError(err, 'Erro ao enviar o certificado digital.');
        },
      });
  }

  abrirNovoCredenciamento(): void {
    this.credenciamentoForm.reset({ uf: '', modelo: 'NFE', status: 'PENDENTE' });
    this.displayCredenciamentoForm = true;
  }

  salvarCredenciamento(): void {
    if (this.credenciamentoForm.invalid) {
      this.credenciamentoForm.markAllAsTouched();
      return;
    }
    const valor = this.credenciamentoForm.value as CredenciamentoSefaz;
    this.service
      .salvarCredenciamento(this.estabelecimentoId, { ...valor, uf: valor.uf.toUpperCase() })
      .subscribe({
        next: () => {
          this.displayCredenciamentoForm = false;
          this.messageService.add({
            severity: 'success',
            summary: 'Sucesso',
            detail: 'Credenciamento salvo.',
          });
          this.carregarCredenciamentos();
        },
        error: (err: HttpErrorResponse) => {
          this.handleError(err, 'Erro ao salvar o credenciamento.');
        },
      });
  }

  cancelarCredenciamento(): void {
    this.displayCredenciamentoForm = false;
  }

  voltar(): void {
    this.router.navigate(['/web/cadastros/pessoas', this.pessoaId, 'estabelecimentos']);
  }

  private handleError(err: HttpErrorResponse, defaultSummary: string): void {
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
