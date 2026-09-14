import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { FormArray, FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { MessageService } from 'primeng/api';
import { ButtonDirective } from 'primeng/button';
import { Dialog } from 'primeng/dialog';
import { Select } from 'primeng/select';
import { InputNumber } from 'primeng/inputnumber';
import { Textarea } from 'primeng/textarea';
import { Tooltip } from 'primeng/tooltip';
import { Toast } from 'primeng/toast';
import { Breadcrumb } from '../../../../components/breadcrumb/breadcrumb';
import { PrimaryButtonComponent } from '../../../../components/primary-button/primary-button';
import { CotacaoCompraService } from '../cotacao.service';
import {
  CotacaoCompra,
  CotacaoCompraFornecedorResponse,
  CotacaoCompraRespostaRequest,
  STATUS_COTACAO_FORNECEDOR_LABEL,
  STATUS_COTACAO_LABEL,
  StatusCotacaoCompra,
  StatusCotacaoCompraFornecedor,
} from '../cotacao.model';
import { FornecedorService } from '../../../cadastros/fornecedores/fornecedor.service';
import { CondPagamentoService } from '../../../cadastros/cond-pagamento/cond-pagamento.service';
import { DepositoService } from '../../../cadastros/deposito/deposito.service';
import { ProdutoService } from '../../../cadastros/produtos/produto.service';

interface LinhaMapaComparativo {
  produtoId: string;
  nomeProduto: string;
  celulas: { fornecedorId: string; preco: number | null; menorPreco: boolean }[];
}

@Component({
  selector: 'app-cotacao-detalhe',
  imports: [
    CommonModule,
    RouterModule,
    ReactiveFormsModule,
    ButtonDirective,
    Dialog,
    Select,
    InputNumber,
    Textarea,
    Tooltip,
    Toast,
    Breadcrumb,
    PrimaryButtonComponent,
  ],
  templateUrl: './cotacao-detalhe.html',
  styleUrl: './cotacao-detalhe.scss',
})
export class CotacaoDetalhe implements OnInit {
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private cotacaoService = inject(CotacaoCompraService);
  private fornecedorService = inject(FornecedorService);
  private condPagamentoService = inject(CondPagamentoService);
  private depositoService = inject(DepositoService);
  private produtoService = inject(ProdutoService);
  private messageService = inject(MessageService);
  private fb = inject(FormBuilder);

  cotacao = signal<CotacaoCompra | null>(null);
  loading = signal<boolean>(true);
  processando = signal<boolean>(false);

  fornecedoresMap = new Map<string, string>();
  condicoesMap = new Map<string, string>();
  depositosMap = new Map<string, string>();
  produtosMap = new Map<string, string>();
  condicoesOptions: { label: string; value: string }[] = [];

  statusLabel = STATUS_COTACAO_LABEL;
  statusFornecedorLabel = STATUS_COTACAO_FORNECEDOR_LABEL;

  fornecedorSelecionado: CotacaoCompraFornecedorResponse | null = null;

  displayRespostaDialog = false;
  displayDeclinarDialog = false;
  displayEncerrarDialog = false;
  displayCancelarDialog = false;

  respostaForm!: FormGroup;
  declinarForm: FormGroup = this.fb.group({ motivo: [''] });
  encerrarForm: FormGroup = this.fb.group({ cotacaoFornecedorVencedorId: [null, Validators.required] });
  cancelarForm: FormGroup = this.fb.group({ motivo: ['', Validators.required] });

  ngOnInit(): void {
    this.carregarMapas();
    this.carregarCotacao();
  }

  private carregarCotacao(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (!id) {
      this.router.navigate(['/web/compras/cotacoes']);
      return;
    }
    this.loading.set(true);
    this.cotacaoService.buscarPorId(id).subscribe({
      next: (cotacao) => {
        this.cotacao.set(cotacao);
        this.loading.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.handleError(err, 'Erro ao carregar a cotação de compra.');
        this.loading.set(false);
      },
    });
  }

  private carregarMapas(): void {
    this.fornecedorService.getAll(0, 1000).subscribe((res: any) => {
      const content = res._embedded ? Object.values(res._embedded)[0] : res.content || [];
      (content as any[]).forEach((f: any) => this.fornecedoresMap.set(f.id, f.pessoaNomeRazao));
    });
    this.condPagamentoService.listar(0, 1000).subscribe((res: any) => {
      const content = res._embedded ? res._embedded.condicaopagamento : res.content || [];
      content.forEach((c: any) => this.condicoesMap.set(c.id, c.nome));
      this.condicoesOptions = content.map((c: any) => ({ label: c.nome, value: c.id }));
    });
    this.depositoService.listar(0, 1000).subscribe((res) => {
      const content = res.content || [];
      content.forEach((d) => this.depositosMap.set(d.id!, d.nome));
    });
    this.produtoService.getAll(0, 1000).subscribe((res: any) => {
      const content = res._embedded ? Object.values(res._embedded)[0] : res.content || [];
      (content as any[]).forEach((p: any) => this.produtosMap.set(p.id, `${p.sku} - ${p.nome}`));
    });
  }

  nomeFornecedor(id?: string): string {
    return (id && this.fornecedoresMap.get(id)) || '-';
  }
  nomeCondicao(id?: string): string {
    return (id && this.condicoesMap.get(id)) || '-';
  }
  nomeDeposito(id?: string): string {
    return (id && this.depositosMap.get(id)) || '-';
  }
  nomeProduto(id?: string): string {
    return (id && this.produtosMap.get(id)) || '-';
  }

  statusBadgeClass(status?: StatusCotacaoCompra): string {
    switch (status) {
      case 'ABERTA':
        return 'info';
      case 'ENCERRADA':
        return 'ok';
      case 'CANCELADA':
        return 'bad';
      default:
        return 'neutral';
    }
  }

  statusFornecedorBadgeClass(status?: StatusCotacaoCompraFornecedor): string {
    switch (status) {
      case 'RESPONDIDA':
        return 'ok';
      case 'DECLINADA':
        return 'bad';
      default:
        return 'info';
    }
  }

  historicoLabel(statusNovo: string): string {
    return (this.statusLabel as Record<string, string>)[statusNovo] || statusNovo;
  }

  historicoBadgeClass(statusNovo: string): string {
    return this.statusBadgeClass(statusNovo as StatusCotacaoCompra);
  }

  podeResponder(fornecedor: CotacaoCompraFornecedorResponse): boolean {
    return this.cotacao()?.status === 'ABERTA' && fornecedor.status === 'AGUARDANDO';
  }
  podeDeclinar(fornecedor: CotacaoCompraFornecedorResponse): boolean {
    return this.cotacao()?.status === 'ABERTA' && fornecedor.status === 'AGUARDANDO';
  }

  private fornecedoresRespondidos(): CotacaoCompraFornecedorResponse[] {
    return (this.cotacao()?.fornecedores || []).filter((f) => f.status === 'RESPONDIDA');
  }

  podeEncerrar(): boolean {
    return this.cotacao()?.status === 'ABERTA' && this.fornecedoresRespondidos().length > 0;
  }
  podeCancelar(): boolean {
    return this.cotacao()?.status === 'ABERTA';
  }
  temAcaoDisponivel(): boolean {
    return this.podeEncerrar() || this.podeCancelar();
  }

  encerrarOptions(): { label: string; value: string }[] {
    return this.fornecedoresRespondidos().map((f) => ({
      label: `${this.nomeFornecedor(f.fornecedorId)} — ${this.formatarMoeda(f.valorTotalOfertado)}`,
      value: f.id,
    }));
  }

  private formatarMoeda(valor?: number): string {
    return (valor ?? 0).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
  }

  mapaComparativo(): LinhaMapaComparativo[] {
    const cotacao = this.cotacao();
    if (!cotacao) return [];
    const respondidos = this.fornecedoresRespondidos();

    return (cotacao.itens || []).map((item) => {
      const celulas = respondidos.map((f) => {
        const oferta = f.itens.find((i) => i.cotacaoItemId === item.id);
        return { fornecedorId: f.fornecedorId, preco: oferta?.precoUnitario ?? null, menorPreco: false };
      });
      const precos = celulas.map((c) => c.preco).filter((p): p is number => p !== null);
      const menor = precos.length ? Math.min(...precos) : null;
      celulas.forEach((c) => (c.menorPreco = c.preco !== null && c.preco === menor));

      return { produtoId: item.produtoId, nomeProduto: this.nomeProduto(item.produtoId), celulas };
    });
  }

  colunasFornecedoresMapa(): CotacaoCompraFornecedorResponse[] {
    return this.fornecedoresRespondidos();
  }

  abrirResponder(fornecedor: CotacaoCompraFornecedorResponse): void {
    this.fornecedorSelecionado = fornecedor;
    const cotacao = this.cotacao();

    this.respostaForm = this.fb.group({
      condicaoPagamentoId: [null, Validators.required],
      prazoEntregaDias: [null],
      valorFrete: [null],
      observacao: ['', Validators.maxLength(500)],
      itens: this.fb.array([]),
    });

    (cotacao?.itens || []).forEach((item) => {
      this.respostaItensArray.push(
        this.fb.group({
          cotacaoItemId: [item.id],
          produtoId: [item.produtoId],
          precoUnitario: [0, [Validators.required, Validators.min(0)]],
        }),
      );
    });

    this.displayRespostaDialog = true;
  }

  get respostaItensArray(): FormArray {
    return this.respostaForm.get('itens') as FormArray;
  }

  confirmarResposta(): void {
    if (this.respostaForm.invalid) {
      this.respostaForm.markAllAsTouched();
      return;
    }
    const cotacaoId = this.cotacao()?.id;
    const fornecedorId = this.fornecedorSelecionado?.id;
    if (!cotacaoId || !fornecedorId) return;

    const formValue = this.respostaForm.value;
    const payload: CotacaoCompraRespostaRequest = {
      condicaoPagamentoId: formValue.condicaoPagamentoId,
      prazoEntregaDias: formValue.prazoEntregaDias,
      valorFrete: formValue.valorFrete,
      observacao: formValue.observacao || null,
      itens: formValue.itens.map((i: any) => ({
        cotacaoItemId: i.cotacaoItemId,
        precoUnitario: i.precoUnitario,
      })),
    };

    this.processando.set(true);
    this.cotacaoService.registrarResposta(cotacaoId, fornecedorId, payload).subscribe({
      next: (c) => {
        this.displayRespostaDialog = false;
        this.onAcaoSucesso(c, 'Resposta do fornecedor registrada.');
      },
      error: (err) => this.onAcaoErro(err, 'Erro ao registrar a resposta do fornecedor.'),
    });
  }

  abrirDeclinar(fornecedor: CotacaoCompraFornecedorResponse): void {
    this.fornecedorSelecionado = fornecedor;
    this.declinarForm.reset();
    this.displayDeclinarDialog = true;
  }

  confirmarDeclinio(): void {
    const cotacaoId = this.cotacao()?.id;
    const fornecedorId = this.fornecedorSelecionado?.id;
    if (!cotacaoId || !fornecedorId) return;

    this.processando.set(true);
    this.cotacaoService.declinar(cotacaoId, fornecedorId, this.declinarForm.value).subscribe({
      next: (c) => {
        this.displayDeclinarDialog = false;
        this.onAcaoSucesso(c, 'Convite declinado.');
      },
      error: (err) => this.onAcaoErro(err, 'Erro ao declinar o convite.'),
    });
  }

  abrirEncerrar(): void {
    this.encerrarForm.reset();
    this.displayEncerrarDialog = true;
  }

  confirmarEncerramento(): void {
    if (this.encerrarForm.invalid) {
      this.encerrarForm.markAllAsTouched();
      return;
    }
    const id = this.cotacao()?.id;
    if (!id) return;

    this.processando.set(true);
    this.cotacaoService.encerrar(id, this.encerrarForm.value).subscribe({
      next: (c) => {
        this.displayEncerrarDialog = false;
        this.onAcaoSucesso(c, 'Cotação encerrada e pedido de compra gerado em rascunho.');
      },
      error: (err) => this.onAcaoErro(err, 'Erro ao encerrar a cotação.'),
    });
  }

  abrirCancelar(): void {
    this.cancelarForm.reset();
    this.displayCancelarDialog = true;
  }

  confirmarCancelamento(): void {
    if (this.cancelarForm.invalid) {
      this.cancelarForm.markAllAsTouched();
      return;
    }
    const id = this.cotacao()?.id;
    if (!id) return;

    this.processando.set(true);
    this.cotacaoService.cancelar(id, this.cancelarForm.value).subscribe({
      next: (c) => {
        this.displayCancelarDialog = false;
        this.onAcaoSucesso(c, 'Cotação cancelada.');
      },
      error: (err) => this.onAcaoErro(err, 'Erro ao cancelar a cotação.'),
    });
  }

  voltar(): void {
    this.router.navigate(['/web/compras/cotacoes']);
  }

  private onAcaoSucesso(cotacao: CotacaoCompra, mensagem: string): void {
    this.cotacao.set(cotacao);
    this.processando.set(false);
    this.messageService.add({ severity: 'success', summary: 'Sucesso', detail: mensagem });
  }

  private onAcaoErro(err: HttpErrorResponse, defaultSummary: string): void {
    this.processando.set(false);
    this.handleError(err, defaultSummary);
  }

  private handleError(err: HttpErrorResponse, defaultSummary: string): void {
    if (err.error?.message && err.error?.error && err.error?.status) {
      this.messageService.add({
        severity: 'error',
        summary: defaultSummary,
        detail: `[${err.error.status}] ${err.error.error} - ${err.error.message}`,
        life: 5000,
      });
    } else {
      this.messageService.add({
        severity: 'error',
        summary: defaultSummary,
        detail: 'Erro inesperado de comunicação com o servidor.',
        life: 5000,
      });
    }
  }
}
