<script setup lang="ts">
import { ref } from 'vue';
import { useAppStore } from '../stores/app';
import { api } from '../api/client';
import UiIcon from './UiIcon.vue';
import UiDialog from './UiDialog.vue';
const app = useAppStore();
const createOpen = ref(false),
  name = ref(''),
  error = ref(''),
  busy = ref(false);
function choose(event: Event) {
  const element = event.target as HTMLSelectElement;
  if (element.value === '__create') {
    createOpen.value = true;
    name.value = '';
    error.value = '';
    element.value = app.workspaceId;
  } else app.workspaceId = element.value;
}
async function create() {
  busy.value = true;
  error.value = '';
  try {
    const workspace = await api.createWorkspace(name.value.trim());
    await app.loadWorkspaces();
    app.workspaceId = workspace.id;
    createOpen.value = false;
  } catch (e) {
    error.value = (e as Error).message;
  } finally {
    busy.value = false;
  }
}
</script>
<template>
  <header class="app-header">
    <RouterLink to="/" class="brand"><UiIcon name="film" :size="27" /><span>映序</span></RouterLink>
    <nav class="app-nav" aria-label="主导航">
      <RouterLink to="/" exact-active-class="active">首页</RouterLink
      ><RouterLink to="/canvas" active-class="active">智能画布</RouterLink
      ><RouterLink to="/assets" active-class="active">素材库</RouterLink>
    </nav>
    <div class="app-account">
      <template v-if="app.user"
        ><select :value="app.workspaceId" aria-label="工作空间" @change="choose">
          <option v-if="!app.workspaces.length" value="">选择工作空间</option>
          <option v-for="workspace in app.workspaces" :value="workspace.id" :key="workspace.id">
            {{ workspace.name }}
          </option>
          <option value="__create">创建工作空间…</option></select
        ><span class="avatar" :title="app.user.username">{{
          app.user.username.slice(0, 1).toUpperCase()
        }}</span
        ><button class="icon-button" title="退出登录" aria-label="退出登录" @click="app.logout">
          <UiIcon name="logout" /></button></template
      ><button v-else class="quiet-button" @click="app.loginOpen = true">
        登录工作室<UiIcon name="right" :size="16" />
      </button>
    </div>
  </header>
  <UiDialog :open="createOpen" title="创建工作空间" @close="!busy && (createOpen = false)"
    ><form class="form-stack" @submit.prevent="create">
      <label>空间名称<input v-model="name" required maxlength="120" autofocus /></label>
      <p v-if="error" class="error-text" role="alert">{{ error }}</p>
      <button class="primary" :disabled="busy">创建工作空间</button>
    </form></UiDialog
  >
</template>
