<script setup lang="ts">
import { computed, ref } from 'vue';
import { useRoute } from 'vue-router';
import { useStudio } from '../composables/useStudio';
import { stages } from '../domain/types';
import { useAppStore } from '../stores/app';
import { downloadFile, truncateName } from '../lib/format';
import UiIcon from '../components/UiIcon.vue';
import UiDialog from '../components/UiDialog.vue';
import ScriptEditor from '../components/studio/ScriptEditor.vue';
import AgentPanel from '../components/studio/AgentPanel.vue';
import CharactersStage from '../components/studio/CharactersStage.vue';
import StoryboardStage from '../components/studio/StoryboardStage.vue';
import TimelineStage from '../components/studio/TimelineStage.vue';
import type { JSONContent } from '@tiptap/vue-3';

const route = useRoute(),
  app = useAppStore();
const workspaceId = String(route.params.workspaceId),
  projectId = String(route.params.projectId);
const {
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
  getKey,
} = useStudio(workspaceId, projectId);
const episodesOpen = ref(false),
  exportOpen = ref(false),
  agentOpen = ref(false),
  episodeDialog = ref(false),
  rename = ref(false),
  title = ref(''),
  count = ref(0),
  clearChatOpen = ref(false);
const scriptEditor = ref<InstanceType<typeof ScriptEditor>>();
const stageTitle = computed(() => stages.find((item) => item.key === stage.value)!.title);
function editEpisode(isRename = false) {
  rename.value = isRename;
  title.value = isRename ? episode.value?.title || '' : '';
  episodeDialog.value = true;
  episodesOpen.value = false;
}
function submitEpisode() {
  if (!canEdit.value) return;
  if (rename.value && episode.value) episode.value.title = title.value.trim() || '未命名';
  else addEpisode(title.value);
  episodeDialog.value = false;
}
function scriptText(node: JSONContent): string {
  if (node.type === 'text') return node.text || '';
  return (node.content || [])
    .map(scriptText)
    .join(['doc', 'blockquote'].includes(node.type || '') ? '\n\n' : '');
}
function exportDraft() {
  flush();
  downloadFile(
    `${project.value?.name || '项目'}-草稿.json`,
    JSON.stringify(
      {
        project: project.value,
        draft: draft.value,
        savedIn: 'local-browser',
        exportedAt: new Date().toISOString(),
      },
      null,
      2,
    ),
    'application/json',
  );
  exportOpen.value = false;
}
function exportScript() {
  if (episode.value)
    downloadFile(
      `${project.value?.name}-第${episodeIndex.value + 1}集.txt`,
      scriptText(episode.value.script),
    );
  exportOpen.value = false;
}
function insertSuggestion(text: string) {
  if (!episode.value || !canEdit.value) return;
  if (stage.value === 'script' && scriptEditor.value) scriptEditor.value.insert(text);
  else episode.value.script.content?.push({ type: 'paragraph', content: [{ type: 'text', text }] });
  app.notify('已插入本集剧本末尾');
}
function recovery() {
  const raw = localStorage.getItem(getKey());
  if (raw) downloadFile('草稿恢复副本.json', raw, 'application/json');
  else app.notify('未找到可导出的本机副本', true);
}
</script>
<template>
  <main v-if="loading" class="full-state">
    <UiIcon name="loading" class="spin" :size="30" />
    <p>正在打开项目…</p>
  </main>
  <main v-else-if="error || !project || !draft || !episode" class="full-state">
    <UiIcon name="warning" :size="32" />
    <h2>暂时无法打开项目</h2>
    <p>{{ error }}</p>
    <div class="row-actions">
      <RouterLink to="/" class="quiet-button">返回首页</RouterLink
      ><button class="text-button" @click="recovery">导出本机副本</button>
    </div>
  </main>
  <div
    v-else
    class="studio-shell"
    @keydown.esc="
      episodesOpen = false;
      exportOpen = false;
      agentOpen = false;
    "
  >
    <header class="studio-header">
      <div class="project-context">
        <RouterLink to="/" class="icon-button" aria-label="返回项目首页"
          ><UiIcon name="back" :size="24" /></RouterLink
        ><span class="project-name" :title="project.name">{{ truncateName(project.name) }}</span
        ><span class="divider" /><button
          class="episode-trigger"
          :aria-expanded="episodesOpen"
          aria-controls="episode-menu"
          @click="episodesOpen = !episodesOpen"
        >
          <span>第 {{ episodeIndex + 1 }} 集</span><UiIcon name="down" :size="16" /></button
        ><span class="episode-name" :title="episode.title">{{
          truncateName(episode.title, 8)
        }}</span
        ><button
          class="icon-button round episode-add"
          aria-label="新增剧集"
          title="新增剧集"
          :disabled="!canEdit"
          @click="editEpisode()"
        >
          <UiIcon name="plus" :size="23" />
        </button>
      </div>
      <nav class="stage-tabs" aria-label="创作步骤">
        <button
          v-for="item in stages"
          :key="item.key"
          :class="{ active: stage === item.key }"
          :aria-current="stage === item.key ? 'step' : undefined"
          @click="stage = item.key"
        >
          {{ item.title }}
        </button>
      </nav>
      <div class="studio-actions">
        <span
          class="save-status"
          :class="{ 'error-text': saveError }"
          :title="
            saveError ||
            (demo
              ? '示例项目的所有修改仅保存于当前浏览器。'
              : '剧集、剧本、角色与编排保存于当前浏览器；镜头保存于服务器。')
          "
          ><UiIcon :name="saveError ? 'warning' : 'saved'" :size="22" />{{ saveLabel }}</span
        ><span class="divider" /><button
          class="quiet-button export-button"
          aria-label="导出"
          :aria-expanded="exportOpen"
          @click="exportOpen = !exportOpen"
        >
          导出<UiIcon name="down" :size="14" /></button
        ><button
          class="icon-button agent-mobile-toggle"
          aria-label="打开智能体"
          @click="agentOpen = !agentOpen"
        >
          <UiIcon name="sparkle" />
        </button>
      </div>
    </header>
    <template v-if="episodesOpen"
      ><button class="popover-backdrop" aria-label="关闭剧集选择" @click="episodesOpen = false" />
      <section id="episode-menu" class="episode-popover" aria-label="选择剧集">
        <header>
          <span>剧集</span
          ><button class="icon-button" aria-label="关闭剧集选择" @click="episodesOpen = false">
            <UiIcon name="close" :size="16" />
          </button>
        </header>
        <div class="episode-options">
          <button
            v-for="(item, index) in draft.episodes"
            :key="item.id"
            :class="{ selected: selectedId === item.id }"
            @click="
              flush();
              selectedId = item.id;
              episodesOpen = false;
            "
          >
            <span class="episode-option-index">{{ String(index + 1).padStart(2, '0') }}</span
            ><span
              ><strong>第 {{ index + 1 }} 集</strong><small>{{ item.title }}</small></span
            ><UiIcon v-if="selectedId === item.id" name="check" :size="18" />
          </button>
        </div>
        <footer>
          <button class="text-button" :disabled="!canEdit" @click="editEpisode()">
            <UiIcon name="plus" :size="16" />新增剧集</button
          ><button class="text-button" :disabled="!canEdit" @click="editEpisode(true)">
            <UiIcon name="edit" :size="16" />重命名本集
          </button>
        </footer>
      </section></template
    >
    <template v-if="exportOpen"
      ><button class="popover-backdrop" aria-label="关闭导出菜单" @click="exportOpen = false" />
      <div class="export-popover">
        <button @click="exportScript"><UiIcon name="script" />导出本集剧本</button
        ><button @click="exportDraft"><UiIcon name="download" />导出完整草稿</button>
        <p>成片视频导出尚未接入</p>
      </div></template
    >
    <div class="studio-body">
      <div class="creative-pane">
        <p v-if="saveError" class="save-error" role="alert">
          {{ saveError }}<button class="text-button" @click="exportDraft">导出备份</button>
        </p>
        <ScriptEditor
          v-if="stage === 'script'"
          :key="episode.id"
          ref="scriptEditor"
          :content="episode.script"
          :editable="canEdit"
          :confirmed="episode.confirmed"
          @update="
            episode.script = $event;
            episode.confirmed = false;
          "
          @count="count = $event"
          @confirm="
            episode.confirmed = true;
            stage = 'characters';
          "
          ><template #status
            ><span class="editor-save-label"
              ><span v-if="demo" class="demo-label">示例项目</span
              ><span v-else>{{ canEdit ? '本机草稿' : '只读' }}</span
              ><span class="divider" />{{ count.toLocaleString('zh-CN') }} 字</span
            ></template
          ></ScriptEditor
        ><CharactersStage
          v-else-if="stage === 'characters'"
          :characters="episode.characters"
          :editable="canEdit"
          :workspace-id="workspaceId"
          @update="episode.characters = $event"
        /><StoryboardStage
          v-else-if="stage === 'storyboard'"
          :shots="episode.shots"
          :clips="episode.clips"
          :editable="canEdit"
          :workspace-id="workspaceId"
          :save="saveShot"
          @update-clips="episode.clips = $event"
          @timeline="stage = 'timeline'"
        /><TimelineStage
          v-else
          :key="episode.id"
          :shots="episode.shots"
          :clips="episode.clips"
          :editable="canEdit"
          :workspace-id="workspaceId"
          @update="episode.clips = $event"
        />
      </div>
      <AgentPanel
        :key="episode.id + stage"
        :class="{ 'mobile-open': agentOpen }"
        :messages="episode.messages"
        :episode-number="episodeIndex + 1"
        :stage="stageTitle"
        :demo="demo"
        :editable="canEdit"
        @add="episode.messages.push($event)"
        @clear="clearChatOpen = true"
        @insert="insertSuggestion"
        @close="agentOpen = false"
      />
    </div>
  </div>
  <UiDialog
    :open="episodeDialog"
    :title="rename ? '重命名本集' : '新增剧集'"
    @close="episodeDialog = false"
    ><form class="form-stack" @submit.prevent="submitEpisode">
      <label
        >集名<input
          v-model="title"
          maxlength="120"
          placeholder="给这一集起个名字"
          autofocus /></label
      ><button class="primary">
        {{ rename ? '保存名称' : '创建并进入新剧集' }}<UiIcon name="right" :size="18" />
      </button></form
  ></UiDialog>
  <UiDialog :open="clearChatOpen" title="开始新对话" @close="clearChatOpen = false"
    ><p class="muted">当前对话将从本集草稿中清空。剧本、角色和镜头不会改变。</p>
    <div class="dialog-actions">
      <button class="quiet-button" @click="clearChatOpen = false">取消</button
      ><button
        class="primary"
        @click="
          episode && (episode.messages = []);
          clearChatOpen = false;
        "
      >
        开始新对话
      </button>
    </div></UiDialog
  >
</template>
