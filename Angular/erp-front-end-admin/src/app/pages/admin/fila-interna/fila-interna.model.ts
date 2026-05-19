export interface FilaInternaItem {
  id: number;
  tipo: string;
  status: string;
  tenantId: number;
  tenantName: string;
  tenantCnpj: string;
  tenantEmail: string;
  payload: string;
  createdAt: string;
  updatedAt?: string;
  resolvedBy?: string;
  resolutionNotes?: string;
}

export interface UpdateStatusRequest {
  status: string;
  resolvedBy?: string;
  resolutionNotes?: string;
}

export interface FilaPage {
  content: FilaInternaItem[];
  page: { totalElements: number; totalPages: number; size: number; number: number };
}