<script setup lang="ts">
import { computed } from 'vue';
import {
  canvasTools,
  textCreationHint,
  type CanvasData,
  type CanvasNode,
} from '../../domain/canvas';
import { useCanvasNodeEditing } from '../../composables/useCanvasNodeEditing';
import UiIcon from '../UiIcon.vue';
import CanvasReferences from './CanvasReferences.vue';
import CanvasMediaPreview from './CanvasMediaPreview.vue';
import CanvasNodeParameters from './CanvasNodeParameters.vue';
const props = defineProps<{
  node: CanvasNode;
  references: CanvasNode[];
  readOnly?: boolean;
  canBack: boolean;
}>();
const emit = defineEmits<{
  checkpoint: [];
  update: [patch: Partial<CanvasData>];
  media: [];
  generate: [];
  reference: [id: string];
  removeReference: [id: string];
  back: [];
  close: [];
  locate: [];
  more: [event: MouseEvent];
}>();
const tool = computed(() => canvasTools.find((t) => t.kind === props.node.data.kind)!);
const textual = computed(() => ['text', 'script'].includes(props.node.data.kind));
const { beginEdit, input, parameter } = useCanvasNodeEditing(
  () => props.node.data,
  () => props.readOnly,
  () => emit('checkpoint'),
  (patch) => emit('update', patch),
);
</script>

<template>
  <article class="story-detail" aria-label="节点详情">
    <header class="story-detail-header">
      <button
        class="story-icon-button"
        :aria-label="canBack ? '返回上一内容' : '返回故事板总览'"
        :title="canBack ? '返回上一内容' : '返回总览'"
        @click="emit('back')"
      >
        <UiIcon name="back" :size="19" weight="regular" />
      </button>
      <UiIcon :name="tool.icon" :size="17" weight="regular" />
      <input
        class="inline-input"
        :value="node.data.title"
        aria-label="节点名称"
        :readonly="readOnly"
        maxlength="80"
        @focus="beginEdit"
        @input="input($event, 'title')"
      />
      <button
        class="story-icon-button"
        aria-label="在工作流中定位"
        title="在工作流中定位"
        @click="emit('locate')"
      >
        <UiIcon name="flow" :size="19" weight="regular" />
      </button>
      <button
        class="story-icon-button"
        aria-label="节点更多操作"
        title="更多操作"
        @click="emit('more', $event)"
      >
        <UiIcon name="more" :size="21" weight="bold" />
      </button>
      <button
        class="story-icon-button"
        aria-label="关闭节点详情"
        title="关闭详情 · Esc"
        @click="emit('close')"
      >
        <UiIcon name="close" :size="19" weight="regular" />
      </button>
    </header>
    <div class="story-detail-content" :class="{ textual, 'editor-surface': textual }">
      <template v-if="textual">
        <textarea
          class="story-text-editor"
          :value="node.data.text"
          :aria-label="tool.label + '内容'"
          :placeholder="'输入' + tool.label + '内容…'"
          :readonly="readOnly"
          maxlength="20000"
          @focus="beginEdit"
          @input="input($event, 'text')"
        />
        <span class="story-character-count">{{ node.data.text.length }} 字</span>
      </template>
      <CanvasMediaPreview v-else :data="node.data" interactive />
    </div>
    <div class="story-editor input-surface">
      <div v-if="!textual" class="story-editor-heading">
        <span>提示词</span>
        <button :disabled="readOnly" @click="emit('media')">
          <UiIcon :name="node.data.media ? 'folder' : 'plus'" :size="15" weight="regular" />{{
            node.data.media ? '替换素材' : '添加素材'
          }}
        </button>
      </div>
      <CanvasReferences
        v-if="references.length"
        :nodes="references"
        :read-only="readOnly"
        navigation="detail"
        @focus="emit('reference', $event)"
        @remove="emit('removeReference', $event)"
      />
      <textarea
        :value="textual ? node.data.prompt : node.data.text"
        :aria-label="tool.label + (textual ? '对话输入' : '提示词')"
        :placeholder="textual ? textCreationHint : tool.hint"
        :readonly="readOnly"
        maxlength="20000"
        @focus="beginEdit"
        @input="input($event, textual ? 'prompt' : 'text')"
      />
      <CanvasNodeParameters
        :data="node.data"
        :read-only="readOnly"
        @parameter="parameter"
        @generate="emit('generate')"
      />
    </div>
  </article>
</template>
