import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CnpjService, CnpjConsulta } from '../../services/cnpj.service';
import { PartnerSessionService } from '../../services/partner-session.service';
import { CadastrarClienteModalService } from '../../services/cadastrar-cliente-modal.service';

export type ModalState = 'idle' | 'validando' | 'ativa' | 'inativa' | 'erro' | 'enviando' | 'sucesso';

@Component({
  selector: 'app-cadastrar-cliente-modal',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './cadastrar-cliente-modal.html',
  styleUrl: './cadastrar-cliente-modal.scss',
})
export class CadastrarClienteModal implements OnInit {
  private readonly cnpjService = inject(CnpjService);
  private readonly session = inject(PartnerSessionService);
  readonly modalService = inject(CadastrarClienteModalService);

  cnpjInput = signal('');
  razaoSocial = signal('');
  nomeFantasia = signal('');
  email = signal('');
  telefone = signal('');

  state = signal<ModalState>('idle');
  consultaResult = signal<CnpjConsulta | null>(null);
  erroMsg = signal('');

  readonly referralCode = computed(() => this.session.info()?.referralCode ?? '');

  readonly podeEnviar = computed(
    () =>
      this.state() === 'ativa' &&
      this.razaoSocial().trim().length > 0 &&
      this.email().trim().length > 0,
  );

  ngOnInit(): void {
    this.session.load();
  }

  close(): void {
    this.resetForm();
    this.modalService.close();
  }

  onCnpjInput(value: string): void {
    const masked = this.cnpjService.aplicarMascara(value);
    this.cnpjInput.set(masked);

    const digits = masked.replace(/\D/g, '');
    if (digits.length === 14 && this.cnpjService.validarDigitos(masked)) {
      this.buscarCnpj();
    } else if (digits.length === 14) {
      this.state.set('erro');
      this.erroMsg.set('CNPJ inválido — dígitos verificadores incorretos.');
      this.consultaResult.set(null);
    } else {
      this.state.set('idle');
      this.consultaResult.set(null);
    }
  }

  private buscarCnpj(): void {
    this.state.set('validando');
    this.consultaResult.set(null);

    this.cnpjService.consultar(this.cnpjInput()).subscribe({
      next: res => {
        this.consultaResult.set(res);
        if (res.ativa) {
          this.state.set('ativa');
          this.razaoSocial.set(res.razaoSocial ?? '');
          this.nomeFantasia.set(res.nomeFantasia ?? '');
          this.email.set(res.email ?? '');
          this.telefone.set(res.telefone ?? '');
        } else {
          this.state.set('inativa');
          this.erroMsg.set(
            `CNPJ com situação "${res.situacaoCadastral}" na Receita Federal. Convite bloqueado.`,
          );
        }
      },
      error: err => {
        this.state.set('erro');
        if (err.status === 404) {
          this.erroMsg.set('CNPJ não encontrado na Receita Federal.');
        } else {
          this.erroMsg.set('Erro ao consultar Receita Federal. Tente novamente.');
        }
      },
    });
  }

  enviarConvite(): void {
    if (!this.podeEnviar()) return;
    this.state.set('enviando');
    // TODO: chamar endpoint de convite quando disponível
    setTimeout(() => this.state.set('sucesso'), 1000);
  }

  private resetForm(): void {
    this.cnpjInput.set('');
    this.razaoSocial.set('');
    this.nomeFantasia.set('');
    this.email.set('');
    this.telefone.set('');
    this.state.set('idle');
    this.consultaResult.set(null);
    this.erroMsg.set('');
  }
}