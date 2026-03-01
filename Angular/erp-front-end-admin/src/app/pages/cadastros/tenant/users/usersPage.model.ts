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
