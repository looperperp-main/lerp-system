import {
  Component,
  EventEmitter,
  Input,
  OnInit,
  Output,
  OnChanges,
  SimpleChanges,
  ChangeDetectorRef
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { DialogModule } from 'primeng/dialog';
import { ButtonModule } from 'primeng/button';
import { PickListModule } from 'primeng/picklist';
import { MessageService } from 'primeng/api';
import { Ripple } from 'primeng/ripple';

import { UserRoleService, UserRoleRequest } from '../user-role.service';
import {RoleModel} from '../../../../cadastros/roles/roles/role.model';
import {UserAccountModel, UsersPageModel} from '../../../../cadastros/tenant/users/usersPage.model';
import {RoleService} from '../../../../cadastros/roles/roles/role.service';

@Component({
  selector: 'app-user-role-form',
  standalone: true,
  imports: [CommonModule, FormsModule, DialogModule, ButtonModule, PickListModule, Ripple],
  templateUrl: './user-role-form.html',
  styleUrl: './user-role-form.scss'
})
export class UserRoleForm implements OnInit, OnChanges {
  @Input() visible: boolean = false;
  @Input() user!: UsersPageModel;
  @Output() visibleChange = new EventEmitter<boolean>();
  @Output() save = new EventEmitter<void>();

  sourceRoles: RoleModel[] = []; // Roles disponíveis
  targetRoles: RoleModel[] = []; // Roles do Usuário

  saving: boolean = false;

  constructor(
    private userRoleService: UserRoleService,
    private roleService: RoleService,
    private messageService: MessageService,
    private cdr: ChangeDetectorRef
  ) {}

  ngOnInit(): void {}

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['visible'] && changes['visible'].currentValue === true) {
      this.loadData();
    }
  }

  loadData() {
    if (!this.user || !this.user.id) return;

    // 1. Busca TODAS as Roles do sistema
    // Idealmente, poderíamos filtrar para trazer apenas as Roles do Tenant do Usuário
    this.roleService.getRoles().subscribe({
      next: (allRoles) => {
        // Filtragem Frontend: Mostra apenas roles que pertencem ao mesmo Tenant do usuário
        //const rolesDoTenant = allRoles.filter(r =>
          // Tenta comparar de forma mais flexível caso um seja string e outro number (ex: "1" == 1)
          //r.tenantId == (this.user.tenantId as any)
        //);

        // 2. Busca as Roles que o usuário já tem
        this.userRoleService.getRolesByUser(this.user.id!).subscribe({
          next: (userRoles) => {
            this.targetRoles = userRoles;

            // 3. A Source (Disponíveis) = Todas do Tenant MINUS as que o usuário já tem
            const targetIds = new Set(this.targetRoles.map(r => r.id));
            this.sourceRoles = allRoles.filter(r => !targetIds.has(r.id));

            // <--- Adicionado aqui: Força o Angular a processar as mudanças das listas sem dar o erro NG0100
            this.cdr.detectChanges();
          },
          error: () => this.showError('Erro ao carregar roles do usuário')
        });
      },
      error: () => this.showError('Erro ao carregar lista de roles')
    });
  }

  hideDialog() {
    this.visible = false;
    this.visibleChange.emit(this.visible);
  }

  onSave() {
    if (!this.user.id) return;

    this.saving = true;

    const requestIds = this.targetRoles.map(r => r.id!).filter(id => id !== undefined);

    const request: UserRoleRequest = {
      userId: this.user.id,
      roleIds: requestIds
    };

    this.userRoleService.assignRolesToUser(this.user.id, request).subscribe({
      next: () => {
        this.messageService.add({ severity: 'success', summary: 'Sucesso', detail: 'Acessos salvos no usuário!' });
        this.saving = false;
        this.save.emit();
        this.hideDialog();
      },
      error: () => {
        this.showError('Erro ao salvar atribuições');
        this.saving = false;
      }
    });
  }

  onMoveToSource(event: any) {
    if (!this.user.id) return;
    const itemsMoved: RoleModel[] = event.items;

    itemsMoved.forEach(item => {
      if(item.id) {
        this.userRoleService.removeRoleFromUser(this.user.id!, item.id).subscribe({
          next: () => console.log(`Role ${item.name} removida do usuário`),
          error: () => this.showError(`Falha ao remover a role ${item.name}`)
        });
      }
    });
  }

  private showError(msg: string) {
    this.messageService.add({ severity: 'error', summary: 'Erro', detail: msg });
  }
}
