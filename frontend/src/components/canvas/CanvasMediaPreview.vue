<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { api } from '../../api/client';
import { canvasTools, type CanvasData } from '../../domain/canvas';
import { useAppStore } from '../../stores/app';
import UiIcon from '../UiIcon.vue';
const props = defineProps<{ data: CanvasData; interactive?: boolean }>();
const app = useAppStore();
const failed = ref(false);
const source = computed(() =>
  props.data.media && props.data.media.workspaceId === app.workspaceId
    ? api.mediaUrl(props.data.media.workspaceId, props.data.media.id)
    : '',
);
const tool = computed(() => canvasTools.find((t) => t.kind === props.data.kind)!);
watch(source, () => (failed.value = false));
</script>

<template>
  <div class="story-media" :class="{ 'is-interactive': interactive }">
    <template v-if="source && !failed">
      <img
        v-if="data.media?.contentType.startsWith('image/')"
        :src="source"
        :alt="data.media.fileName"
        draggable="false"
        @error="failed = true"
      />
      <video
        v-else-if="data.media?.contentType.startsWith('video/')"
        :src="source"
        :controls="interactive"
        preload="metadata"
        :aria-label="data.title"
        @error="failed = true"
      />
      <div v-else class="story-audio">
        <UiIcon name="waveform" :size="42" weight="regular" /><audio
          v-if="interactive"
          :src="source"
          controls
          preload="metadata"
          :aria-label="data.title"
          @error="failed = true"
        /><span v-else>音频素材</span>
      </div>
      <UiIcon
        v-if="!interactive && data.media?.contentType.startsWith('video/')"
        class="story-play"
        name="play"
        :size="30"
        weight="fill"
      />
    </template>
    <div v-else class="story-media-empty">
      <UiIcon :name="failed ? 'warning' : tool.icon" :size="36" weight="regular" /><span>{{
        data.media ? '素材暂时无法读取' : '未添加' + tool.label
      }}</span>
    </div>
  </div>
</template>
