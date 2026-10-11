<script setup lang="ts">
import { onMounted } from 'vue';
import { useAppStore } from './stores/app';
import LoginDialog from './components/LoginDialog.vue';
import UiIcon from './components/UiIcon.vue';
const app = useAppStore();
onMounted(app.bootstrap);
function reloadPage() {
  window.location.reload();
}
</script>
<template>
  <RouterView v-slot="{ Component, route }"
    ><component :is="Component" :key="route.path"
  /></RouterView>
  <LoginDialog />
  <div v-if="app.resourceError" class="resource-error" role="alert">
    <UiIcon name="warning" />
    <span>页面资源已更新或暂时不可用，请检查连接后刷新。</span>
    <button class="quiet-button" @click="reloadPage">刷新页面</button>
  </div>
  <div class="toast-stack" aria-live="polite">
    <div
      v-for="notice in app.notices"
      :key="notice.id"
      class="toast"
      :class="{ error: notice.error }"
    >
      <UiIcon :name="notice.error ? 'warning' : 'checkCircle'" /><span>{{ notice.message }}</span
      ><button class="icon-button" aria-label="关闭提示" @click="app.dismiss(notice.id)">
        <UiIcon name="close" :size="16" />
      </button>
    </div>
  </div>
</template>
