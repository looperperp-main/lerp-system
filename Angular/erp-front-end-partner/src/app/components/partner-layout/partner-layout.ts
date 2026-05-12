import { Component, computed, inject, signal } from '@angular/core';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs/operators';
import { CadastrarClienteModal } from '../cadastrar-cliente-modal/cadastrar-cliente-modal';
import { PartnerSessionService } from '../../services/partner-session.service';
import { CadastrarClienteModalService } from '../../services/cadastrar-cliente-modal.service';

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
  readonly cadastrarModal = inject(CadastrarClienteModalService);

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
    this.router.events
      .pipe(filter(e => e instanceof NavigationEnd))
      .subscribe(e => this.currentUrl.set((e as NavigationEnd).urlAfterRedirects));
  }

  toggleSidebar(): void {
    this.isSidebarOpen.update(v => !v);
  }
}