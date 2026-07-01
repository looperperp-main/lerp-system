import { Component, OnInit } from '@angular/core';
import {
  FormControl,
  FormGroup,
  FormsModule,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { ToastrService } from 'ngx-toastr';
import { HttpErrorResponse } from '@angular/common/http';
import { DefaultLoginLayout } from '../../components/default-login-layout/default-login-layout';
import { PrimaryInput } from '../../components/primary-input/primary-input';
import { TenantLoginService } from './service/tenant-login.service';
import { MessageService } from 'primeng/api';
import { Toast } from 'primeng/toast';

@Component({
  selector: 'app-tenant-login',
  standalone: true,
  imports: [
    DefaultLoginLayout,
    ReactiveFormsModule,
    PrimaryInput,
    FormsModule,
    ReactiveFormsModule,
    Toast,
    RouterLink,
  ],
  templateUrl: './login.html',
  styleUrl: './login.scss',
})
export class TenantLogin implements OnInit {
  loginForm!: FormGroup;

  constructor(
    private router: Router,
    private loginService: TenantLoginService,
    private toastService: ToastrService,
    private messageService: MessageService,
  ) {
    this.loginForm = new FormGroup({
      cnpj: new FormControl('', [Validators.required, Validators.pattern(/^\d{14}$/)]),
      email: new FormControl('', [Validators.required, Validators.email]),
      password: new FormControl('', [
        Validators.required,
        Validators.minLength(8),
        Validators.pattern(/^(?=.*[a-z])(?=.*[A-Z])(?=.*\d).+$/),
      ]),
      rememberMe: new FormControl(false),
    });
  }

  ngOnInit(): void {
    const savedEmail = localStorage.getItem('rememberedEmail');
    const savedCnpj = localStorage.getItem('rememberedCnpj');
    if (savedEmail) {
      this.loginForm.patchValue({
        cnpj: savedCnpj,
        email: savedEmail,
        rememberMe: true,
      });
    }
  }

  submit() {
    if (this.loginForm.valid) {
      const { cnpj, email, password, rememberMe } = this.loginForm.value;

      this.loginService.login(cnpj, email, password, rememberMe).subscribe({
        next: (response) => {
          if (this.loginForm.value.rememberMe) {
            localStorage.setItem('rememberedEmail', this.loginForm.value.email);
            localStorage.setItem('rememberedCnpj', this.loginForm.value.cnpj);
          } else {
            localStorage.removeItem('rememberedEmail');
            localStorage.removeItem('rememberedCnpj');
          }
          localStorage.removeItem('rememberedPW');
          this.messageService.add({
            severity: 'success',
            summary: response.tenantName,
            detail: `Bem-vindo(a), ${response.username}!`,
            sticky: true,
          });

          this.router.navigate(['/web']);
        },
        error: (err: HttpErrorResponse) => this.handleLoginError(err),
      });
    }
  }

  private handleLoginError(err: HttpErrorResponse) {
    if (err.status === 423 && err.error?.error === 'USER_LOCKED') {
      this.toastService.error(err.error.message, 'Usuário Bloqueado', {
        timeOut: 10000,
        closeButton: true,
        progressBar: true,
      });
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

    this.toastService.error('Erro ao fazer login. Tente novamente mais tarde.', 'Erro', {
      timeOut: 5000,
    });
  }

  navigateToHelp() {
    // Futuramente: navegar para página de ajuda ou recuperação de acesso
    this.toastService.info('Funcionalidade em desenvolvimento', 'Em breve');
  }
}
