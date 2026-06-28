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
}
