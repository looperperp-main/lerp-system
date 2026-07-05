import { Component, inject, signal } from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { TableModule } from 'primeng/table';
import { ButtonDirective } from 'primeng/button';
import { environment } from '../../../../environments/environment';

interface AuditRow {
  id: string;
  actorUserId: string;
  action: string;
  targetType: string;
  targetId: string;
  result: string;
  eventDate: string;
}

/** #1 Rastreador de correlationId: linha do tempo de uma operação através dos serviços. */
@Component({
  selector: 'app-rastreador',
  standalone: true,
  imports: [CommonModule, FormsModule, DatePipe, TableModule, ButtonDirective],
  template: `
    <div class="p-2 lg:p-6 bg-[#FDFBF7] min-h-screen font-sans text-gray-800">
      <div class="mb-6">
        <h1 class="text-xl lg:text-2xl font-bold text-gray-900">Rastreador de operação</h1>
        <p class="text-gray-500 text-sm mt-1">
          Cole um <b>correlationId</b> (ex.: da Visão 360 ou de um log) e veja tudo que aconteceu naquela operação.
        </p>
      </div>

      <div class="bg-white p-5 rounded-2xl shadow-sm border border-gray-100 mb-6">
        <div class="flex flex-col sm:flex-row gap-2">
          <input
            type="text"
            [(ngModel)]="correlationId"
            (keydown.enter)="buscar()"
            placeholder="correlationId (UUID)"
            class="flex-1 border border-gray-300 rounded-md p-2 text-sm font-mono" />
          <button pButton type="button" icon="pi pi-search" label="Rastrear"
                  class="p-button-sm" [disabled]="!correlationId || loading()" (click)="buscar()"></button>
        </div>
      </div>

      <div class="bg-white p-5 rounded-2xl shadow-sm border border-gray-100">
        <p-table [value]="events()" [loading]="loading()" styleClass="p-datatable-sm text-sm">
          <ng-template pTemplate="header">
            <tr>
              <th class="text-xs uppercase text-gray-400 font-semibold pb-3">Data/Hora</th>
              <th class="text-xs uppercase text-gray-400 font-semibold pb-3">Ação</th>
              <th class="text-xs uppercase text-gray-400 font-semibold pb-3">Alvo</th>
              <th class="text-xs uppercase text-gray-400 font-semibold pb-3">Resultado</th>
              <th class="text-xs uppercase text-gray-400 font-semibold pb-3">Ator</th>
            </tr>
          </ng-template>
          <ng-template pTemplate="body" let-e>
            <tr class="hover:bg-gray-50 border-b border-gray-50 transition-colors">
              <td class="py-3 text-gray-600">{{ e.eventDate ? (e.eventDate | date: 'dd/MM/yyyy HH:mm:ss') : '—' }}</td>
              <td class="py-3 text-gray-800 font-medium">{{ e.action }}</td>
              <td class="py-3 text-gray-500 text-xs">{{ e.targetType }} {{ e.targetId }}</td>
              <td class="py-3">
                <span [class]="'px-2 py-1 rounded text-xs font-medium ' +
                  (e.result === 'SUCCESS' ? 'bg-green-100 text-green-700' : 'bg-red-100 text-red-700')">
                  {{ e.result }}
                </span>
              </td>
              <td class="py-3 text-gray-500 text-xs">{{ e.actorUserId }}</td>
            </tr>
          </ng-template>
          <ng-template pTemplate="emptymessage">
            <tr><td colspan="5" class="text-center py-6 text-gray-500">
              {{ buscou() ? 'Nenhum evento para esse correlationId.' : 'Informe um correlationId e clique em Rastrear.' }}
            </td></tr>
          </ng-template>
        </p-table>
      </div>
    </div>
  `,
})
export class Rastreador {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/auth/audits/trace`;

  correlationId = '';
  readonly events = signal<AuditRow[]>([]);
  readonly loading = signal(false);
  readonly buscou = signal(false);

  buscar(): void {
    const id = this.correlationId.trim();
    if (!id) return;
    this.loading.set(true);
    this.http.get<AuditRow[]>(`${this.base}/${id}`).subscribe({
      next: (res) => {
        this.events.set(res ?? []);
        this.buscou.set(true);
        this.loading.set(false);
      },
      error: () => {
        this.events.set([]);
        this.buscou.set(true);
        this.loading.set(false);
      },
    });
  }
}
