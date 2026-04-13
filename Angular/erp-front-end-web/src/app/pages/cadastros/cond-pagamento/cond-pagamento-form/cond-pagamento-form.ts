import {Component, EventEmitter, inject, Input, OnInit, Output} from '@angular/core';
import {CondPagamento, CondPagamentoParcela} from '../cond-pagamento.model';
import {FormArray, FormBuilder, FormGroup, ReactiveFormsModule, Validators} from '@angular/forms';
import {ToastrService} from 'ngx-toastr';
import {CondPagamentoService} from '../cond-pagamento.service';
import {Checkbox} from 'primeng/checkbox';
import {Button, ButtonDirective} from 'primeng/button';
import {NgClass, NgForOf, NgIf} from '@angular/common';
import {Textarea} from 'primeng/textarea';
import {InputText} from 'primeng/inputtext';
import {Tooltip} from 'primeng/tooltip';
import {Select} from 'primeng/select';
import {MessageService} from 'primeng/api';
import {Toast} from 'primeng/toast';

@Component({
  selector: 'app-cond-pagamento-form',
  imports: [
    Checkbox,
    ReactiveFormsModule,
    Button,
    NgClass,
    Textarea,
    InputText,
    NgIf,
    ButtonDirective,
    Tooltip,
    NgForOf,
    Select,
    Toast
  ],
  providers: [MessageService],
  templateUrl: './cond-pagamento-form.html',
  styleUrl: './cond-pagamento-form.scss',
})
export class CondPagamentoForm implements OnInit {
  @Input() cPagData: CondPagamento | null = null;
  @Output() saved = new EventEmitter<void>();
  @Output() canceled = new EventEmitter<void>();

  private fb = inject(FormBuilder);
  private grupoService = inject(CondPagamentoService);
  private messageService = inject(MessageService);

  form!: FormGroup;
  isSaving = false;
  isLoadingParcelas = false;

  formasPagamento = [
    { label: 'Boleto', value: 'BOLETO' },
    { label: 'PIX', value: 'PIX' },
    { label: 'Cartão de Crédito', value: 'CARTAO_CREDITO' },
    { label: 'Cartão de Débito', value: 'CARTAO_DEBITO' },
    { label: 'Dinheiro', value: 'DINHEIRO' },
    { label: 'Transferência', value: 'TRANSFERENCIA' }
  ];

  ngOnInit(): void {
    this.form = this.fb.group({
      id: [this.cPagData?.id || null],
      nome: [this.cPagData?.nome || '', [Validators.required, Validators.maxLength(100)]],
      descricao: [this.cPagData?.descricao || '', [Validators.maxLength(500)]],
      ativo: [this.cPagData ? this.cPagData.ativo : true],
      parcelas: this.fb.array([])
    });

    if (this.cPagData?.id) {
      this.carregarParcelas(this.cPagData.id);
    } else {
      // Se for novo, adiciona pelo menos uma parcela (à vista) por padrão
      this.addParcela(1, 0, 100);
    }
  }

  get parcelasArray(): FormArray {
    return this.form.get('parcelas') as FormArray;
  }

  carregarParcelas(condicaoId: string) {
    this.isLoadingParcelas = true;
    this.grupoService.buscarParcelas(condicaoId).subscribe({
      next: (response) => {
        // HATEOAS collection fallback
        const parcelas: CondPagamentoParcela[] = response._embedded?.parcelas || response.content || [];
        this.parcelasArray.clear();
        parcelas.forEach(p => {
          // Agora a formaPagamento que vem da API é passada e setada corretamente no formulário
          this.addParcela(p.numeroParcela, p.diasPrazo, p.percentual, p.formaPagamento);
        });
        this.isLoadingParcelas = false;
      },
      error: () => {
        this.messageService.add({ severity: 'error', summary: 'Erro', detail: 'Erro ao carregar as parcelas.' });
        this.isLoadingParcelas = false;
      }
    });
  }

  addParcela(numero?: number, diasPrazo?: number, percentual?: number, formaPagamento?: string) {
    const nextNum = numero || (this.parcelasArray.length + 1);
    const parcelaForm = this.fb.group({
      numeroParcela: [nextNum, [Validators.required, Validators.min(1)]],
      diasPrazo: [diasPrazo ?? 30, [Validators.required, Validators.min(0)]],
      percentual: [percentual ?? 0, [Validators.required, Validators.min(0.01)]],
      formaPagamento: [formaPagamento ?? 'BOLETO', [Validators.required]]
    });
    this.parcelasArray.push(parcelaForm);
  }

  removeParcela(index: number) {
    this.parcelasArray.removeAt(index);
    this.recalcularNumeros();
  }

  recalcularNumeros() {
    this.parcelasArray.controls.forEach((ctrl, index) => {
      ctrl.get('numeroParcela')?.setValue(index + 1);
    });
  }

  validarSomaPercentual(): boolean {
    const total = this.parcelasArray.controls.reduce((sum, ctrl) => {
      return sum + (ctrl.get('percentual')?.value || 0);
    }, 0);
    // Margem de erro de arredondamento (Ex: 33.33 x 3)
    return total >= 99.9 && total <= 100.1;
  }

  onSubmit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    if (!this.validarSomaPercentual()) {
      this.messageService.add({ severity: 'warning', summary: 'Aviso', detail: 'A soma dos percentuais das parcelas deve ser 100%' });
      return;
    }

    if (this.parcelasArray.length === 0) {
      this.messageService.add({ severity: 'warning', summary: 'Aviso', detail: 'Adicione pelo menos uma parcela.' });
      return;
    }

    this.isSaving = true;
    const formValue = { ...this.form.value };
    delete formValue.parcelas; // Remove para salvar só a entidade pai

    this.grupoService.salvar(formValue).subscribe({
      next: (condSaved) => {
        const condId = condSaved.id as string;

        // Prepara DTO das parcelas com o ID pai
        const parcelasPayload: CondPagamentoParcela[] = this.parcelasArray.value.map((p: any) => ({
          ...p,
          condicaoPagamentoId: condId
        }));

        this.grupoService.salvarParcelasEmLote(condId, parcelasPayload).subscribe({
          next: () => {
            this.messageService.add({ severity: 'success', summary: 'Sucesso', detail: 'Condição de Pagamento e Parcelas salvas com sucesso!', life: 3000 });
            this.isSaving = false;
            this.saved.emit();
          },
          error: () => {
            this.messageService.add({ severity: 'error', summary: 'Aviso', detail: 'Condição salva, mas houve erro ao salvar as parcelas.' });
            this.isSaving = false;
            this.saved.emit();
          }
        });
      },
      error: (err) => {
        this.isSaving = false;
        // O backend joga "GROUP_C_ALREADY_EXISTS" caso já exista
        if (err.error?.message === 'COND_PAG_ALREADY_EXISTS') {
          this.messageService.add({ severity: 'error', summary: 'Erro', detail: 'Já existe uma condição com este nome.' });
          this.canceled.emit();
        } else {
          this.messageService.add({ severity: 'error', summary: 'Erro', detail: 'Erro ao salvar a Condição de Pagamento.' });
          this.canceled.emit();
        }
      }
    });
  }

  onCancel(): void {
    this.canceled.emit();
  }

  // Helpers para o template
  isFieldInvalid(field: string): boolean {
    const control = this.form.get(field);
    return !!(control && control.invalid && (control.dirty || control.touched));
  }
}
