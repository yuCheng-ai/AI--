<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { Handle, Position, type ValidConnectionFunc } from '@vue-flow/core';
import { api } from '../../api/client';
import {
  canvasTools,
  textCreationHint,
  type CanvasData,
  type CanvasNode,
} from '../../domain/canvas';
import { useAppStore } from '../../stores/app';
import UiIcon from '../UiIcon.vue';
import CanvasReferences from './CanvasReferences.vue';
import CanvasNodeParameters from './CanvasNodeParameters.vue';
import { useCanvasNodeEditing } from '../../composables/useCanvasNodeEditing';
const props = defineProps<{
  id: string;
  data: CanvasData;
  selected?: boolean;
  readOnly?: boolean;
  references: CanvasNode[];
  validateConnection: ValidConnectionFunc;
}>();
const emit = defineEmits<{
  checkpoint: [];
  update: [patch: Partial<CanvasData>];
  media: [];
  generate: [];
  focusReference: [nodeId: string];
  removeReference: [nodeId: string];
}>();
const app = useAppStore();
const tool = computed(() => canvasTools.find((t) => t.kind === props.data.kind)!);
const textual = computed(() => ['text', 'script'].includes(props.data.kind));
const failed = ref(false);
const source = computed(() =>
  props.data.media && props.data.media.workspaceId === app.workspaceId
    ? api.mediaUrl(props.data.media.workspaceId, props.data.media.id)
    : '',
);
watch(source, () => (failed.value = false));
const { beginEdit, input, parameter } = useCanvasNodeEditing(
  () => props.data,
  () => props.readOnly,
  () => emit('checkpoint'),
  (patch) => emit('update', patch),
);
</script>
<template>
  <article class="creative-node" :class="[{ selected, textual }, data.kind]">
    <header class="creative-node-label">
      <UiIcon :name="tool.icon" :size="15" weight="regular" />
      <input
        class="nodrag inline-input"
        :value="data.title"
        :aria-label="tool.label + '节点名称'"
        maxlength="80"
        :readonly="readOnly"
        @focus="beginEdit"
        @input="input($event, 'title')"
      />
      <span v-if="data.media" class="node-source">素材</span>
    </header>
    <div class="creative-node-body input-surface">
      <Handle
        type="target"
        :position="Position.Left"
        :connectable="!readOnly"
        :is-valid-connection="validateConnection"
        aria-label="节点输入连接点"
        ><UiIcon name="plus" :size="12" weight="regular"
      /></Handle>
      <Handle
        type="source"
        :position="Position.Right"
        :connectable="!readOnly"
        :is-valid-connection="validateConnection"
        aria-label="节点输出连接点"
        ><UiIcon name="plus" :size="12" weight="regular"
      /></Handle>
      <template v-if="textual">
        <textarea
          class="nodrag nowheel"
          :aria-label="tool.label + '内容'"
          :placeholder="tool.hint"
          :value="data.text"
          maxlength="20000"
          :readonly="readOnly"
          @focus="beginEdit"
          @input="input($event, 'text')"
        />
        <div class="node-text-footer">
          <UiIcon :name="tool.icon" :size="16" weight="regular" /><span
            >{{ data.text.length }} 字</span
          >
        </div>
      </template>
      <template v-else>
        <div
          class="node-preview"
          :style="{
            aspectRatio: data.kind === 'audio' ? '16 / 9' : data.ratio.replace(':', ' / '),
          }"
        >
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
              class="nodrag nowheel"
              :src="source"
              controls
              preload="metadata"
              @error="failed = true"
            />
            <div v-else class="node-audio">
              <UiIcon name="waveform" :size="52" weight="regular" /><audio
                class="nodrag nowheel"
                :src="source"
                controls
                preload="metadata"
                @error="failed = true"
              />
            </div>
          </template>
          <div v-else class="node-preview-empty">
            <UiIcon :name="tool.icon" :size="42" weight="regular" />
            <span>{{ data.media ? '素材暂时无法读取' : tool.label + '节点' }}</span>
            <button class="nodrag" :disabled="readOnly" @click="emit('media')">
              <UiIcon name="plus" :size="15" weight="regular" />{{
                data.media ? '重新选择素材' : '添加参考素材'
              }}
            </button>
          </div>
          <button
            v-if="data.media && selected && !readOnly"
            class="node-replace nodrag"
            @click="emit('media')"
          >
            <UiIcon name="image" :size="14" weight="regular" />替换素材
          </button>
        </div>
      </template>
    </div>
    <div v-if="selected" class="node-composer input-surface nodrag nowheel">
      <CanvasReferences
        v-if="!textual || references.length"
        :nodes="references"
        :read-only="readOnly"
        @focus="emit('focusReference', $event)"
        @remove="emit('removeReference', $event)"
      />
      <textarea
        :value="textual ? data.prompt : data.text"
        :aria-label="tool.label + (textual ? '对话输入' : '提示词')"
        :placeholder="textual ? textCreationHint : tool.hint"
        maxlength="20000"
        :readonly="readOnly"
        @focus="beginEdit"
        @input="input($event, textual ? 'prompt' : 'text')"
      />
      <CanvasNodeParameters
        :data="data"
        :read-only="readOnly"
        @parameter="parameter"
        @generate="emit('generate')"
      />
    </div>
  </article>
</template>
