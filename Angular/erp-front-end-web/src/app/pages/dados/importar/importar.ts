import { Component, computed, inject, OnDestroy, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { MessageService } from 'primeng/api';
import { ButtonDirective } from 'primeng/button';
import { Select } from 'primeng/select';
import { Dialog } from 'primeng/dialog';
import { Breadcrumb } from '../../../components/breadcrumb/breadcrumb';
import { DadosService } from '../dados.service';
import {
  ENTIDADES_IMPORT,
  IMPORT_STATUS,
  IMPORT_TRANSITORIOS,
  IMPORT_TRILHA,
  ImportLote,
  Mapeamento,
  RelatorioErros,
} from '../dados.model';

const TAMANHO_MAX = 50 * 1024 * 1024;
const POLLING_MS = 5000;
const POLLING_ASSISTENTE_MS = 2000;
const PASSOS = ['O que importar', 'Enviar arquivo', 'Mapear colunas', 'Revisar', 'Confirmar'];

/** Importar Dados (spec exportacao-importacao-front §4): histórico + assistente retomável em 5 etapas. */
@Component({
  selector: 'app-importar',
  imports: [CommonModule, FormsModule, ButtonDirective, Select, Dialog, Breadcrumb],
  templateUrl: './importar.html',
  styleUrl: '../dados.scss',
})
export class Importar implements OnInit, OnDestroy {
  private dados = inject(DadosService);
  private msg = inject(MessageService);

  entidades = ENTIDADES_IMPORT;
  passos = PASSOS;

  lotes = signal<ImportLote[]>([]);
  carregando = signal(true);
  erroLista = signal(false);

  // ── assistente ──
  aberto = false;
  etapa = signal(1);
  entidade = signal<string | null>(null);
  arquivo = signal<File | null>(null);
  arrastando = signal(false);
  erroArquivo = signal<string | null>(null);
  lote = signal<ImportLote | null>(null);
  ocupado = signal(false);
  mapeamento = signal<Mapeamento | null>(null);
  escolhas = signal<Record<string, string>>({});
  erros = signal<RelatorioErros | null>(null);
  pedindoAjuda = signal(false);

  private timerLista: ReturnType<typeof setTimeout> | null = null;
  private timerLote: ReturnType<typeof setTimeout> | null = null;

  rotuloEntidade = computed(() => ENTIDADES_IMPORT.find((e) => e.value === this.entidade())?.label ?? '');

  /** Campos obrigatórios ainda sem coluna: ficam em vermelho e escondem o "Continuar". */
  faltando = computed(() => {
    const m = this.mapeamento();
    const e = this.escolhas();
    return m ? m.campos.filter((c) => c.obrigatorio && !e[c.campo]).map((c) => c.campo) : [];
  });

  ngOnInit(): void {
    this.carregarLista();
  }

  ngOnDestroy(): void {
    if (this.timerLista) clearTimeout(this.timerLista);
    if (this.timerLote) clearTimeout(this.timerLote);
  }

  // ───────────────────────── histórico ─────────────────────────
  carregarLista(silencioso = false): void {
    if (!silencioso) this.carregando.set(true);
    this.erroLista.set(false);
    this.dados.listarImports(0, 20).subscribe({
      next: (p) => {
        this.lotes.set(p.itens);
        this.carregando.set(false);
        this.agendarLista();
      },
      error: () => {
        this.carregando.set(false);
        if (!silencioso) this.erroLista.set(true);
        this.agendarLista();
      },
    });
  }

  private agendarLista(): void {
    if (this.timerLista) clearTimeout(this.timerLista);
    if (this.lotes().some((l) => IMPORT_TRANSITORIOS.includes(l.status))) {
      this.timerLista = setTimeout(() => this.carregarLista(true), POLLING_MS);
    }
  }

  chip(l: ImportLote): { rotulo: string; cor: string } {
    return IMPORT_STATUS[l.status] ?? { rotulo: l.status, cor: 'cinza' };
  }

  trilha(l: ImportLote): string {
    return IMPORT_TRILHA[l.trilha] ?? l.trilha;
  }

  rotuloDe(entidade: string): string {
    return ENTIDADES_IMPORT.find((e) => e.value === entidade)?.label ?? entidade;
  }

  /** Ação principal por estado (spec §4.1). Estados em andamento não têm ação. */
  acaoPrincipal(l: ImportLote): string | null {
    switch (l.status) {
      case 'AWAITING_MAPPING': return 'Mapear colunas';
      case 'AWAITING_FIX': return 'Corrigir';
      case 'READY': return 'Revisar e importar';
      case 'DONE': return 'Ver resumo';
      case 'FAILED': return l.trilha === 'CONCIERGE' ? null : 'Enviar novo arquivo';
      default: return null;
    }
  }

  prazoVencido(l: ImportLote): boolean {
    return l.status === 'AWAITING_CONCIERGE' && !!l.prazoConciergeEm && new Date(l.prazoConciergeEm).getTime() < Date.now();
  }

  // ───────────────────────── assistente ─────────────────────────
  novaImportacao(): void {
    this.reiniciar();
    this.aberto = true;
  }

  /** Retoma o lote na etapa que corresponde ao estado dele. */
  retomar(l: ImportLote): void {
    this.reiniciar();
    this.entidade.set(l.entidade);
    this.lote.set(l);
    this.aberto = true;
    switch (l.status) {
      case 'AWAITING_MAPPING': this.irParaMapeamento(l); break;
      case 'FAILED': this.etapa.set(2); break;
      case 'DONE': this.etapa.set(5); break;
      default: this.irParaRevisao(l);
    }
  }

  fechar(): void {
    this.aberto = false;
    if (this.timerLote) clearTimeout(this.timerLote);
    this.carregarLista(true);
  }

  private reiniciar(): void {
    if (this.timerLote) clearTimeout(this.timerLote);
    this.etapa.set(1);
    this.entidade.set(null);
    this.arquivo.set(null);
    this.erroArquivo.set(null);
    this.lote.set(null);
    this.mapeamento.set(null);
    this.escolhas.set({});
    this.erros.set(null);
    this.ocupado.set(false);
    this.pedindoAjuda.set(false);
  }

  escolherEntidade(valor: string): void {
    this.entidade.set(valor);
  }

  baixarModelo(formato: 'xlsx' | 'csv'): void {
    const e = this.entidade();
    if (!e) return;
    this.dados.baixarModelo(e, formato).subscribe({
      next: (res) => this.dados.salvar(res, `modelo.${formato}`),
      error: () => this.toast('error', 'Não foi possível baixar o modelo agora. Tente novamente.'),
    });
  }

  // ── etapa 2: arquivo ──
  aoSelecionar(ev: Event): void {
    const f = (ev.target as HTMLInputElement).files?.[0] ?? null;
    this.definirArquivo(f);
    (ev.target as HTMLInputElement).value = '';
  }

  aoSoltar(ev: DragEvent): void {
    ev.preventDefault();
    this.arrastando.set(false);
    this.definirArquivo(ev.dataTransfer?.files?.[0] ?? null);
  }

  private definirArquivo(f: File | null): void {
    this.erroArquivo.set(null);
    if (!f) return;
    const ext = f.name.split('.').pop()?.toLowerCase();
    if (ext !== 'xlsx' && ext !== 'csv') {
      this.arquivo.set(null);
      this.erroArquivo.set('Formato não aceito. Envie um arquivo .xlsx ou .csv.');
      return;
    }
    if (f.size > TAMANHO_MAX) {
      this.arquivo.set(null);
      this.erroArquivo.set('O arquivo excede o limite de 50 MB. Divida-o em arquivos menores e envie um de cada vez.');
      return;
    }
    this.arquivo.set(f);
  }

  enviar(): void {
    const e = this.entidade();
    const f = this.arquivo();
    if (!e || !f || this.ocupado()) return;
    this.ocupado.set(true);
    this.dados.enviarArquivo(e, f).subscribe({
      next: (l) => {
        this.ocupado.set(false);
        this.lote.set(l);
        if (l.status === 'AWAITING_MAPPING') this.irParaMapeamento(l);
        else this.irParaRevisao(l);
      },
      error: (err: HttpErrorResponse) => {
        this.ocupado.set(false);
        this.erroArquivo.set(this.mensagemDe(err, 'Não foi possível enviar o arquivo. Tente novamente.'));
      },
    });
  }

  // ── Trilha 3: pedido de ajuda (ação explícita do cliente, nunca automática) ──
  confirmarAjuda(): void {
    this.pedindoAjuda.set(true);
  }

  cancelarAjuda(): void {
    this.pedindoAjuda.set(false);
  }

  pedirAjuda(): void {
    const e = this.entidade();
    if (!e || this.ocupado()) return;
    this.ocupado.set(true);
    this.dados.pedirAjuda(e, this.arquivo()).subscribe({
      next: () => {
        this.ocupado.set(false);
        this.aberto = false;
        this.toast('success', 'Recebemos seu pedido. Retornamos em até 48 horas com uma prévia para você conferir.');
        this.carregarLista(true);
      },
      error: (err: HttpErrorResponse) => {
        this.ocupado.set(false);
        this.pedindoAjuda.set(false);
        this.erroArquivo.set(this.mensagemDe(err, 'Não foi possível registrar o pedido. Tente novamente.'));
      },
    });
  }

  // ── etapa 3: mapeamento (Trilha 2, automática) ──
  private irParaMapeamento(l: ImportLote): void {
    this.etapa.set(3);
    this.ocupado.set(true);
    this.dados.mapeamento(l.id).subscribe({
      next: (m) => {
        this.mapeamento.set(m);
        this.escolhas.set({ ...m.sugestao });
        this.ocupado.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.ocupado.set(false);
        this.toast('error', this.mensagemDe(err, 'Não foi possível carregar as colunas do arquivo.'));
        this.etapa.set(2);
      },
    });
  }

  escolher(campo: string, coluna: string | null): void {
    const novo = { ...this.escolhas() };
    if (coluna) novo[campo] = coluna;
    else delete novo[campo];
    this.escolhas.set(novo);
  }

  opcoesColuna(): { label: string; value: string }[] {
    return (this.mapeamento()?.colunasArquivo ?? []).map((c) => ({ label: c, value: c }));
  }

  sugerido(campo: string): boolean {
    return !!this.mapeamento()?.sugestao[campo];
  }

  temSugestao(): boolean {
    return Object.keys(this.mapeamento()?.sugestao ?? {}).length > 0;
  }

  aplicarMapeamento(): void {
    const l = this.lote();
    if (!l || this.ocupado() || this.faltando().length > 0) return;
    this.ocupado.set(true);
    this.dados.aplicarMapeamento(l.id, this.escolhas()).subscribe({
      next: (r) => {
        this.ocupado.set(false);
        this.lote.set(r);
        this.irParaRevisao(r);
      },
      error: (err: HttpErrorResponse) => {
        this.ocupado.set(false);
        this.toast('error', this.mensagemDe(err, 'Não foi possível aplicar o mapeamento. Tente novamente.'));
      },
    });
  }

  // ── etapa 4: revisão ──
  private irParaRevisao(l: ImportLote): void {
    this.etapa.set(4);
    this.acompanhar(l.id);
  }

  /** Consulta o lote a cada 2 s enquanto o servidor valida; quando para, busca o relatório de erros. */
  private acompanhar(id: string): void {
    if (this.timerLote) clearTimeout(this.timerLote);
    this.dados.detalharImport(id).subscribe({
      next: (l) => {
        this.lote.set(l);
        if (IMPORT_TRANSITORIOS.includes(l.status)) {
          this.timerLote = setTimeout(() => this.acompanhar(id), POLLING_ASSISTENTE_MS);
        } else if (l.status === 'AWAITING_FIX') {
          this.carregarErros(id);
        }
        if (l.status === 'DONE') this.etapa.set(5);
      },
      error: () => this.toast('error', 'Não foi possível atualizar o andamento da importação.'),
    });
  }

  carregarErros(id: string): void {
    this.dados.erros(id, 0, 100).subscribe({
      next: (r) => this.erros.set(r),
      error: () => this.toast('error', 'Não foi possível carregar o relatório de erros.'),
    });
  }

  baixarRelatorio(): void {
    const r = this.erros();
    if (!r) return;
    const linhas = ['linha;campo;mensagem;valor informado', ...r.erros.map((e) =>
      [e.linha, e.campo, e.mensagem, e.valorInformado ?? ''].map((c) => `"${String(c).replace(/"/g, '""')}"`).join(';'))];
    const blob = new Blob([String.fromCharCode(0xFEFF) + linhas.join('\r\n')], { type: 'text/csv;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'erros-importacao.csv';
    a.click();
    URL.revokeObjectURL(url);
  }

  reenviarCorrigido(): void {
    if (this.timerLote) clearTimeout(this.timerLote);
    this.arquivo.set(null);
    this.erroArquivo.set(null);
    this.erros.set(null);
    this.etapa.set(2);
  }

  seguirParaConfirmar(): void {
    this.etapa.set(5);
  }

  recusarPrevia(): void {
    const l = this.lote();
    if (!l || this.ocupado()) return;
    this.ocupado.set(true);
    this.dados.recusarPrevia(l.id).subscribe({
      next: () => {
        this.ocupado.set(false);
        this.aberto = false;
        this.toast('info', 'Pedimos ao nosso time uma nova prévia. Você será avisado.');
        this.carregarLista(true);
      },
      error: (err: HttpErrorResponse) => {
        this.ocupado.set(false);
        this.toast('error', this.mensagemDe(err, 'Não foi possível recusar a prévia agora.'));
      },
    });
  }

  // ── etapa 5: confirmar ──
  importar(): void {
    const l = this.lote();
    if (!l || this.ocupado()) return;
    this.ocupado.set(true);
    this.dados.promover(l.id).subscribe({
      next: (r) => {
        this.lote.set(r);
        this.ocupado.set(false);
        this.acompanharPromocao(r.id);
      },
      error: (err: HttpErrorResponse) => {
        this.ocupado.set(false);
        this.toast('error', this.mensagemDe(err, 'Não foi possível confirmar a importação.'));
        this.irParaRevisao(l);       // pré-requisito perdido: volta à revisão com o estado atual
      },
    });
  }

  private acompanharPromocao(id: string): void {
    this.dados.detalharImport(id).subscribe({
      next: (l) => {
        this.lote.set(l);
        if (IMPORT_TRANSITORIOS.includes(l.status)) {
          this.timerLote = setTimeout(() => this.acompanharPromocao(id), POLLING_ASSISTENTE_MS);
        } else if (l.status === 'DONE') {
          this.carregarLista(true);
        } else if (l.status !== 'FAILED') {
          this.irParaRevisao(l);
        }
      },
      error: () => this.toast('error', 'Não foi possível atualizar o andamento da importação.'),
    });
  }

  podeConfirmar(l: ImportLote | null): boolean {
    return !!l && l.status === 'READY';
  }

  doConcierge(l: ImportLote | null): boolean {
    return !!l && l.trilha === 'CONCIERGE';
  }

  private mensagemDe(e: HttpErrorResponse, padrao: string): string {
    const m = e.error?.message;
    return e.status >= 400 && e.status < 500 && typeof m === 'string' && m ? m : padrao;
  }

  private toast(severity: 'success' | 'info' | 'error', detail: string): void {
    this.msg.add({ severity, summary: severity === 'error' ? 'Erro' : 'Importar dados', detail, life: 5000 });
  }
}
