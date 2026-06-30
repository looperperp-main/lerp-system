import { Component, OnInit } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ToastrService } from 'ngx-toastr';
import { HttpErrorResponse } from '@angular/common/http';
import { DefaultLoginLayout } from '../../components/default-login-layout/default-login-layout';
import { PrimaryInput } from '../../components/primary-input/primary-input';
import { PasswordResetService } from './password-reset.service';

@Component({
  selector: 'app-redefinir-senha',
  standalone: true,
  imports: [ReactiveFormsModule, DefaultLoginLayout, PrimaryInput, RouterLink],
  templateUrl: './redefinir-senha.html',
})
export class RedefinirSenha implements OnInit {
  form: FormGroup;
  token = '';
  concluido = false;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private service: PasswordResetService,
    private toast: ToastrService,
  ) {
    this.form = new FormGroup({
      novaSenha: new FormControl('', [Validators.required, Validators.minLength(14)]),
      confirmacaoSenha: new FormControl('', [Validators.required]),
    });
  }

  ngOnInit(): void {
    this.token = this.route.snapshot.queryParamMap.get('token') || '';
    if (!this.token) {
      this.toast.error(
        'Link inválido ou incompleto. Solicite um novo e-mail de redefinição.',
        'Token ausente',
        { timeOut: 8000 },
      );
    }
  }

  get senhasNaoConferem(): boolean {
    const { novaSenha, confirmacaoSenha } = this.form.value;
    return !!confirmacaoSenha && novaSenha !== confirmacaoSenha;
  }

  submit() {
    if (this.form.invalid || this.senhasNaoConferem || !this.token) return;
    const { novaSenha, confirmacaoSenha } = this.form.value;
    this.service.redefinirSenha(this.token, novaSenha, confirmacaoSenha).subscribe({
      next: () => {
        this.concluido = true;
        this.toast.success(
          'Senha redefinida com sucesso. Faça login com a nova senha.',
          'Tudo certo!',
          { timeOut: 6000 },
        );
      },
      error: (err: HttpErrorResponse) => {
        const msg =
          err.error?.message || 'Não foi possível redefinir a senha. O link pode ter expirado.';
        this.toast.error(msg, 'Erro', { timeOut: 7000 });
      },
    });
  }

  voltarLogin() {
    this.router.navigate(['/login']);
  }
}
