<template>
  <el-alert
    v-if="message"
    data-testid="error-alert"
    type="error"
    show-icon
    :closable="true"
    :title="message"
    @close="$emit('clear')"
  />
</template>

<script setup lang="ts">
defineProps<{
  message: string;
}>();

defineEmits<{
  clear: [];
}>();
</script>
