<template>
  <div class="tenant-picker">
    <el-input
      v-model="keyword"
      data-testid="tenant-search"
      clearable
      placeholder="搜索租户"
    />
    <div class="tenant-options">
      <button
        v-for="option in options"
        :key="option.tenantId"
        type="button"
        class="tenant-option"
        :class="{ selected: option.tenantId === modelValue }"
        :data-testid="'tenant-option-' + option.tenantId"
        @click="select(option)"
      >
        {{ option.name }}
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { pageTenants } from '../api/dict';
import type { TenantOption } from '../api/types';
import { clampPageSize, DEFAULT_PAGE_SIZE } from '../pagination';

const props = withDefaults(defineProps<{
  modelValue?: string;
  pageSize?: number;
}>(), {
  pageSize: DEFAULT_PAGE_SIZE
});

const emit = defineEmits<{
  'update:modelValue': [value: string];
  change: [tenant: TenantOption];
}>();

const keyword = ref('');
const options = ref<TenantOption[]>([]);
const size = computed(() => clampPageSize(props.pageSize));
let timer: ReturnType<typeof setTimeout> | undefined;

async function search(term?: string): Promise<void> {
  const result = await pageTenants({
    keyword: term && term.length > 0 ? term : undefined,
    page: 1,
    size: size.value
  });
  options.value = result.items;
}

onMounted(() => {
  void search();
});

watch(keyword, (value) => {
  if (timer !== undefined) {
    clearTimeout(timer);
  }
  timer = setTimeout(() => {
    void search(value);
  }, 300);
});

function select(option: TenantOption): void {
  emit('update:modelValue', option.tenantId);
  emit('change', option);
}
</script>

<style scoped>
.tenant-picker {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 12px;
}

.tenant-options {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.tenant-option {
  border: 1px solid var(--el-border-color);
  background: var(--el-bg-color);
  border-radius: 4px;
  padding: 4px 10px;
  cursor: pointer;
}

.tenant-option.selected {
  border-color: var(--el-color-primary);
  color: var(--el-color-primary);
}
</style>
