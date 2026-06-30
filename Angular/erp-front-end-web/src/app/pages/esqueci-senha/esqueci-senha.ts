import { Component } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { ToastrService } from 'ngx-toastr';
import { HttpErrorResponse } from '@angular/common/http';
import { DefaultLoginLayout } from '../../components/default-login-layout/default-login-layout';
import { PrimaryInput } from '../../components/primary-input/primary-input';
import { PasswordResetService } from './password-reset.service';

@Component({
  selector: 'app-esqueci-senha',
  standalone: true,
  imports: [ReactiveFormsModule, DefaultLoginLayout, PrimaryInput, RouterLink],
  templateUrl: './esqueci-senha.html',
})
export class EsqueciSenha {
  form: FormGroup;
  enviado = false;

  constructor(
    private router: Router,
    private service: PasswordResetService,
    private toast: ToastrService,
  ) {
    this.form = new FormGroup({
      cnpj: new FormControl('', [Validators.required, Validators.pattern(/^\d{14}$/)]),
      email: new FormControl('', [Validators.required, Validators.email]),
    });
  }

  submit() {
    if (this.form.invalid) return;
    const { email, cnpj } = this.form.value;
    this.service.esqueciSenhaTenant(email, cnpj).subscribe({
      next: (res) => {
        this.enviado = true;
        this.toast.success(
          res?.message || 'Se os dados forem válidos, você receberá um e-mail.',
          'Solicitação enviada',
          { timeOut: 8000 },
        );
      },
      // Mesmo em erro mostramos mensagem genérica (anti-enumeração; o backend já responde 200).
      error: (_err: HttpErrorResponse) => {
        this.enviado = true;
        this.toast.success(
          'Se os dados forem válidos, você receberá um e-mail.',
          'Solicitação enviada',
          { timeOut: 8000 },
        );
      },
    });
  }

  voltarLogin() {
    this.router.navigate(['/login']);
  }
}
