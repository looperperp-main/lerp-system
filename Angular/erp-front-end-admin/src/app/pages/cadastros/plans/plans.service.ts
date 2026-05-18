import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, map } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { PlanModel } from './plans.model';

export interface HateoasPage<T> {
  _embedded?: { [key: string]: T[] };
  page: { size: number; totalElements: number; totalPages: number; number: number };
}

@Injectable({ providedIn: 'root' })
export class PlansService {
  private apiUrl = `${environment.apiUrl}/billing/api/v1/plans`;

  constructor(private http: HttpClient) {}

  getAll(page = 0, size = 10, sort = 'name,asc'): Observable<HateoasPage<PlanModel>> {
    const params = new HttpParams()
      .set('page', page)
      .set('size', size)
      .set('sort', sort);
    return this.http.get<HateoasPage<PlanModel>>(`${this.apiUrl}/all`, { params });
  }

  create(plan: PlanModel): Observable<PlanModel> {
    return this.http.post<PlanModel>(this.apiUrl, plan);
  }

  update(id: string, plan: PlanModel): Observable<PlanModel> {
    return this.http.put<PlanModel>(`${this.apiUrl}/${id}`, plan);
  }

  toggleStatus(id: string): Observable<PlanModel> {
    return this.http.patch<PlanModel>(`${this.apiUrl}/${id}/status`, {});
  }

  extractPlans(response: HateoasPage<PlanModel>): PlanModel[] {
    if (!response._embedded) return [];
    const key = Object.keys(response._embedded)[0];
    return response._embedded[key] ?? [];
  }
}