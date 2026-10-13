<script setup lang="ts">
import type { CanvasData } from '../../domain/canvas';
import UiIcon from '../UiIcon.vue';
defineProps<{ data: CanvasData; readOnly?: boolean }>();
const emit = defineEmits<{
  parameter: [event: Event, field: 'ratio' | 'duration'];
  generate: [];
}>();
</script>

<template>
  <div class="node-parameters">
    <button class="node-model" @click="emit('generate')">
      <UiIcon name="sparkle" :size="14" weight="regular" />选择模型<UiIcon
        name="down"
        :size="12"
        weight="regular"
      />
    </button>
    <select
      v-if="data.kind === 'image' || data.kind === 'video'"
      :value="data.ratio"
      :disabled="readOnly"
      aria-label="画面比例"
      @change="emit('parameter', $event, 'ratio')"
    >
      <option>16:9</option>
      <option>9:16</option>
      <option>1:1</option>
    </select>
    <select
      v-if="data.kind === 'video'"
      :value="data.duration"
      :disabled="readOnly"
      aria-label="视频时长"
      @change="emit('parameter', $event, 'duration')"
    >
      <option value="5">5 秒</option>
      <option value="10">10 秒</option>
      <option value="15">15 秒</option>
    </select>
    <button
      class="node-run"
      aria-label="查看生成接入状态"
      title="查看生成接入状态"
      @click="emit('generate')"
    >
      <UiIcon name="up" :size="18" weight="regular" />
    </button>
  </div>
</template>
