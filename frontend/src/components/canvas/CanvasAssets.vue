<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue';
import { api } from '../../api/client';
import { useAppStore } from '../../stores/app';
import { formatSize } from '../../lib/format';
import type { Media } from '../../domain/types';
import UiIcon from '../UiIcon.vue';
const props = defineProps<{ kind?: string }>();
const emit = defineEmits<{ close: []; select: [media: Media] }>();
const app = useAppStore(),
  items = ref<Media[]>([]),
  search = ref(''),
  filter = ref(props.kind || 'all'),
  loading = ref(false),
  uploading = ref(false),
  error = ref(''),
  input = ref<HTMLInputElement>();
let sequence = 0;
let disposed = false;
const visible = computed(() =>
  items.value.filter(
    (m) =>
      /^(image|video|audio)\//.test(m.contentType) &&
      (filter.value === 'all' || m.contentType.startsWith(filter.value + '/')) &&
      m.fileName.toLowerCase().includes(search.value.toLowerCase()),
  ),
);
async function load() {
  const seq = ++sequence,
    wid = app.workspaceId;
  items.value = [];
  error.value = '';
  if (!wid) return;
  loading.value = true;
  try {
    const result = await api.media(wid);
    if (seq === sequence) items.value = result;
  } catch (e) {
    if (seq === sequence) error.value = (e as Error).message;
  } finally {
    if (seq === sequence) loading.value = false;
  }
}
watch(() => app.workspaceId, load, { immediate: true });
onBeforeUnmount(() => {
  sequence++;
  disposed = true;
});
async function upload(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0],
    wid = app.workspaceId;
  if (!file || !wid) return;
  uploading.value = true;
  error.value = '';
  try {
    if (file.size > 100 * 1024 * 1024) throw new Error('文件不能超过 100 MB。');
    const item = await api.upload(wid, file);
    if (!disposed && wid === app.workspaceId) {
      emit('select', item);
      app.notify('素材已上传并加入画布');
    }
  } catch (e) {
    error.value = (e as Error).message;
  } finally {
    uploading.value = false;
    if (input.value) input.value.value = '';
  }
}
</script>
<template>
  <aside class="canvas-drawer" aria-label="画布素材库">
    <header>
      <div>
        <h2>素材库</h2>
        <span>工作空间 · 最近 100 个素材</span>
      </div>
      <button class="icon-button" aria-label="关闭素材库" @click="emit('close')">
        <UiIcon name="close" weight="regular" />
      </button>
    </header>
    <template v-if="app.workspaceId">
      <div class="canvas-asset-search input-surface">
        <UiIcon name="search" :size="17" weight="regular" /><input
          v-model="search"
          placeholder="搜索素材"
          aria-label="搜索素材"
        />
      </div>
      <div class="canvas-filter-row">
        <button
          v-for="f in [
            { id: 'all', name: '全部' },
            { id: 'image', name: '图片' },
            { id: 'video', name: '视频' },
            { id: 'audio', name: '音频' },
          ]"
          :key="f.id"
          :class="{ active: filter === f.id }"
          @click="filter = f.id"
        >
          {{ f.name }}
        </button>
      </div>
      <button class="canvas-upload" :disabled="!app.canEdit || uploading" @click="input?.click()">
        <UiIcon
          :name="uploading ? 'loading' : 'upload'"
          :class="{ spin: uploading }"
          weight="regular"
        />{{ uploading ? '正在上传…' : '上传素材' }}
      </button>
      <input
        ref="input"
        type="file"
        hidden
        accept="image/png,image/jpeg,image/webp,image/gif,video/mp4,video/webm,audio/mpeg,audio/wav,audio/x-wav,audio/ogg"
        @change="upload"
      />
      <p v-if="error" class="error-text" role="alert">
        {{ error }}<button class="text-button" @click="load">重试</button>
      </p>
      <div v-if="loading" class="canvas-panel-empty">
        <UiIcon name="loading" class="spin" weight="regular" />
        <p>正在读取素材</p>
      </div>
      <div v-else-if="!visible.length" class="canvas-panel-empty">
        <UiIcon name="image" :size="32" weight="regular" />
        <p>{{ search ? '没有匹配的素材' : '还没有可用素材' }}</p>
        <span>上传后点击素材，即可加入画布。</span>
      </div>
      <div v-else class="canvas-asset-grid">
        <button
          v-for="item in visible"
          :key="item.id"
          :title="'添加 ' + item.fileName"
          @click="emit('select', item)"
        >
          <div class="canvas-asset-preview">
            <img
              v-if="item.contentType.startsWith('image/')"
              :src="api.mediaUrl(app.workspaceId, item.id)"
              :alt="item.fileName"
              loading="lazy"
            /><UiIcon
              v-else
              :name="item.contentType.startsWith('video/') ? 'video' : 'waveform'"
              :size="30"
              weight="regular"
            />
          </div>
          <strong>{{ item.fileName }}</strong
          ><small>{{ formatSize(item.sizeBytes) }}</small>
        </button>
      </div>
    </template>
    <div v-else class="canvas-panel-empty">
      <UiIcon name="folder" :size="32" weight="regular" />
      <h3>连接你的素材库</h3>
      <p>登录工作室后，可上传并引用图片、视频和音频。</p>
      <button class="quiet-button" @click="app.loginOpen = true">登录工作室</button>
    </div>
  </aside>
</template>
