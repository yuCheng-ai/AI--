<script setup lang="ts">
import { ref } from 'vue';
import { useAppStore } from '../stores/app';
import UiDialog from './UiDialog.vue';
import UiIcon from './UiIcon.vue';
const app = useAppStore(),
  username = ref(''),
  password = ref(''),
  error = ref(''),
  busy = ref(false);
async function submit() {
  busy.value = true;
  error.value = '';
  try {
    await app.login(username.value, password.value);
    password.value = '';
  } catch (e) {
    error.value = (e as Error).message;
  } finally {
    busy.value = false;
  }
}
</script>
<template>
  <UiDialog :open="app.loginOpen" title="登录工作室" @close="app.loginOpen = false"
    ><form class="form-stack" @submit.prevent="submit">
      <p class="muted">继续你的故事，管理项目与素材。</p>
      <label
        >用户名<input
          v-model="username"
          name="username"
          autocomplete="username"
          required
          maxlength="64"
          autofocus
      /></label>
      <label
        >密码<input
          v-model="password"
          name="password"
          autocomplete="current-password"
          type="password"
          required
          maxlength="256"
      /></label>
      <p v-if="error" class="error-text" role="alert">{{ error }}</p>
      <button class="primary" :disabled="busy">
        <UiIcon v-if="busy" name="loading" class="spin" />{{ busy ? '正在登录' : '登录' }}
      </button>
    </form></UiDialog
  >
</template>
