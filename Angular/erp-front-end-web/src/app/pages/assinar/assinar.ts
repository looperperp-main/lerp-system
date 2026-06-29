import { Component, OnInit, inject, signal } from '@angular/core';
import { CurrencyPipe } from '@angular/common';
import { BillingService, Plan, CheckoutResponse } from '../../services/billing.service';
import { TenantService, TenantProfile } from '../../services/tenant.service';

@Component({
  selector: 'app-assinar',
  standalone: true,
  imports: [CurrencyPipe],
  templateUrl: './assinar.html',
  styleUrl: './assinar.scss',
})
export class Assinar implements OnInit {
  private readonly billing = inject(BillingService);
  private readonly tenantService = inject(TenantService);

  readonly carregando = signal(true);
  readonly erro = signal<string | null>(null);
  readonly planos = signal<Plan[]>([]);
  readonly tenant = signal<TenantProfile | null>(null);
  readonly selecionado = signal<Plan | null>(null);

  readonly processando = signal(false);
  readonly resultado = signal<CheckoutResponse | null>(null);

  readonly cancelando = signal(false);
  readonly cancelMsg = signal<string | null>(null);

  ngOnInit(): void {
    this.billing.getPlans().subscribe({
      next: (planos) => {
        this.planos.set(planos);
        this.carregando.set(false);
      },
      error: () => {
        this.erro.set('Não foi possível carregar os planos.');
        this.carregando.set(false);
      },
    });
    this.tenantService.getMe().subscribe({
      next: (t) => this.tenant.set(t),
      error: () => {},
    });
  }

  selecionar(p: Plan): void {
    this.selecionado.set(p);
  }

  assinar(): void {
    const plano = this.selecionado();
    const t = this.tenant();
    if (!plano || !t || this.processando()) return;

    this.processando.set(true);
    this.erro.set(null);
    this.billing
      .checkout({ planType: plano.planType, cnpj: t.cnpj, email: t.email, razaoSocial: t.name })
      .subscribe({
        next: (res) => {
          this.resultado.set(res);
          this.processando.set(false);
        },
        error: () => {
          this.erro.set('Falha ao gerar o pagamento. Tente novamente.');
          this.processando.set(false);
        },
      });
  }

  cancelar(): void {
    if (this.cancelando()) return;
    if (!confirm('Cancelar sua assinatura? O acesso continua até o fim do período já pago.'))
      return;
    this.cancelando.set(true);
    this.cancelMsg.set(null);
    this.billing.cancelarAssinatura().subscribe({
      next: (r) => {
        const ate = r.acessoAte
          ? new Date(r.acessoAte).toLocaleDateString('pt-BR')
          : 'o fim do período pago';
        this.cancelMsg.set(`Cancelamento registrado. Seu acesso continua até ${ate}.`);
        this.cancelando.set(false);
      },
      error: (err) => {
        this.cancelMsg.set(
          err?.status === 404
            ? 'Nenhuma assinatura ativa para cancelar.'
            : 'Falha ao cancelar. Tente novamente.',
        );
        this.cancelando.set(false);
      },
    });
  }
}
