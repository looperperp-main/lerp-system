import {Component, EventEmitter, Input, Output} from '@angular/core';
import {Dialog} from 'primeng/dialog';
import {NgClass} from '@angular/common';
import {InputText} from 'primeng/inputtext';
import {Textarea} from 'primeng/textarea';
import {FormsModule} from '@angular/forms';
import {ButtonDirective} from 'primeng/button';
import {Ripple} from 'primeng/ripple';
import {PrimeTemplate} from 'primeng/api';
import {PermissionModel} from '../permission.model';

@Component({
  selector: 'app-permission-form',
  imports: [
    Dialog,
    NgClass,
    InputText,
    Textarea,
    FormsModule,
    ButtonDirective,
    Ripple,
    PrimeTemplate
  ],
  templateUrl: './permission-form.html',
  styleUrl: './permission-form.scss',
})
export class PermissionForm {
  @Input() visible: boolean = false;
  @Input() permission: PermissionModel = { code: '', domain: '', description: '', scope: 'TENANT' };

  @Output() visibleChange = new EventEmitter<boolean>();
  @Output() save = new EventEmitter<PermissionModel>();

  submitted: boolean = false;

  // Checkbox "Permissão de Plataforma" <-> scope PLATFORM/TENANT
  get isPlatform(): boolean {
    return this.permission.scope === 'PLATFORM';
  }
  set isPlatform(value: boolean) {
    this.permission.scope = value ? 'PLATFORM' : 'TENANT';
  }

  close() {
    this.visible = false;
    this.submitted = false;
    this.visibleChange.emit(this.visible);
  }

  savePermission() {
    this.submitted = true;

    // Validação simples antes de emitir
    if (this.permission.code?.trim() && this.permission.domain?.trim() && this.permission.description?.trim()) {
      // Força uppercase no código no front-end também
      this.permission.code = this.permission.code.toUpperCase().replace(/\s/g, '_');
      if (!this.permission.scope) {
        this.permission.scope = 'TENANT';
      }
      this.save.emit(this.permission);
      this.close();
    }
  }
}
