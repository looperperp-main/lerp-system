export interface RoleModel {
  id?: string;
  name: string;
  tenantId: number; // Mudamos para number/Long conforme seu banco
  createdDate?: string;
  createdBy?: string;
  lastUpdateDate?: string;
  lastUpdateBy?: string;
}
