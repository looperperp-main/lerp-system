import { Component } from '@angular/core';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.scss',
})
export class DashboardComponent {
  readonly funnelSteps = [
    { label: 'Convidados', value: 15, color: '#3b82f6', pct: 100 },
    { label: 'Ativados', value: 12, color: '#f97316', pct: 80 },
    { label: 'Em Trial', value: 4, color: '#eab308', pct: 27 },
    { label: 'Convertidos', value: 8, color: '#22c55e', pct: 53 },
  ];

  readonly urgentTrials = [
    { name: 'Auto Peças Sul', cnpj: '23.456.789/0001-12', daysLeft: 2, engagement: 18 },
    { name: 'Mercado Bom Preço', cnpj: '18.234.567/0001-43', daysLeft: 5, engagement: 52 },
    { name: 'Pet Shop Amigo Fiel', cnpj: '31.987.654/0001-09', daysLeft: 9, engagement: 74 },
  ];

  engagementColor(pct: number): string {
    if (pct < 30) return '#ef4444';
    if (pct < 60) return '#f59e0b';
    return '#22c55e';
  }
}