import { Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';

export interface PartnerInfo {
  name: string;
  email: string;
  referralCode: string;
}

@Injectable({ providedIn: 'root' })
export class PartnerSessionService {
  private readonly API = 'http://localhost:8090/partner/api/v1/partners';

  readonly info = signal<PartnerInfo | null>(null);

  constructor(private http: HttpClient) {}

  load(): void {
    if (this.info()) return;
    const email = localStorage.getItem('username');
    if (!email) return;

    this.http.get<any>(`${this.API}/me`, { params: { email } }).subscribe({
      next: res => {
        this.info.set({
          name: res.name,
          email: res.email,
          referralCode: res.referralCode ?? '',
        });
      },
      error: () => {
        this.info.set({
          name: localStorage.getItem('username') ?? '',
          email: localStorage.getItem('username') ?? '',
          referralCode: 'CTR-00000',
        });
      },
    });
  }

  clear(): void {
    this.info.set(null);
  }
}