import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface CnpjConsulta {
  cnpj: string;
  razaoSocial: string;
  nomeFantasia: string | null;
  situacaoCadastral: string;
  ativa: boolean;
  email: string | null;
  telefone: string | null;
}

@Injectable({ providedIn: 'root' })
export class CnpjService {
  private readonly API = 'http://localhost:8090/partner/api/v1/partners/cnpj';

  constructor(private http: HttpClient) {}

  consultar(cnpj: string): Observable<CnpjConsulta> {
    const digits = cnpj.replace(/\D/g, '');
    return this.http.get<CnpjConsulta>(`${this.API}/${digits}`);
  }

  /** Validates CNPJ check digits (mod 11 algorithm) */
  validarDigitos(cnpj: string): boolean {
    const n = cnpj.replace(/\D/g, '');
    if (n.length !== 14) return false;
    if (/^(\d)\1+$/.test(n)) return false;

    const calc = (slice: string, weights: number[]) =>
      slice.split('').reduce((acc, d, i) => acc + parseInt(d) * weights[i], 0);

    const r1 = calc(n.slice(0, 12), [5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2]) % 11;
    const d1 = r1 < 2 ? 0 : 11 - r1;

    const r2 = calc(n.slice(0, 13), [6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2]) % 11;
    const d2 = r2 < 2 ? 0 : 11 - r2;

    return parseInt(n[12]) === d1 && parseInt(n[13]) === d2;
  }

  /** Applies XX.XXX.XXX/XXXX-XX mask as the user types */
  aplicarMascara(value: string): string {
    const d = value.replace(/\D/g, '').slice(0, 14);
    if (d.length <= 2) return d;
    if (d.length <= 5) return `${d.slice(0, 2)}.${d.slice(2)}`;
    if (d.length <= 8) return `${d.slice(0, 2)}.${d.slice(2, 5)}.${d.slice(5)}`;
    if (d.length <= 12) return `${d.slice(0, 2)}.${d.slice(2, 5)}.${d.slice(5, 8)}/${d.slice(8)}`;
    return `${d.slice(0, 2)}.${d.slice(2, 5)}.${d.slice(5, 8)}/${d.slice(8, 12)}-${d.slice(12)}`;
  }
}