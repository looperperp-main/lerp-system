import { Component, OnInit, signal } from '@angular/core';
import { CurrencyPipe, NgForOf, NgIf } from '@angular/common';
import { Toast } from 'primeng/toast';
import { TableModule } from 'primeng/table';
import { ButtonDirective } from 'primeng/button';
import { Ripple } from 'primeng/ripple';
import { Tooltip } from 'primeng/tooltip';
import { PrimeTemplate, MessageService } from 'primeng/api';
import { Dialog } from 'primeng/dialog';
import { ColumnConfig } from '../../../components/table/data-table';
import { PlanModel } from './plans.model';
import { PlansService } from './plans.service';
import { PlansForms } from './plans-forms/plans-forms';
import { HttpErrorResponse } from '@angular/common/http';

@Component({
  selector: 'app-plans',
  standalone: true,
  imports: [
    Toast,
    TableModule,
    ButtonDirective,
    Ripple,
    Tooltip,
    CurrencyPipe,
    NgForOf,
    NgIf,
    PrimeTemplate,
    Dialog,
    PlansForms,
  ],
  providers: [MessageService],
  templateUrl: './plans.html',
  styleUrl: './plans.scss',
})
export class Plans implements OnInit {
  plans = signal<PlanModel[]>([]);
  totalRecords = signal<number>(0);
  loading = signal<boolean>(true);
  page = 0;
  size = 10;

  formVisible = false;
  selectedPlan: PlanModel | null = null;

  toggleDialogVisible = false;
  planToToggle: PlanModel | null = null;
  toggleLoading = false;

  cols: ColumnConfig[] = [
    { field: 'name', header: 'Nome', type: 'text' },
    { field: 'planType', header: 'Tipo', type: 'text' },
    { field: 'value', header: 'Valor', type: 'text' },
    { field: 'active', header: 'Status', type: 'status' },
    { field: 'description', header: 'Descrição', type: 'text' },
    { field: 'actions', header: 'Ações', type: 'actions' },
  ];

  constructor(
    private plansService: PlansService,
    private messageService: MessageService,
  ) {}

  ngOnInit(): void {
    this.loadPlans();
  }

  loadPlans(page = this.page, size = this.size): void {
    this.loading.set(true);
    this.plansService.getAll(page, size).subscribe({
      next: (response) => {
        this.plans.set(this.plansService.extractPlans(response));
        this.totalRecords.set(response.page?.totalElements ?? 0);
        this.loading.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.messageService.add({ severity: 'error', summary: 'Erro', detail: 'Falha ao carregar planos.', life: 5000 });
        this.loading.set(false);
      },
    });
  }

  onLazyLoad(event: any): void {
    this.page = event.first / event.rows;
    this.size = event.rows;
    this.loadPlans(this.page, this.size);
  }

  openNew(): void {
    this.selectedPlan = null;
    this.formVisible = true;
  }

  openEdit(plan: PlanModel): void {
    this.selectedPlan = { ...plan };
    this.formVisible = true;
  }

  onSave(plan: PlanModel): void {
    if (plan.id) {
      this.plansService.update(plan.id, plan).subscribe({
        next: () => {
          this.loadPlans();
          this.messageService.add({ severity: 'success', summary: 'Sucesso', detail: 'Plano atualizado.', life: 3000 });
        },
        error: () => this.messageService.add({ severity: 'error', summary: 'Erro', detail: 'Falha ao atualizar plano.', life: 5000 }),
      });
    } else {
      this.plansService.create(plan).subscribe({
        next: () => {
          this.loadPlans();
          this.messageService.add({ severity: 'success', summary: 'Sucesso', detail: 'Plano criado.', life: 3000 });
        },
        error: () => this.messageService.add({ severity: 'error', summary: 'Erro', detail: 'Falha ao criar plano.', life: 5000 }),
      });
    }
  }

  openToggleDialog(plan: PlanModel): void {
    this.planToToggle = { ...plan };
    this.toggleDialogVisible = true;
  }

  closeToggleDialog(): void {
    this.toggleDialogVisible = false;
    this.planToToggle = null;
  }

  confirmToggle(): void {
    if (!this.planToToggle?.id) return;
    const id = this.planToToggle.id;
    this.toggleLoading = true;
    this.plansService.toggleStatus(id).subscribe({
      next: () => {
        this.toggleLoading = false;
        this.closeToggleDialog();
        this.loadPlans();
        this.messageService.add({ severity: 'success', summary: 'Sucesso', detail: 'Status alterado.', life: 3000 });
      },
      error: () => {
        this.toggleLoading = false;
        this.messageService.add({ severity: 'error', summary: 'Erro', detail: 'Falha ao alterar status.', life: 5000 });
      },
    });
  }
}