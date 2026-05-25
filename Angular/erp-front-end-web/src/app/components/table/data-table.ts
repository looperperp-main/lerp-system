import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { TagModule } from 'primeng/tag';
import {Ripple} from 'primeng/ripple';

export type TagSeverity = 'success' | 'info' | 'warn' | 'danger' | 'secondary' | 'contrast' | undefined;

export interface ColumnConfig {
  field: string;
  header: string;
  type: 'text' | 'date' | 'status' | 'percent' |'actions' | 'currency' ;
}

@Component({
  selector: 'app-data-table',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, TableModule, ButtonModule, InputTextModule, TagModule, Ripple],
  template: `
    <div class="card">
      <p-table
        #dt
        [value]="data"
        [columns]="cols"
        [paginator]="true"
        [rows]="10"
        [rowsPerPageOptions]="[10, 20, 50]"
        [loading]="loading"
        [globalFilterFields]="globalFilterFields"
        styleClass="p-datatable-sm"
        responsiveLayout="scroll"
        [rowHover]="true"
      >
        <ng-template pTemplate="caption">
          <div class="flex flex-column md:flex-row md:justify-content-between md:align-items-center">
            <h5 class="m-0 title-color">{{ title }}</h5>
            <span class="p-input-icon-left block mt-2 md:mt-0">
              <i class="pi pi-search"></i>
              <input
                pInputText
                type="text"
                (input)="onGlobalFilter(dt, $event)"
                placeholder="Buscar..."
                class="w-full sm:w-auto"
              />
            </span>
          </div>
        </ng-template>

        <ng-template pTemplate="header" let-columns>
          <tr>
            <th *ngFor="let col of columns" [pSortableColumn]="col.field">
              {{ col.header }}
              <p-sortIcon *ngIf="col.type !== 'actions'" [field]="col.field"></p-sortIcon>
            </th>
          </tr>
        </ng-template>

        <ng-template pTemplate="body" let-rowData let-columns="columns">
          <tr>
            <td *ngFor="let col of columns">
              <!-- Tipo Texto -->
              <span *ngIf="col.type === 'text'">
                {{ rowData[col.field] }}
              </span>

              <!-- Tipo Data -->
              <span *ngIf="col.type === 'date'">
                {{ rowData[col.field] | date:'dd/MM/yyyy HH:mm' }}
              </span>

              <span *ngIf="col.type === 'percent'">
                {{ rowData[col.field] | number:'1.2-2' }} %
              </span>

              <!-- Tipo Status -->
              <span *ngIf="col.type === 'status'">
                <p-tag
                  [value]="rowData[col.field]"
                  [severity]="getSeverity(rowData[col.field])">
                </p-tag>
              </span>

              <!-- Tipo Ações -->
              <div *ngIf="col.type === 'actions'" class="flex gap-2">
                <button
                  pButton
                  pRipple
                  icon="pi pi-pencil"
                  class="p-button-rounded p-button-success p-button-text mr-2"
                  style="color: #F24405 !important;"
                  (click)="edit.emit(rowData)">
                </button>
                <button
                  pButton
                  pRipple
                  icon="pi pi-trash"
                  class="p-button-rounded p-button-danger p-button-text"
                  (click)="delete.emit(rowData)">
                </button>
              </div>
            </td>
          </tr>
        </ng-template>

        <ng-template pTemplate="emptymessage">
            <tr>
                <td [attr.colspan]="cols.length" class="text-center p-4">
                    Nenhum registro encontrado.
                </td>
            </tr>
        </ng-template>
      </p-table>
    </div>
  `,
  styles: [`
    :host ::ng-deep .p-datatable-header {
      background: transparent;
      border: none;
      padding-bottom: 1rem;
    }
    .card {
        background: var(--surface-card);
        padding: 2rem;
        border-radius: 12px;
        box-shadow: 0 3px 6px rgba(0,0,0,0.03);
        margin-bottom: 2rem;
    }

    .title-color{
      color: #1E2772;
    }
  `]
})
export class DataTableComponent {
  @Input() title = 'Lista';
  @Input() data: any[] = [];
  @Input() cols: ColumnConfig[] = [];
  @Input() loading = false;
  @Input() globalFilterFields: string[] = [];

  @Output() edit = new EventEmitter<any>();
  @Output() delete = new EventEmitter<any>();

  onGlobalFilter(table: any, event: Event) {
    table.filterGlobal((event.target as HTMLInputElement).value, 'contains');
  }

  getSeverity(status: string): TagSeverity {
    switch (status) {
      case 'ATIVO': return 'success';
      case 'SUSPENSO': return 'warn';
      case 'CANCELADO': return 'danger';
      default: return 'info';
    }
  }
}
