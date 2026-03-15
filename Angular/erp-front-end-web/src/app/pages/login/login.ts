import { Component } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { ToastrService } from 'ngx-toastr';
import { HttpErrorResponse } from '@angular/common/http';
import { DefaultLoginLayout } from '../../components/default-login-layout/default-login-layout';
import { PrimaryInput } from '../../components/primary-input/primary-input';
import { TenantLoginService } from './service/tenant-login.service';

@Component({
  selector: 'app-tenant-login',
  standalone: true,
  imports: [DefaultLoginLayout, ReactiveFormsModule, PrimaryInput],
  templateUrl: './login.html',
  styleUrl: './login.scss',
})
export class TenantLogin {
  loginForm!: FormGroup;

  constructor(
    private router: Router,
    private loginService: TenantLoginService,
    private toastService: ToastrService
  ) {
    this.loginForm = new FormGroup({
      cnpj: new FormControl('', [
        Validators.required,
        Validators.pattern(/^\d{14}$/)
      ]),
      email: new FormControl('', [
        Validators.required,
        Validators.email
      ]),
      password: new FormControl('', [
        Validators.required,
        Validators.minLength(6)
      ]),
    });
  }

  submit() {
    if (this.loginForm.valid) {
      const { cnpj, email, password } = this.loginForm.value;

      this.loginService.login(cnpj, email, password).subscribe({
        next: (response) => {
          this.toastService.success(
            `Bem-vindo(a), ${response.username}!`,
            response.tenantName
          );
          this.router.navigate(['/dashboard']);
        },
        error: (err: HttpErrorResponse) => this.handleLoginError(err)
      });
    }
  }

  private handleLoginError(err: HttpErrorResponse) {
    if (err.status === 423 && err.error?.error === 'USER_LOCKED') {
      this.toastService.error(
        err.error.message,
        'Usuário Bloqueado',
        { timeOut: 10000, closeButton: true, progressBar: true }
      );
      return;
    }

    if (err.status === 401) {
      const message = err.error?.message || 'CNPJ, e-mail ou senha incorretos';
      this.toastService.error(message, 'Erro de Login', { timeOut: 5000 });
      return;
    }

    if (err.error?.message) {
      this.toastService.error(err.error.message, 'Erro de Login', { timeOut: 5000 });
      return;
    }

    this.toastService.error(
      'Erro ao fazer login. Tente novamente mais tarde.',
      'Erro',
      { timeOut: 5000 }
    );
  }

  navigateToHelp() {
    // Futuramente: navegar para página de ajuda ou recuperação de acesso
    this.toastService.info('Funcionalidade em desenvolvimento', 'Em breve');
  }
}
