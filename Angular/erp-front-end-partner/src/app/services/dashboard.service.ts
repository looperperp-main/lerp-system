import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, of, tap } from 'rxjs';

export interface TrialUrgenteDTO {
  referralId: string;
  razaoSocial: string;
  cnpj: string;
  trialExpiresAt: string;
  daysLeft: number;
}

export interface AtividadeItemDTO {
  tipo: 'CONVIDADO' | 'ATIVADO';
  razaoSocial: string;
  timestamp: string;
}

export interface DashboardResponse {
  statsConvidados: number;
  statsAtivos: number;
  statsTrial: number;
  statsFollowup: number;
  statsConvertidos: number;
  trialsExpirando3Dias: number;
  comissaoMesAtual: number;
  totalComissoesPagas: number;
  trialsUrgentes: TrialUrgenteDTO[];
  atividadeRecente: AtividadeItemDTO[];
}

export interface ComissaoItemDTO {
  id: string;
  tenantId: number;
  razaoSocial: string;
  cnpj: string;
  amount: number;
  period: string;
  status: string;
  calculatedAt: string;
  paidAt: string | null;
}

export interface ExtratoComissoesDTO {
  comissaoMesAtual: number;
  totalPago: number;
  historico: ComissaoItemDTO[];
}

export interface FeatureStatDTO {
  featureKey: string;
  label: string;
  accessCount: number;
  lastAccessedAt: string | null;
}

export interface ClienteDetalheResponse {
  referral: {
    referralId: string;
    cnpj: string;
    razaoSocial: string;
    emailContato: string;
    status: string;
    followupAttempts: number;
    trialStartedAt: string | null;
    trialExpiresAt: string | null;
  };
  loginCount: number;
  lastLoginAt: string | null;
  daysActive: number;
  features: FeatureStatDTO[];
  adoptionGaps: string[];
}

@Injectable({ providedIn: 'root' })
export class DashboardService {
  private readonly http = inject(HttpClient);
  private readonly BASE = 'http://localhost:8090/partner/api/v1/partners';

  // Cache de sessão: o dashboard é carregado 1x (1ª visita) e reusado nas navegações seguintes.
  // Só refaz a chamada quando forçado (botão de refresh). Service é singleton (providedIn root).
  private cache: DashboardResponse | null = null;
  private comissoesCache: ExtratoComissoesDTO | null = null;

  getDashboard(force = false): Observable<DashboardResponse> {
    if (this.cache && !force) {
      return of(this.cache);
    }
    return this.http
      .get<DashboardResponse>(`${this.BASE}/me/dashboard`)
      .pipe(tap((data) => (this.cache = data)));
  }

  getClienteDetalhe(referralId: string): Observable<ClienteDetalheResponse> {
    return this.http.get<ClienteDetalheResponse>(`${this.BASE}/me/convites/${referralId}/detalhe`);
  }

  iniciarFollowup(referralId: string, message: string): Observable<void> {
    return this.http.post<void>(`${this.BASE}/me/convites/${referralId}/followup`, { message });
  }

  getComissoes(force = false): Observable<ExtratoComissoesDTO> {
    if (this.comissoesCache && !force) {
      return of(this.comissoesCache);
    }
    return this.http
      .get<ExtratoComissoesDTO>(`${this.BASE}/me/comissoes`)
      .pipe(tap((data) => (this.comissoesCache = data)));
  }
}
