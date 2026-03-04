import {
  ChangeDetectorRef,
  Component,
  EventEmitter,
  Input,
  OnChanges,
  OnInit,
  Output,
  SimpleChanges
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { DialogModule } from 'primeng/dialog';
import { ButtonModule } from 'primeng/button';
import { PickListModule } from 'primeng/picklist';
import { MessageService } from 'primeng/api';
import {RoleModel} from '../../../../cadastros/roles/roles/role.model';
import {PermissionModel} from '../../../../cadastros/permission/permission.model';
import {RolePermissionsService} from '../role-permission.service';
import {PermissionService} from '../../../../cadastros/permission/permission.service';
import {Ripple} from 'primeng/ripple';

@Component({
  selector: 'app-role-permissions-form',
  standalone: true,
  imports: [CommonModule, FormsModule, DialogModule, ButtonModule, PickListModule, Ripple],
  templateUrl: './role-permission-form.html',
  styleUrl: './role-permission-form.scss'
})
export class RolePermissionsForm implements OnInit, OnChanges {
  @Input() visible: boolean = false;
  @Input() role!: RoleModel;
  @Output() visibleChange = new EventEmitter<boolean>();
  @Output() save = new EventEmitter<void>();

  sourcePermissions: PermissionModel[] = []; // Permissões disponíveis
  targetPermissions: PermissionModel[] = []; // Permissões da Role

  saving: boolean = false;

  constructor(
    private rolePermissionsService: RolePermissionsService,
    private permissionService: PermissionService,
    private messageService: MessageService,
    private cdr: ChangeDetectorRef
  ) {}

  ngOnInit(): void {}

  // Quando a modal for aberta, buscamos os dados
  ngOnChanges(changes: SimpleChanges): void {
    if (changes['visible'] && changes['visible'].currentValue === true) {
      this.loadData();
    }
  }

  loadData() {
    // 1. Busca todas as permissões do sistema (Para fins didáticos, buscamos 500, o ideal é não ter paginação para picklist ou buscar só o que precisa)
    this.permissionService.getPermissions(0, 500).subscribe({
      next: (allPermsPage) => {
        const allPerms = allPermsPage.content || [];

        // 2. Busca as permissões que a role já tem
        if (this.role.id) {
          this.rolePermissionsService.getPermissionsByRole(this.role.id).subscribe({
            next: (rolePerms) => {
              this.targetPermissions = rolePerms;

              // 3. A Source (Disponíveis) = Todas do sistema MINUS as que a Role já tem
              const targetIds = new Set(this.targetPermissions.map(p => p.id));
              this.sourcePermissions = allPerms.filter(p => !targetIds.has(p.id));
              // <--- AVISANDO O ANGULAR PARA ATUALIZAR A TELA SEM ERRO
              this.cdr.detectChanges();
            },
            error: () => this.showError('Erro ao carregar permissões da Role')
          });
        }
      },
      error: () => this.showError('Erro ao carregar lista de permissões')
    });
  }

  hideDialog() {
    this.visible = false;
    this.visibleChange.emit(this.visible);
  }

  onSave() {
    if (!this.role.id) return;

    this.saving = true;

    // Pegamos apenas os IDs das permissões que estão na lista Target (Atribuídas)
    const requestIds = this.targetPermissions.map(p => p.id!).filter(id => id !== undefined);

    const request = {
      roleId: this.role.id,
      permissionIds: requestIds
    };

    this.rolePermissionsService.assignPermissionsToRole(this.role.id, request).subscribe({
      next: () => {
        this.messageService.add({ severity: 'success', summary: 'Sucesso', detail: 'Permissões salvas na Role!' });
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

  // Método auxiliar pra chamar as requisições de DELETE quando um item é movido pra esquerda
  // (Opcional: você pode deixar para salvar tudo no botão "Salvar" geral,
  // mas se a API foi desenhada para deletar unitário, você chama no evento onMoveToSource do Picklist)
  onMoveToSource(event: any) {
    if (!this.role.id) return;
    const itemsMoved: PermissionModel[] = event.items;

    itemsMoved.forEach(item => {
      if(item.id) {
        this.rolePermissionsService.removePermissionFromRole(this.role.id!, item.id).subscribe({
          next: () => console.log(`Permissão ${item.code} removida da Role`),
          error: () => this.showError(`Falha ao remover a permissão ${item.code}`)
        });
      }
    });
  }

  private showError(msg: string) {
    this.messageService.add({ severity: 'error', summary: 'Erro', detail: msg });
  }
}
