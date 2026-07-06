import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { TableModule } from 'primeng/table';
import { ButtonDirective } from 'primeng/button';
import { environment } from '../../../../environments/environment';

interface JobRow {
  key: string;
  label: string;
  lastRun: string | null;
  lastStatus: string | null;
  lastDurationMs: number | null;
  service: string;
  base: string;
}

/** #5 Runner de jobs: dispara os crons manualmente ("rodar agora") e mostra a última execução. */
@Component({
  selector: 'app-jobs',
  standalone: true,
  imports: [CommonModule, DatePipe, TableModule, ButtonDirective],
  template: `
    <div class="p-2 lg:p-6 bg-[#FDFBF7] min-h-screen font-sans text-gray-800">
      <div class="flex items-center justify-between mb-6">
        <div>
          <h1 class="text-xl lg:text-2xl font-bold text-gray-900">Jobs agendados</h1>
          <p class="text-gray-500 text-sm mt-1">
            Dispara um cron na hora, sem esperar o horário. Os jobs são idempotentes (lock próprio).
          </p>
        </div>
        <button
          pButton
          type="button"
          icon="pi pi-refresh"
          label="Atualizar"
          class="p-button-sm p-button-outlined"
          [disabled]="loading()"
          (click)="carregar()"
        ></button>
      </div>

      <div class="bg-white p-5 rounded-2xl shadow-sm border border-gray-100">
        <p-table [value]="jobs()" [loading]="loading()" styleClass="p-datatable-sm text-sm">
          <ng-template pTemplate="header">
            <tr>
              <th class="text-xs uppercase text-gray-400 font-semibold pb-3">Job</th>
              <th class="text-xs uppercase text-gray-400 font-semibold pb-3">Serviço</th>
              <th class="text-xs uppercase text-gray-400 font-semibold pb-3">Última execução</th>
              <th class="text-xs uppercase text-gray-400 font-semibold pb-3">Status</th>
              <th class="text-xs uppercase text-gray-400 font-semibold pb-3">Duração</th>
              <th class="text-xs uppercase text-gray-400 font-semibold pb-3"></th>
            </tr>
          </ng-template>
          <ng-template pTemplate="body" let-j>
            <tr class="hover:bg-gray-50 border-b border-gray-50 transition-colors">
              <td class="py-3 text-gray-800 font-medium">{{ j.label }}</td>
              <td class="py-3 text-gray-500 text-xs uppercase">{{ j.service }}</td>
              <td class="py-3 text-gray-600">
                {{ j.lastRun ? (j.lastRun | date: 'dd/MM/yyyy HH:mm:ss') : '—' }}
              </td>
              <td class="py-3">
                @if (j.lastStatus) {
                  <span
                    [class]="'px-2 py-1 rounded text-xs font-medium ' + statusClass(j.lastStatus)"
                  >
                    {{ j.lastStatus }}
                  </span>
                } @else {
                  <span class="text-gray-400 text-xs">nunca executado</span>
                }
              </td>
              <td class="py-3 text-gray-600">
                {{ j.lastDurationMs != null ? j.lastDurationMs + ' ms' : '—' }}
              </td>
              <td class="py-3 text-right">
                <button
                  pButton
                  type="button"
                  icon="pi pi-play"
                  label="Rodar agora"
                  class="p-button-sm p-button-outlined"
                  [disabled]="j.lastStatus === 'EXECUTANDO'"
                  (click)="rodar(j)"
                ></button>
              </td>
            </tr>
          </ng-template>
          <ng-template pTemplate="emptymessage">
            <tr>
              <td colspan="6" class="text-center py-6 text-gray-500">Nenhum job disponível.</td>
            </tr>
          </ng-template>
        </p-table>
        @if (erro()) {
          <p class="mt-4 text-sm text-red-700">{{ erro() }}</p>
        }
      </div>
    </div>
  `,
})
export class Jobs implements OnInit {
  private readonly http = inject(HttpClient);
  // cada fonte é um serviço com seu próprio runner de jobs
  private readonly sources = [
    { service: 'billing', base: `${environment.apiUrl}/billing/api/v1/diagnostics/jobs` },
    { service: 'auth', base: `${environment.apiUrl}/auth/diagnostics/jobs` },
  ];

  readonly jobs = signal<JobRow[]>([]);
  readonly loading = signal(false);
  readonly erro = signal('');

  ngOnInit(): void {
    this.carregar();
  }

  carregar(): void {
    this.loading.set(true);
    this.erro.set('');
    const acc: JobRow[] = [];
    let pendentes = this.sources.length;
    for (const src of this.sources) {
      this.http.get<Omit<JobRow, 'service' | 'base'>[]>(src.base).subscribe({
        next: (res) => {
          for (const j of res ?? []) acc.push({ ...j, service: src.service, base: src.base });
          if (--pendentes === 0) this.finalizar(acc);
        },
        error: () => {
          this.erro.set('Alguns serviços não responderam (verifique se estão no ar).');
          if (--pendentes === 0) this.finalizar(acc);
        },
      });
    }
  }

  private finalizar(acc: JobRow[]): void {
    this.jobs.set(acc);
    this.loading.set(false);
  }

  rodar(job: JobRow): void {
    this.http.post<JobRow>(`${job.base}/${job.key}/run`, null).subscribe({
      next: () => {
        // marca EXECUTANDO na hora; o Atualizar mostra OK/ERRO quando o async terminar
        this.jobs.update((list) =>
          list.map((j) =>
            j === job ? { ...j, lastStatus: 'EXECUTANDO', lastRun: new Date().toISOString() } : j,
          ),
        );
      },
      error: (e) => this.erro.set(`Falhou: ${e?.error?.message || e?.message || 'erro'}`),
    });
  }

  statusClass(status: string): string {
    if (status === 'OK') return 'bg-green-100 text-green-700';
    if (status === 'EXECUTANDO') return 'bg-blue-100 text-blue-700';
    return 'bg-red-100 text-red-700';
  }
}
