<script setup lang="ts">
import { computed, ref } from 'vue';
import { api } from '../../api/client';
import { canvasTools, type CanvasNode } from '../../domain/canvas';
import { useAppStore } from '../../stores/app';
import UiIcon from '../UiIcon.vue';

const props = defineProps<{
  nodes: CanvasNode[];
  readOnly?: boolean;
  navigation?: 'canvas' | 'detail';
}>();
const emit = defineEmits<{ focus: [nodeId: string]; remove: [nodeId: string] }>();
const app = useAppStore();
const failedImages = ref<Record<string, string>>({});
const references = computed(() =>
  props.nodes.map((node) => {
    const tool = canvasTools.find((item) => item.kind === node.data.kind)!;
    const media = node.data.media;
    const title = node.data.title || tool.label + '节点';
    const imageUrl =
      media?.contentType.startsWith('image/') && media.workspaceId === app.workspaceId
        ? api.mediaUrl(media.workspaceId, media.id)
        : '';
    const detail =
      node.data.kind === 'text' || node.data.kind === 'script'
        ? node.data.text.trim().slice(0, 180) || '内容为空'
        : media?.workspaceId === app.workspaceId
          ? media.fileName
          : '尚无可用素材';
    return { id: node.id, title, icon: tool.icon, imageUrl, detail, label: tool.label };
  }),
);
</script>

<template>
  <section class="node-references" aria-label="上游参考节点" @pointerdown.stop @click.stop>
    <div class="node-composer-top">
      <span><UiIcon name="link" :size="13" weight="regular" />参考</span>
      <span>{{ references.length ? references.length + ' 个节点' : '连接节点添加参考' }}</span>
    </div>
    <div v-if="references.length" class="node-reference-list" role="list">
      <div
        v-for="(reference, index) in references"
        :key="reference.id"
        class="node-reference-item"
        role="listitem"
      >
        <button
          class="node-reference"
          :aria-label="
            (navigation === 'detail' ? '查看参考 ' : '定位参考 ') +
            (index + 1) +
            '：' +
            reference.title
          "
          :title="
            reference.label +
            ' · ' +
            reference.title +
            '\n' +
            reference.detail +
            (navigation === 'detail' ? '\n点击查看内容' : '\n点击定位原节点')
          "
          @click="emit('focus', reference.id)"
        >
          <span class="node-reference-preview">
            <img
              v-if="reference.imageUrl && failedImages[reference.id] !== reference.imageUrl"
              :src="reference.imageUrl"
              :alt="reference.title"
              draggable="false"
              @error="failedImages[reference.id] = reference.imageUrl"
            />
            <UiIcon v-else :name="reference.icon" :size="22" weight="regular" />
            <span class="node-reference-number" aria-hidden="true">{{ index + 1 }}</span>
          </span>
          <span class="node-reference-title">{{ reference.title }}</span>
        </button>
        <button
          v-if="!readOnly"
          type="button"
          class="node-reference-remove"
          :aria-label="'移除参考：' + reference.title"
          title="移除参考"
          @pointerdown.stop
          @click.stop="emit('remove', reference.id)"
        >
          <UiIcon name="close" :size="12" weight="bold" />
        </button>
      </div>
    </div>
  </section>
</template>
