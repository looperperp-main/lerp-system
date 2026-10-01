import { DatePipe } from '@angular/common';
import { Component } from '@angular/core';
import { CHANGELOG, TipoNovidade } from './changelog';

const ROTULOS: Record<TipoNovidade, string> = {
  novo: 'Novo',
  melhoria: 'Melhoria',
  correcao: 'Correção',
};

@Component({
  selector: 'app-novidades',
  standalone: true,
  imports: [DatePipe],
  template: `
    <div class="jb-page-head">
      <h1>Novidades</h1>
      <p class="sub">Acompanhe o que há de novo no sistema.</p>
    </div>

    @if (novidades.length === 0) {
      <div class="jb-panel" style="text-align: center; padding: 48px;">
        <i class="pi pi-inbox" style="font-size: 2rem; color: var(--muted);"></i>
        <p class="sub" style="margin-top: 12px;">Nenhuma novidade publicada ainda.</p>
      </div>
    } @else {
      <div class="jb-panel" style="display: grid; gap: 20px;">
        @for (n of novidades; track $index) {
          <article>
            <div class="sub" style="font-size: 12px;">
              {{ n.data + 'T00:00:00' | date: 'dd/MM/yyyy' }} · {{ rotulo(n.tipo) }}
            </div>
            <h3 style="margin: 4px 0;">{{ n.titulo }}</h3>
            <p class="sub" style="margin: 0;">{{ n.descricao }}</p>
          </article>
        }
      </div>
    }
  `,
})
export class Novidades {
  novidades = CHANGELOG;

  rotulo(tipo: TipoNovidade): string {
    return ROTULOS[tipo];
  }
}
