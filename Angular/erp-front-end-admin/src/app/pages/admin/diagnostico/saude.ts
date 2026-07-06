import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { TableModule } from 'primeng/table';
import { ButtonDirective } from 'primeng/button';
import { environment } from '../../../../environments/environment';

interface ServiceHealth {
  name: string;
  status: string;
  instances: number;
  down: string[];
}

/** #2 Painel de saúde: status e nº de instâncias de cada serviço registrado no Eureka. */
@Component({
  selector: 'app-saude',
  standalone: true,
  imports: [CommonModule, TableModule, ButtonDirective],
  template: `
    <div class="p-2 lg:p-6 bg-[#FDFBF7] min-h-screen font-sans text-gray-800">
      <div class="flex items-center justify-between mb-6">
        <div>
          <h1 class="text-xl lg:text-2xl font-bold text-gray-900">Saúde dos serviços</h1>
          <p class="text-gray-500 text-sm mt-1">
            Status e instâncias de cada serviço (via Eureka + /actuator/health).
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
        <p-table [value]="services()" [loading]="loading()" styleClass="p-datatable-sm text-sm">
          <ng-template pTemplate="header">
            <tr>
              <th class="text-xs uppercase text-gray-400 font-semibold pb-3">Serviço</th>
              <th class="text-xs uppercase text-gray-400 font-semibold pb-3">Status</th>
              <th class="text-xs uppercase text-gray-400 font-semibold pb-3">Instâncias</th>
            </tr>
          </ng-template>
          <ng-template pTemplate="body" let-s>
            <tr class="hover:bg-gray-50 border-b border-gray-50 transition-colors">
              <td class="py-3 text-gray-800 font-medium uppercase">{{ s.name }}</td>
              <td class="py-3">
                <span
                  [class]="
                    'px-2 py-1 rounded text-xs font-medium ' +
                    (s.status === 'UP' ? 'bg-green-100 text-green-700' : 'bg-red-100 text-red-700')
                  "
                >
                  {{ s.status }}
                </span>
                @if (s.down?.length) {
                  <span class="ml-2 text-xs text-red-600">↓ {{ s.down.join(', ') }}</span>
                }
              </td>
              <td class="py-3 text-gray-600">{{ s.instances }}</td>
            </tr>
          </ng-template>
          <ng-template pTemplate="emptymessage">
            <tr>
              <td colspan="3" class="text-center py-6 text-gray-500">Nenhum serviço registrado.</td>
            </tr>
          </ng-template>
        </p-table>
      </div>
    </div>
  `,
})
export class Saude implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiUrl}/auth/diagnostics/health`;

  readonly services = signal<ServiceHealth[]>([]);
  readonly loading = signal(true);

  ngOnInit(): void {
    this.carregar();
  }

  carregar(): void {
    this.loading.set(true);
    this.http.get<ServiceHealth[]>(this.url).subscribe({
      next: (res) => {
        this.services.set(res ?? []);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }
}
