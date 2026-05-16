import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { PartnerLoginService } from './partner-login.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [ReactiveFormsModule],
  templateUrl: './login.html',
  styleUrl: './login.scss',
})
export class LoginComponent {
  private readonly fb = inject(FormBuilder);
  private readonly loginService = inject(PartnerLoginService);
  private readonly router = inject(Router);

  showPassword = signal(false);
  loading = signal(false);
  errorMessage = signal('');

  form = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', Validators.required],
  });

  togglePassword(): void {
    this.showPassword.update(v => !v);
  }

  onSubmit(): void {
    if (this.form.invalid || this.loading()) return;
    this.loading.set(true);
    this.errorMessage.set('');
    const { email, password } = this.form.getRawValue();
    this.loginService.login(email, password).subscribe({
      next: res => {
        localStorage.setItem('partner_token', res.token);
        localStorage.setItem('username', res.username);
        localStorage.setItem('email', email);
        this.router.navigate(['/dashboard']);
      },
      error: () => {
        this.errorMessage.set('Credenciais inválidas. Tente novamente.');
        this.loading.set(false);
      },
    });
  }
}
