import { Component, OnInit, signal, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { PayoutService } from '../../services/payout.service';

@Component({
  selector: 'app-configuracoes',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './configuracoes.html',
  styleUrl: './configuracoes.scss',
})
export class ConfiguracoesComponent implements OnInit {
  private readonly payoutService = inject(PayoutService);

  readonly tipos = ['CPF', 'CNPJ', 'EMAIL', 'PHONE', 'EVP'];

  readonly pixKey = signal('');
  readonly pixKeyType = signal('EVP');
  readonly carregando = signal(true);
  readonly salvando = signal(false);
  readonly mensagem = signal<string | null>(null);
  readonly erro = signal<string | null>(null);

  ngOnInit(): void {
    this.payoutService.getPayoutInfo().subscribe({
      next: (info) => {
        this.pixKey.set(info.pixKey ?? '');
        this.pixKeyType.set(info.pixKeyType ?? 'EVP');
        this.carregando.set(false);
      },
      error: () => {
        this.erro.set('Erro ao carregar os dados de repasse.');
        this.carregando.set(false);
      },
    });
  }

  salvar(): void {
    this.mensagem.set(null);
    this.erro.set(null);
    if (!this.pixKey().trim()) {
      this.erro.set('Informe a chave PIX.');
      return;
    }
    this.salvando.set(true);
    this.payoutService
      .updatePayoutInfo({ pixKey: this.pixKey().trim(), pixKeyType: this.pixKeyType() })
      .subscribe({
        next: () => {
          this.mensagem.set('Chave PIX salva com sucesso. As comissões serão repassadas para ela.');
          this.salvando.set(false);
        },
        error: () => {
          this.erro.set('Erro ao salvar. Confira a chave e o tipo selecionado.');
          this.salvando.set(false);
        },
      });
  }
}
