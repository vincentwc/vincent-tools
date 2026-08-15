import { flushPromises, mount, type VueWrapper } from '@vue/test-utils';
import ElementPlus from 'element-plus';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import TenantPicker from './TenantPicker.vue';

const api = vi.hoisted(() => ({
  pageTenants: vi.fn()
}));

vi.mock('../api/dict', () => api);

describe('TenantPicker', () => {
  let wrapper: VueWrapper;
  let warnSpy: ReturnType<typeof vi.spyOn>;

  beforeEach(() => {
    warnSpy = vi.spyOn(console, 'warn').mockImplementation(() => undefined);
    api.pageTenants.mockResolvedValue({
      items: [{ tenantId: 'tenant-b', name: 'Tenant B' }],
      total: 1,
      page: 1,
      size: 20
    });
  });

  afterEach(() => {
    const warnings = warnSpy.mock.calls.map((args) => args.map(String).join(' '));
    warnSpy.mockRestore();
    wrapper?.unmount();
    document.body.innerHTML = '';
    vi.useRealTimers();
    vi.clearAllMocks();
    expect(warnings).toEqual([]);
  });

  function mountPicker(pageSize?: number): VueWrapper {
    wrapper = mount(TenantPicker, {
      attachTo: document.body,
      props: pageSize === undefined ? {} : { pageSize },
      global: { plugins: [ElementPlus] }
    });
    return wrapper;
  }

  it('debounces tenant search by 300ms', async () => {
    mountPicker();
    await flushPromises();
    api.pageTenants.mockClear();
    vi.useFakeTimers();

    await wrapper.get('[data-testid="tenant-search"]').setValue('ten');
    expect(api.pageTenants).not.toHaveBeenCalled();

    await vi.advanceTimersByTimeAsync(299);
    expect(api.pageTenants).not.toHaveBeenCalled();

    await vi.advanceTimersByTimeAsync(1);
    await flushPromises();
    expect(api.pageTenants).toHaveBeenCalledWith(expect.objectContaining({
      keyword: 'ten',
      page: 1,
      size: 20
    }));
  });

  it('never requests a page size greater than 100', async () => {
    mountPicker(250);
    await flushPromises();
    expect(api.pageTenants).toHaveBeenCalledWith(expect.objectContaining({
      page: 1,
      size: 100
    }));
  });
});
