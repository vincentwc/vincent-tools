import { flushPromises, mount, type VueWrapper } from '@vue/test-utils';
import ElementPlus from 'element-plus';
import { createMemoryHistory, createRouter } from 'vue-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { nextTick } from 'vue';
import { ApiError } from '../api/http';
import type { AdminCapabilities, DictDetail, DictItemDetail, PageResult, TenantOption } from '../api/types';
import DictDetailView from './DictDetailView.vue';

const api = vi.hoisted(() => ({
  getCapabilities: vi.fn(),
  getDict: vi.fn(),
  pageItems: vi.fn(),
  pageTenants: vi.fn(),
  createDefaultItem: vi.fn(),
  createTenantItem: vi.fn(),
  updateItem: vi.fn(),
  changeItemStatus: vi.fn(),
  deleteItem: vi.fn(),
  restoreItem: vi.fn()
}));

vi.mock('../api/dict', () => api);

const ALL_PERMISSIONS: Record<string, boolean> = {
  DICT_VIEW: true,
  DICT_CREATE: true,
  DICT_UPDATE: true,
  DICT_ENABLE_DISABLE: true,
  DICT_DELETE: true,
  DICT_RESTORE: true,
  ITEM_CREATE: true,
  ITEM_UPDATE: true,
  ITEM_ENABLE_DISABLE: true,
  ITEM_DELETE: true,
  ITEM_RESTORE: true
};

const DICT: DictDetail = {
  id: 10,
  code: 'ORDER_STATUS',
  name: 'Order',
  description: 'Lifecycle',
  enabled: true,
  sortNo: 1,
  deleted: false,
  version: 1,
  createdBy: 'op',
  createdAt: '2026-08-14T00:00:00Z',
  updatedBy: 'op',
  updatedAt: '2026-08-14T00:00:00Z'
};

const DEFAULT_ITEM: DictItemDetail = {
  id: 90,
  dictId: 10,
  code: 'WAIT_CONFIRM',
  name: 'Waiting',
  tenantId: null,
  description: '',
  enabled: true,
  sortNo: 20,
  deleted: false,
  source: 'DEFAULT',
  version: 1,
  createdBy: 'op',
  createdAt: '2026-08-14T00:00:00Z',
  updatedBy: 'op',
  updatedAt: '2026-08-14T00:00:00Z'
};

const DELETED_ITEM: DictItemDetail = {
  ...DEFAULT_ITEM,
  id: 91,
  code: 'OLD_ITEM',
  name: 'Old item',
  deleted: true,
  enabled: false
};

const TENANT_ITEM: DictItemDetail = {
  id: 92,
  dictId: 10,
  code: 'WAIT_PAY',
  name: 'Pay',
  tenantId: 'tenant-b',
  description: '',
  enabled: true,
  sortNo: 21,
  deleted: false,
  source: 'TENANT',
  version: 1,
  createdBy: 'op',
  createdAt: '2026-08-14T00:00:00Z',
  updatedBy: 'op',
  updatedAt: '2026-08-14T00:00:00Z'
};

const TENANT: TenantOption = { tenantId: 'tenant-b', name: 'Tenant B' };

function itemPage(items: DictItemDetail[]): PageResult<DictItemDetail> {
  return { items, total: items.length, page: 1, size: 20 };
}

function caps(tenantDirectoryAvailable: boolean): AdminCapabilities {
  return { tenantDirectoryAvailable, permissions: { ...ALL_PERMISSIONS } };
}

describe('DictDetailView', () => {
  let wrapper: VueWrapper;
  let warnSpy: ReturnType<typeof vi.spyOn>;

  beforeEach(() => {
    warnSpy = vi.spyOn(console, 'warn').mockImplementation(() => undefined);
    api.getCapabilities.mockResolvedValue(caps(true));
    api.getDict.mockResolvedValue(DICT);
    api.pageItems.mockImplementation((_dictId: number, query: { tenantId?: string } = {}) => {
      if (query.tenantId) {
        return Promise.resolve(itemPage([TENANT_ITEM]));
      }
      return Promise.resolve(itemPage([DEFAULT_ITEM, DELETED_ITEM]));
    });
    api.pageTenants.mockResolvedValue({ items: [TENANT], total: 1, page: 1, size: 20 });
    api.createDefaultItem.mockResolvedValue({ id: 93 });
    api.createTenantItem.mockResolvedValue({ id: 94 });
    api.updateItem.mockResolvedValue(undefined);
    api.changeItemStatus.mockResolvedValue(undefined);
    api.deleteItem.mockResolvedValue(undefined);
    api.restoreItem.mockResolvedValue(undefined);
  });

  afterEach(() => {
    const warnings = warnSpy.mock.calls.map((args) => args.map(String).join(' '));
    warnSpy.mockRestore();
    wrapper?.unmount();
    document.body.innerHTML = '';
    vi.clearAllMocks();
    expect(warnings).toEqual([]);
  });

  async function mountDetail(): Promise<VueWrapper> {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: '/', component: { template: '<div />' } },
        { path: '/dicts/:dictId', name: 'dict-detail', component: DictDetailView }
      ]
    });
    await router.push('/dicts/10');
    await router.isReady();
    wrapper = mount(DictDetailView, {
      attachTo: document.body,
      global: { plugins: [ElementPlus, router] }
    });
    await flushPromises();
    await nextTick();
    return wrapper;
  }

  async function openTenantTab(): Promise<void> {
    const tab = wrapper.findAll('.el-tabs__item').find((node) => node.text() === '租户条目');
    expect(tab).toBeTruthy();
    await tab!.trigger('click');
    await flushPromises();
    await nextTick();
  }

  it('hides the tenant items tab when the tenant directory is unavailable', async () => {
    api.getCapabilities.mockResolvedValue(caps(false));
    await mountDetail();
    expect(wrapper.text()).toContain('默认条目');
    expect(wrapper.text()).not.toContain('租户条目');
  });

  it('shows source DEFAULT/TENANT and the tenant display name', async () => {
    await mountDetail();
    expect(wrapper.text()).toContain('DEFAULT');
    expect(wrapper.text()).toContain('WAIT_CONFIRM');

    await openTenantTab();
    const option = wrapper.get('[data-testid="tenant-option-tenant-b"]');
    await option.trigger('click');
    await flushPromises();
    await nextTick();

    expect(wrapper.text()).toContain('TENANT');
    expect(wrapper.text()).toContain('Tenant B');
    expect(wrapper.text()).toContain('WAIT_PAY');
  });

  it('re-validates the selected tenant on the server when creating a tenant item', async () => {
    await mountDetail();
    await openTenantTab();
    await wrapper.get('[data-testid="tenant-option-tenant-b"]').trigger('click');
    await flushPromises();
    await nextTick();

    await wrapper.get('[data-testid="create-item"]').trigger('click');
    await nextTick();
    await wrapper.get('[data-testid="item-code"]').setValue('WAIT_PAY');
    await wrapper.get('[data-testid="item-name"]').setValue('Pay');
    await wrapper.get('[data-testid="item-submit"]').trigger('click');
    await flushPromises();

    expect(api.createTenantItem).toHaveBeenCalledWith(10, 'tenant-b', expect.objectContaining({
      code: 'WAIT_PAY',
      name: 'Pay'
    }));
  });

  it('lets code be edited only on create and omits code, dict and tenant on update', async () => {
    await mountDetail();
    await wrapper.get('[data-testid="create-item"]').trigger('click');
    await nextTick();
    expect(wrapper.find('[data-testid="item-code"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="item-dict"]').exists()).toBe(false);
    expect(wrapper.find('[data-testid="item-tenant"]').exists()).toBe(false);
    await wrapper.get('[data-testid="item-cancel"]').trigger('click');
    await nextTick();

    await wrapper.get('[data-testid="edit-90"]').trigger('click');
    await nextTick();
    expect(wrapper.find('[data-testid="item-code"]').exists()).toBe(false);
    expect(wrapper.find('[data-testid="item-dict"]').exists()).toBe(false);
    expect(wrapper.find('[data-testid="item-tenant"]').exists()).toBe(false);
    expect(wrapper.find('[data-testid="item-name"]').exists()).toBe(true);
  });

  it('sends includeDeleted when listing items for restore', async () => {
    await mountDetail();
    api.pageItems.mockClear();
    await wrapper.getComponent('[data-testid="filter-deleted-items"]').setValue(true);
    await wrapper.get('[data-testid="search-items"]').trigger('click');
    await flushPromises();
    expect(api.pageItems).toHaveBeenCalledWith(10, expect.objectContaining({
      includeDeleted: true,
      page: 1,
      size: 20
    }));
  });

  it('deleted items only expose view and restore', async () => {
    await mountDetail();
    const actions = wrapper.get('[data-testid="item-actions-91"]');
    expect(actions.text()).toContain('查看');
    expect(actions.text()).toContain('恢复');
    expect(actions.text()).not.toContain('编辑');
    expect(actions.text()).not.toContain('删除');
    expect(actions.text()).not.toContain('启用');
    expect(actions.text()).not.toContain('停用');
  });

  it('does not load or mutate default items on the tenant tab until a tenant is selected', async () => {
    await mountDetail();
    api.pageItems.mockClear();
    await openTenantTab();

    expect(wrapper.get('[data-testid="search-items"]').attributes('disabled')).toBeDefined();
    const pagers = wrapper.findAllComponents({ name: 'ElPagination' });
    expect(pagers[pagers.length - 1].props('disabled')).toBe(true);
    expect(wrapper.find('[data-testid="edit-90"]').exists()).toBe(false);
    expect(wrapper.find('[data-testid="create-item"]').attributes('disabled')).toBeDefined();

    await wrapper.get('[data-testid="search-items"]').trigger('click');
    await flushPromises();
    expect(api.pageItems).not.toHaveBeenCalled();
    expect(wrapper.text()).not.toContain('WAIT_CONFIRM');
  });

  it('shows a form API error inside the open item dialog', async () => {
    api.createDefaultItem.mockRejectedValue(new ApiError('ITEM_CODE_CONFLICT', 'item code already exists'));
    await mountDetail();
    await wrapper.get('[data-testid="create-item"]').trigger('click');
    await nextTick();
    await wrapper.get('[data-testid="item-code"]').setValue('WAIT_PAY');
    await wrapper.get('[data-testid="item-name"]').setValue('Pay');
    await wrapper.get('[data-testid="item-submit"]').trigger('click');
    await flushPromises();
    await nextTick();

    const dialog = wrapper.get('.el-dialog');
    expect(dialog.find('[data-testid="item-submit"]').exists()).toBe(true);
    expect(dialog.get('[data-testid="error-alert"]').text()).toContain('item code already exists');
  });

  it('shows local validation for a lowercase item code', async () => {
    await mountDetail();
    await wrapper.get('[data-testid="create-item"]').trigger('click');
    await nextTick();
    await wrapper.get('[data-testid="item-code"]').setValue('wait_confirm');
    await wrapper.get('[data-testid="item-name"]').setValue('Waiting');
    await wrapper.get('[data-testid="item-submit"]').trigger('click');
    await flushPromises();
    await nextTick();

    expect(api.createDefaultItem).not.toHaveBeenCalled();
    expect(wrapper.text()).toContain('编码须为大写英文、数字或下划线，且以字母开头');
  });
});
