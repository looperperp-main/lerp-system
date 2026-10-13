import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { ButtonDirective } from 'primeng/button';
import { Ripple } from 'primeng/ripple';
import { ImportacoesService, ItemFila, OnboardingSla, PainelSla } from './importacoes.service';

const STATUS_ROTULO: Record<string, string> = {
  AWAITING_CONCIERGE: 'Na fila',
  UPLOADED: 'Validando',
  VALIDATING: 'Validando',
  AWAITING_FIX: 'Prévia com erros',
  READY: 'Prévia pronta',
  PROMOTING: 'Importando',
  DONE: 'Concluído',
  FAILED: 'Falhou',
};

const ENTIDADE_ROTULO: Record<string, string> = { CUSTOMER: 'Clientes', PRODUCT: 'Produtos' };

/** Fila do concierge + painel de SLA (spec migracao §8 e §13). Mensagens inline: o admin não tem toast global. */
@Component({
  selector: 'app-importacoes',
  standalone: true,
  imports: [CommonModule, ButtonDirective, Ripple],
  templateUrl: './importacoes.html',
})
export class Importacoes implements OnInit {
  painel = signal<PainelSla | null>(null);
  carregando = signal(true);
  erro = signal<string | null>(null);
  aviso = signal<{ tipo: 'ok' | 'erro'; texto: string } | null>(null);
  enviando = signal<string | null>(null);

  constructor(private service: ImportacoesService) {}

  ngOnInit(): void {
    this.carregar();
  }

  carregar(): void {
    this.carregando.set(true);
    this.erro.set(null);
    this.service.sla().subscribe({
      next: (p) => {
        this.painel.set(p);
        this.carregando.set(false);
      },
      error: () => {
        this.erro.set('Falha ao carregar a fila do concierge.');
        this.carregando.set(false);
      },
    });
  }

  baixar(item: ItemFila): void {
    this.service.arquivoDoCliente(item.lote.id).subscribe({
      next: (res: HttpResponse<Blob>) => {
        const url = URL.createObjectURL(res.body as Blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = item.lote.arquivo || `pedido-${item.lote.id}`;
        a.click();
        URL.revokeObjectURL(url);
      },
      error: () => this.aviso.set({ tipo: 'erro', texto: 'O cliente não enviou arquivo neste pedido ou ele não está mais disponível.' }),
    });
  }

  enviarPrevia(item: ItemFila, ev: Event): void {
    const input = ev.target as HTMLInputElement;
    const arquivo = input.files?.[0];
    input.value = '';
    if (!arquivo) return;
    this.enviando.set(item.lote.id);
    this.aviso.set(null);
    this.service.enviarPrevia(item.lote.id, arquivo).subscribe({
      next: () => {
        this.enviando.set(null);
        this.aviso.set({ tipo: 'ok', texto: 'Prévia enviada. O cliente já pode conferir a prévia na tela de importação.' });
        this.carregar();
      },
      error: (e: HttpErrorResponse) => {
        this.enviando.set(null);
        const m = e.error?.message;
        this.aviso.set({ tipo: 'erro', texto: typeof m === 'string' && m ? m : 'Falha ao enviar a prévia.' });
      },
    });
  }

  podeBaixar(item: ItemFila): boolean {
    return !!item.lote.arquivo;
  }

  /** A prévia só pode ser (re)enviada com o pedido na fila. */
  podeEnviarPrevia(item: ItemFila): boolean {
    return item.lote.status === 'AWAITING_CONCIERGE';
  }

  statusRotulo(s: string): string {
    return STATUS_ROTULO[s] ?? s;
  }

  entidadeRotulo(e: string): string {
    return ENTIDADE_ROTULO[e] ?? e;
  }

  entidadesRotulo(o: OnboardingSla): string {
    return o.entidadesConcluidas.length ? o.entidadesConcluidas.map((e) => this.entidadeRotulo(e)).join(', ') : 'nenhuma';
  }

  dias(horas: number): string {
    return horas >= 48 ? `${Math.floor(horas / 24)} dias` : `${horas} h`;
  }
}
