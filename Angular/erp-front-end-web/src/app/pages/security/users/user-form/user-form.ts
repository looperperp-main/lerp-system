import { Component, EventEmitter, inject, Input, OnInit, Output } from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { NgIf } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { MessageService } from 'primeng/api';
import {Button, ButtonDirective} from 'primeng/button';
import { InputText } from 'primeng/inputtext';
import { SecurityService, UserModel } from '../../security.service';

@Component({
  selector: 'app-user-form',
  imports: [Button, InputText, NgIf, ReactiveFormsModule, ButtonDirective],
  templateUrl: './user-form.html',
  styleUrl: './user-form.scss',
})
export class UserForm implements OnInit {
  @Input() userData: UserModel | null = null;
  @Output() saved = new EventEmitter<void>();
  @Output() canceled = new EventEmitter<void>();

  private fb = inject(FormBuilder);
  private securityService = inject(SecurityService);
  private messages = inject(MessageService);

  form!: FormGroup;
  isSaving = false;
  editing = false;

  ngOnInit(): void {
    this.editing = !!this.userData?.id;

    this.form = this.fb.group({
      email: [this.userData?.email || '', this.editing ? [] : [Validators.required]],
      displayName: [this.userData?.displayName || '', [Validators.required]],
      passwordHash: ['', this.editing ? [] : [Validators.required]],
    });
  }

  onSubmit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.isSaving = true;
    const { email, displayName, passwordHash } = this.form.value;

    const done = (msg: string) => {
      this.messages.add({ severity: 'success', summary: 'Sucesso', detail: msg });
      this.isSaving = false;
      this.saved.emit();
    };

    if (this.editing && this.userData?.id) {
      const body: any = { email, displayName };
      if (passwordHash?.trim()) body.passwordHash = passwordHash;
      this.securityService.updateUser(this.userData.id, body).subscribe({
        next: () => done('Usuário atualizado!'),
        error: (err: HttpErrorResponse) => this.handleError(err, 'Erro ao atualizar usuário'),
      });
    } else {
      this.securityService.createUser({ email, displayName, passwordHash }).subscribe({
        next: () => done('Usuário criado!'),
        error: (err: HttpErrorResponse) => this.handleError(err, 'Erro ao criar usuário'),
      });
    }
  }

  onCancel(): void {
    this.canceled.emit();
  }

  isFieldInvalid(field: string): boolean {
    const control = this.form.get(field);
    return !!(control && control.invalid && (control.dirty || control.touched));
  }

  private handleError(err: HttpErrorResponse, summary: string): void {
    const detail = err.error?.message
      ? `[${err.error.status}] ${err.error.error} - ${err.error.message}`
      : 'Erro de comunicação com o servidor.';
    this.messages.add({ severity: 'error', summary, detail, life: 5000 });
    this.isSaving = false;
  }
}
