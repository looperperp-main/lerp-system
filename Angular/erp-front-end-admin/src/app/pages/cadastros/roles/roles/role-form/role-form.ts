import {Component, EventEmitter, Input, Output} from '@angular/core';
import {NgClass} from '@angular/common';
import {PrimeTemplate} from 'primeng/api';
import {InputText} from 'primeng/inputtext';
import {Dialog} from 'primeng/dialog';
import {FormsModule} from '@angular/forms';
import {Ripple} from 'primeng/ripple';
import {ButtonDirective} from 'primeng/button';
import {RoleModel} from '../role.model';
import {TenantModel} from '../../../tenant/tenant/tenant.model';
import {Select} from 'primeng/select';

@Component({
  selector: 'app-role-form',
  imports: [
    NgClass,
    PrimeTemplate,
    InputText,
    Dialog,
    FormsModule,
    Ripple,
    ButtonDirective,
    Select
  ],
  templateUrl: './role-form.html',
  styleUrl: './role-form.scss',
})
export class RoleForm {
  @Input() visible: boolean = false;
  @Input() role: RoleModel = { name: '', tenantId: 0 };
  @Input() tenants: TenantModel[] = [];

  @Output() visibleChange = new EventEmitter<boolean>();
  @Output() save = new EventEmitter<RoleModel>();

  submitted: boolean = false;

  hideDialog() {
    this.visible = false;
    this.visibleChange.emit(this.visible);
    this.submitted = false;
  }

  onSave() {
    this.submitted = true;
    if (this.role.name?.trim() && this.role.tenantId) {
      // Forçar uppercase para o nome da role
      this.role.name = this.role.name.toUpperCase().replace(/\s/g, '_');
      this.save.emit(this.role);
      this.hideDialog();
    }
  }
}
