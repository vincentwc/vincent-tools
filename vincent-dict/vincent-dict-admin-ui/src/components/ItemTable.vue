<template>
  <el-table :data="items" row-key="id" style="width: 100%">
    <el-table-column prop="code" label="编码" min-width="140" />
    <el-table-column prop="name" label="名称" min-width="140" />
    <el-table-column label="状态" width="90">
      <template #default="{ row }">{{ row.enabled ? '启用' : '停用' }}</template>
    </el-table-column>
    <el-table-column label="来源" width="100">
      <template #default="{ row }">{{ row.source }}</template>
    </el-table-column>
    <el-table-column v-if="showTenant" label="租户" min-width="140">
      <template #default="{ row }">{{ tenantLabel(row) }}</template>
    </el-table-column>
    <el-table-column label="操作" min-width="240">
      <template #default="{ row }">
        <div :data-testid="'item-actions-' + row.id">
          <el-button type="primary" text @click="$emit('view', row)">查看</el-button>
          <template v-if="!row.deleted && !readOnly">
            <el-button
              v-if="can('ITEM_UPDATE')"
              type="primary"
              text
              :data-testid="'edit-' + row.id"
              @click="$emit('edit', row)"
            >
              编辑
            </el-button>
            <el-button
              v-if="can('ITEM_ENABLE_DISABLE')"
              type="primary"
              text
              @click="$emit('status', row)"
            >
              {{ row.enabled ? '停用' : '启用' }}
            </el-button>
            <el-button
              v-if="can('ITEM_DELETE')"
              type="danger"
              text
              @click="$emit('delete', row)"
            >
              删除
            </el-button>
          </template>
          <el-button
            v-if="row.deleted && can('ITEM_RESTORE')"
            type="primary"
            text
            @click="$emit('restore', row)"
          >
            恢复
          </el-button>
        </div>
      </template>
    </el-table-column>
  </el-table>
</template>

<script setup lang="ts">
import type { AdminCapabilities, DictItemDetail } from '../api/types';
import { hasPermission } from '../permissions';

const props = defineProps<{
  items: DictItemDetail[];
  capabilities: AdminCapabilities | null;
  tenantName?: string;
  showTenant?: boolean;
  readOnly?: boolean;
}>();

defineEmits<{
  view: [item: DictItemDetail];
  edit: [item: DictItemDetail];
  status: [item: DictItemDetail];
  delete: [item: DictItemDetail];
  restore: [item: DictItemDetail];
}>();

function can(name: string): boolean {
  return hasPermission(props.capabilities, name);
}

function tenantLabel(item: DictItemDetail): string {
  if (item.source === 'DEFAULT' || item.tenantId === null || item.tenantId === '0') {
    return '—';
  }
  return props.tenantName && props.tenantName.length > 0 ? props.tenantName : item.tenantId;
}
</script>
