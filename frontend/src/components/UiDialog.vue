<script setup lang="ts">
import { nextTick, ref, useId, watch } from 'vue';
import UiIcon from './UiIcon.vue';
const props = defineProps<{ open: boolean; title: string; wide?: boolean }>();
const emit = defineEmits<{ close: [] }>();
const dialog = ref<HTMLDialogElement>();
const titleId = useId();
watch(
  () => props.open,
  async (open) => {
    await nextTick();
    if (open && !dialog.value?.open) dialog.value?.showModal();
    else if (!open && dialog.value?.open) dialog.value.close();
  },
  { immediate: true },
);
</script>
<template>
  <dialog
    ref="dialog"
    class="dialog"
    :class="{ wide }"
    :aria-labelledby="titleId"
    @cancel.prevent="emit('close')"
    @click="$event.target === $event.currentTarget && emit('close')"
  >
    <header>
      <h2 :id="titleId">{{ title }}</h2>
      <button class="icon-button" aria-label="关闭对话框" @click="emit('close')">
        <UiIcon name="close" />
      </button>
    </header>
    <div class="dialog-body"><slot /></div>
  </dialog>
</template>
