import { computed, onBeforeUnmount, ref, watch } from 'vue';
import { api, collectPages } from '../api/client';
import {
  blankDraft,
  exampleDraft,
  exampleProject,
  newEpisode,
  refreshExampleFrames,
} from '../domain/examples';
import type { Project, ProjectDraft, Shot, ShotInput, Stage } from '../domain/types';
import { draftKey, readDraft, saveDraft } from '../lib/drafts';
import { useAppStore } from '../stores/app';

export function useStudio(workspaceId: string, projectId: string) {
  const app = useAppStore();
  const demo = workspaceId === 'demo';
  const project = ref<Project | null>(null);
  const draft = ref<ProjectDraft | null>(null);
  const selectedId = ref('');
  const stage = ref<Stage>('script');
  const episode = computed(() =>
    draft.value?.episodes.find((value) => value.id === selectedId.value),
  );
  const episodeIndex = computed(
    () => draft.value?.episodes.findIndex((value) => value.id === selectedId.value) ?? 0,
  );
  const canEdit = computed(() => demo || (app.workspace?.id === workspaceId && app.canEdit));
  const loading = ref(true),
    error = ref(''),
    saveError = ref('');
  let key = '',
    timer: ReturnType<typeof setTimeout> | undefined,
    dirty = false,
    disposed = false;
  const saveLabel = computed(() => (saveError.value ? '保存失败' : '本机草稿'));
  function flush() {
    clearTimeout(timer);
    if (!dirty || !draft.value || !canEdit.value) return;
    try {
      saveDraft(key, { ...draft.value, updatedAt: new Date().toISOString() });
      dirty = false;
      saveError.value = '';
    } catch (e) {
      saveError.value = (e as Error).message;
      app.notify(saveError.value, true);
    }
  }
  watch(
    draft,
    () => {
      if (loading.value || !draft.value || !canEdit.value) return;
      dirty = true;
      clearTimeout(timer);
      timer = setTimeout(flush, 300);
    },
    { deep: true },
  );
  const onUnload = (event: BeforeUnloadEvent) => {
    flush();
    if (dirty) {
      event.preventDefault();
    }
  };
  window.addEventListener('beforeunload', onUnload);
  onBeforeUnmount(() => {
    disposed = true;
    flush();
    window.removeEventListener('beforeunload', onUnload);
  });
  async function load() {
    try {
      await app.bootstrap();
      if (!demo && !app.user) {
        app.loginOpen = true;
        throw new Error('请先登录工作室，再打开项目。');
      }
      if (!demo) app.workspaceId = workspaceId;
      key = draftKey(demo ? 'demo' : app.user!.id, workspaceId, projectId);
      const loadedProject = demo
        ? exampleProject(projectId)
        : await api.project(workspaceId, projectId);
      if (disposed) return;
      project.value = loadedProject;
      draft.value = readDraft(key) || (demo ? exampleDraft(projectId) : blankDraft());
      if (demo) refreshExampleFrames(draft.value, projectId);
      selectedId.value = draft.value.episodes[0]!.id;
      if (!demo) {
        const shots = await collectPages((cursor) => api.shots(workspaceId, projectId, cursor));
        if (disposed) return;
        const byId = new Map(shots.map((shot) => [shot.id, shot]));
        const assigned = new Set<string>();
        for (const item of draft.value.episodes)
          item.shots = item.shots
            .filter((shot) => byId.has(shot.id))
            .map((shot) => {
              assigned.add(shot.id);
              return byId.get(shot.id)!;
            });
        draft.value.episodes[0]!.shots.push(...shots.filter((shot) => !assigned.has(shot.id)));
      }
      // Save initial episode IDs immediately so empty projects retain stable identity on reload.
      if (canEdit.value) {
        dirty = true;
        flush();
      }
    } catch (e) {
      error.value = (e as Error).message;
    } finally {
      loading.value = false;
    }
  }
  function addEpisode(title: string) {
    if (!draft.value || !canEdit.value) return;
    if (draft.value.episodes.length >= 500) {
      app.notify('单个项目最多支持 500 集，请另建项目继续创作。', true);
      return;
    }
    const item = newEpisode(title.trim() || '未命名');
    draft.value.episodes.push(item);
    selectedId.value = item.id;
    stage.value = 'script';
  }
  async function saveShot(input: ShotInput, existing?: Shot) {
    const targetEpisode = episode.value;
    if (!targetEpisode || !canEdit.value) return;
    let result: Shot;
    if (demo)
      result = {
        ...input,
        id: existing?.id || crypto.randomUUID(),
        shotNumber: existing?.shotNumber || targetEpisode.shots.length + 1,
        workspaceId,
        projectId,
        revision: String(Number(existing?.revision || 0) + 1),
        createdAt: existing?.createdAt || new Date().toISOString(),
        updatedAt: new Date().toISOString(),
      };
    else
      result = existing
        ? await api.reviseShot(workspaceId, existing.id, input, existing.revision)
        : await api.createShot(workspaceId, projectId, input);
    const index = targetEpisode.shots.findIndex((shot) => shot.id === result.id);
    if (index === -1) targetEpisode.shots.push(result);
    else targetEpisode.shots[index] = result;
    app.notify(demo ? '镜头已保存到本机草稿' : '镜头已保存到服务器');
  }
  void load();
  return {
    demo,
    project,
    draft,
    episode,
    selectedId,
    episodeIndex,
    stage,
    loading,
    error,
    saveError,
    saveLabel,
    canEdit,
    addEpisode,
    saveShot,
    flush,
    getKey: () => key,
  };
}
