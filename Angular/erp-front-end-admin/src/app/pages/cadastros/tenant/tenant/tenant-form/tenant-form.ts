import {Component, EventEmitter, Input, Output} from '@angular/core';
import {TenantModel} from '../tenant.model';
import {FormsModule} from '@angular/forms';
import {Dialog} from 'primeng/dialog';
import {NgClass} from '@angular/common';
import {PrimeTemplate} from 'primeng/api';
import {InputText} from 'primeng/inputtext';
import {ButtonDirective} from 'primeng/button';
import {Ripple} from 'primeng/ripple';
import {InputMask} from 'primeng/inputmask';

@Component({
  selector: 'app-tenant-form',
  imports: [
    FormsModule,
    Dialog,
    NgClass,
    PrimeTemplate,
    InputText,
    ButtonDirective,
    Ripple,
    InputMask
  ],
  templateUrl: './tenant-form.html',
  styleUrl: './tenant-form.scss',
})
export class TenantForm {
  @Input() visible: boolean = false;
  @Input() tenant: TenantModel = { name: '', cnpj: '', status: 'ATIVO' };

  @Output() visibleChange = new EventEmitter<boolean>();
  @Output() save = new EventEmitter<TenantModel>();

  submitted: boolean = false;

  statuses = [
    { label: 'Ativo', value: 'ATIVO' },
    { label: 'Suspenso', value: 'SUSPENSO' },
    { label: 'Cancelado', value: 'CANCELADO' }
  ];

  hideDialog() {
    this.visible = false;
    this.visibleChange.emit(this.visible);
    this.submitted = false;
  }

  onSave() {
    this.submitted = true;

    // Remove a máscara (pontos, barra e traço) antes de enviar pro backend, se o seu backend esperar apenas números.
    // Se o seu backend espera a string formatada, remova esse bloco e passe direto.
    // Baseado na sua entidade Java (length=14), parece que o backend espera SÓ OS NÚMEROS (14 caracteres).
    const unmaskedCnpj = this.tenant.cnpj ? this.tenant.cnpj.replace(/\D/g, '') : '';

    // Validação básica (garante que tenha 14 dígitos de fato)
    if (this.tenant.name?.trim() && unmaskedCnpj.length === 14) {

      // Cria uma cópia para enviar ao pai com o CNPJ limpo
      const tenantToSave = {
        ...this.tenant,
        cnpj: unmaskedCnpj
      };

      // Emite o tenant para o componente pai e fecha o modal
      this.save.emit(tenantToSave);
      this.hideDialog();
    }
  }
}
