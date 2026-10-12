import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MessageService } from 'primeng/api';
import { Toast } from 'primeng/toast';
import { TableModule } from 'primeng/table';
import { ButtonDirective } from 'primeng/button';
import { Ripple } from 'primeng/ripple';
import { Tooltip } from 'primeng/tooltip';
import { Dialog } from 'primeng/dialog';
import { Select } from 'primeng/select';
import { ExportAdmin } from './exportacoes.model';
import { ExportacoesService } from './exportacoes.service';

interface Opcao {
  label: string;
  value: string;
}

const STATUS_ROTULO: Record<string, string> = {
  REQUESTED: 'Solicitada',
  PROCESSING: 'Gerando',
  READY: 'Pronta',
  DOWNLOADED: 'Baixada',
  EXPIRED: 'Expirada',
  FAILED: 'Falhou',
};

const EVENTO_ROTULO: Record<string, string> = {
  REQUESTED: 'Solicitada',
  GENERATED: 'Arquivos gerados',
  DOWNLOADED: 'Baixada pelo cliente',
  DOWNLOAD_DENIED: 'Download negado',
  EXPIRED: 'Expirada e apagada',
};

/** Suporte ao export: o admin da plataforma vê estado, falhas e auditoria de qualquer tenant — nunca o conteúdo. */
@Component({
  selector: 'app-exportacoes',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    Toast,
    TableModule,
    ButtonDirective,
    Ripple,
    Tooltip,
    Dialog,
    Select,
  ],
  providers: [MessageService],
  templateUrl: './exportacoes.html',
})
export class Exportacoes implements OnInit {
  itens = signal<ExportAdmin[]>([]);
  totalRecords = signal(0);
  loading = signal(true);
  page = 0;
  size = 20;

  filtroStatus = '';
  filtroTenant: number | null = null;
  statusOpcoes: Opcao[] = [
    { label: 'Todos', value: '' },
    ...Object.entries(STATUS_ROTULO).map(([value, label]) => ({ label, value })),
  ];

  detalheVisible = false;
  detalheLoading = false;
  detalhe: ExportAdmin | null = null;

  constructor(
    private service: ExportacoesService,
    private msg: MessageService,
  ) {}

  ngOnInit(): void {
    this.load();
  }

  load(page = this.page, size = this.size): void {
    this.loading.set(true);
    this.service.listar(this.filtroTenant, this.filtroStatus, page, size).subscribe({
      next: (res) => {
        this.itens.set(res.itens);
        this.totalRecords.set(res.total || 0);
        this.loading.set(false);
      },
      error: () => {
        this.msg.add({
          severity: 'error',
          summary: 'Erro',
          detail: 'Falha ao carregar as exportações.',
          life: 4000,
        });
        this.loading.set(false);
      },
    });
  }

  onLazyLoad(event: any): void {
    this.page = event.first / event.rows;
    this.size = event.rows;
    this.load(this.page, this.size);
  }

  onFiltroChange(): void {
    this.page = 0;
    this.load();
  }

  abrirDetalhe(item: ExportAdmin): void {
    this.detalhe = item;
    this.detalheVisible = true;
    this.detalheLoading = true;
    // A listagem não traz a linha do tempo; o detalhe busca com os eventos de auditoria.
    this.service.detalhar(item.exportacao.id).subscribe({
      next: (res) => {
        this.detalhe = res;
        this.detalheLoading = false;
      },
      error: () => {
        this.msg.add({
          severity: 'error',
          summary: 'Erro',
          detail: 'Falha ao carregar o detalhe.',
          life: 4000,
        });
        this.detalheLoading = false;
      },
    });
  }

  statusRotulo(status: string): string {
    return STATUS_ROTULO[status] ?? status;
  }

  eventoRotulo(acao: string): string {
    return EVENTO_ROTULO[acao] ?? acao;
  }

  statusClass(status: string): string {
    switch (status) {
      case 'FAILED':
        return 'bg-red-100 text-red-700';
      case 'PROCESSING':
        return 'bg-blue-100 text-blue-700';
      case 'REQUESTED':
        return 'bg-orange-100 text-orange-700';
      case 'READY':
      case 'DOWNLOADED':
        return 'bg-green-100 text-green-700';
      default:
        return 'bg-gray-100 text-gray-600';
    }
  }

  /** Ator nulo = sistema (geração e expiração automáticas). */
  atorRotulo(ator: string | null): string {
    return ator ? ator.substring(0, 8) : 'Sistema';
  }
}
