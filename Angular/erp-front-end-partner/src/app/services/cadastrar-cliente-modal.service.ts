import { Injectable, signal } from '@angular/core';
import { Subject } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class CadastrarClienteModalService {
  readonly isOpen = signal(false);
  readonly inviteSent$ = new Subject<void>();

  open(): void  { this.isOpen.set(true); }
  close(): void { this.isOpen.set(false); }
}