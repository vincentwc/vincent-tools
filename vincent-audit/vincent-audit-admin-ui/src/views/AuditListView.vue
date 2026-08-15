<template>
  <div class="audit-list">
    <header class="page-header">
      <h1>Vincent Audit Admin</h1>
      <p class="subtitle">只读操作审计检索</p>
    </header>

    <ErrorAlert :message="error" @clear="error = ''" />

    <el-form :inline="true" @submit.prevent="search">
      <el-form-item label="租户">
        <el-input v-model="filters.tenantId" data-testid="filter-tenant" clearable />
      </el-form-item>
      <el-form-item label="操作人">
        <el-input v-model="filters.operatorId" data-testid="filter-operator" clearable />
      </el-form-item>
      <el-form-item label="动作">
        <el-input v-model="filters.action" data-testid="filter-action" clearable />
      </el-form-item>
      <el-form-item label="资源类型">
        <el-input v-model="filters.resourceType" data-testid="filter-resource-type" clearable />
      </el-form-item>
      <el-form-item label="资源 ID">
        <el-input v-model="filters.resourceId" data-testid="filter-resource-id" clearable />
      </el-form-item>
      <el-form-item label="时间范围">
        <el-date-picker
          v-model="filters.createdRange"
          data-testid="filter-created-range"
          type="datetimerange"
          range-separator="至"
          start-placeholder="开始"
          end-placeholder="结束"
          value-format="YYYY-MM-DDTHH:mm:ss.SSS[Z]"
          clearable
        />
      </el-form-item>
      <el-form-item>
        <el-button data-testid="search" type="primary" native-type="submit">查询</el-button>
        <el-button data-testid="reset" @click="resetFilters">重置</el-button>
      </el-form-item>
    </el-form>

    <el-table :data="items" row-key="id" style="width: 100%" v-loading="loading">
      <el-table-column prop="id" label="ID" width="90" />
      <el-table-column prop="tenantId" label="租户" min-width="120" />
      <el-table-column prop="operatorId" label="操作人" min-width="120" />
      <el-table-column prop="action" label="动作" min-width="120" />
      <el-table-column prop="resourceType" label="资源类型" min-width="120" />
      <el-table-column prop="resourceId" label="资源 ID" min-width="140" />
      <el-table-column label="时间" min-width="190">
        <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column prop="clientIp" label="IP" min-width="120" />
      <el-table-column prop="traceId" label="Trace" min-width="140" show-overflow-tooltip />
      <el-table-column label="详情" width="90" fixed="right">
        <template #default="{ row }">
          <el-button
            type="primary"
            text
            :data-testid="'detail-' + row.id"
            @click="openDetail(row)"
          >
            查看
          </el-button>
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
      v-if="detailVisible"
      :model-value="true"
      title="审计详情"
      width="720px"
      :teleported="false"
      :lock-scroll="false"
      @close="detailVisible = false"
    >
      <el-descriptions v-if="selected" :column="1" border>
        <el-descriptions-item label="ID">{{ selected.id }}</el-descriptions-item>
        <el-descriptions-item label="租户">{{ selected.tenantId }}</el-descriptions-item>
        <el-descriptions-item label="操作人">{{ selected.operatorId }}</el-descriptions-item>
        <el-descriptions-item label="动作">{{ selected.action }}</el-descriptions-item>
        <el-descriptions-item label="资源类型">{{ selected.resourceType }}</el-descriptions-item>
        <el-descriptions-item label="资源 ID">{{ selected.resourceId }}</el-descriptions-item>
        <el-descriptions-item label="时间">{{ formatTime(selected.createdAt) }}</el-descriptions-item>
        <el-descriptions-item label="Client IP">{{ selected.clientIp ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="User Agent">{{ selected.userAgent ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="Trace ID">{{ selected.traceId ?? '-' }}</el-descriptions-item>
      </el-descriptions>
      <div v-if="selected" class="json-blocks">
        <div class="json-block">
          <h3>变更前</h3>
          <pre data-testid="before-json">{{ prettyJson(selected.beforeJson) }}</pre>
        </div>
        <div class="json-block">
          <h3>变更后</h3>
          <pre data-testid="after-json">{{ prettyJson(selected.afterJson) }}</pre>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { searchRecords } from '../api/audit';
import type { AuditRecordView } from '../api/types';
import ErrorAlert from '../components/ErrorAlert.vue';
import { errorMessage } from '../errors';
import { clampPageSize, DEFAULT_PAGE_SIZE, PAGE_SIZES } from '../pagination';

const error = ref('');
const loading = ref(false);
const items = ref<AuditRecordView[]>([]);
const total = ref(0);
const detailVisible = ref(false);
const selected = ref<AuditRecordView | null>(null);

const filters = reactive({
  tenantId: '',
  operatorId: '',
  action: '',
  resourceType: '',
  resourceId: '',
  createdRange: null as [string, string] | null,
  page: 1,
  size: DEFAULT_PAGE_SIZE
});

function buildQuery() {
  const query: Record<string, string | number | undefined> = {
    tenantId: filters.tenantId || undefined,
    operatorId: filters.operatorId || undefined,
    action: filters.action || undefined,
    resourceType: filters.resourceType || undefined,
    resourceId: filters.resourceId || undefined,
    page: filters.page,
    size: clampPageSize(filters.size)
  };
  if (filters.createdRange !== null) {
    query.createdFrom = filters.createdRange[0];
    query.createdTo = filters.createdRange[1];
  }
  return query;
}

async function load(): Promise<void> {
  loading.value = true;
  try {
    const result = await searchRecords(buildQuery());
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

function resetFilters(): void {
  filters.tenantId = '';
  filters.operatorId = '';
  filters.action = '';
  filters.resourceType = '';
  filters.resourceId = '';
  filters.createdRange = null;
  filters.page = 1;
  filters.size = DEFAULT_PAGE_SIZE;
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

function openDetail(row: AuditRecordView): void {
  selected.value = row;
  detailVisible.value = true;
}

function formatTime(value: string): string {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }
  return date.toLocaleString('zh-CN', { hour12: false });
}

function prettyJson(value: string | null): string {
  if (value === null || value.length === 0) {
    return '-';
  }
  try {
    return JSON.stringify(JSON.parse(value), null, 2);
  } catch {
    return value;
  }
}

onMounted(async () => {
  await load();
});
</script>

<style scoped>
.audit-list {
  padding: 16px;
}

.page-header {
  margin-bottom: 16px;
}

.page-header h1 {
  margin: 0;
  font-size: 24px;
}

.subtitle {
  margin: 4px 0 0;
  color: #606266;
}

.pager {
  margin-top: 16px;
  justify-content: flex-end;
}

.json-blocks {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  margin-top: 16px;
}

.json-block h3 {
  margin: 0 0 8px;
  font-size: 14px;
}

.json-block pre {
  margin: 0;
  padding: 12px;
  background: #f5f7fa;
  border-radius: 4px;
  overflow: auto;
  max-height: 240px;
  white-space: pre-wrap;
  word-break: break-word;
}
</style>
