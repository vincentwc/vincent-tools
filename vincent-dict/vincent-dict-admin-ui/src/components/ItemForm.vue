<template>
  <el-form :model="form" label-width="80px" @submit.prevent="submit">
    <el-form-item v-if="mode === 'create'" label="编码">
      <el-input v-model="form.code" data-testid="item-code" maxlength="64" :validate-event="false" />
    </el-form-item>
    <el-form-item v-else-if="mode === 'view'" label="编码">
      <el-input :model-value="form.code" disabled />
    </el-form-item>
    <el-form-item label="名称">
      <el-input
        v-model="form.name"
        data-testid="item-name"
        maxlength="128"
        :disabled="readonly"
        :validate-event="false"
      />
    </el-form-item>
    <el-form-item label="描述">
      <el-input v-model="form.description" type="textarea" maxlength="500" :disabled="readonly" />
    </el-form-item>
    <el-form-item label="排序">
      <el-input v-model.number="form.sortNo" data-testid="item-sort" :disabled="readonly" />
    </el-form-item>
    <p v-if="codeError" class="code-error">{{ codeError }}</p>
    <el-form-item>
      <el-button data-testid="item-cancel" @click="$emit('cancel')">取消</el-button>
      <el-button
        v-if="!readonly"
        data-testid="item-submit"
        type="primary"
        native-type="submit"
      >
        保存
      </el-button>
    </el-form-item>
  </el-form>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue';
import { CODE_PATTERN_MESSAGE, isValidCode } from '../validation/code';

const props = defineProps<{
  mode: 'create' | 'edit' | 'view';
  model?: {
    code?: string;
    name?: string;
    description?: string;
    sortNo?: number;
  };
}>();

const emit = defineEmits<{
  submit: [payload: { code?: string; name: string; description?: string; sortNo?: number }];
  cancel: [];
}>();

const codeError = ref('');
const readonly = computed(() => props.mode === 'view');
const form = reactive({
  code: props.model?.code ?? '',
  name: props.model?.name ?? '',
  description: props.model?.description ?? '',
  sortNo: props.model?.sortNo ?? 0
});

function submit(): void {
  if (readonly.value) {
    return;
  }
  codeError.value = '';
  if (props.mode === 'create' && !isValidCode(form.code)) {
    codeError.value = CODE_PATTERN_MESSAGE;
    return;
  }
  if (!form.name.trim()) {
    return;
  }
  const sortNo = Number.isFinite(form.sortNo) ? form.sortNo : 0;
  if (props.mode === 'create') {
    emit('submit', {
      code: form.code,
      name: form.name,
      description: form.description,
      sortNo
    });
    return;
  }
  emit('submit', {
    name: form.name,
    description: form.description,
    sortNo
  });
}
</script>

<style scoped>
.code-error {
  color: var(--el-color-danger);
  margin: 0 0 12px;
}
</style>
