import { Component, EventEmitter, Input, OnChanges, Output, SimpleChanges } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ButtonDirective } from 'primeng/button';
import { Dialog } from 'primeng/dialog';
import { InputText } from 'primeng/inputtext';
import { InputNumber } from 'primeng/inputnumber';
import { Select } from 'primeng/select';
import { Textarea } from 'primeng/textarea';
import { PrimeTemplate } from 'primeng/api';
import { PlanModel } from '../plans.model';

interface SelectOption {
  label: string;
  value: string;
}

@Component({
  selector: 'app-plans-forms',
  standalone: true,
  imports: [
    FormsModule,
    ButtonDirective,
    Dialog,
    InputText,
    InputNumber,
    Select,
    Textarea,
    PrimeTemplate,
  ],
  templateUrl: './plans-forms.html',
  styleUrl: './plans-forms.scss',
})
export class PlansForms implements OnChanges {
  @Input() visible = false;
  @Input() plan: PlanModel | null = null;

  @Output() visibleChange = new EventEmitter<boolean>();
  @Output() save = new EventEmitter<PlanModel>();

  form: PlanModel = this.emptyForm();
  submitted = false;
  editMode = false;

  planTypeOptions: SelectOption[] = [
    { label: 'Mensal', value: 'MENSAL' },
    { label: 'Anual', value: 'ANUAL' },
  ];

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['plan'] || changes['visible']) {
      if (this.visible) {
        this.submitted = false;
        if (this.plan?.id) {
          this.editMode = true;
          this.form = { ...this.plan };
        } else {
          this.editMode = false;
          this.form = this.emptyForm();
        }
      }
    }
  }

  onPlanTypeChange(): void {
    this.form.billingCycle = this.form.planType === 'ANUAL' ? 'YEARLY' : 'MONTHLY';
  }

  onSave(): void {
    this.submitted = true;
    if (!this.form.name?.trim() || !this.form.planType || !this.form.value) return;
    this.save.emit({ ...this.form });
    this.hide();
  }

  hide(): void {
    this.visible = false;
    this.visibleChange.emit(false);
    this.submitted = false;
  }

  private emptyForm(): PlanModel {
    return { name: '', planType: 'MENSAL', billingCycle: 'MONTHLY', value: undefined, description: '' };
  }
}