import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface PayoutInfo {
  pixKey: string | null;
  pixKeyType: string | null;
}

@Injectable({ providedIn: 'root' })
export class PayoutService {
  private readonly http = inject(HttpClient);
  private readonly BASE = 'http://localhost:8090/partner/api/v1/partners';

  getPayoutInfo(): Observable<PayoutInfo> {
    return this.http.get<PayoutInfo>(`${this.BASE}/me/payout-info`);
  }

  updatePayoutInfo(body: PayoutInfo): Observable<PayoutInfo> {
    return this.http.put<PayoutInfo>(`${this.BASE}/me/payout-info`, body);
  }
}
