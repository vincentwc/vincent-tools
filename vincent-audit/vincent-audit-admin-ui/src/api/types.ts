export interface ApiResponse<T> {
  success: boolean;
  code: string;
  message: string;
  data: T;
}

export interface PageResult<T> {
  items: T[];
  total: number;
  page: number;
  size: number;
}

export interface AuditRecordView {
  id: number;
  tenantId: string;
  operatorId: string;
  action: string;
  resourceType: string;
  resourceId: string;
  beforeJson: string | null;
  afterJson: string | null;
  clientIp: string | null;
  userAgent: string | null;
  traceId: string | null;
  createdAt: string;
}

export interface AuditSearchQuery {
  tenantId?: string;
  operatorId?: string;
  action?: string;
  resourceType?: string;
  resourceId?: string;
  createdFrom?: string;
  createdTo?: string;
  page?: number;
  size?: number;
}
