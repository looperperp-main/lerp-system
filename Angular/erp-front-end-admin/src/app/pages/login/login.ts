import { Component } from '@angular/core';
import {DefaultLoginLayout} from '../../components/default-login-layout/default-login-layout';
import {FormControl, FormGroup, ReactiveFormsModule, Validators} from '@angular/forms';
import {PrimaryInput} from '../../components/primary-input/primary-input';
import {NgOptimizedImage} from '@angular/common';
import {Router} from '@angular/router';
import {LoginService} from './service/login';
import {ToastrService} from 'ngx-toastr';
import {HttpErrorResponse} from '@angular/common/http';

@Component({
  selector: 'app-login',
  imports: [DefaultLoginLayout, ReactiveFormsModule, PrimaryInput, NgOptimizedImage],
  providers: [LoginService],
  templateUrl: './login.html',
  styleUrl: './login.scss',
})
export class Login {

  loginForm!: FormGroup;

  constructor(
    private router: Router,
    private loginService: LoginService,
    private toastService: ToastrService
  ) {
    this.loginForm = new FormGroup({
      email: new FormControl('', [Validators.required, Validators.email]),
      password: new FormControl('', [Validators.required, Validators.minLength(6)]),
      rememberMe: new FormControl(false)
    });
  }

  submit() {
    if (this.loginForm.valid) {
      console.log('Form is valid, perform login logic here');
      this.loginService.login(this.loginForm.value.email, this.loginForm.value.password).subscribe({
        next: () => {
          this.toastService.success("Login Feito com sucesso");
          this.router.navigate(['/admin']);
        },//TODO: navegar para as outar páginas
        error: (err: HttpErrorResponse) => this.handleLoginError(err)
      });
    } else {
      console.log('Form is invalid, show validation errors');
    }
  }

  private handleLoginError(err: HttpErrorResponse) {
    // Status 423 = LOCKED (usuário bloqueado)
    if (err.status === 423 && err.error?.error === 'USER_LOCKED') {
      this.toastService.error(
        err.error.message,
        'Usuário Bloqueado',
        { timeOut: 10000, closeButton: true, progressBar: true }
      );
      return;
    }

    // Erro de negócio genérico (ex: status 403)
    if (err.error?.message) {
      this.toastService.error(err.error.message, 'Erro de Login', { timeOut: 7000 });
      return;
    }

    // Fallback para erros genéricos
    this.toastService.error(
      "Erro ao fazer login! Verifique as credenciais ou tente novamente mais tarde.",
      'Erro',
      { timeOut: 5000 }
    );
  }

  navigate() {
    console.log('Navegar para a página de planos. Criar uma página de planos quando esse componente for criado no Módulo de principal nao no administrativo.');
    this.router.navigate(['/request-access']);
  }
}
