<template>
  <div class="dict-list">
    <ErrorAlert :message="error" @clear="error = ''" />
    <el-form :inline="true" @submit.prevent="search">
      <el-form-item label="编码">
        <el-input v-model="filters.code" data-testid="filter-code" clearable />
      </el-form-item>
      <el-form-item label="名称">
        <el-input v-model="filters.name" data-testid="filter-name" clearable />
      </el-form-item>
      <el-form-item label="状态">
        <el-select
          v-model="filters.enabled"
          data-testid="filter-status"
          clearable
          placeholder="状态"
        >
          <el-option label="启用" :value="true" />
          <el-option label="停用" :value="false" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-checkbox v-model="filters.includeDeleted" data-testid="filter-deleted">
          包含已删除
        </el-checkbox>
      </el-form-item>
      <el-form-item>
        <el-button data-testid="search" type="primary" native-type="submit">查询</el-button>
        <el-button
          v-if="can('DICT_CREATE')"
          data-testid="create-dict"
          @click="openCreate"
        >
          创建
        </el-button>
      </el-form-item>
    </el-form>

    <el-table :data="items" row-key="id" style="width: 100%" v-loading="loading">
      <el-table-column prop="code" label="编码" min-width="160" />
      <el-table-column prop="name" label="名称" min-width="160" />
      <el-table-column label="状态" width="90">
        <template #default="{ row }">{{ row.enabled ? '启用' : '停用' }}</template>
      </el-table-column>
      <el-table-column label="已删除" width="90">
        <template #default="{ row }">{{ row.deleted ? '是' : '否' }}</template>
      </el-table-column>
      <el-table-column label="操作" min-width="260">
        <template #default="{ row }">
          <div :data-testid="'dict-actions-' + row.id">
            <el-button type="primary" text @click="viewDict(row)">查看</el-button>
            <template v-if="!row.deleted">
              <el-button
                v-if="can('DICT_UPDATE')"
                type="primary"
                text
                @click="openEdit(row)"
              >
                编辑
              </el-button>
              <el-button
                v-if="can('DICT_ENABLE_DISABLE')"
                type="primary"
                text
                @click="toggleStatus(row)"
              >
                {{ row.enabled ? '停用' : '启用' }}
              </el-button>
              <el-button
                v-if="can('DICT_DELETE')"
                type="danger"
                text
                :data-testid="'delete-' + row.id"
                @click="askDelete(row)"
              >
                删除
              </el-button>
            </template>
            <el-button
              v-if="row.deleted && can('DICT_RESTORE')"
              type="primary"
              text
              @click="restore(row)"
            >
              恢复
            </el-button>
          </div>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      class="pager"
      layout="total, sizes, prev, pager, next"
      :current-page="filters.page"
      :page-size="filters.size"
      :page-sizes="PAGE_SIZES"
      :total="total"
      @current-change="onPageChange"
      @size-change="onSizeChange"
    />

    <el-dialog
      v-if="formVisible"
      :model-value="true"
      :title="formMode === 'create' ? '创建字典' : '编辑字典'"
      :teleported="false"
      :lock-scroll="false"
      @close="formVisible = false"
    >
      <ErrorAlert :message="formError" @clear="formError = ''" />
      <DictForm
        :mode="formMode"
        :model="editing ?? undefined"
        @submit="onFormSubmit"
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
      <span>确定删除该字典？</span>
      <template #footer>
        <el-button @click="confirmVisible = false">取消</el-button>
        <el-button data-testid="confirm-delete" type="primary" @click="confirmDelete">
          确定
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import {
  changeDictStatus,
  createDict,
  deleteDict,
  getCapabilities,
  pageDicts,
  restoreDict,
  updateDict
} from '../api/dict';
import type { AdminCapabilities, CreateDictPayload, DictSummary, UpdateDictPayload } from '../api/types';
import DictForm from '../components/DictForm.vue';
import ErrorAlert from '../components/ErrorAlert.vue';
import { errorMessage } from '../errors';
import { clampPageSize, DEFAULT_PAGE_SIZE, PAGE_SIZES } from '../pagination';
import { hasPermission } from '../permissions';

const router = useRouter();
const error = ref('');
const formError = ref('');
const loading = ref(false);
const capabilities = ref<AdminCapabilities | null>(null);
const items = ref<DictSummary[]>([]);
const total = ref(0);
const filters = reactive({
  code: '',
  name: '',
  enabled: undefined as boolean | undefined,
  includeDeleted: false,
  page: 1,
  size: DEFAULT_PAGE_SIZE
});
const formVisible = ref(false);
const formMode = ref<'create' | 'edit'>('create');
const editing = ref<DictSummary | null>(null);
const confirmVisible = ref(false);
const pendingDelete = ref<DictSummary | null>(null);

function can(name: string): boolean {
  return hasPermission(capabilities.value, name);
}

async function load(): Promise<void> {
  loading.value = true;
  try {
    const result = await pageDicts({
      code: filters.code || undefined,
      name: filters.name || undefined,
      enabled: filters.enabled,
      includeDeleted: filters.includeDeleted,
      page: filters.page,
      size: clampPageSize(filters.size)
    });
    items.value = result.items;
    total.value = result.total;
  } catch (err) {
    error.value = errorMessage(err);
  } finally {
    loading.value = false;
  }
}

function search(): void {
  filters.page = 1;
  void load();
}

function onPageChange(page: number): void {
  filters.page = page;
  void load();
}

function onSizeChange(size: number): void {
  filters.size = clampPageSize(size);
  filters.page = 1;
  void load();
}

function openCreate(): void {
  formError.value = '';
  formMode.value = 'create';
  editing.value = null;
  formVisible.value = true;
}

function openEdit(row: DictSummary): void {
  formError.value = '';
  formMode.value = 'edit';
  editing.value = row;
  formVisible.value = true;
}

function viewDict(row: DictSummary): void {
  void router.push({ name: 'dict-detail', params: { dictId: String(row.id) } });
}

async function toggleStatus(row: DictSummary): Promise<void> {
  try {
    await changeDictStatus(row.id, !row.enabled);
    await load();
  } catch (err) {
    error.value = errorMessage(err);
  }
}

function askDelete(row: DictSummary): void {
  pendingDelete.value = row;
  confirmVisible.value = true;
}

async function confirmDelete(): Promise<void> {
  const row = pendingDelete.value;
  confirmVisible.value = false;
  if (row === null) {
    return;
  }
  try {
    await deleteDict(row.id);
    await load();
  } catch (err) {
    error.value = errorMessage(err);
  }
}

async function restore(row: DictSummary): Promise<void> {
  try {
    await restoreDict(row.id);
    await load();
  } catch (err) {
    error.value = errorMessage(err);
  }
}

async function onFormSubmit(payload: CreateDictPayload | UpdateDictPayload): Promise<void> {
  try {
    if (formMode.value === 'create') {
      await createDict(payload as CreateDictPayload);
    } else if (editing.value !== null) {
      await updateDict(editing.value.id, payload as UpdateDictPayload);
    }
    formVisible.value = false;
    formError.value = '';
    await load();
  } catch (err) {
    formError.value = errorMessage(err);
  }
}

onMounted(async () => {
  try {
    capabilities.value = await getCapabilities();
  } catch (err) {
    error.value = errorMessage(err);
  }
  await load();
});
</script>

<style scoped>
.dict-list {
  padding: 16px;
}

.pager {
  margin-top: 16px;
  justify-content: flex-end;
}
</style>
