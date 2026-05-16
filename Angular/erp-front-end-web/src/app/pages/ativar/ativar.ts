import { Component, inject, OnInit, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpClient } from '@angular/common/http';

@Component({
  selector: 'app-ativar',
  standalone: true,
  imports: [ReactiveFormsModule],
  templateUrl: './ativar.html',
  styleUrl: './ativar.scss',
})
export class AtivarConta implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly http = inject(HttpClient);

  token = signal('');
  email = signal('');
  razaoSocial = signal('');
  cnpj = signal('');
  showSenha = signal(false);
  showConfirmacao = signal(false);
  loading = signal(false);
  erro = signal('');
  sucesso = signal(false);

  form = this.fb.nonNullable.group({
    senha: ['', [Validators.required, Validators.minLength(14)]],
    confirmacaoSenha: ['', Validators.required],
  });

  ngOnInit(): void {
    const t = this.route.snapshot.queryParamMap.get('token') ?? '';
    if (!t) {
      this.erro.set('Link de ativação inválido.');
      return;
    }
    this.token.set(t);

    try {
      const payload = JSON.parse(atob(t.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')));
      this.email.set(payload['email'] ?? '');
      this.razaoSocial.set(payload['razaoSocial'] ?? '');
      this.cnpj.set(this.formatarCnpj(payload['cnpj'] ?? ''));
    } catch {
      this.erro.set('Token de ativação inválido.');
    }
  }

  private formatarCnpj(cnpj: string): string {
    const d = cnpj.replace(/\D/g, '');
    if (d.length !== 14) return cnpj;
    return `${d.slice(0, 2)}.${d.slice(2, 5)}.${d.slice(5, 8)}/${d.slice(8, 12)}-${d.slice(12)}`;
  }

  onSubmit(): void {
    if (this.form.invalid || this.loading()) return;
    const { senha, confirmacaoSenha } = this.form.getRawValue();
    if (senha !== confirmacaoSenha) {
      this.erro.set('As senhas não conferem.');
      return;
    }
    this.loading.set(true);
    this.erro.set('');
    this.http.post('http://localhost:8090/auth/ativar', {
      token: this.token(),
      senha,
      confirmacaoSenha,
    }).subscribe({
      next: () => {
        this.sucesso.set(true);
        setTimeout(() => this.router.navigate(['/login']), 2500);
      },
      error: (err) => {
        this.erro.set(err.error?.message ?? 'Erro ao ativar conta. Tente novamente.');
        this.loading.set(false);
      },
    });
  }
}