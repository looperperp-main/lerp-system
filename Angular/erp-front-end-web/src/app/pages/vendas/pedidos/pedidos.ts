import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule, Router } from '@angular/router';
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
import { PedidoService } from './pedido.service';
import { Pedido, STATUS_PEDIDO_LABEL, StatusPedido } from './pedido.model';
import { PedidoForm } from './pedido-form/pedido-form';
import { ClientesService } from '../../cadastros/cliente/clientes.service';
import { VendedorService } from '../../cadastros/vendedores/vendedor.service';

@Component({
  selector: 'app-pedidos',
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
  private pedidoService = inject(PedidoService);
  private clientesService = inject(ClientesService);
  private vendedorService = inject(VendedorService);
  private messageService = inject(MessageService);
  private fb = inject(FormBuilder);
  private router = inject(Router);

  pedidos = signal<Pedido[]>([]);
  loading = signal<boolean>(true);
  totalRecords = signal<number>(0);
  page = 0;
  size = 10;

  clientesMap = new Map<string, string>();
  vendedoresMap = new Map<string, string>();

  displayForm = false;
  selectedPedido: Pedido | null = null;

  statusLabel = STATUS_PEDIDO_LABEL;
  statusOptions = [
    { label: 'Todos', value: null },
    ...Object.entries(STATUS_PEDIDO_LABEL).map(([value, label]) => ({ label, value })),
  ];

  filtroForm: FormGroup = this.fb.group({
    numero: [null],
    status: [null],
    clienteId: [null],
    vendedorId: [null],
  });

  ngOnInit(): void {
    this.carregarMapas();
  }

  private carregarMapas(): void {
    this.clientesService.getAll(0, 1000).subscribe({
      next: (res) => {
        const content = res._embedded ? res._embedded.clientes : res.content || [];
        setTimeout(() =>
          content.forEach((c: any) =>
            this.clientesMap.set(c.id, c.nome || c.codigoInterno || c.id),
          ),
        );
      },
    });
    this.vendedorService.getAll(0, 1000).subscribe({
      next: (res) => {
        const content = res._embedded ? res._embedded.vendedor : res.content || [];
        setTimeout(() => content.forEach((v: any) => this.vendedoresMap.set(v.id, v.nome)));
      },
    });
  }

  nomeCliente(id?: string): string {
    return (id && this.clientesMap.get(id)) || '-';
  }

  nomeVendedor(id?: string): string {
    return (id && this.vendedoresMap.get(id)) || '-';
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
        this.handleError(err, 'Erro ao carregar pedidos.');
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

  editarPedido(pedido: Pedido): void {
    // Abre o diálogo já no clique (síncrono, como openNew()) — abrir só dentro do
    // subscribe do HTTP causava NG0100 no [(visible)] do p-dialog (o setter de visible
    // do PrimeNG grava signals internos durante o ciclo de CD, e isso só quebra quando
    // a mudança vem de um callback assíncrono, não de um clique). Ver mesmo fix em
    // produtos.ts:editProduto().
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

  verPedido(pedido: Pedido): void {
    this.router.navigate(['/web/vendas/pedidos', pedido.id]);
  }

  podeEditar(pedido: Pedido): boolean {
    return pedido.status === 'ORCAMENTO';
  }

  onFormSaved(): void {
    this.displayForm = false;
    this.loadPedidos({ first: 0, rows: this.size });
  }

  onFormCanceled(): void {
    this.displayForm = false;
  }

  label(status: StatusPedido): string {
    return this.statusLabel[status];
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
