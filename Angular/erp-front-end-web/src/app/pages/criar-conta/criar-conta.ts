import { Component, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { CnpjService, CnpjConsulta } from '../../services/cnpj.service';

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

  goToLanding(): void {
    this.router.navigate(['/']);
  }

  goToLogin(): void {
    this.router.navigate(['/login']);
  }
}