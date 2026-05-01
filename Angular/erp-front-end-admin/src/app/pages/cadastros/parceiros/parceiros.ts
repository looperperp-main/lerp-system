import {Component, signal} from '@angular/core';
import {Toast} from "primeng/toast";
import {ButtonDirective} from 'primeng/button';
import {Ripple} from 'primeng/ripple';
import {Tooltip} from 'primeng/tooltip';
import {MessageService, PrimeTemplate} from 'primeng/api';
import {CnpjPipe} from '../../../util/pipe/cnpj.pipe';
import {DatePipe, NgForOf, NgIf} from '@angular/common';
import {HtmlDecodePipe} from '../../../util/pipe/html-decode.pipe';
import {TableModule} from 'primeng/table';
import {ParceirosModel} from './parceiros.model';
import {ColumnConfig} from '../../../components/table/data-table';
import {ParceirosService} from './parceiros.service';
import {Dialog} from 'primeng/dialog';
import {Select} from 'primeng/select';
import {Textarea} from 'primeng/textarea';
import {FormsModule} from '@angular/forms';
import {HttpErrorResponse} from '@angular/common/http';

interface ReviewOption {
  label: string;
  value: 'approve' | 'reject';
}

@Component({
  selector: 'app-parceiros',
  imports: [
    Toast,
    ButtonDirective,
    Ripple,
    Tooltip,
    CnpjPipe,
    DatePipe,
    HtmlDecodePipe,
    NgForOf,
    NgIf,
    PrimeTemplate,
    TableModule,
    Dialog,
    Select,
    Textarea,
    FormsModule
  ],
  providers: [MessageService],
  templateUrl: './parceiros.html',
  styleUrl: './parceiros.scss',
})
export class Parceiros {

  parceiros = signal<ParceirosModel[]>([]);
  totalRecords = signal<number>(0);
  loading = signal<boolean>(true);
  page: number = 0;
  size: number = 10;
  selectedPartner: ParceirosModel | null = null;

  reviewDialogVisible: boolean = false;
  partnerToReview: ParceirosModel | null = null;
  selectedAction: 'approve' | 'reject' | null = null;
  reviewNotes: string = '';
  reviewLoading: boolean = false;

  inactivateDialogVisible: boolean = false;
  partnerToInactivate: ParceirosModel | null = null;
  inactivateLoading: boolean = false;

  reviewOptions: ReviewOption[] = [
    { label: 'Aprovar', value: 'approve' },
    { label: 'Rejeitar', value: 'reject' },
  ];

  cols: ColumnConfig[] = [
    { field: 'id', header: 'ID', type: 'text' },
    { field: 'name', header: 'Nome', type: 'text' },
    { field: 'status', header: 'Status', type: 'status' },
    { field: 'crc', header: 'CRC', type: 'text' },
    { field: 'cnpj', header: 'CNPJ', type: 'text' },
    { field: 'email', header: 'Email', type: 'text' },
    { field: 'phone', header: 'Telefone', type: 'text' },
    { field: 'referralCode', header: 'Código do Parceiro', type: 'text' },
    { field: 'commissionRate', header: 'Comissão', type: 'text' },
    { field: 'createdAt', header: 'Data de Criação', type: 'date' },
    { field: 'updatedAt', header: 'Última Atualização', type: 'date' },
    { field: 'updatedBy', header: 'Atualizado Por', type: 'text' },
    { field: 'reviewedBy', header: 'Revisado Por', type: 'text' },
    { field: 'reviewedAt', header: 'Data de Revisão', type: 'date' },
    { field: 'actions', header: 'Ações', type: 'actions' }
  ];

  constructor(
    private messageService: MessageService,
    private partnerService: ParceirosService
  ) {}

  exportData() {
    this.messageService.add({ severity: 'info', summary: 'Info', detail: 'Funcionalidade de exportação aqui' });
  }

  loadPartners(event?: any) {
    setTimeout(() => { this.loading.set(true); });

    if (event) {
      this.page = event.first / event.rows;
      this.size = event.rows;
    }
    this.partnerService.getAll(this.page, this.size).subscribe({
      next: (response) => {
        if (response._embedded?.partnerResponseDTOList) {
          this.parceiros.set(response._embedded.partnerResponseDTOList || []);
        }
        this.totalRecords.set(response.page?.totalElements || 0);
        this.loading.set(false);
      },
      error: (err) => {
        this.messageService.add({ severity: 'error', summary: 'Erro', detail: 'Erro ao buscar Parceiros.' });
        this.loading.set(false);
      }
    });
  }

  openReviewDialog(partner: ParceirosModel) {
    this.partnerToReview = { ...partner };
    this.selectedAction = null;
    this.reviewNotes = '';
    this.reviewDialogVisible = true;
  }

  closeReviewDialog() {
    this.reviewDialogVisible = false;
    this.partnerToReview = null;
    this.selectedAction = null;
    this.reviewNotes = '';
  }

  openInactivateDialog(partner: ParceirosModel) {
    this.partnerToInactivate = { ...partner };
    this.inactivateDialogVisible = true;
  }

  closeInactivateDialog() {
    this.inactivateDialogVisible = false;
    this.partnerToInactivate = null;
  }

  confirmInactivate() {
    if (!this.partnerToInactivate?.id) return;
    this.inactivateLoading = true;
    this.partnerService.inactivate(this.partnerToInactivate.id).subscribe({
      next: (updated) => {
        this.parceiros.update(list => list.map(p => p.id === updated.id ? updated : p));
        this.messageService.add({ severity: 'warn', summary: 'Inativado', detail: 'Parceiro inativado com sucesso.', life: 3000 });
        this.inactivateLoading = false;
        this.closeInactivateDialog();
      },
      error: () => {
        this.messageService.add({ severity: 'error', summary: 'Erro', detail: 'Falha ao inativar parceiro.', life: 5000 });
        this.inactivateLoading = false;
      }
    });
  }

  confirmReview() {
    if (!this.partnerToReview?.id || !this.selectedAction) return;

    this.reviewLoading = true;
    const payload = { notes: this.reviewNotes || null };
    const id = this.partnerToReview.id;

    const call$ = this.selectedAction === 'approve'
      ? this.partnerService.approve(id, payload)
      : this.partnerService.reject(id, payload);

    call$.subscribe({
      next: (updated) => {
        this.parceiros.update(list => list.map(p => p.id === updated.id ? updated : p));
        this.messageService.add({
          severity: 'success',
          summary: 'Sucesso',
          detail: this.selectedAction === 'approve' ? 'Parceiro aprovado.' : 'Parceiro rejeitado.',
          life: 3000
        });
        this.reviewLoading = false;
        this.closeReviewDialog();
      },
      error: (err: HttpErrorResponse) => {
        this.messageService.add({ severity: 'error', summary: 'Erro', detail: 'Falha ao revisar parceiro.', life: 5000 });
        this.reviewLoading = false;
      }
    });
  }
}
