import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, map } from 'rxjs';
import { environment } from '../../environments/environment';

export interface Plan {
  id: string;
  name: string;
  planType: string;
  billingCycle: string;
  value: number;
  active: boolean;
  description?: string;
}

export interface CheckoutRequest {
  planType: string;
  cnpj: string;
  email: string;
  razaoSocial: string;
}

export interface CheckoutResponse {
  paymentUrl: string;
  boletoUrl: string;
  pixQrCode: string | null;
  pixCopyPaste: string | null;
  dueDate: string;
  planType: string;
  planName: string;
  value: number;
}

@Injectable({ providedIn: 'root' })
export class BillingService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/billing/api/v1`;

  /** Planos ativos. A resposta é HATEOAS paginada — extrai o array de dentro de _embedded. */
  getPlans(): Observable<Plan[]> {
    return this.http.get<any>(`${this.base}/plans`).pipe(
      map((res) => {
        const emb = res?._embedded;
        return emb ? ((Object.values(emb)[0] as Plan[]) ?? []) : [];
      }),
    );
  }

  /** Cria a assinatura no Asaas. O gateway injeta X-Tenant-Id a partir do JWT. */
  checkout(req: CheckoutRequest): Observable<CheckoutResponse> {
    return this.http.post<CheckoutResponse>(`${this.base}/checkout`, req);
  }
}
