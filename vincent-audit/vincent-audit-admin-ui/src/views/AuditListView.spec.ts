import { flushPromises, mount, type VueWrapper } from '@vue/test-utils';
import ElementPlus from 'element-plus';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { nextTick } from 'vue';
import { ApiError } from '../api/http';
import type { AuditRecordView, PageResult } from '../api/types';
import AuditListView from './AuditListView.vue';

const api = vi.hoisted(() => ({
  searchRecords: vi.fn()
}));

vi.mock('../api/audit', () => api);

const SAMPLE: AuditRecordView = {
  id: 1,
  tenantId: 'tenant-a',
  operatorId: 'operator',
  action: 'UPDATE',
  resourceType: 'ORDER',
  resourceId: '1001',
  beforeJson: '{"status":"NEW"}',
  afterJson: '{"status":"DONE"}',
  clientIp: '127.0.0.1',
  userAgent: 'JUnit',
  traceId: 'trace-1',
  createdAt: '2026-08-15T00:00:00.000Z'
};

function pageOf(items: AuditRecordView[]): PageResult<AuditRecordView> {
  return { items, total: items.length, page: 1, size: 20 };
}

describe('AuditListView', () => {
  let wrapper: VueWrapper;
  let warnSpy: ReturnType<typeof vi.spyOn>;

  beforeEach(() => {
    warnSpy = vi.spyOn(console, 'warn').mockImplementation(() => undefined);
    api.searchRecords.mockResolvedValue(pageOf([SAMPLE]));
  });

  afterEach(() => {
    const warnings = warnSpy.mock.calls.map((args) => args.map(String).join(' '));
    warnSpy.mockRestore();
    wrapper?.unmount();
    document.body.innerHTML = '';
    vi.clearAllMocks();
    expect(warnings).toEqual([]);
  });

  async function mountList(): Promise<VueWrapper> {
    wrapper = mount(AuditListView, {
      attachTo: document.body,
      global: { plugins: [ElementPlus] }
    });
    await flushPromises();
    await nextTick();
    return wrapper;
  }

  it('loads records with default page size 20', async () => {
    await mountList();
    expect(api.searchRecords).toHaveBeenCalledWith(expect.objectContaining({
      page: 1,
      size: 20
    }));
    expect(wrapper.text()).toContain('ORDER');
    expect(wrapper.text()).toContain('1001');
  });

  it('sends filter values on search', async () => {
    await mountList();
    api.searchRecords.mockClear();

    await wrapper.get('[data-testid="filter-tenant"]').setValue('tenant-b');
    await wrapper.get('[data-testid="filter-action"]').setValue('DELETE');
    await wrapper.get('[data-testid="search"]').trigger('click');
    await flushPromises();

    expect(api.searchRecords).toHaveBeenCalledWith(expect.objectContaining({
      tenantId: 'tenant-b',
      action: 'DELETE',
      page: 1,
      size: 20
    }));
  });

  it('shows API errors in the alert', async () => {
    api.searchRecords.mockRejectedValue(new ApiError('PERMISSION_DENIED', 'permission denied'));
    await mountList();
    expect(wrapper.get('[data-testid="error-alert"]').text()).toContain('permission denied');
  });

  it('opens detail dialog with before and after json', async () => {
    await mountList();
    await wrapper.get('[data-testid="detail-1"]').trigger('click');
    await nextTick();
    expect(wrapper.get('[data-testid="before-json"]').text()).toContain('"status": "NEW"');
    expect(wrapper.get('[data-testid="after-json"]').text()).toContain('"status": "DONE"');
  });
});
