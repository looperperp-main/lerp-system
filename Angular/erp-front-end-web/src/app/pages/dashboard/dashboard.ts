import { Component } from '@angular/core';
import { TenantLoginService } from '../login/service/tenant-login.service';
import { Router } from '@angular/router';


@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.scss',
})
export class Dashboard {
  username: string | null;
  tenantName: string | null;

  constructor(
    private loginService: TenantLoginService,
    private router: Router
  ) {
    this.username = this.loginService.getUsername();
    this.tenantName = this.loginService.getTenantName();
  }

  logout() {
    this.loginService.logout();
    this.router.navigate(['/login']);
  }
}
