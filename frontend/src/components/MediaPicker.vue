<script setup lang="ts">
import { ref, watch } from 'vue';
import { api } from '../api/client';
import type { Media } from '../domain/types';
import { useAppStore } from '../stores/app';
import UiDialog from './UiDialog.vue';
import UiIcon from './UiIcon.vue';
import { formatSize } from '../lib/format';
const props = defineProps<{ open: boolean; workspaceId: string; imagesOnly?: boolean }>();
const emit = defineEmits<{ close: []; select: [media: Media] }>();
const app = useAppStore(),
  media = ref<Media[]>([]),
  loading = ref(false),
  error = ref(''),
  input = ref<HTMLInputElement>();
async function load() {
  loading.value = true;
  error.value = '';
  try {
    media.value = await api.media(props.workspaceId);
  } catch (e) {
    error.value = (e as Error).message;
  } finally {
    loading.value = false;
  }
}
watch(
  () => props.open,
  (open) => {
    if (open && props.workspaceId !== 'demo') void load();
  },
);
async function upload(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0];
  if (!file) return;
  loading.value = true;
  error.value = '';
  try {
    if (file.size > 100 * 1024 * 1024) throw new Error('文件不能超过 100 MB。');
    const item = await api.upload(props.workspaceId, file);
    emit('select', item);
    emit('close');
    app.notify('素材已上传');
  } catch (e) {
    error.value = (e as Error).message;
  } finally {
    loading.value = false;
    (event.target as HTMLInputElement).value = '';
  }
}
</script>
<template>
  <UiDialog :open="open" title="选择参考素材" wide @close="emit('close')"
    ><div v-if="workspaceId === 'demo'" class="empty-state">
      <UiIcon name="image" :size="32" />
      <h3>在工作室中管理素材</h3>
      <p>示例可编辑本机草稿。登录并创建项目后，可上传自己的参考素材。</p>
      <button
        class="quiet-button"
        @click="
          app.loginOpen = true;
          emit('close');
        "
      >
        登录工作室
      </button>
    </div>
    <template v-else
      ><div class="picker-toolbar">
        <p class="muted small">最近上传的 100 个素材</p>
        <button class="quiet-button" :disabled="loading || !app.canEdit" @click="input?.click()">
          <UiIcon name="upload" :size="18" />上传素材</button
        ><input
          ref="input"
          type="file"
          hidden
          :accept="
            imagesOnly
              ? 'image/png,image/jpeg,image/webp,image/gif'
              : 'image/png,image/jpeg,image/webp,image/gif,video/mp4,video/webm'
          "
          @change="upload"
        />
      </div>
      <p v-if="error" class="error-text" role="alert">{{ error }}</p>
      <div v-if="loading" class="loading-state">
        <UiIcon name="loading" class="spin" />正在读取素材
      </div>
      <div v-else class="media-grid picker-grid">
        <button
          v-for="item in media.filter(
            (m) =>
              m.contentType.startsWith('image/') ||
              (!imagesOnly && m.contentType.startsWith('video/')),
          )"
          :key="item.id"
          class="media-card"
          @click="
            emit('select', item);
            emit('close');
          "
        >
          <img
            v-if="item.contentType.startsWith('image/')"
            :src="api.mediaUrl(workspaceId, item.id)"
            :alt="item.fileName"
          />
          <div v-else class="media-placeholder"><UiIcon name="video" :size="30" /></div>
          <strong>{{ item.fileName }}</strong
          ><span>{{ formatSize(item.sizeBytes) }}</span>
        </button>
      </div>
      <p v-if="!loading && !media.length" class="empty-state">
        还没有素材，可以先上传一张参考图。
      </p></template
    ></UiDialog
  >
</template>
