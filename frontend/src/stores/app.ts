import { defineStore } from 'pinia';
import { computed, ref } from 'vue';
import { api, ApiError, collectPages } from '../api/client';
import type { User, Workspace } from '../domain/types';

export const useAppStore = defineStore('app', () => {
  const user = ref<User | null>(null);
  const workspaces = ref<Workspace[]>([]);
  const workspaceId = ref('');
  const workspace = computed(() => workspaces.value.find((item) => item.id === workspaceId.value));
  const canEdit = computed(
    () => workspace.value?.role === 'ADMIN' || workspace.value?.role === 'EDITOR',
  );
  const loading = ref(true);
  const connectionError = ref('');
  const resourceError = ref(false);
  const loginOpen = ref(false);
  const notices = ref<{ id: number; message: string; error: boolean }[]>([]);
  let noticeId = 0;
  let boot: Promise<void> | undefined;
  function notify(message: string, error = false) {
    const id = ++noticeId;
    notices.value.push({ id, message, error });
    setTimeout(() => dismiss(id), error ? 10000 : 4500);
  }
  function dismiss(id: number) {
    notices.value = notices.value.filter((item) => item.id !== id);
  }
  function report(error: unknown) {
    if (error instanceof ApiError && error.status === 401) {
      user.value = null;
      workspaces.value = [];
      workspaceId.value = '';
      loginOpen.value = true;
    }
    notify(error instanceof Error ? error.message : '操作失败，请重试。', true);
  }
  async function loadWorkspaces() {
    workspaces.value = await collectPages((cursor) => api.workspaces(cursor));
    if (!workspaces.value.some((w) => w.id === workspaceId.value))
      workspaceId.value = workspaces.value[0]?.id || '';
  }
  function bootstrap() {
    return (boot ??= (async () => {
      loading.value = true;
      connectionError.value = '';
      try {
        user.value = await api.me();
        await loadWorkspaces();
      } catch (error) {
        if (!(error instanceof ApiError && error.status === 401))
          connectionError.value = error instanceof Error ? error.message : '连接失败';
      } finally {
        loading.value = false;
      }
    })());
  }
  async function login(username: string, password: string) {
    user.value = await api.login(username, password);
    await loadWorkspaces();
    loginOpen.value = false;
    connectionError.value = '';
    notify('已登录工作室');
  }
  async function logout() {
    try {
      await api.logout();
      user.value = null;
      workspaces.value = [];
      workspaceId.value = '';
      notify('已退出登录');
    } catch (error) {
      report(error);
    }
  }
  return {
    user,
    workspaces,
    workspaceId,
    workspace,
    canEdit,
    loading,
    connectionError,
    resourceError,
    loginOpen,
    notices,
    bootstrap,
    loadWorkspaces,
    login,
    logout,
    notify,
    dismiss,
    report,
  };
});
