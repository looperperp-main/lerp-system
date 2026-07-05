import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient, HttpParams } from '@angular/common/http';
import { ButtonDirective } from 'primeng/button';
import { environment } from '../../../../environments/environment';

interface HealthRow {
  name: string;
}

/** #3 Toggle de log level em runtime: sobe/baixa o nível de um pacote sem redeploy. */
@Component({
  selector: 'app-logs',
  standalone: true,
  imports: [CommonModule, FormsModule, ButtonDirective],
  template: `
    <div class="p-2 lg:p-6 bg-[#FDFBF7] min-h-screen font-sans text-gray-800">
      <div class="mb-6">
        <h1 class="text-xl lg:text-2xl font-bold text-gray-900">Nível de log em runtime</h1>
        <p class="text-gray-500 text-sm mt-1">
          Sobe um pacote pra DEBUG (ou qualquer nível) num serviço ao vivo, sem reiniciar o
          container.
        </p>
      </div>

      <div class="bg-white p-5 rounded-2xl shadow-sm border border-gray-100 max-w-2xl">
        <div class="grid grid-cols-1 sm:grid-cols-3 gap-3 mb-4">
          <div>
            <label class="block text-xs uppercase text-gray-400 font-semibold mb-1">Serviço</label>
            <select
              [(ngModel)]="service"
              class="w-full border border-gray-300 rounded-md p-2 text-sm bg-white"
            >
              @for (s of services(); track s) {
                <option [value]="s">{{ s }}</option>
              }
            </select>
          </div>
          <div>
            <label class="block text-xs uppercase text-gray-400 font-semibold mb-1">Pacote</label>
            <input
              type="text"
              [(ngModel)]="logger"
              placeholder="com.l.erp"
              class="w-full border border-gray-300 rounded-md p-2 text-sm font-mono"
            />
          </div>
          <div>
            <label class="block text-xs uppercase text-gray-400 font-semibold mb-1">Nível</label>
            <select
              [(ngModel)]="level"
              class="w-full border border-gray-300 rounded-md p-2 text-sm bg-white"
            >
              @for (l of levels; track l) {
                <option [value]="l">{{ l || '(resetar)' }}</option>
              }
            </select>
          </div>
        </div>

        <div class="flex gap-2">
          <button
            pButton
            type="button"
            icon="pi pi-check"
            label="Aplicar"
            class="p-button-sm"
            [disabled]="!service || !logger || saving()"
            (click)="aplicar()"
          ></button>
          <button
            pButton
            type="button"
            icon="pi pi-search"
            label="Consultar nível"
            class="p-button-sm p-button-outlined"
            [disabled]="!service || !logger"
            (click)="consultar()"
          ></button>
        </div>

        @if (result()) {
          <p class="mt-4 text-sm" [class.text-green-700]="!erro()" [class.text-red-700]="erro()">
            {{ result() }}
          </p>
        }
      </div>
    </div>
  `,
})
export class Logs implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/auth/diagnostics`;

  readonly levels = ['TRACE', 'DEBUG', 'INFO', 'WARN', 'ERROR', 'OFF', ''];
  readonly services = signal<string[]>([]);
  readonly result = signal('');
  readonly erro = signal(false);
  readonly saving = signal(false);

  service = '';
  logger = 'com.l.erp';
  level = 'DEBUG';

  ngOnInit(): void {
    // reaproveita o painel de saúde pra listar os serviços registrados no Eureka
    this.http.get<HealthRow[]>(`${this.base}/health`).subscribe({
      next: (res) => {
        const names = (res ?? []).map((s) => s.name);
        this.services.set(names);
        if (names.length && !this.service) this.service = names[0];
      },
      error: () => {},
    });
  }

  aplicar(): void {
    this.saving.set(true);
    const params = new HttpParams()
      .set('service', this.service)
      .set('logger', this.logger)
      .set('level', this.level);
    this.http.post(`${this.base}/loggers`, null, { params }).subscribe({
      next: () => {
        this.erro.set(false);
        this.result.set(
          `Nível de ${this.logger} em ${this.service} → ${this.level || '(resetado)'}.`,
        );
        this.saving.set(false);
      },
      error: (e) => {
        this.erro.set(true);
        this.result.set(`Falhou: ${e?.error?.message || e?.message || 'erro'}`);
        this.saving.set(false);
      },
    });
  }

  consultar(): void {
    const params = new HttpParams().set('service', this.service);
    this.http.get<any>(`${this.base}/loggers`, { params }).subscribe({
      next: (res) => {
        const l = res?.loggers?.[this.logger];
        this.erro.set(false);
        this.result.set(
          l
            ? `${this.logger}: configurado=${l.configuredLevel ?? '(herdado)'} efetivo=${l.effectiveLevel}`
            : `${this.logger} não encontrado (efetivo herda do root).`,
        );
      },
      error: (e) => {
        this.erro.set(true);
        this.result.set(`Falhou: ${e?.error?.message || e?.message || 'erro'}`);
      },
    });
  }
}
