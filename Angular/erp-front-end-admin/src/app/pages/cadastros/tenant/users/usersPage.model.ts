export interface UsersPageModel {
  id?: string;
  tenantId: string;
  email: string;
  displayName: string;
  active: boolean;
  lockedUntil?: string;
  createdDate?: string;
  createdBy?: string;
  lastUpdateDate?: string;
  lastUpdatedBy?: string;
}

export interface UserAccountModel {
  id?: string;
  tenantId?: number;
  email?: string;
  passwordHash?: string; // Senha em texto plano do form
  displayName?: string;
  active?: boolean;
}
