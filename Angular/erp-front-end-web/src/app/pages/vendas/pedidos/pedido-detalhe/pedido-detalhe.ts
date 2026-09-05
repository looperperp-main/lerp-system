import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { MessageService } from 'primeng/api';
import { ButtonDirective } from 'primeng/button';
import { Dialog } from 'primeng/dialog';
import { Select } from 'primeng/select';
import { InputNumber } from 'primeng/inputnumber';
import { Textarea } from 'primeng/textarea';
import { Toast } from 'primeng/toast';
import { Breadcrumb } from '../../../../components/breadcrumb/breadcrumb';
import { PedidoService } from '../pedido.service';
import { ModalidadeFrete, Pedido, STATUS_PEDIDO_LABEL, StatusPedido } from '../pedido.model';
import { ClientesService } from '../../../cadastros/cliente/clientes.service';
import { VendedorService } from '../../../cadastros/vendedores/vendedor.service';
import { CondPagamentoService } from '../../../cadastros/cond-pagamento/cond-pagamento.service';
import { ProdutoService } from '../../../cadastros/produtos/produto.service';
import { DepositoService } from '../../../cadastros/deposito/deposito.service';
import { TransportadoraService } from '../../../cadastros/transportadoras/transportadora.service';

@Component({
  selector: 'app-pedido-detalhe',
  imports: [
    CommonModule,
    RouterModule,
    ReactiveFormsModule,
    ButtonDirective,
    Dialog,
    Select,
    InputNumber,
    Textarea,
    Toast,
    Breadcrumb,
  ],
  templateUrl: './pedido-detalhe.html',
  styleUrl: './pedido-detalhe.scss',
})
export class PedidoDetalhe implements OnInit {
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private pedidoService = inject(PedidoService);
  private clientesService = inject(ClientesService);
  private vendedorService = inject(VendedorService);
  private condPagamentoService = inject(CondPagamentoService);
  private produtoService = inject(ProdutoService);
  private depositoService = inject(DepositoService);
  private transportadoraService = inject(TransportadoraService);
  private messageService = inject(MessageService);
  private fb = inject(FormBuilder);

  pedido = signal<Pedido | null>(null);
  loading = signal<boolean>(true);
  processando = signal<boolean>(false);

  clientesMap = new Map<string, string>();
  vendedoresMap = new Map<string, string>();
  condicoesMap = new Map<string, string>();
  produtosMap = new Map<string, string>();
  depositosMap = new Map<string, string>();
  transportadorasMap = new Map<string, string>();

  depositosOptions: { label: string; value: string }[] = [];
  transportadorasOptions: { label: string; value: string }[] = [];
  modalidadeFreteOptions: { label: string; value: ModalidadeFrete }[] = [
    { label: 'CIF (frete por conta do emitente)', value: 'CIF' },
    { label: 'FOB (frete por conta do destinatário)', value: 'FOB' },
    { label: 'Sem Frete', value: 'SEM_FRETE' },
  ];

  statusLabel = STATUS_PEDIDO_LABEL;

  displayCancelarDialog = false;
  displayExpedirDialog = false;

  cancelarForm: FormGroup = this.fb.group({
    motivo: ['', Validators.required],
  });

  expedirForm: FormGroup = this.fb.group({
    depositoId: [null, Validators.required],
    transportadoraId: [null],
    valorFrete: [null],
    modalidadeFrete: [null],
  });

  ngOnInit(): void {
    this.carregarMapas();
    this.carregarPedido();
  }

  private carregarPedido(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (!id) {
      this.router.navigate(['/web/vendas/pedidos']);
      return;
    }
    this.loading.set(true);
    this.pedidoService.buscarPorId(id).subscribe({
      next: (pedido) => {
        this.pedido.set(pedido);
        this.loading.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.handleError(err, 'Erro ao carregar o pedido.');
        this.loading.set(false);
      },
    });
  }

  private carregarMapas(): void {
    this.clientesService.getAll(0, 1000).subscribe((res: any) => {
      const content = res._embedded ? res._embedded.clientes : res.content || [];
      content.forEach((c: any) => this.clientesMap.set(c.id, c.nome || c.id));
    });
    this.vendedorService.getAll(0, 1000).subscribe((res: any) => {
      const content = res._embedded ? res._embedded.vendedor : res.content || [];
      content.forEach((v: any) => this.vendedoresMap.set(v.id, v.nome));
    });
    this.condPagamentoService.listar(0, 1000).subscribe((res: any) => {
      const content = res._embedded ? res._embedded.condicaopagamento : res.content || [];
      content.forEach((c: any) => this.condicoesMap.set(c.id, c.nome));
    });
    this.produtoService.getAll(0, 1000).subscribe((res: any) => {
      const content = res._embedded ? Object.values(res._embedded)[0] : res.content || [];
      (content as any[]).forEach((p: any) => this.produtosMap.set(p.id, `${p.sku} - ${p.nome}`));
    });
    this.depositoService.listar(0, 1000).subscribe((res) => {
      const content = res.content || [];
      content.forEach((d) => this.depositosMap.set(d.id!, d.nome));
      this.depositosOptions = content.map((d) => ({ label: d.nome, value: d.id! }));
    });
    this.transportadoraService.getAll(0, 1000).subscribe((res: any) => {
      const content = res._embedded ? Object.values(res._embedded)[0] : res.content || [];
      (content as any[]).forEach((t: any) => this.transportadorasMap.set(t.id, t.pessoaNomeRazao));
      this.transportadorasOptions = (content as any[]).map((t: any) => ({
        label: t.pessoaNomeRazao,
        value: t.id,
      }));
    });
  }

  nomeCliente(id?: string): string {
    return (id && this.clientesMap.get(id)) || '-';
  }
  nomeVendedor(id?: string): string {
    return (id && this.vendedoresMap.get(id)) || '-';
  }
  nomeCondicao(id?: string): string {
    return (id && this.condicoesMap.get(id)) || '-';
  }
  nomeProduto(id?: string): string {
    return (id && this.produtosMap.get(id)) || '-';
  }
  nomeDeposito(id?: string): string {
    return (id && this.depositosMap.get(id)) || '-';
  }
  nomeTransportadora(id?: string): string {
    return (id && this.transportadorasMap.get(id)) || '-';
  }

  statusBadgeClass(status?: StatusPedido): string {
    switch (status) {
      case 'CONFIRMADO':
      case 'EXPEDIDO':
        return 'info';
      case 'FATURADO':
        return 'ok';
      case 'CANCELADO':
      case 'BLOQUEADO_CREDITO':
        return 'bad';
      default:
        return 'neutral';
    }
  }

  todosItensSaoServico(): boolean {
    const itens = this.pedido()?.itens || [];
    return itens.length > 0 && itens.every((i) => i.tipoItem === 'SERVICO');
  }

  podeConfirmar(): boolean {
    return this.pedido()?.status === 'ORCAMENTO';
  }
  podeRecalcularPrecos(): boolean {
    return this.pedido()?.status === 'ORCAMENTO';
  }
  podeReabrir(): boolean {
    return this.pedido()?.status === 'CONFIRMADO';
  }
  podeExpedir(): boolean {
    return this.pedido()?.status === 'CONFIRMADO' && !this.todosItensSaoServico();
  }
  podeFaturar(): boolean {
    const status = this.pedido()?.status;
    return status === 'EXPEDIDO' || (status === 'CONFIRMADO' && this.todosItensSaoServico());
  }
  valorImpostos(): number {
    const p = this.pedido();
    if (!p?.valorTotalNf) return 0;
    return p.valorTotalNf - (p.valorTotal || 0);
  }

  temAcaoDisponivel(): boolean {
    return (
      this.podeConfirmar() ||
      this.podeRecalcularPrecos() ||
      this.podeExpedir() ||
      this.podeFaturar() ||
      this.podeReabrir() ||
      this.podeCancelar()
    );
  }

  podeCancelar(): boolean {
    const status = this.pedido()?.status;
    return !!status && status !== 'FATURADO' && status !== 'CANCELADO';
  }

  confirmar(): void {
    const id = this.pedido()?.id;
    if (!id) return;
    this.processando.set(true);
    this.pedidoService.confirmar(id).subscribe({
      next: (p) => this.onAcaoSucesso(p, 'Pedido confirmado com sucesso.'),
      error: (err) => this.onAcaoErro(err, 'Erro ao confirmar o pedido.'),
    });
  }

  recalcularPrecos(): void {
    const id = this.pedido()?.id;
    if (!id) return;
    this.processando.set(true);
    this.pedidoService.recalcularPrecos(id).subscribe({
      next: (p) => this.onAcaoSucesso(p, 'Preços recalculados com sucesso.'),
      error: (err) => this.onAcaoErro(err, 'Erro ao recalcular preços.'),
    });
  }

  reabrir(): void {
    const id = this.pedido()?.id;
    if (!id) return;
    this.processando.set(true);
    this.pedidoService.reabrir(id).subscribe({
      next: (p) => this.onAcaoSucesso(p, 'Pedido reaberto para orçamento.'),
      error: (err) => this.onAcaoErro(err, 'Erro ao reabrir o pedido.'),
    });
  }

  faturar(): void {
    const id = this.pedido()?.id;
    if (!id) return;
    this.processando.set(true);
    this.pedidoService.faturar(id).subscribe({
      next: (p) => this.onAcaoSucesso(p, 'Pedido faturado com sucesso.'),
      error: (err) => this.onAcaoErro(err, 'Erro ao faturar o pedido.'),
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

  abrirExpedir(): void {
    this.expedirForm.reset({
      depositoId: null,
      transportadoraId: this.pedido()?.transportadoraId || null,
      valorFrete: this.pedido()?.valorFrete || null,
      modalidadeFrete: this.pedido()?.modalidadeFrete || null,
    });
    this.displayExpedirDialog = true;
  }

  confirmarExpedicao(): void {
    if (this.expedirForm.invalid) {
      this.expedirForm.markAllAsTouched();
      return;
    }
    const id = this.pedido()?.id;
    if (!id) return;
    this.processando.set(true);
    this.pedidoService.expedir(id, this.expedirForm.value).subscribe({
      next: (p) => {
        this.displayExpedirDialog = false;
        this.onAcaoSucesso(p, 'Pedido expedido com sucesso.');
      },
      error: (err) => this.onAcaoErro(err, 'Erro ao expedir o pedido.'),
    });
  }

  voltar(): void {
    this.router.navigate(['/web/vendas/pedidos']);
  }

  private onAcaoSucesso(pedido: Pedido, mensagem: string): void {
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
