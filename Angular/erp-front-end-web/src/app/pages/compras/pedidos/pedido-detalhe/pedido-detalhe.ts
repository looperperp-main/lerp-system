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
import { InputText } from 'primeng/inputtext';
import { DatePicker } from 'primeng/datepicker';
import { Textarea } from 'primeng/textarea';
import { Tooltip } from 'primeng/tooltip';
import { Toast } from 'primeng/toast';
import { Breadcrumb } from '../../../../components/breadcrumb/breadcrumb';
import { PrimaryButtonComponent } from '../../../../components/primary-button/primary-button';
import { PedidoCompraService } from '../pedido-compra.service';
import {
  PedidoCompra,
  PedidoCompraItemResponse,
  STATUS_PEDIDO_COMPRA_LABEL,
  StatusPedidoCompra,
} from '../pedido-compra.model';
import { RecebimentoMercadoriaService } from '../../recebimentos/recebimento.service';
import {
  RecebimentoMercadoria,
  RecebimentoMercadoriaRequest,
  STATUS_RECEBIMENTO_LABEL,
  StatusRecebimentoMercadoria,
  TipoDocumentoFiscal,
} from '../../recebimentos/recebimento.model';
import { FornecedorService } from '../../../cadastros/fornecedores/fornecedor.service';
import { CondPagamentoService } from '../../../cadastros/cond-pagamento/cond-pagamento.service';
import { DepositoService } from '../../../cadastros/deposito/deposito.service';
import { ProdutoService } from '../../../cadastros/produtos/produto.service';

@Component({
  selector: 'app-pedido-compra-detalhe',
  imports: [
    CommonModule,
    RouterModule,
    ReactiveFormsModule,
    ButtonDirective,
    Dialog,
    Select,
    InputNumber,
    InputText,
    DatePicker,
    Textarea,
    Tooltip,
    Toast,
    Breadcrumb,
    PrimaryButtonComponent,
  ],
  templateUrl: './pedido-detalhe.html',
  styleUrl: './pedido-detalhe.scss',
})
export class PedidoDetalhe implements OnInit {
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private pedidoService = inject(PedidoCompraService);
  private recebimentoService = inject(RecebimentoMercadoriaService);
  private fornecedorService = inject(FornecedorService);
  private condPagamentoService = inject(CondPagamentoService);
  private depositoService = inject(DepositoService);
  private produtoService = inject(ProdutoService);
  private messageService = inject(MessageService);
  private fb = inject(FormBuilder);

  pedido = signal<PedidoCompra | null>(null);
  recebimentos = signal<RecebimentoMercadoria[]>([]);
  loading = signal<boolean>(true);
  processando = signal<boolean>(false);

  fornecedoresMap = new Map<string, string>();
  condicoesMap = new Map<string, string>();
  depositosMap = new Map<string, string>();
  produtosMap = new Map<string, string>();
  depositosOptions: { label: string; value: string }[] = [];
  condicoesOptions: { label: string; value: string }[] = [];

  statusLabel = STATUS_PEDIDO_COMPRA_LABEL;
  statusRecebimentoLabel = STATUS_RECEBIMENTO_LABEL;

  tipoDocumentoFiscalOptions: { label: string; value: TipoDocumentoFiscal }[] = [
    { label: 'NF-e', value: 'NFE' },
    { label: 'NFS-e', value: 'NFSE' },
  ];

  displayReprovarDialog = false;
  displayCancelarDialog = false;
  displayEncerrarSaldoDialog = false;
  displayRecebimentoDialog = false;

  reprovarForm: FormGroup = this.fb.group({ motivo: ['', Validators.required] });
  cancelarForm: FormGroup = this.fb.group({ motivo: ['', Validators.required] });
  encerrarSaldoForm: FormGroup = this.fb.group({ motivo: ['', Validators.required] });

  recebimentoForm!: FormGroup;

  ngOnInit(): void {
    this.carregarMapas();
    this.carregarPedido();
  }

  private carregarPedido(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (!id) {
      this.router.navigate(['/web/compras/pedidos']);
      return;
    }
    this.loading.set(true);
    this.pedidoService.buscarPorId(id).subscribe({
      next: (pedido) => {
        this.pedido.set(pedido);
        this.loading.set(false);
        this.carregarRecebimentos(id);
      },
      error: (err: HttpErrorResponse) => {
        this.handleError(err, 'Erro ao carregar o pedido de compra.');
        this.loading.set(false);
      },
    });
  }

  private carregarRecebimentos(pedidoId: string): void {
    this.recebimentoService.listar({ pedidoId }, 0, 100).subscribe({
      next: (res: any) => {
        const content = res._embedded ? Object.values(res._embedded)[0] : res.content || [];
        this.recebimentos.set(content as RecebimentoMercadoria[]);
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
      this.depositosOptions = content.map((d) => ({ label: d.nome, value: d.id! }));
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

  statusBadgeClass(status?: StatusPedidoCompra): string {
    switch (status) {
      case 'PENDENTE_APROVACAO':
      case 'ENVIADO':
      case 'RECEBIDO_PARCIAL':
        return 'info';
      case 'APROVADO':
      case 'RECEBIDO_TOTAL':
      case 'ENCERRADO':
        return 'ok';
      case 'REPROVADO':
      case 'CANCELADO':
        return 'bad';
      default:
        return 'neutral';
    }
  }

  historicoLabel(statusNovo: string): string {
    return (this.statusLabel as Record<string, string>)[statusNovo] || statusNovo;
  }

  historicoBadgeClass(statusNovo: string): string {
    return this.statusBadgeClass(statusNovo as StatusPedidoCompra);
  }

  statusRecebimentoBadgeClass(status?: StatusRecebimentoMercadoria): string {
    switch (status) {
      case 'CONFIRMADO':
        return 'info';
      case 'FATURADO':
        return 'ok';
      case 'CANCELADO':
        return 'bad';
      default:
        return 'neutral';
    }
  }

  quantidadePendente(item: PedidoCompraItemResponse): number {
    return Math.max(0, (item.quantidade || 0) - (item.quantidadeRecebida || 0));
  }

  temItensPendentes(): boolean {
    return (this.pedido()?.itens || []).some((i) => this.quantidadePendente(i) > 0);
  }

  podeEnviarAprovacao(): boolean {
    return this.pedido()?.status === 'RASCUNHO';
  }
  podeAprovar(): boolean {
    return this.pedido()?.status === 'PENDENTE_APROVACAO';
  }
  podeReprovar(): boolean {
    return this.pedido()?.status === 'PENDENTE_APROVACAO';
  }
  podeReabrir(): boolean {
    return this.pedido()?.status === 'REPROVADO';
  }
  podeEnviar(): boolean {
    return this.pedido()?.status === 'APROVADO';
  }
  podeCancelar(): boolean {
    const status = this.pedido()?.status;
    return (
      status === 'RASCUNHO' ||
      status === 'PENDENTE_APROVACAO' ||
      status === 'APROVADO' ||
      status === 'ENVIADO'
    );
  }
  podeEncerrarSaldo(): boolean {
    const status = this.pedido()?.status;
    return status === 'RECEBIDO_PARCIAL' || status === 'RECEBIDO_TOTAL';
  }
  podeRegistrarRecebimento(): boolean {
    const status = this.pedido()?.status;
    return (status === 'ENVIADO' || status === 'RECEBIDO_PARCIAL') && this.temItensPendentes();
  }

  temAcaoDisponivel(): boolean {
    return (
      this.podeEnviarAprovacao() ||
      this.podeAprovar() ||
      this.podeReprovar() ||
      this.podeReabrir() ||
      this.podeEnviar() ||
      this.podeCancelar() ||
      this.podeEncerrarSaldo()
    );
  }

  enviarParaAprovacao(): void {
    const id = this.pedido()?.id;
    if (!id) return;
    this.processando.set(true);
    this.pedidoService.enviarParaAprovacao(id).subscribe({
      next: (p) => this.onAcaoSucesso(p, 'Pedido enviado para aprovação.'),
      error: (err) => this.onAcaoErro(err, 'Erro ao enviar o pedido para aprovação.'),
    });
  }

  aprovar(): void {
    const id = this.pedido()?.id;
    if (!id) return;
    this.processando.set(true);
    this.pedidoService.aprovar(id).subscribe({
      next: (p) => this.onAcaoSucesso(p, 'Pedido aprovado.'),
      error: (err) => this.onAcaoErro(err, 'Erro ao aprovar o pedido.'),
    });
  }

  abrirReprovar(): void {
    this.reprovarForm.reset();
    this.displayReprovarDialog = true;
  }

  confirmarReprovacao(): void {
    if (this.reprovarForm.invalid) {
      this.reprovarForm.markAllAsTouched();
      return;
    }
    const id = this.pedido()?.id;
    if (!id) return;
    this.processando.set(true);
    this.pedidoService.reprovar(id, this.reprovarForm.value).subscribe({
      next: (p) => {
        this.displayReprovarDialog = false;
        this.onAcaoSucesso(p, 'Pedido reprovado.');
      },
      error: (err) => this.onAcaoErro(err, 'Erro ao reprovar o pedido.'),
    });
  }

  reabrir(): void {
    const id = this.pedido()?.id;
    if (!id) return;
    this.processando.set(true);
    this.pedidoService.reabrir(id).subscribe({
      next: (p) => this.onAcaoSucesso(p, 'Pedido reaberto para rascunho.'),
      error: (err) => this.onAcaoErro(err, 'Erro ao reabrir o pedido.'),
    });
  }

  enviar(): void {
    const id = this.pedido()?.id;
    if (!id) return;
    this.processando.set(true);
    this.pedidoService.enviar(id).subscribe({
      next: (p) => this.onAcaoSucesso(p, 'Pedido marcado como enviado ao fornecedor.'),
      error: (err) => this.onAcaoErro(err, 'Erro ao marcar o pedido como enviado.'),
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
    const id = this.pedido()?.id;
    if (!id) return;
    this.processando.set(true);
    this.pedidoService.cancelar(id, this.cancelarForm.value).subscribe({
      next: (p) => {
        this.displayCancelarDialog = false;
        this.onAcaoSucesso(p, 'Pedido cancelado.');
      },
      error: (err) => this.onAcaoErro(err, 'Erro ao cancelar o pedido.'),
    });
  }

  abrirEncerrarSaldo(): void {
    this.encerrarSaldoForm.reset();
    this.displayEncerrarSaldoDialog = true;
  }

  confirmarEncerramentoSaldo(): void {
    if (this.encerrarSaldoForm.invalid) {
      this.encerrarSaldoForm.markAllAsTouched();
      return;
    }
    const id = this.pedido()?.id;
    if (!id) return;
    this.processando.set(true);
    this.pedidoService.encerrarSaldo(id, this.encerrarSaldoForm.value).subscribe({
      next: (p) => {
        this.displayEncerrarSaldoDialog = false;
        this.onAcaoSucesso(p, 'Saldo do pedido encerrado.');
      },
      error: (err) => this.onAcaoErro(err, 'Erro ao encerrar o saldo do pedido.'),
    });
  }

  get recebimentoItensArray(): FormArray {
    return this.recebimentoForm.get('itens') as FormArray;
  }

  abrirRegistrarRecebimento(): void {
    const pedido = this.pedido();
    if (!pedido) return;

    this.recebimentoForm = this.fb.group({
      dataRecebimento: [new Date(), Validators.required],
      tipoDocumentoFiscal: ['NFE', Validators.required],
      nfeNumero: ['', Validators.required],
      nfeSerie: [''],
      nfeChave: [''],
      nfseCodigoVerificacao: [''],
      nfeDataEmissao: [new Date(), Validators.required],
      valorTotalNf: [0, [Validators.required, Validators.min(0)]],
      condicaoPagamentoId: [pedido.condicaoPagamentoId || null],
      observacao: [''],
      itens: this.fb.array([]),
    });

    (pedido.itens || [])
      .filter((item) => this.quantidadePendente(item) > 0)
      .forEach((item) => {
        this.recebimentoItensArray.push(
          this.fb.group({
            pedidoItemId: [item.id],
            produtoId: [item.produtoId],
            quantidadePendente: [this.quantidadePendente(item)],
            quantidade: [
              this.quantidadePendente(item),
              [
                Validators.required,
                Validators.min(0.0001),
                Validators.max(this.quantidadePendente(item)),
              ],
            ],
            precoUnitarioNf: [item.precoUnitario, [Validators.required, Validators.min(0)]],
          }),
        );
      });

    this.displayRecebimentoDialog = true;
  }

  confirmarRegistrarRecebimento(): void {
    if (this.recebimentoForm.invalid) {
      this.recebimentoForm.markAllAsTouched();
      return;
    }
    const pedido = this.pedido();
    if (!pedido?.id) return;

    const formValue = this.recebimentoForm.value;
    const payload: RecebimentoMercadoriaRequest = {
      depositoId: pedido.depositoId,
      dataRecebimento: new Date(formValue.dataRecebimento).toISOString().split('T')[0],
      tipoDocumentoFiscal: formValue.tipoDocumentoFiscal,
      nfeNumero: formValue.nfeNumero,
      nfeSerie: formValue.nfeSerie || null,
      nfeChave: formValue.nfeChave || null,
      nfseCodigoVerificacao: formValue.nfseCodigoVerificacao || null,
      nfeDataEmissao: new Date(formValue.nfeDataEmissao).toISOString().split('T')[0],
      valorTotalNf: formValue.valorTotalNf,
      condicaoPagamentoId: formValue.condicaoPagamentoId,
      observacao: formValue.observacao || null,
      itens: formValue.itens.map((i: any) => ({
        pedidoItemId: i.pedidoItemId,
        quantidade: i.quantidade,
        precoUnitarioNf: i.precoUnitarioNf,
      })),
    };

    this.processando.set(true);
    this.recebimentoService.criar(pedido.id, payload).subscribe({
      next: () => {
        this.displayRecebimentoDialog = false;
        this.processando.set(false);
        this.messageService.add({
          severity: 'success',
          summary: 'Sucesso',
          detail: 'Recebimento registrado com sucesso.',
        });
        this.carregarPedido();
      },
      error: (err: HttpErrorResponse) => {
        this.processando.set(false);
        this.handleError(err, 'Erro ao registrar o recebimento.');
      },
    });
  }

  confirmarRecebimento(recebimento: RecebimentoMercadoria): void {
    if (!recebimento.id) return;
    this.processando.set(true);
    this.recebimentoService.confirmar(recebimento.id).subscribe({
      next: () => {
        this.processando.set(false);
        this.messageService.add({
          severity: 'success',
          summary: 'Sucesso',
          detail: 'Recebimento confirmado.',
        });
        this.carregarPedido();
      },
      error: (err: HttpErrorResponse) => {
        this.processando.set(false);
        this.handleError(err, 'Erro ao confirmar o recebimento.');
      },
    });
  }

  cancelarRecebimento(recebimento: RecebimentoMercadoria): void {
    if (!recebimento.id) return;
    this.processando.set(true);
    this.recebimentoService.cancelar(recebimento.id, {}).subscribe({
      next: () => {
        this.processando.set(false);
        this.messageService.add({
          severity: 'success',
          summary: 'Sucesso',
          detail: 'Recebimento cancelado.',
        });
        this.carregarPedido();
      },
      error: (err: HttpErrorResponse) => {
        this.processando.set(false);
        this.handleError(err, 'Erro ao cancelar o recebimento.');
      },
    });
  }

  faturarRecebimento(recebimento: RecebimentoMercadoria): void {
    if (!recebimento.id) return;
    this.processando.set(true);
    this.recebimentoService.faturar(recebimento.id).subscribe({
      next: () => {
        this.processando.set(false);
        this.messageService.add({
          severity: 'success',
          summary: 'Sucesso',
          detail: 'Recebimento faturado.',
        });
        this.carregarPedido();
      },
      error: (err: HttpErrorResponse) => {
        this.processando.set(false);
        this.handleError(err, 'Erro ao faturar o recebimento.');
      },
    });
  }

  voltar(): void {
    this.router.navigate(['/web/compras/pedidos']);
  }

  private onAcaoSucesso(pedido: PedidoCompra, mensagem: string): void {
    this.pedido.set(pedido);
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
