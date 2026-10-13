import { Component, computed, inject, OnDestroy, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { MessageService } from 'primeng/api';
import { ButtonDirective } from 'primeng/button';
import { Select } from 'primeng/select';
import { DatePicker } from 'primeng/datepicker';
import { Dialog } from 'primeng/dialog';
import { Breadcrumb } from '../../../components/breadcrumb/breadcrumb';
import { DadosService } from '../dados.service';
import {
  CatalogoGrupo,
  EXPORT_MOTIVOS,
  EXPORT_TRANSITORIOS,
  ExportLote,
} from '../dados.model';

const STATUS: Record<string, { rotulo: string; cor: string }> = {
  REQUESTED: { rotulo: 'Na fila', cor: 'cinza' },
  PROCESSING: { rotulo: 'Gerando…', cor: 'azul' },
  READY: { rotulo: 'Pronto', cor: 'verde' },
  DOWNLOADED: { rotulo: 'Baixado', cor: 'verde' },
  FAILED: { rotulo: 'Falhou', cor: 'vermelho' },
  EXPIRED: { rotulo: 'Expirado', cor: 'cinza' },
};

const EVENTO: Record<string, string> = {
  REQUESTED: 'Solicitado',
  GENERATED: 'Arquivos gerados',
  DOWNLOADED: 'Baixado',
  EXPIRED: 'Expirado e apagado',
};

const MAX_TENTATIVAS = 3;
const POLLING_MS = 5000;

/** Exportar Dados (spec exportacao-importacao-front §3): solicita, acompanha por polling e baixa. */
@Component({
  selector: 'app-exportar',
  imports: [CommonModule, FormsModule, ButtonDirective, Select, DatePicker, Dialog, Breadcrumb],
  templateUrl: './exportar.html',
  styleUrl: '../dados.scss',
})
export class Exportar implements OnInit, OnDestroy {
  private dados = inject(DadosService);
  private msg = inject(MessageService);

  catalogo = signal<CatalogoGrupo[]>([]);
  catalogoErro = signal(false);
  selecionadas = signal<Set<string>>(new Set());
  periodoInicial: Date | null = null;
  periodoFinal: Date | null = null;
  formato: 'XLSX' | 'CSV' = 'XLSX';
  motivo: string | null = null;
  motivos = EXPORT_MOTIVOS;
  formatos = [
    { label: 'Excel (.xlsx)', value: 'XLSX' },
    { label: 'CSV (.csv)', value: 'CSV' },
  ];
  enviando = signal(false);

  lotes = signal<ExportLote[]>([]);
  carregando = signal(true);
  erroLista = signal(false);
  destaque = signal<string | null>(null);
  baixando = signal<string | null>(null);

  detalhe = signal<ExportLote | null>(null);
  detalheAberto = false;
  detalheCarregando = signal(false);

  private timer: ReturnType<typeof setTimeout> | null = null;

  totalSelecionadas = computed(() => this.selecionadas().size);
  /** O período só vale para tabelas transacionais; sem nenhuma delas marcada o filtro some. */
  temTabelaDePeriodo = computed(() =>
    this.catalogo().some((g) => g.tabelas.some((t) => t.filtraPorPeriodo && this.selecionadas().has(t.id))),
  );

  ngOnInit(): void {
    this.carregarCatalogo();
    this.carregarLista();
  }

  ngOnDestroy(): void {
    if (this.timer) clearTimeout(this.timer);
  }

  carregarCatalogo(): void {
    this.catalogoErro.set(false);
    this.dados.catalogo().subscribe({
      next: (c) => {
        this.catalogo.set(c);
        this.selecionadas.set(new Set(c.flatMap((g) => g.tabelas.map((t) => t.id))));   // tudo marcado por padrão
      },
      error: () => this.catalogoErro.set(true),
    });
  }

  carregarLista(silencioso = false): void {
    if (!silencioso) this.carregando.set(true);
    this.erroLista.set(false);
    this.dados.listarExports(0, 20).subscribe({
      next: (p) => {
        this.lotes.set(p.itens);
        this.carregando.set(false);
        this.agendarPolling();
      },
      error: () => {
        this.carregando.set(false);
        if (!silencioso) this.erroLista.set(true);
        this.agendarPolling();
      },
    });
  }

  /** Atualiza a cada 5 s enquanto houver export em andamento; para sozinho quando não houver mais nenhum. */
  private agendarPolling(): void {
    if (this.timer) clearTimeout(this.timer);
    if (this.lotes().some((l) => EXPORT_TRANSITORIOS.includes(l.status))) {
      this.timer = setTimeout(() => this.carregarLista(true), POLLING_MS);
    }
  }

  // ── seleção ──
  grupoMarcado(g: CatalogoGrupo): boolean {
    return g.tabelas.every((t) => this.selecionadas().has(t.id));
  }

  grupoParcial(g: CatalogoGrupo): boolean {
    return !this.grupoMarcado(g) && g.tabelas.some((t) => this.selecionadas().has(t.id));
  }

  alternarGrupo(g: CatalogoGrupo): void {
    const novo = new Set(this.selecionadas());
    const marcar = !this.grupoMarcado(g);
    g.tabelas.forEach((t) => (marcar ? novo.add(t.id) : novo.delete(t.id)));
    this.selecionadas.set(novo);
  }

  alternarTabela(id: string): void {
    const novo = new Set(this.selecionadas());
    if (!novo.delete(id)) novo.add(id);
    this.selecionadas.set(novo);
  }

  // ── solicitar ──
  solicitar(): void {
    if (this.enviando() || this.totalSelecionadas() === 0) return;
    this.enviando.set(true);
    const usaPeriodo = this.temTabelaDePeriodo();
    this.dados
      .solicitarExport({
        tabelas: [...this.selecionadas()],
        dataInicial: usaPeriodo ? this.iso(this.periodoInicial) : null,
        dataFinal: usaPeriodo ? this.iso(this.periodoFinal) : null,
        formato: this.formato,
        motivo: this.motivo,
      })
      .subscribe({
        next: (res) => {
          this.enviando.set(false);
          const lote = res.body as ExportLote;
          if (lote.reaproveitado) {
            this.destaque.set(lote.id);
            this.toast('info', 'Você já tem uma exportação pronta com esses dados.');
          } else {
            this.toast('success', 'Exportação solicitada. Avisaremos por e-mail quando estiver pronta.');
          }
          this.carregarLista(true);
        },
        error: (e: HttpErrorResponse) => {
          this.enviando.set(false);
          this.toast('error', this.mensagemDe(e, 'Não foi possível solicitar a exportação. Tente novamente.'));
        },
      });
  }

  /** Reaproveita o escopo de um export que falhou ou expirou. */
  solicitarNovamente(l: ExportLote): void {
    this.enviando.set(true);
    this.dados
      .solicitarExport({
        tabelas: l.tabelas,
        dataInicial: l.dataInicial,
        dataFinal: l.dataFinal,
        formato: l.formato === 'CSV' ? 'CSV' : 'XLSX',
        motivo: l.motivo,
      })
      .subscribe({
        next: () => {
          this.enviando.set(false);
          this.toast('success', 'Exportação solicitada. Avisaremos por e-mail quando estiver pronta.');
          this.carregarLista(true);
        },
        error: (e: HttpErrorResponse) => {
          this.enviando.set(false);
          this.toast('error', this.mensagemDe(e, 'Não foi possível solicitar a exportação. Tente novamente.'));
        },
      });
  }

  baixar(l: ExportLote): void {
    this.baixando.set(l.id);
    this.dados.baixarExport(l.id).subscribe({
      next: (res) => {
        this.dados.salvar(res, `exportacao-${l.id}`);
        this.baixando.set(null);
        this.carregarLista(true);
      },
      error: (e: HttpErrorResponse) => {
        this.baixando.set(null);
        // O corpo do erro de um download vem como Blob; a tela traduz sem depender dele.
        const msg =
          e.status === 404 || e.status === 410
            ? 'Este arquivo não está mais disponível. Solicite uma nova exportação.'
            : 'Não foi possível baixar o arquivo agora. Tente novamente.';
        this.toast('error', msg);
        this.carregarLista(true);
      },
    });
  }

  abrirDetalhe(l: ExportLote): void {
    this.detalhe.set(l);
    this.detalheAberto = true;
    this.detalheCarregando.set(true);
    this.dados.detalharExport(l.id).subscribe({
      next: (d) => {
        this.detalhe.set(d);
        this.detalheCarregando.set(false);
      },
      error: () => {
        this.detalheCarregando.set(false);
        this.toast('error', 'Não foi possível carregar os detalhes.');
      },
    });
  }

  // ── apresentação ──
  chip(l: ExportLote): { rotulo: string; cor: string } {
    if (l.status === 'FAILED' && l.tentativas < MAX_TENTATIVAS) return { rotulo: 'Tentando novamente', cor: 'ambar' };
    return STATUS[l.status] ?? { rotulo: l.status, cor: 'cinza' };
  }

  podeBaixar(l: ExportLote): boolean {
    return l.status === 'READY' || l.status === 'DOWNLOADED';
  }

  podeSolicitarNovamente(l: ExportLote): boolean {
    return l.status === 'EXPIRED' || (l.status === 'FAILED' && l.tentativas >= MAX_TENTATIVAS);
  }

  falhaDefinitiva(l: ExportLote): boolean {
    return l.status === 'FAILED' && l.tentativas >= MAX_TENTATIVAS;
  }

  /** Destaque âmbar quando faltar menos de 24h para expirar. */
  expiraEmBreve(l: ExportLote): boolean {
    return !!l.expiraEm && this.podeBaixar(l) && new Date(l.expiraEm).getTime() - Date.now() < 24 * 3600 * 1000;
  }

  resumoEscopo(l: ExportLote): string {
    return `${l.tabelas.length} ${l.tabelas.length === 1 ? 'tabela' : 'tabelas'}`;
  }

  rotuloTabela(id: string): string {
    for (const g of this.catalogo()) {
      const t = g.tabelas.find((x) => x.id === id);
      if (t) return t.rotulo;
    }
    return id;
  }

  eventoRotulo(acao: string): string {
    return EVENTO[acao] ?? acao;
  }

  idCurto(id: string): string {
    return id.substring(0, 8);
  }

  copiar(id: string): void {
    navigator.clipboard?.writeText(id).then(() => this.toast('info', 'ID copiado.'));
  }

  private iso(d: Date | null): string | null {
    if (!d) return null;
    const mes = String(d.getMonth() + 1).padStart(2, '0');
    const dia = String(d.getDate()).padStart(2, '0');
    return `${d.getFullYear()}-${mes}-${dia}`;
  }

  private mensagemDe(e: HttpErrorResponse, padrao: string): string {
    const m = e.error?.message;
    return e.status >= 400 && e.status < 500 && typeof m === 'string' && m ? m : padrao;
  }

  private toast(severity: 'success' | 'info' | 'error', detail: string): void {
    this.msg.add({ severity, summary: severity === 'error' ? 'Erro' : 'Exportar dados', detail, life: 5000 });
  }
}
