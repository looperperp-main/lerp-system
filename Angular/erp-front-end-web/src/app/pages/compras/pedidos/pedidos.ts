import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterModule } from '@angular/router';
import { TableModule } from 'primeng/table';
import { Dialog } from 'primeng/dialog';
import { PrimeTemplate, MessageService } from 'primeng/api';
import { ButtonDirective } from 'primeng/button';
import { Ripple } from 'primeng/ripple';
import { Tooltip } from 'primeng/tooltip';
import { Toast } from 'primeng/toast';
import { Select } from 'primeng/select';
import { InputText } from 'primeng/inputtext';
import { ReactiveFormsModule, FormBuilder, FormGroup } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { PrimaryButtonComponent } from '../../../components/primary-button/primary-button';
import { Breadcrumb } from '../../../components/breadcrumb/breadcrumb';
import { PedidoCompraService } from './pedido-compra.service';
import { PedidoCompra, STATUS_PEDIDO_COMPRA_LABEL, StatusPedidoCompra } from './pedido-compra.model';
import { PedidoForm } from './pedido-form/pedido-form';
import { FornecedorService } from '../../cadastros/fornecedores/fornecedor.service';

@Component({
  selector: 'app-pedidos-compra',
  imports: [
    CommonModule,
    RouterModule,
    TableModule,
    Dialog,
    PrimeTemplate,
    ButtonDirective,
    Ripple,
    Tooltip,
    Toast,
    Select,
    InputText,
    ReactiveFormsModule,
    PrimaryButtonComponent,
    Breadcrumb,
    PedidoForm,
  ],
  templateUrl: './pedidos.html',
  styleUrl: './pedidos.scss',
})
export class Pedidos implements OnInit {
  private pedidoService = inject(PedidoCompraService);
  private fornecedorService = inject(FornecedorService);
  private messageService = inject(MessageService);
  private fb = inject(FormBuilder);
  private router = inject(Router);

  pedidos = signal<PedidoCompra[]>([]);
  loading = signal<boolean>(true);
  totalRecords = signal<number>(0);
  page = 0;
  size = 10;

  fornecedoresMap = new Map<string, string>();

  displayForm = false;
  selectedPedido: PedidoCompra | null = null;

  statusLabel = STATUS_PEDIDO_COMPRA_LABEL;
  statusOptions = [
    { label: 'Todos', value: null },
    ...Object.entries(STATUS_PEDIDO_COMPRA_LABEL).map(([value, label]) => ({ label, value })),
  ];

  filtroForm: FormGroup = this.fb.group({
    status: [null],
    fornecedorId: [null],
  });

  ngOnInit(): void {
    this.carregarMapas();
  }

  private carregarMapas(): void {
    this.fornecedorService.getAll(0, 1000).subscribe({
      next: (res: any) => {
        const content = res._embedded ? Object.values(res._embedded)[0] : res.content || [];
        (content as any[]).forEach((f: any) => this.fornecedoresMap.set(f.id, f.pessoaNomeRazao));
      },
    });
  }

  nomeFornecedor(id?: string): string {
    return (id && this.fornecedoresMap.get(id)) || '-';
  }

  loadPedidos(event?: any): void {
    setTimeout(() => this.loading.set(true));

    if (event) {
      this.page = event.first / event.rows;
      this.size = event.rows;
    }

    const filtro = this.filtroForm.value;
    this.pedidoService.listar(filtro, this.page, this.size).subscribe({
      next: (res) => {
        const content = res._embedded ? res._embedded.pedidos : res.content || [];
        this.pedidos.set(content);
        this.totalRecords.set(res.page?.totalElements ?? res.totalElements ?? 0);
        this.loading.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.handleError(err, 'Erro ao carregar pedidos de compra.');
        this.loading.set(false);
      },
    });
  }

  aplicarFiltro(): void {
    this.loadPedidos({ first: 0, rows: this.size });
  }

  limparFiltro(): void {
    this.filtroForm.reset();
    this.loadPedidos({ first: 0, rows: this.size });
  }

  openNew(): void {
    this.selectedPedido = null;
    this.displayForm = true;
  }

  editarPedido(pedido: PedidoCompra): void {
    this.selectedPedido = pedido;
    this.displayForm = true;

    this.pedidoService.buscarPorId(pedido.id!).subscribe({
      next: (p) => {
        this.selectedPedido = p;
      },
      error: (err: HttpErrorResponse) => {
        this.displayForm = false;
        this.handleError(err, 'Erro ao carregar o pedido para edição.');
      },
    });
  }

  verPedido(pedido: PedidoCompra): void {
    this.router.navigate(['/web/compras/pedidos', pedido.id]);
  }

  podeEditar(pedido: PedidoCompra): boolean {
    return pedido.status === 'RASCUNHO';
  }

  onFormSaved(): void {
    this.displayForm = false;
    this.loadPedidos({ first: 0, rows: this.size });
  }

  onFormCanceled(): void {
    this.displayForm = false;
  }

  label(status: StatusPedidoCompra): string {
    return this.statusLabel[status];
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
