import { Component, EventEmitter, inject, Input, OnInit, Output, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { PickListModule } from 'primeng/picklist';
import { ButtonModule } from 'primeng/button';
import { MessageService } from 'primeng/api';
import { GrupoClienteTabelaPrecoService } from '../grupo-cliente-tabela-preco.service';
import { DialogModule } from 'primeng/dialog';
import {TabelaPrecoService} from '../../tabela-precos/tabela-preco.service';
import {TabelaPreco} from '../../tabela-precos/tabela-preco.model';

@Component({
  selector: 'app-grupo-cliente-tabela-preco-form',
  standalone: true,
  imports: [CommonModule, PickListModule, ButtonModule, DialogModule],
  templateUrl: './grupo-cliente-tabela-preco-form.html'
})
export class GrupoClienteTabelaPrecoFormComponent implements OnInit {
  @Input() grupoClienteId!: string;
  @Input() grupoClienteNome!: string;
  @Input() visible: boolean = false;
  @Output() visibleChange = new EventEmitter<boolean>();
  @Output() onSaved = new EventEmitter<void>();

  private tabelaPrecoService = inject(TabelaPrecoService);
  private associacaoService = inject(GrupoClienteTabelaPrecoService);
  private messageService = inject(MessageService);

  sourceTabelas = signal<TabelaPreco[]>([]);
  targetTabelas = signal<TabelaPreco[]>([]);
  loading = signal<boolean>(true);

  ngOnInit(): void {
    if (this.grupoClienteId) {
      this.loadData();
    }
  }

  loadData(): void {
    this.loading.set(true);

    // 1. Busca todas as tabelas de preço ativas
    this.tabelaPrecoService.getAll(0, 1000).subscribe({
      next: (tabelasRes) => {
        let todasTabelas: TabelaPreco[] = tabelasRes._embedded?.tabelaPrecoResponseDTOList || [];

        // 2. Busca as associações atuais do grupo de cliente
        this.associacaoService.getAssociacoes(this.grupoClienteId).subscribe({
          next: (assocRes) => {
            const associacoes = assocRes._embedded?.associacoes || [];
            const associadosIds = associacoes.map((a: any) => a.tabelaPrecoId);

            // 3. Separa as listas Source (disponíveis) e Target (selecionadas)
            const target = todasTabelas.filter(t => t.id && associadosIds.includes(t.id));
            const source = todasTabelas.filter(t => t.id && !associadosIds.includes(t.id));

            this.targetTabelas.set(target);
            this.sourceTabelas.set(source);
            this.loading.set(false);
          },
          error: () => this.handleError('Erro ao carregar associações.')
        });
      },
      error: () => this.handleError('Erro ao carregar tabelas de preço.')
    });
  }

  salvar(): void {
    this.loading.set(true);

    // Pega apenas os IDs das tabelas que estão no target (selecionadas)
    const requestIds = this.targetTabelas().map(t => t.id).filter(id => id !== undefined) as string[];

    this.associacaoService.sincronizarAssociacoes(this.grupoClienteId, { tabelaPrecoIds: requestIds }).subscribe({
      next: () => {
        this.messageService.add({ severity: 'success', summary: 'Sucesso', detail: 'Tabelas de preço sincronizadas.' });
        this.loading.set(false);
        this.fechar();
        this.onSaved.emit();
      },
      error: () => this.handleError('Erro ao salvar as associações.')
    });
  }

  fechar(): void {
    this.visible = false;
    this.visibleChange.emit(this.visible);
  }

  private handleError(msg: string): void {
    this.messageService.add({ severity: 'error', summary: 'Erro', detail: msg });
    this.loading.set(false);
  }
}
