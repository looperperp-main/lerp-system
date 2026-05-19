import { Component, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { CnpjService, CnpjConsulta } from '../../services/cnpj.service';
import { CriarContaService } from './criar-conta.service';

type CnpjState = 'idle' | 'validando' | 'ativa' | 'inativa' | 'erro';

@Component({
  selector: 'app-criar-conta',
  standalone: true,
  imports: [],
  templateUrl: './criar-conta.html',
  styleUrl: './criar-conta.scss',
})
export class CriarConta {
  private readonly cnpjService = inject(CnpjService);
  private readonly criarContaService = inject(CriarContaService);
  private readonly router = inject(Router);

  cnpjInput = signal('');
  razaoSocial = signal('');
  email = signal('');
  senha = signal('');
  confirmSenha = signal('');
  showSenha = signal(false);
  showConfirmSenha = signal(false);

  cnpjState = signal<CnpjState>('idle');
  consultaResult = signal<CnpjConsulta | null>(null);
  erroMsg = signal('');

  loading = signal(false);
  erroEnvio = signal('');

  readonly senhasDiferentes = computed(
    () => this.confirmSenha().length > 0 && this.senha() !== this.confirmSenha(),
  );

  readonly podeEnviar = computed(
    () =>
      this.cnpjState() === 'ativa' &&
      this.email().trim().length > 0 &&
      this.senha().length >= 14 &&
      this.senha() === this.confirmSenha(),
  );

  onCnpjInput(value: string): void {
    const masked = this.cnpjService.aplicarMascara(value);
    this.cnpjInput.set(masked);

    const digits = masked.replace(/\D/g, '');
    if (digits.length === 14 && this.cnpjService.validarDigitos(masked)) {
      this.buscarCnpj();
    } else if (digits.length === 14) {
      this.cnpjState.set('erro');
      this.erroMsg.set('CNPJ inválido — dígitos verificadores incorretos.');
      this.consultaResult.set(null);
      this.razaoSocial.set('');
    } else {
      this.cnpjState.set('idle');
      this.consultaResult.set(null);
    }
  }

  private buscarCnpj(): void {
    this.cnpjState.set('validando');
    this.consultaResult.set(null);

    this.cnpjService.consultar(this.cnpjInput()).subscribe({
      next: res => {
        this.consultaResult.set(res);
        if (res.ativa) {
          this.cnpjState.set('ativa');
          this.razaoSocial.set(res.razaoSocial ?? '');
        } else {
          this.cnpjState.set('inativa');
          this.erroMsg.set(
            `CNPJ com situação "${res.situacaoCadastral}" na Receita Federal.`,
          );
          this.razaoSocial.set('');
        }
      },
      error: err => {
        this.cnpjState.set('erro');
        this.erroMsg.set(
          err.status === 404
            ? 'CNPJ não encontrado na Receita Federal.'
            : 'Erro ao consultar Receita Federal. Tente novamente.',
        );
      },
    });
  }

  onSubmit(): void {
    if (!this.podeEnviar() || this.loading()) return;

    this.loading.set(true);
    this.erroEnvio.set('');

    const result = this.consultaResult();
    this.criarContaService.criarConta({
      cnpj: this.cnpjInput(),
      razaoSocial: this.razaoSocial(),
      nomeFantasia: result?.nomeFantasia ?? null,
      email: this.email().trim(),
      senha: this.senha(),
      telefone: result?.telefone ?? null,
    }).subscribe({
      next: () => this.router.navigate(['/web']),
      error: (err: HttpErrorResponse) => {
        this.loading.set(false);
        if (err.status === 409) {
          this.erroEnvio.set(err.error?.message ?? 'CNPJ ou e-mail já cadastrado.');
        } else if (err.status === 400 && err.error?.message) {
          this.erroEnvio.set(err.error.message);
        } else {
          this.erroEnvio.set('Erro ao criar conta. Tente novamente.');
        }
      },
    });
  }

  goToLanding(): void {
    this.router.navigate(['/']);
  }

  goToLogin(): void {
    this.router.navigate(['/login']);
  }
}