import { Component, Input, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ButtonModule } from 'primeng/button';
import { TooltipModule } from 'primeng/tooltip';

@Component({
  selector: 'app-primary-button',
  standalone: true,
  imports: [CommonModule, ButtonModule, TooltipModule],
  template: `
    <button
      pButton
      pRipple
      [label]="label"
      [icon]="icon"
      [loading]="loading"
      class="p-button-sm rounded-lg"
      [style]="{ 'background-color': backgroundColor, 'color': 'white', 'border': 'none', 'padding': padding }"
      (click)="onClick.emit($event)"
      [pTooltip]="tooltip"
      [tooltipPosition]="tooltipPosition">
    </button>
  `
})
export class PrimaryButtonComponent {
  @Input() label: string = '';
  @Input() icon: string = '';
  @Input() padding: string = '0.5rem 1rem';
  @Input() tooltip: string = '';
  @Input() tooltipPosition: any = 'bottom';
  @Input() backgroundColor: any = '#f04e1a';
  @Input() loading: boolean = false;

  @Output() onClick = new EventEmitter<Event>();
}
