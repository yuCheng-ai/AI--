<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import AppHeader from '../components/AppHeader.vue';
import UiIcon from '../components/UiIcon.vue';
import UiDialog from '../components/UiDialog.vue';
import { api } from '../api/client';
import { useAppStore } from '../stores/app';
import type { Media } from '../domain/types';
import { formatSize, formatTime } from '../lib/format';
const app = useAppStore(),
  assets = ref<Media[]>([]),
  error = ref(''),
  loading = ref(false),
  uploadBusy = ref(false),
  input = ref<HTMLInputElement>(),
  search = ref(''),
  filter = ref('全部'),
  preview = ref<Media | null>(null);
const items = computed(() =>
  assets.value.filter(
    (m) =>
      m.fileName.toLowerCase().includes(search.value.toLowerCase()) &&
      (filter.value === '全部' ||
        m.contentType.startsWith(
          ({ 图片: 'image/', 视频: 'video/', 音频: 'audio/' } as Record<string, string>)[
            filter.value
          ]!,
        )),
  ),
);
let generation = 0;
async function load() {
  const id = ++generation;
  assets.value = [];
  error.value = '';
  if (!app.workspaceId) return;
  loading.value = true;
  try {
    const value = await api.media(app.workspaceId);
    if (id === generation) assets.value = value;
  } catch (e) {
    if (id === generation) error.value = (e as Error).message;
  } finally {
    if (id === generation) loading.value = false;
  }
}
watch(() => app.workspaceId, load, { immediate: true });
async function upload(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0];
  if (!file) return;
  uploadBusy.value = true;
  const wid = app.workspaceId;
  try {
    if (file.size > 100 * 1024 * 1024) throw new Error('文件不能超过 100 MB。');
    await api.upload(wid, file);
    app.notify('素材已上传');
    if (wid === app.workspaceId) await load();
  } catch (e) {
    app.report(e);
  } finally {
    uploadBusy.value = false;
    (event.target as HTMLInputElement).value = '';
  }
}
</script>
<template>
  <div>
    <AppHeader />
    <main class="home-main">
      <div class="section-heading">
        <div>
          <h1>素材库</h1>
          <p class="muted small">让灵感与画面，有迹可循。</p>
        </div>
        <div class="heading-actions">
          <label class="search-field"
            ><UiIcon name="search" :size="18" /><input
              v-model="search"
              aria-label="搜索素材"
              placeholder="搜索素材" /></label
          ><button class="primary" :disabled="!app.canEdit || uploadBusy" @click="input?.click()">
            <UiIcon :name="uploadBusy ? 'loading' : 'upload'" :class="{ spin: uploadBusy }" />{{
              uploadBusy ? '正在上传' : '上传素材'
            }}</button
          ><input
            ref="input"
            type="file"
            hidden
            accept="image/png,image/jpeg,image/webp,image/gif,video/mp4,video/webm,audio/mpeg,audio/mp4,audio/wav,audio/ogg,audio/flac,audio/webm"
            @change="upload"
          />
        </div>
      </div>
      <div class="filter-tabs asset-filters">
        <button
          v-for="tab in ['全部', '图片', '视频', '音频']"
          :class="{ active: tab === filter }"
          :key="tab"
          @click="filter = tab"
        >
          {{ tab }}</button
        ><span class="muted small">最近上传的 100 个素材 · 最大 100 MB</span>
      </div>
      <p v-if="error" class="inline-error" role="alert">
        {{ error }}<button class="text-button" @click="load">重试</button>
      </p>
      <div v-if="!app.user" class="empty-state tall">
        <UiIcon name="lock" :size="32" />
        <h3>登录后管理你的素材</h3>
        <button class="quiet-button" @click="app.loginOpen = true">登录工作室</button>
      </div>
      <div v-else-if="loading" class="loading-state">
        <UiIcon name="loading" class="spin" />正在读取素材
      </div>
      <div v-else-if="!items.length" class="empty-state tall">
        <UiIcon name="image" :size="36" />
        <h3>{{ search ? '没有找到素材' : '把第一份灵感放进来' }}</h3>
        <p>{{ search ? '换个关键词或类型试试。' : '上传图片、视频或音频，在项目中随时调用。' }}</p>
      </div>
      <div v-else class="media-grid">
        <button v-for="item in items" :key="item.id" class="media-card" @click="preview = item">
          <img
            v-if="item.contentType.startsWith('image/')"
            :src="api.mediaUrl(app.workspaceId, item.id)"
            :alt="item.fileName"
            loading="lazy"
          />
          <div v-else class="media-placeholder">
            <UiIcon :name="item.contentType.startsWith('video/') ? 'video' : 'music'" :size="36" />
          </div>
          <strong>{{ item.fileName }}</strong
          ><span>{{ formatSize(item.sizeBytes) }} · {{ formatTime(item.createdAt) }}</span>
        </button>
      </div>
    </main>
    <UiDialog
      :open="!!preview"
      :title="preview?.fileName || '素材预览'"
      wide
      @close="preview = null"
      ><template v-if="preview"
        ><div class="media-preview">
          <img
            v-if="preview.contentType.startsWith('image/')"
            :src="api.mediaUrl(app.workspaceId, preview.id)"
            :alt="preview.fileName"
          /><video
            v-else-if="preview.contentType.startsWith('video/')"
            :src="api.mediaUrl(app.workspaceId, preview.id)"
            controls
          /><audio v-else :src="api.mediaUrl(app.workspaceId, preview.id)" controls />
        </div>
        <div class="dialog-actions">
          <a class="quiet-button" :href="api.mediaUrl(app.workspaceId, preview.id, true)" download
            ><UiIcon name="download" />下载原文件</a
          >
        </div></template
      ></UiDialog
    >
  </div>
</template>
