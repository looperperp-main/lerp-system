export interface TenantLoginResponse {
  username: string;
  token: string;
  refreshToken: string;
  tenantId: number;
  tenantName: string;
  tenantCnpj: string;
}
