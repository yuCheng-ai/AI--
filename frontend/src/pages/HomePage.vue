<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import AppHeader from '../components/AppHeader.vue';
import UiIcon from '../components/UiIcon.vue';
import UiDialog from '../components/UiDialog.vue';
import { useAppStore } from '../stores/app';
import { api, collectPages } from '../api/client';
import type { Project } from '../domain/types';
import { examples, blankDraft } from '../domain/examples';
import { draftKey, readDraft, saveDraft } from '../lib/drafts';
import { formatTime } from '../lib/format';

const app = useAppStore(),
  router = useRouter();
const projects = ref<Project[]>([]),
  query = ref(''),
  view = ref('grid'),
  loading = ref(false),
  error = ref('');
const dialog = ref(false),
  workspaceDialog = ref(false),
  workspaceName = ref('我的工作室'),
  busy = ref(false);
const editTarget = ref<Project | null>(null),
  name = ref(''),
  description = ref(''),
  aspect = ref('16:9'),
  cover = ref(''),
  formError = ref('');
const genre = ref('全部');
const shown = computed(() =>
  projects.value.filter((p) => p.name.toLowerCase().includes(query.value.toLowerCase())),
);
const cases = computed(() =>
  examples.filter((item) => genre.value === '全部' || genre.value === item.genre),
);
let requestId = 0;
watch(
  () => app.workspaceId,
  () => {
    void load();
  },
  { immediate: true },
);
async function load() {
  const current = ++requestId,
    wid = app.workspaceId;
  projects.value = [];
  error.value = '';
  if (!wid) {
    loading.value = false;
    return;
  }
  loading.value = true;
  try {
    const result = await collectPages((cursor) => api.projects(wid, cursor));
    if (current === requestId) projects.value = result;
  } catch (e) {
    if (current === requestId) error.value = (e as Error).message;
  } finally {
    if (current === requestId) loading.value = false;
  }
}
function projectCover(project: Project) {
  try {
    return readDraft(draftKey(app.user!.id, project.workspaceId, project.id))?.cover || '';
  } catch {
    return '';
  }
}
function newProject() {
  if (!app.user) {
    app.loginOpen = true;
    return;
  }
  if (!app.workspaceId) {
    workspaceDialog.value = true;
    return;
  }
  if (!app.canEdit) return;
  editTarget.value = null;
  name.value = '';
  description.value = '';
  aspect.value = '16:9';
  cover.value = '';
  formError.value = '';
  dialog.value = true;
}
function edit(project: Project) {
  editTarget.value = project;
  name.value = project.name;
  description.value = project.description || '';
  aspect.value = project.aspectRatio;
  cover.value = projectCover(project);
  formError.value = '';
  dialog.value = true;
}
async function submit() {
  if (!name.value.trim()) return;
  busy.value = true;
  formError.value = '';
  try {
    const input = {
      name: name.value.trim(),
      description: description.value.trim(),
      aspectRatio: aspect.value,
    };
    const project = editTarget.value
      ? await api.updateProject(
          app.workspaceId,
          editTarget.value.id,
          input,
          editTarget.value.revision,
        )
      : await api.createProject(app.workspaceId, input);
    const key = draftKey(app.user!.id, app.workspaceId, project.id);
    try {
      const draft = readDraft(key) || blankDraft();
      draft.cover = cover.value || undefined;
      saveDraft(key, draft);
    } catch (e) {
      app.report(e);
    }
    dialog.value = false;
    if (editTarget.value) await load();
    else await router.push(`/studio/${project.workspaceId}/${project.id}`);
  } catch (e) {
    formError.value = (e as Error).message;
  } finally {
    busy.value = false;
  }
}
async function createWorkspace() {
  busy.value = true;
  formError.value = '';
  try {
    const workspace = await api.createWorkspace(workspaceName.value.trim());
    await app.loadWorkspaces();
    app.workspaceId = workspace.id;
    workspaceDialog.value = false;
    newProject();
  } catch (e) {
    formError.value = (e as Error).message;
  } finally {
    busy.value = false;
  }
}
</script>
<template>
  <div class="home-page">
    <AppHeader />
    <main class="home-main">
      <section aria-labelledby="projects-title">
        <div class="section-heading">
          <div>
            <h1 id="projects-title">我的项目</h1>
            <p class="muted small">每一个好故事，都从这里开始。</p>
          </div>
          <div class="heading-actions">
            <label class="search-field"
              ><UiIcon name="search" :size="18" /><input
                v-model="query"
                placeholder="搜索项目"
                aria-label="搜索项目"
            /></label>
            <div class="view-switch">
              <button
                :class="{ active: view === 'grid' }"
                class="icon-button"
                aria-label="卡片视图"
                @click="view = 'grid'"
              >
                <UiIcon name="grid" /></button
              ><button
                :class="{ active: view === 'list' }"
                class="icon-button"
                aria-label="列表视图"
                @click="view = 'list'"
              >
                <UiIcon name="rows" />
              </button>
            </div>
            <button class="primary" :disabled="!!app.workspace && !app.canEdit" @click="newProject">
              <UiIcon name="plus" :size="18" />新建项目
            </button>
          </div>
        </div>
        <div v-if="app.connectionError || error" class="inline-error" role="alert">
          <UiIcon name="warning" />{{ error || app.connectionError
          }}<button v-if="error" class="text-button" @click="load">重试</button>
        </div>
        <div v-if="loading || app.loading" class="loading-state">
          <UiIcon name="loading" class="spin" />正在读取项目
        </div>
        <div
          v-else-if="shown.length"
          class="projects-grid"
          :class="{ 'list-view': view === 'list' }"
        >
          <article v-for="project in shown" :key="project.id" class="project-card">
            <RouterLink :to="`/studio/${project.workspaceId}/${project.id}`" class="project-link"
              ><div class="project-cover">
                <img v-if="projectCover(project)" :src="projectCover(project)" alt="" />
                <div v-else class="unpictured">
                  <UiIcon name="film" :size="40" /><span>{{ project.aspectRatio }}</span>
                </div>
                <span class="cover-open"><UiIcon name="right" /></span>
              </div>
              <div class="project-info">
                <h3>{{ project.name }}</h3>
                <p>{{ project.description || '准备好，写下故事的第一幕。' }}</p>
                <span class="project-time">更新于 {{ formatTime(project.updatedAt) }}</span>
              </div></RouterLink
            ><button
              v-if="app.canEdit"
              class="icon-button project-more"
              :aria-label="`编辑项目 ${project.name}`"
              @click="edit(project)"
            >
              <UiIcon name="more" />
            </button>
          </article>
        </div>
        <div v-else-if="query" class="empty-state">
          <UiIcon name="search" :size="32" />
          <h3>没有找到这个项目</h3>
          <p>换个关键词试试。</p>
          <button class="text-button" @click="query = ''">清空搜索</button>
        </div>
        <div v-else class="project-empty">
          <div class="empty-icon"><UiIcon name="film" :size="32" /></div>
          <div>
            <h3>{{ app.user ? '开始你的第一部作品' : '把灵感，写成下一幕。' }}</h3>
            <p>
              {{
                app.user ? '创建一个项目，开始编写剧本。' : '登录管理项目，或先体验下面的创作案例。'
              }}
            </p>
          </div>
          <button class="quiet-button" @click="newProject">
            {{ app.user ? '创建项目' : '登录开始创作' }}<UiIcon name="right" :size="18" />
          </button>
        </div>
      </section>
      <section class="cases-section" aria-labelledby="cases-title">
        <div class="section-heading">
          <div class="inline-heading">
            <h2 id="cases-title">案例精选</h2>
            <span class="subtle-label">创作示例</span>
          </div>
          <div class="filter-tabs" aria-label="案例类型">
            <button
              v-for="item in ['全部', '悬疑', '治愈', '剧情']"
              :key="item"
              :class="{ active: genre === item }"
              @click="genre = item"
            >
              {{ item }}
            </button>
          </div>
        </div>
        <div class="cases-grid">
          <RouterLink
            v-for="item in cases"
            :key="item.id"
            :to="`/studio/demo/${item.id}`"
            class="case-card"
            ><div class="case-cover">
              <img :src="item.image" :alt="item.title" /><span class="case-label">{{
                item.genre
              }}</span
              ><span class="case-cta">打开创作示例<UiIcon name="outward" :size="18" /></span>
            </div>
            <div class="case-info">
              <h3>{{ item.title }}<UiIcon name="outward" :size="18" /></h3>
              <p>{{ item.subtitle }}</p>
            </div></RouterLink
          >
        </div>
      </section>
      <footer class="home-footer">
        <span>映序 · 从故事到画面</span><span>让创作，回到故事本身。</span>
      </footer>
    </main>
  </div>
  <UiDialog
    :open="dialog"
    :title="editTarget ? '编辑项目' : '新建项目'"
    @close="!busy && (dialog = false)"
    ><form class="form-stack" @submit.prevent="submit">
      <label
        >项目名称<input
          v-model="name"
          maxlength="120"
          required
          placeholder="给这个故事起个名字"
          autofocus /></label
      ><label
        >故事简介<textarea
          v-model="description"
          rows="3"
          maxlength="4000"
          placeholder="用一两句话，记下故事的起点…"
        /></label
      ><label
        >画幅<select v-model="aspect">
          <option>16:9</option>
          <option>9:16</option>
          <option>1:1</option>
        </select></label
      >
      <fieldset class="cover-options">
        <legend>封面 <span class="muted">仅保存于本机</span></legend>
        <button
          type="button"
          :class="{ active: !cover }"
          @click="cover = ''"
          aria-label="不设置封面"
        >
          <UiIcon name="film" /></button
        ><button
          v-for="item in examples"
          :key="item.id"
          type="button"
          :class="{ active: cover === item.image }"
          @click="cover = item.image"
          :aria-label="`${item.title}封面`"
        >
          <img :src="item.image" alt="" />
        </button>
      </fieldset>
      <p v-if="formError" role="alert" class="error-text">{{ formError }}</p>
      <button class="primary" :disabled="busy">
        {{ busy ? '正在保存' : editTarget ? '保存修改' : '创建并开始写作'
        }}<UiIcon name="right" :size="18" />
      </button></form
  ></UiDialog>
  <UiDialog :open="workspaceDialog" title="创建工作空间" @close="!busy && (workspaceDialog = false)"
    ><form class="form-stack" @submit.prevent="createWorkspace">
      <p class="muted">项目和素材会归属于这个工作空间。</p>
      <label>空间名称<input v-model="workspaceName" required maxlength="120" /></label>
      <p v-if="formError" class="error-text">{{ formError }}</p>
      <button class="primary" :disabled="busy">创建工作空间</button>
    </form></UiDialog
  >
</template>
