import {Component, EventEmitter, Input, Output} from '@angular/core';
import {UserAccountModel} from '../usersPage.model';
import {NgClass} from '@angular/common';
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

    // Validação básica
    if (this.user.displayName?.trim() && this.user.email?.trim() && this.user.passwordHash && this.user.tenantId) {
      this.save.emit(this.user);
      this.hideDialog();
    }
  }
}
