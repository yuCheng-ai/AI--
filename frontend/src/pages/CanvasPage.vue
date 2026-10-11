<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue';
import AppHeader from '../components/AppHeader.vue';
import UiIcon from '../components/UiIcon.vue';
import { downloadFile } from '../lib/format';
import { useAppStore } from '../stores/app';
interface Node {
  id: string;
  x: number;
  y: number;
  title: string;
  text: string;
}
const app = useAppStore(),
  nodes = ref<Node[]>([]),
  zoom = ref(1),
  loaded = ref(false),
  error = ref('');
const key = computed(
  () => `yingxu:canvas:v1:${app.user?.id || 'guest'}:${app.workspaceId || 'local'}`,
);
watch(
  key,
  () => {
    loaded.value = false;
    try {
      const raw = localStorage.getItem(key.value);
      const value: unknown = raw ? JSON.parse(raw) : [];
      if (
        !Array.isArray(value) ||
        !value.every(
          (n) =>
            n &&
            typeof n.id === 'string' &&
            typeof n.title === 'string' &&
            typeof n.text === 'string' &&
            Number.isFinite(n.x) &&
            Number.isFinite(n.y),
        )
      )
        throw new Error();
      nodes.value = value;
      error.value = '';
    } catch {
      nodes.value = [];
      error.value = '画布草稿无法读取，原始数据已保留。';
    }
    loaded.value = true;
  },
  { immediate: true },
);
watch(
  nodes,
  () => {
    if (!loaded.value || error.value) return;
    try {
      localStorage.setItem(key.value, JSON.stringify(nodes.value));
    } catch {
      error.value = '本机存储不足，请导出画布备份。';
    }
  },
  { deep: true },
);
function add() {
  const index = nodes.value.length;
  nodes.value.push({
    id: crypto.randomUUID(),
    title: `灵感 ${index + 1}`,
    text: '',
    x: 70 + (index % 3) * 350,
    y: 70 + Math.floor(index / 3) * 300,
  });
}
let dragging: { id: string; x: number; y: number; px: number; py: number } | undefined;
function down(event: PointerEvent, node: Node) {
  if ((event.target as HTMLElement).closest('button,input')) return;
  dragging = { id: node.id, x: node.x, y: node.y, px: event.clientX, py: event.clientY };
  (event.currentTarget as HTMLElement).setPointerCapture(event.pointerId);
}
function move(event: PointerEvent) {
  if (!dragging) return;
  const node = nodes.value.find((n) => n.id === dragging!.id);
  if (node) {
    node.x = Math.max(0, dragging.x + (event.clientX - dragging.px) / zoom.value);
    node.y = Math.max(0, dragging.y + (event.clientY - dragging.py) / zoom.value);
  }
}
function exportCanvas() {
  downloadFile(
    '灵感画布.json',
    JSON.stringify({ version: 1, nodes: nodes.value }, null, 2),
    'application/json',
  );
}
onBeforeUnmount(() => {
  dragging = undefined;
});
</script>
<template>
  <div class="canvas-page">
    <AppHeader />
    <header class="canvas-heading">
      <div>
        <h1>智能画布</h1>
        <span class="muted small">本机灵感草稿 · 生成节点待接入</span>
      </div>
      <div class="row-actions">
        <button class="quiet-button" :disabled="!!error" @click="add">
          <UiIcon name="plus" />文字灵感</button
        ><button class="text-button" :disabled="!nodes.length" @click="exportCanvas">
          <UiIcon name="export" />导出画布
        </button>
      </div>
    </header>
    <p v-if="error" class="inline-error">{{ error }}</p>
    <div class="canvas-viewport">
      <div class="canvas-surface" :style="{ transform: `scale(${zoom})` }">
        <article
          v-for="node in nodes"
          :key="node.id"
          class="canvas-node"
          :style="{ left: `${node.x}px`, top: `${node.y}px` }"
        >
          <header
            @pointerdown="down($event, node)"
            @pointermove="move"
            @pointerup="dragging = undefined"
            @pointercancel="dragging = undefined"
          >
            <UiIcon name="drag" /><input
              v-model="node.title"
              aria-label="灵感名称"
              maxlength="80"
            /><span class="node-type">文本</span>
          </header>
          <textarea
            v-model="node.text"
            aria-label="灵感内容"
            placeholder="记下一个场景、一句对白，或一个还没成形的念头…"
            maxlength="10000"
          />
        </article>
      </div>
      <div v-if="!nodes.length" class="canvas-empty">
        <UiIcon name="sparkle" :size="36" />
        <h2>灵感，可以从任何地方开始。</h2>
        <p>添加一个文字节点，自由排列你的想法。</p>
        <button class="primary" :disabled="!!error" @click="add">
          写下第一个灵感<UiIcon name="plus" :size="18" />
        </button>
      </div>
    </div>
    <div class="canvas-zoom">
      <button
        class="icon-button"
        aria-label="缩小画布"
        :disabled="zoom <= 0.5"
        @click="zoom = Math.max(0.5, zoom - 0.1)"
      >
        <UiIcon name="minus" /></button
      ><button class="text-button" @click="zoom = 1">{{ Math.round(zoom * 100) }}%</button
      ><button
        class="icon-button"
        aria-label="放大画布"
        :disabled="zoom >= 1.5"
        @click="zoom = Math.min(1.5, zoom + 0.1)"
      >
        <UiIcon name="plus" />
      </button>
    </div>
  </div>
</template>
