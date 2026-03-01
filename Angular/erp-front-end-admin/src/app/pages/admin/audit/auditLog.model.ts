export interface AuditLog {
  id?: string;
  actorUserId: string;
  action: string;
  targetType?: string;
  targetId?: string;
  result: string;
  detailsJson?: string;
  correlationId?: string;
}
