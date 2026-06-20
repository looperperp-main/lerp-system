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
import { HttpErrorResponse } from '@angular/common/http';
import { FilaInternaItem, UpdateStatusRequest } from './fila-interna.model';
import { FilaInternaService } from './fila-interna.service';

interface StatusOpcao { label: string; value: string }

@Component({
  selector: 'app-fila-interna',
  standalone: true,
  imports: [CommonModule, FormsModule, Toast, TableModule, ButtonDirective, Ripple, Tooltip, Dialog, Select],
  providers: [MessageService],
  templateUrl: './fila-interna.html',
})
export class FilaInterna implements OnInit {
  tickets = signal<FilaInternaItem[]>([]);
  totalRecords = signal(0);
  loading = signal(true);
  page = 0;
  size = 20;

  filtroStatus = '';
  statusOpcoes: StatusOpcao[] = [
    { label: 'Todos', value: '' },
    { label: 'Pendente', value: 'PENDENTE' },
    { label: 'Em Andamento', value: 'EM_ANDAMENTO' },
    { label: 'Resolvido', value: 'RESOLVIDO' },
  ];

  detailVisible = false;
  resolveVisible = false;
  selectedTicket: FilaInternaItem | null = null;
  resolveStatus = 'RESOLVIDO';
  resolveNotes = '';
  resolveLoading = false;

  resolveStatusOpcoes: StatusOpcao[] = [
    { label: 'Em Andamento', value: 'EM_ANDAMENTO' },
    { label: 'Resolvido', value: 'RESOLVIDO' },
  ];

  constructor(private service: FilaInternaService, private msg: MessageService) {}

  ngOnInit(): void {
    this.load();
  }

  load(page = this.page, size = this.size): void {
    this.loading.set(true);
    this.service.listar(this.filtroStatus, page, size).subscribe({
      next: (res) => {
        this.tickets.set(res.content);
        this.totalRecords.set(res.page?.totalElements ?? 0);
        this.loading.set(false);
      },
      error: () => {
        this.msg.add({ severity: 'error', summary: 'Erro', detail: 'Falha ao carregar fila.', life: 4000 });
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

  openDetail(ticket: FilaInternaItem): void {
    this.selectedTicket = ticket;
    this.detailVisible = true;
  }

  openResolve(ticket: FilaInternaItem): void {
    this.selectedTicket = ticket;
    this.resolveNotes = ticket.resolutionNotes ?? '';
    this.resolveStatus = ticket.status === 'PENDENTE' ? 'EM_ANDAMENTO' : 'RESOLVIDO';
    this.resolveVisible = true;
  }

  confirmarResolve(): void {
    if (!this.selectedTicket) return;
    this.resolveLoading = true;
    const req: UpdateStatusRequest = {
      status: this.resolveStatus,
      resolvedBy: sessionStorage.getItem('username') ?? 'admin',
      resolutionNotes: this.resolveNotes,
    };
    this.service.atualizarStatus(this.selectedTicket.id, req).subscribe({
      next: () => {
        this.msg.add({ severity: 'success', summary: 'Atualizado', detail: 'Status alterado.', life: 3000 });
        this.resolveVisible = false;
        this.resolveLoading = false;
        this.load();
      },
      error: (err: HttpErrorResponse) => {
        this.msg.add({ severity: 'error', summary: 'Erro', detail: err.error?.message ?? 'Falha ao atualizar.', life: 4000 });
        this.resolveLoading = false;
      },
    });
  }

  parsedPayload(raw: string): Record<string, unknown> {
    try { return JSON.parse(raw); } catch { return {}; }
  }

  tipoLabel(tipo: string): string {
    return tipo === 'RELATORIO_D10' ? 'D+10' : tipo === 'TRIAL_EXPIRADO' ? 'D+15 Expirado' : tipo;
  }

  statusClass(status: string): string {
    if (status === 'PENDENTE') return 'bg-orange-100 text-orange-700';
    if (status === 'EM_ANDAMENTO') return 'bg-blue-100 text-blue-700';
    return 'bg-green-100 text-green-700';
  }

  tipoClass(tipo: string): string {
    return tipo === 'RELATORIO_D10' ? 'bg-purple-100 text-purple-700' : 'bg-red-100 text-red-700';
  }

  asArray(value: unknown): string[] {
    return Array.isArray(value) ? (value as string[]) : [];
  }

  asDate(value: unknown): string | number | Date | null {
    return typeof value === 'string' || typeof value === 'number' || value instanceof Date ? value : null;
  }
}