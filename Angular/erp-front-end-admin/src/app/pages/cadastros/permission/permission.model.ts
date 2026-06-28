export interface PermissionModel {
  id?: string;
  code: string;
  domain: string;
  // TENANT (default) ou PLATFORM. PLATFORM = só admin Syax; não visível no portal de tenant.
  scope?: 'TENANT' | 'PLATFORM';
  description: string;
  createdDate?: string;
  createdBy?: string;
  lastUpdateDate?: string;
  lastUpdatedBy?: string;
}
