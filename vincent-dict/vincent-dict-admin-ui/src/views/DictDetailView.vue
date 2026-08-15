<template>
  <div class="dict-detail">
    <ErrorAlert :message="error" @clear="error = ''" />
    <el-page-header @back="goBack">
      <template #content>
        <span>{{ dict?.name ?? '字典详情' }}</span>
      </template>
    </el-page-header>

    <el-descriptions v-if="dict" class="basics" :column="3" border>
      <el-descriptions-item label="编码">{{ dict.code }}</el-descriptions-item>
      <el-descriptions-item label="名称">{{ dict.name }}</el-descriptions-item>
      <el-descriptions-item label="状态">{{ dict.enabled ? '启用' : '停用' }}</el-descriptions-item>
      <el-descriptions-item label="描述">{{ dict.description }}</el-descriptions-item>
      <el-descriptions-item label="排序">{{ dict.sortNo }}</el-descriptions-item>
      <el-descriptions-item label="已删除">{{ dict.deleted ? '是' : '否' }}</el-descriptions-item>
    </el-descriptions>

    <el-form :inline="true" class="item-filters" @submit.prevent="searchItems">
      <el-form-item label="编码">
        <el-input v-model="query.code" clearable />
      </el-form-item>
      <el-form-item label="名称">
        <el-input v-model="query.name" clearable />
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="query.enabled" clearable placeholder="状态">
          <el-option label="启用" :value="true" />
          <el-option label="停用" :value="false" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-checkbox v-model="query.includeDeleted" data-testid="filter-deleted-items">
          包含已删除
        </el-checkbox>
      </el-form-item>
      <el-form-item>
        <el-button data-testid="search-items" type="primary" native-type="submit">查询</el-button>
      </el-form-item>
    </el-form>

    <el-tabs v-model="activeTab" class="item-tabs">
      <el-tab-pane label="默认条目" name="default">
        <el-button
          v-if="activeTab === 'default' && canMutateItems && can('ITEM_CREATE')"
          data-testid="create-item"
          type="primary"
          @click="openCreate"
        >
          创建
        </el-button>
        <ItemTable
          :items="items"
          :capabilities="capabilities"
          :read-only="!canMutateItems"
          @view="openView"
          @edit="openEdit"
          @status="toggleStatus"
          @delete="askDelete"
          @restore="restore"
        />
        <el-pagination
          class="pager"
          layout="total, sizes, prev, pager, next"
          :current-page="query.page"
          :page-size="query.size"
          :page-sizes="PAGE_SIZES"
          :total="total"
          @current-change="onPageChange"
          @size-change="onSizeChange"
        />
      </el-tab-pane>
      <el-tab-pane
        v-if="capabilities?.tenantDirectoryAvailable"
        label="租户条目"
        name="tenant"
      >
        <TenantPicker v-model="selectedTenantId" @change="onTenantChange" />
        <el-button
          v-if="activeTab === 'tenant' && canMutateItems && can('ITEM_CREATE')"
          data-testid="create-item"
          type="primary"
          :disabled="!selectedTenantId"
          @click="openCreate"
        >
          创建
        </el-button>
        <ItemTable
          :items="items"
          :capabilities="capabilities"
          :show-tenant="true"
          :tenant-name="selectedTenantName"
          :read-only="!canMutateItems"
          @view="openView"
          @edit="openEdit"
          @status="toggleStatus"
          @delete="askDelete"
          @restore="restore"
        />
        <el-pagination
          class="pager"
          layout="total, sizes, prev, pager, next"
          :current-page="query.page"
          :page-size="query.size"
          :page-sizes="PAGE_SIZES"
          :total="total"
          @current-change="onPageChange"
          @size-change="onSizeChange"
        />
      </el-tab-pane>
    </el-tabs>

    <el-dialog
      v-if="formVisible"
      :model-value="true"
      :title="formTitle"
      :teleported="false"
      :lock-scroll="false"
      @close="formVisible = false"
    >
      <ItemForm
        :mode="formMode"
        :model="editing ?? undefined"
        @submit="onFormSubmit"
        @cancel="formVisible = false"
      />
    </el-dialog>

    <el-dialog
      v-if="confirmVisible"
      :model-value="true"
      title="确认删除"
      :teleported="false"
      :lock-scroll="false"
      @close="confirmVisible = false"
    >
      <span>确定删除该条目？</span>
      <template #footer>
        <el-button @click="confirmVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmDelete">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import {
  changeItemStatus,
  createDefaultItem,
  createTenantItem,
  deleteItem,
  getCapabilities,
  getDict,
  pageItems,
  restoreItem,
  updateItem
} from '../api/dict';
import type {
  AdminCapabilities,
  CreateItemPayload,
  DictDetail,
  DictItemDetail,
  TenantOption,
  UpdateItemPayload
} from '../api/types';
import ErrorAlert from '../components/ErrorAlert.vue';
import ItemForm from '../components/ItemForm.vue';
import ItemTable from '../components/ItemTable.vue';
import TenantPicker from '../components/TenantPicker.vue';
import { errorMessage } from '../errors';
import { clampPageSize, DEFAULT_PAGE_SIZE, PAGE_SIZES } from '../pagination';
import { hasPermission } from '../permissions';

const route = useRoute();
const router = useRouter();
const error = ref('');
const dict = ref<DictDetail | null>(null);
const capabilities = ref<AdminCapabilities | null>(null);
const items = ref<DictItemDetail[]>([]);
const total = ref(0);
const activeTab = ref('default');
const selectedTenantId = ref('');
const selectedTenantName = ref('');
const formVisible = ref(false);
const formMode = ref<'create' | 'edit' | 'view'>('create');
const editing = ref<DictItemDetail | null>(null);
const confirmVisible = ref(false);
const pendingDelete = ref<DictItemDetail | null>(null);
const query = reactive({
  code: '',
  name: '',
  enabled: undefined as boolean | undefined,
  includeDeleted: false,
  page: 1,
  size: DEFAULT_PAGE_SIZE
});

const dictId = computed(() => Number(route.params.dictId));
const canMutateItems = computed(() => dict.value !== null && !dict.value.deleted);
const formTitle = computed(() => {
  if (formMode.value === 'create') {
    return '创建条目';
  }
  if (formMode.value === 'edit') {
    return '编辑条目';
  }
  return '查看条目';
});

function can(name: string): boolean {
  return hasPermission(capabilities.value, name);
}

function goBack(): void {
  void router.push({ name: 'dicts' });
}

async function loadDict(): Promise<void> {
  dict.value = await getDict(dictId.value, true);
}

async function loadItems(): Promise<void> {
  const result = await pageItems(dictId.value, {
    tenantId: activeTab.value === 'tenant' ? selectedTenantId.value || undefined : undefined,
    code: query.code || undefined,
    name: query.name || undefined,
    enabled: query.enabled,
    includeDeleted: query.includeDeleted,
    page: query.page,
    size: clampPageSize(query.size)
  });
  items.value = result.items;
  total.value = result.total;
}

async function reload(): Promise<void> {
  try {
    await loadItems();
  } catch (err) {
    error.value = errorMessage(err);
  }
}

function searchItems(): void {
  query.page = 1;
  void reload();
}

function onTenantChange(tenant: TenantOption): void {
  selectedTenantId.value = tenant.tenantId;
  selectedTenantName.value = tenant.name;
  query.page = 1;
  void reload();
}

function onPageChange(page: number): void {
  query.page = page;
  void reload();
}

function onSizeChange(size: number): void {
  query.size = clampPageSize(size);
  query.page = 1;
  void reload();
}

function openCreate(): void {
  if (activeTab.value === 'tenant' && !selectedTenantId.value) {
    error.value = '请先选择租户';
    return;
  }
  formMode.value = 'create';
  editing.value = null;
  formVisible.value = true;
}

function openEdit(item: DictItemDetail): void {
  formMode.value = 'edit';
  editing.value = item;
  formVisible.value = true;
}

function openView(item: DictItemDetail): void {
  formMode.value = 'view';
  editing.value = item;
  formVisible.value = true;
}

async function toggleStatus(item: DictItemDetail): Promise<void> {
  try {
    await changeItemStatus(item.id, !item.enabled);
    await loadItems();
  } catch (err) {
    error.value = errorMessage(err);
  }
}

function askDelete(item: DictItemDetail): void {
  pendingDelete.value = item;
  confirmVisible.value = true;
}

async function confirmDelete(): Promise<void> {
  const item = pendingDelete.value;
  confirmVisible.value = false;
  if (item === null) {
    return;
  }
  try {
    await deleteItem(item.id);
    await loadItems();
  } catch (err) {
    error.value = errorMessage(err);
  }
}

async function restore(item: DictItemDetail): Promise<void> {
  try {
    await restoreItem(item.id);
    await loadItems();
  } catch (err) {
    error.value = errorMessage(err);
  }
}

async function onFormSubmit(payload: CreateItemPayload | UpdateItemPayload): Promise<void> {
  try {
    if (formMode.value === 'create') {
      if (activeTab.value === 'tenant') {
        await createTenantItem(dictId.value, selectedTenantId.value, payload as CreateItemPayload);
      } else {
        await createDefaultItem(dictId.value, payload as CreateItemPayload);
      }
    } else if (formMode.value === 'edit' && editing.value !== null) {
      await updateItem(editing.value.id, payload as UpdateItemPayload);
    }
    formVisible.value = false;
    await loadItems();
  } catch (err) {
    error.value = errorMessage(err);
  }
}

watch(activeTab, () => {
  query.page = 1;
  if (activeTab.value === 'default') {
    void reload();
  } else if (selectedTenantId.value) {
    void reload();
  } else {
    items.value = [];
    total.value = 0;
  }
});

onMounted(async () => {
  try {
    capabilities.value = await getCapabilities();
    await loadDict();
    await loadItems();
  } catch (err) {
    error.value = errorMessage(err);
  }
});
</script>

<style scoped>
.dict-detail {
  padding: 16px;
}

.basics {
  margin: 16px 0;
}

.item-filters {
  margin-top: 16px;
}

.item-tabs {
  margin-top: 8px;
}

.pager {
  margin-top: 16px;
  justify-content: flex-end;
}
</style>
