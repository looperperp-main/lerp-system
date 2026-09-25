import { Component, inject, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { EstabelecimentoService } from '../cadastros/estabelecimento/estabelecimento.service';

@Component({
  selector: 'app-emissao-fiscal-redirect',
  template: `
    <div class="jb-panel" style="text-align: center; padding: 48px;">
      <i class="pi pi-spin pi-spinner" style="font-size: 2rem; color: var(--muted);"></i>
      <p class="sub" style="margin-top: 12px;">
        {{ erro || 'Abrindo Emissão Fiscal do estabelecimento...' }}
      </p>
    </div>
  `,
})
export class EmissaoFiscalRedirect implements OnInit {
  private estabelecimentoService = inject(EstabelecimentoService);
  private router = inject(Router);

  erro: string | null = null;

  ngOnInit(): void {
    // Emissão Fiscal é configurada por estabelecimento, não existe uma tela global — este
    // redirecionamento resolve a pessoa própria do tenant e cai na lista de filiais dela, de
    // onde o botão "Emissão Fiscal" já existente abre o estabelecimento certo.
    this.estabelecimentoService.buscarPessoaIdProprio().subscribe({
      next: (pessoaId) =>
        this.router.navigate(['/web/cadastros/pessoas', pessoaId, 'estabelecimentos']),
      error: () => {
        this.erro = 'Não foi possível localizar o estabelecimento próprio do tenant.';
      },
    });
  }
}
