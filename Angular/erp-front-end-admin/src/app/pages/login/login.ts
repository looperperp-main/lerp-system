import { Component } from '@angular/core';
import {DefaultLoginLayout} from '../../components/default-login-layout/default-login-layout';
import {FormControl, FormGroup, ReactiveFormsModule, Validators} from '@angular/forms';
import {PrimaryInput} from '../../components/primary-input/primary-input';
import {NgOptimizedImage} from '@angular/common';
import {Router} from '@angular/router';
import {LoginService} from './service/login';

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
    private loginService: LoginService
  ) {
    this.loginForm = new FormGroup({
      email: new FormControl('', [Validators.required, Validators.email]),
      password: new FormControl('', [Validators.required, Validators.minLength(6)]),
    });
  }

  submit() {
    if (this.loginForm.valid) {
      console.log('Form is valid, perform login logic here');
      this.loginService.login(this.loginForm.value.email, this.loginForm.value.password).subscribe(
        next => console.log("Sucesso"),//TODO: navegar para as outar páginas
        error => console.log("Erro")
      );
    } else {
      console.log('Form is invalid, show validation errors');
    }
  }

  navigate() {
    console.log('Navegar para a página de planos. Criar uma página de planos quando esse componente for criado no Módulo de principal nao no administrativo.');
    this.router.navigate(['/request-access']);
  }
}
