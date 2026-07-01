import { Component, PLATFORM_ID, computed, inject, signal } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs/operators';
import { CadastrarClienteModal } from '../cadastrar-cliente-modal/cadastrar-cliente-modal';
import { PartnerSessionService } from '../../services/partner-session.service';
import { CadastrarClienteModalService } from '../../services/cadastrar-cliente-modal.service';
import { DashboardService } from '../../services/dashboard.service';
import { ConviteService } from '../../services/convite.service';

@Component({
  selector: 'app-partner-layout',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, CadastrarClienteModal],
  templateUrl: './partner-layout.html',
  styleUrl: './partner-layout.scss',
})
export class PartnerLayout {
  private readonly router = inject(Router);
  private readonly session = inject(PartnerSessionService);
  private readonly dashboardService = inject(DashboardService);
  private readonly conviteService = inject(ConviteService);
  readonly cadastrarModal = inject(CadastrarClienteModalService);

  private readonly comissaoValor = signal(0);
  readonly clientesCount = signal(0);
  readonly comissaoMes = computed(() =>
    this.comissaoValor().toLocaleString('pt-BR', {
      style: 'currency',
      currency: 'BRL',
      minimumFractionDigits: 0,
      maximumFractionDigits: 0,
    }),
  );

  isSidebarOpen = signal(false);
  private readonly currentUrl = signal(this.router.url);

  readonly partnerName = computed(
    () => this.session.info()?.name ?? localStorage.getItem('username') ?? 'Parceiro',
  );
  readonly referralCode = computed(() => this.session.info()?.referralCode ?? '');
  readonly partnerInitials = computed(() => {
    const parts = this.partnerName().split(' ');
    return (parts[0]?.[0] ?? '') + (parts[1]?.[0] ?? '');
  });

  readonly pageTitle = computed(() => {
    const url = this.currentUrl();
    if (url.includes('/clientes')) return 'Meus Clientes';
    if (url.includes('/comissoes')) return 'Comissões';
    if (url.includes('/repasses')) return 'Repasses';
    if (url.includes('/configuracoes')) return 'Configurações';
    return 'Dashboard';
  });

  constructor() {
    this.session.load();
    if (isPlatformBrowser(inject(PLATFORM_ID))) {
      this.conviteService.listar().subscribe((page) => this.clientesCount.set(page.totalElements));
      this.dashboardService
        .getComissoes()
        .subscribe((c) => this.comissaoValor.set(c.comissaoMesAtual));
    }
    this.router.events
      .pipe(filter((e) => e instanceof NavigationEnd))
      .subscribe((e) => this.currentUrl.set((e as NavigationEnd).urlAfterRedirects));
  }

  toggleSidebar(): void {
    this.isSidebarOpen.update((v) => !v);
  }
}
