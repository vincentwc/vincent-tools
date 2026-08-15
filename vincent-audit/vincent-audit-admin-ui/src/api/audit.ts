import { http, unwrap } from './http';
import type { AuditRecordView, AuditSearchQuery, PageResult } from './types';

export function searchRecords(query: AuditSearchQuery = {}): Promise<PageResult<AuditRecordView>> {
  return unwrap(http.get('/records', { params: query }));
}
