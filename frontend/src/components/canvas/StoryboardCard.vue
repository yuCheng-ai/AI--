<script setup lang="ts">
import { computed } from 'vue';
import { canvasTools, type CanvasNode } from '../../domain/canvas';
import UiIcon from '../UiIcon.vue';
import CanvasMediaPreview from './CanvasMediaPreview.vue';
import CanvasReferences from './CanvasReferences.vue';
const props = defineProps<{
  node: CanvasNode;
  references: CanvasNode[];
  selected?: boolean;
  readOnly?: boolean;
}>();
const emit = defineEmits<{
  open: [];
  reference: [id: string];
  more: [event: MouseEvent];
  drag: [event: DragEvent];
}>();
const tool = computed(() => canvasTools.find((t) => t.kind === props.node.data.kind)!);
const textual = computed(() => ['text', 'script'].includes(props.node.data.kind));
</script>

<template>
  <article
    class="story-card"
    :class="{ selected, textual }"
    :data-story-node="node.id"
    @contextmenu.prevent="emit('more', $event)"
  >
    <header>
      <button class="story-card-title" :aria-label="'打开' + node.data.title" @click="emit('open')">
        <UiIcon :name="tool.icon" :size="16" weight="regular" /><span>{{
          node.data.title || tool.label + '节点'
        }}</span
        ><small v-if="node.data.kind === 'script'">剧本</small>
      </button>
      <button
        class="story-icon-button story-drag"
        :disabled="readOnly"
        :draggable="!readOnly"
        :aria-label="'排序' + node.data.title"
        title="拖动排序，也可在更多操作中上移、下移"
        @dragstart="emit('drag', $event)"
        @click="emit('more', $event)"
      >
        <UiIcon name="drag" :size="17" weight="regular" />
      </button>
      <button
        class="story-icon-button story-more-trigger"
        :aria-label="node.data.title + '更多操作'"
        title="更多操作"
        @click="emit('more', $event)"
      >
        <UiIcon name="more" :size="20" weight="bold" />
      </button>
    </header>
    <button class="story-card-content" :aria-label="'查看' + node.data.title" @click="emit('open')">
      <p v-if="textual" :class="{ empty: !node.data.text.trim() }">
        {{ node.data.text.trim() || '还没有内容，点击开始编写' }}
      </p>
      <CanvasMediaPreview v-else :data="node.data" />
    </button>
    <div v-if="!textual" class="story-card-meta">
      <span>{{ node.data.media ? '已添加素材' : '等待创作' }}</span
      ><span v-if="node.data.kind !== 'audio'"
        >{{ node.data.ratio
        }}<template v-if="node.data.kind === 'video'">
          · {{ node.data.duration }} 秒</template
        ></span
      >
    </div>
    <CanvasReferences
      v-if="references.length"
      :nodes="references"
      read-only
      navigation="detail"
      @focus="emit('reference', $event)"
    />
  </article>
</template>
