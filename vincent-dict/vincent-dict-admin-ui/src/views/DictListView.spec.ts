import { flushPromises, mount, type VueWrapper } from '@vue/test-utils';
import ElementPlus from 'element-plus';
import { createMemoryHistory, createRouter, type Router } from 'vue-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { nextTick } from 'vue';
import { ApiError } from '../api/http';
import type { AdminCapabilities, DictSummary, PageResult } from '../api/types';
import DictListView from './DictListView.vue';

const api = vi.hoisted(() => ({
  pageDicts: vi.fn(),
  createDict: vi.fn(),
  getCapabilities: vi.fn(),
  updateDict: vi.fn(),
  changeDictStatus: vi.fn(),
  deleteDict: vi.fn(),
  restoreDict: vi.fn()
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

const ACTIVE: DictSummary = {
  id: 10,
  code: 'ORDER_STATUS',
  name: 'Order',
  description: '',
  enabled: true,
  sortNo: 1,
  deleted: false
};

const DELETED: DictSummary = {
  id: 11,
  code: 'OLD_DICT',
  name: 'Old',
  description: '',
  enabled: false,
  sortNo: 2,
  deleted: true
};

function pageOf(items: DictSummary[]): PageResult<DictSummary> {
  return { items, total: items.length, page: 1, size: 20 };
}

function capabilities(): AdminCapabilities {
  return { tenantDirectoryAvailable: true, permissions: { ...ALL_PERMISSIONS } };
}

describe('DictListView', () => {
  let wrapper: VueWrapper;
  let router: Router;
  let warnSpy: ReturnType<typeof vi.spyOn>;

  beforeEach(() => {
    warnSpy = vi.spyOn(console, 'warn').mockImplementation(() => undefined);
    api.pageDicts.mockResolvedValue(pageOf([ACTIVE, DELETED]));
    api.getCapabilities.mockResolvedValue(capabilities());
    api.createDict.mockResolvedValue({ id: 12 });
    api.deleteDict.mockResolvedValue(undefined);
    api.restoreDict.mockResolvedValue(undefined);
    api.changeDictStatus.mockResolvedValue(undefined);
    api.updateDict.mockResolvedValue(undefined);
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
    router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: '/', name: 'dicts', component: DictListView },
        { path: '/dicts/:dictId', name: 'dict-detail', component: { template: '<div>detail</div>' } }
      ]
    });
    await router.push('/');
    await router.isReady();
    wrapper = mount(DictListView, {
      attachTo: document.body,
      global: { plugins: [ElementPlus, router] }
    });
    await flushPromises();
    await nextTick();
    return wrapper;
  }

  it('sends status and deleted filters with default page size 20', async () => {
    await mountList();
    expect(api.pageDicts).toHaveBeenCalledWith(expect.objectContaining({
      page: 1,
      size: 20
    }));

    api.pageDicts.mockClear();
    await wrapper.getComponent('[data-testid="filter-status"]').setValue(false);
    await wrapper.getComponent('[data-testid="filter-deleted"]').setValue(true);
    await wrapper.get('[data-testid="search"]').trigger('click');
    await flushPromises();

    expect(api.pageDicts).toHaveBeenCalledWith(expect.objectContaining({
      enabled: false,
      includeDeleted: true,
      page: 1,
      size: 20
    }));
  });

  it('shows local validation for a lowercase dict code and does not submit', async () => {
    await mountList();
    await wrapper.get('[data-testid="create-dict"]').trigger('click');
    await nextTick();

    await wrapper.get('[data-testid="dict-code"]').setValue('order_type');
    await wrapper.get('[data-testid="dict-name"]').setValue('Order type');
    await wrapper.get('[data-testid="dict-submit"]').trigger('click');
    await flushPromises();
    await nextTick();

    expect(api.createDict).not.toHaveBeenCalled();
    expect(wrapper.text()).toContain('编码须为大写英文、数字或下划线，且以字母开头');
  });

  it('deleted rows only expose view and restore', async () => {
    await mountList();
    const actions = wrapper.get('[data-testid="dict-actions-11"]');
    expect(actions.text()).toContain('查看');
    expect(actions.text()).toContain('恢复');
    expect(actions.text()).not.toContain('编辑');
    expect(actions.text()).not.toContain('删除');
    expect(actions.text()).not.toContain('启用');
    expect(actions.text()).not.toContain('停用');
  });

  it('shows a form API error inside the open dict dialog', async () => {
    api.createDict.mockRejectedValue(new ApiError('DICT_CODE_CONFLICT', 'dict code already exists'));
    await mountList();
    await wrapper.get('[data-testid="create-dict"]').trigger('click');
    await nextTick();
    await wrapper.get('[data-testid="dict-code"]').setValue('ORDER_TYPE');
    await wrapper.get('[data-testid="dict-name"]').setValue('Order type');
    await wrapper.get('[data-testid="dict-submit"]').trigger('click');
    await flushPromises();
    await nextTick();

    const dialog = wrapper.get('.el-dialog');
    expect(dialog.find('[data-testid="dict-submit"]').exists()).toBe(true);
    expect(dialog.get('[data-testid="error-alert"]').text()).toContain('dict code already exists');
  });

  it('DICT_NOT_EMPTY shows the server message and keeps the row', async () => {
    api.deleteDict.mockRejectedValue(new ApiError('DICT_NOT_EMPTY', 'dictionary contains undeleted items'));
    await mountList();

    await wrapper.get('[data-testid="delete-10"]').trigger('click');
    await nextTick();
    await wrapper.get('[data-testid="confirm-delete"]').trigger('click');
    await flushPromises();
    await nextTick();

    expect(wrapper.get('[data-testid="error-alert"]').text()).toContain('dictionary contains undeleted items');
    expect(wrapper.text()).toContain('ORDER_STATUS');
    expect(wrapper.find('[data-testid="dict-actions-10"]').exists()).toBe(true);
  });
});
