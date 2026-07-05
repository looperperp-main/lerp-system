import { Component, OnInit, signal } from '@angular/core';
import { Toast } from 'primeng/toast';
import { ButtonDirective } from 'primeng/button';
import { MessageService, PrimeTemplate } from 'primeng/api';
import { CnpjPipe } from '../../../../util/pipe/cnpj.pipe';
import { DatePipe, NgForOf, NgIf } from '@angular/common';
import { HtmlDecodePipe } from '../../../../util/pipe/html-decode.pipe';
import { TableModule } from 'primeng/table';
import { ParceirosService } from '../parceiros.service';

export interface IndicacaoModel {
  razaoSocial?: string;
  cnpj?: string;
  email?: string;
  status?: string;
  planoSugerido?: string;
  invitedAt?: string;
  activatedAt?: string;
  convertedAt?: string;
  trialExpiresAt?: string;
}

export interface IndicacoesPorContadorModel {
  contador: string;
  crc?: string;
  referralCode?: string;
  commissionRate?: string;
  totalIndicacoes: number;
  convertidas: number;
  indicacoes: IndicacaoModel[];
}

@Component({
  selector: 'app-indicacoes',
  imports: [
    Toast,
    ButtonDirective,
    CnpjPipe,
    DatePipe,
    HtmlDecodePipe,
    NgForOf,
    NgIf,
    PrimeTemplate,
    TableModule,
  ],
  providers: [MessageService],
  templateUrl: './indicacoes.html',
})
export class Indicacoes implements OnInit {
  contadores = signal<IndicacoesPorContadorModel[]>([]);
  loading = signal<boolean>(true);
  expandedRows: { [key: string]: boolean } = {};

  constructor(
    private messageService: MessageService,
    private partnerService: ParceirosService,
  ) {}

  ngOnInit() {
    this.loadIndicacoes();
  }

  loadIndicacoes() {
    this.loading.set(true);
    this.partnerService.getIndicacoes().subscribe({
      next: (data) => {
        this.contadores.set(data || []);
        this.loading.set(false);
      },
      error: () => {
        this.messageService.add({
          severity: 'error',
          summary: 'Erro',
          detail: 'Erro ao buscar Indicações.',
        });
        this.loading.set(false);
      },
    });
  }

  toggleRow(contador: IndicacoesPorContadorModel) {
    this.expandedRows = {
      ...this.expandedRows,
      [contador.contador]: !this.expandedRows[contador.contador],
    };
  }

  statusClass(status?: string): string {
    switch (status) {
      case 'CONVERTIDO':
        return 'bg-green-100 text-green-700';
      case 'TRIAL':
      case 'ATIVADO':
        return 'bg-blue-100 text-blue-700';
      case 'CONVIDADO':
        return 'bg-orange-100 text-orange-700';
      case 'FOLLOWUP':
        return 'bg-purple-100 text-purple-700';
      default:
        return 'bg-gray-100 text-gray-600';
    }
  }
}
