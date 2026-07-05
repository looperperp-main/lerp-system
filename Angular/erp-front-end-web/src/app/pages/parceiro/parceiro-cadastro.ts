import { Component, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { ParceiroService } from './parceiro.service';
import { MessageService } from 'primeng/api';
import { Toast } from 'primeng/toast';

@Component({
  selector: 'app-parceiro-cadastro',
  standalone: true,
  imports: [ReactiveFormsModule, Toast],
  providers: [MessageService],
  templateUrl: './parceiro-cadastro.html',
  styleUrl: './parceiro-cadastro.scss',
})
export class ParceiroCadastro {
  form = new FormGroup({
    name: new FormControl('', [Validators.required, Validators.maxLength(200)]),
    crc: new FormControl('', [Validators.maxLength(20)]),
    cnpj: new FormControl('', [Validators.required, Validators.pattern(/^\d{14}$/)]),
    email: new FormControl('', [Validators.required, Validators.email, Validators.maxLength(200)]),
    phone: new FormControl('', [Validators.pattern(/^\d{10,11}$/)]),
  });

  // ponytail: signals — app é zoneless, campo simples não dispara change detection no subscribe
  loading = signal(false);
  success = signal(false);
  errorMsg = signal('');

  constructor(
    private service: ParceiroService,
    private router: Router,
    private messageService: MessageService,
  ) {}

  get cnpjDisplay(): string {
    return this.formatCnpj(this.form.value.cnpj ?? '');
  }

  onCnpjInput(event: Event): void {
    const raw = (event.target as HTMLInputElement).value.replace(/\D/g, '').slice(0, 14);
    this.form.get('cnpj')!.setValue(raw, { emitEvent: false });
    (event.target as HTMLInputElement).value = this.formatCnpj(raw);
  }

  onPhoneInput(event: Event): void {
    const raw = (event.target as HTMLInputElement).value.replace(/\D/g, '').slice(0, 11);
    this.form.get('phone')!.setValue(raw, { emitEvent: false });
    (event.target as HTMLInputElement).value = this.formatPhone(raw);
  }

  submit(): void {
    if (this.form.invalid || this.loading()) return;
    this.loading.set(true);
    this.errorMsg.set('');

    const { name, crc, cnpj, email, phone } = this.form.value;

    this.service
      .cadastrar({
        name: name!,
        crc: crc || null,
        cnpj: cnpj!,
        email: email!,
        phone: phone || null,
      })
      .subscribe({
        next: () => {
          this.messageService.add({
            severity: 'success',
            summary: 'Sucesso',
            detail: 'Parceiro cadastrado com sucesso.',
          });
          this.success.set(true);
          this.loading.set(false);
        },
        error: (err: HttpErrorResponse) => {
          this.loading.set(false);
          if (err.status === 409) {
            this.errorMsg.set(err.error?.detail ?? 'CNPJ ou e-mail já cadastrado.');
          } else if (err.status === 0) {
            this.errorMsg.set(
              'Não foi possível conectar ao servidor. Verifique sua conexão e tente novamente.',
            );
          } else {
            this.errorMsg.set(
              err.error?.detail ?? `Ocorreu um erro (${err.status}). Tente novamente em instantes.`,
            );
          }
        },
      });
  }

  goToLanding(): void {
    this.router.navigate(['/']);
  }

  private formatCnpj(v: string): string {
    if (v.length > 12) return v.replace(/^(\d{2})(\d{3})(\d{3})(\d{4})(\d{2})$/, '$1.$2.$3/$4-$5');
    if (v.length > 8) return v.replace(/^(\d{2})(\d{3})(\d{3})(\d+)$/, '$1.$2.$3/$4');
    if (v.length > 5) return v.replace(/^(\d{2})(\d{3})(\d+)$/, '$1.$2.$3');
    if (v.length > 2) return v.replace(/^(\d{2})(\d+)$/, '$1.$2');
    return v;
  }

  private formatPhone(v: string): string {
    if (v.length === 11) return v.replace(/^(\d{2})(\d{5})(\d{4})$/, '($1) $2-$3');
    if (v.length === 10) return v.replace(/^(\d{2})(\d{4})(\d{4})$/, '($1) $2-$3');
    if (v.length > 6) return v.replace(/^(\d{2})(\d+)$/, '($1) $2');
    return v;
  }
}
