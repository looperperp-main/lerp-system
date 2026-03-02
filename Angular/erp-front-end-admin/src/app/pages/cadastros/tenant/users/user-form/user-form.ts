import {Component, EventEmitter, Input, Output} from '@angular/core';
import {UserAccountModel} from '../usersPage.model';
import {NgClass,NgIf} from '@angular/common';
import {Password} from 'primeng/password';
import {FormsModule} from '@angular/forms';
import {Dialog} from 'primeng/dialog';
import {PrimeTemplate} from 'primeng/api';
import {InputText} from 'primeng/inputtext';
import {Ripple} from 'primeng/ripple';
import {ButtonDirective} from 'primeng/button';
import {TenantModel} from '../../tenant/tenant.model';
import {Select} from 'primeng/select';

@Component({
  selector: 'app-user-form',
  imports: [
    NgClass,
    NgIf,
    Password,
    FormsModule,
    Dialog,
    PrimeTemplate,
    InputText,
    Ripple,
    ButtonDirective,
    Select
  ],
  templateUrl: './user-form.html',
  styleUrl: './user-form.scss',
})
export class UserForm {
  @Input() visible: boolean = false;
  @Input() user: UserAccountModel = { displayName: '', email: '', passwordHash: '', tenantId: undefined };
  @Input() tenants: TenantModel[] = [];

  @Output() visibleChange = new EventEmitter<boolean>();
  @Output() save = new EventEmitter<UserAccountModel>();

  submitted: boolean = false;

  hideDialog() {
    this.visible = false;
    this.visibleChange.emit(this.visible);
    this.submitted = false;
  }

  onSave() {
    this.submitted = true;

    // Regra Regex: Min 14 chars, 1 Maiuscula, 1 Minuscula, 1 Número, 1 Caractere Especial
    const passwordRegex = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[!@#$%^&*()_+={}\[\]:;<>,.?/~\\-]).{14,}$/;

    // Validação básica do form + Regex da Senha
    if (this.user.displayName?.trim() &&
      this.user.email?.trim() &&
      this.user.tenantId &&
      this.user.passwordHash &&
      this.isPasswordValid()) {

      this.save.emit(this.user);
      this.hideDialog();
    }
  }

  // --- Validações Dinâmicas de Senha ---
  protected isPasswordValid() {
    return this.hasMinLength() &&
      this.hasUpperCase() &&
      this.hasLowerCase() &&
      this.hasNumber() &&
      this.hasSpecialChar();
  }

  protected hasMinLength(): boolean {
    return !!this.user.passwordHash && this.user.passwordHash.length >= 14;
  }

  protected hasUpperCase(): boolean {
    return !!this.user.passwordHash && /[A-Z]/.test(this.user.passwordHash);
  }

  protected hasLowerCase(): boolean {
    return !!this.user.passwordHash && /[a-z]/.test(this.user.passwordHash);
  }

  protected hasNumber(): boolean {
    return !!this.user.passwordHash && /\d/.test(this.user.passwordHash);
  }

  protected hasSpecialChar(): boolean {
    return !!this.user.passwordHash && /[!@#$%^&*()_+={}\[\]:;<>,.?/~\\-]/.test(this.user.passwordHash);
  }
}
