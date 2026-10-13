<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue';
import {
  storyNodeOrder,
  type CanvasNode,
  type CanvasData,
  type CanvasKind,
} from '../../domain/canvas';
import UiIcon from '../UiIcon.vue';
import StoryboardCard from './StoryboardCard.vue';
import StoryboardDetail from './StoryboardDetail.vue';
import '../../styles/storyboard.css';

const props = defineProps<{
  nodes: CanvasNode[];
  order: string[];
  references: Map<string, CanvasNode[]>;
  readOnly?: boolean;
}>();
const selectedId = defineModel<string>('selectedId', { default: '' });
const emit = defineEmits<{
  add: [kind: CanvasKind];
  checkpoint: [];
  update: [id: string, patch: Partial<CanvasData>];
  media: [id: string];
  generate: [];
  locate: [id: string];
  duplicate: [id: string];
  remove: [id: string];
  removeReference: [target: string, source: string];
  reorder: [ids: string[]];
}>();
type Group = 'text' | 'image' | 'video' | 'audio';
const groupOf = (node: CanvasNode): Group =>
  node.data.kind === 'script' ? 'text' : node.data.kind;
const allGroups: { id: Group; label: string; icon: string }[] = [
  { id: 'text', label: '文本', icon: 'align' },
  { id: 'image', label: '图片', icon: 'image' },
  { id: 'video', label: '视频', icon: 'video' },
  { id: 'audio', label: '音频', icon: 'waveform' },
];
const groups = computed(() =>
  allGroups.filter((g) => g.id !== 'audio' || props.nodes.some((n) => n.data.kind === 'audio')),
);
const root = ref<HTMLElement>();
const expanded = ref<Group | ''>('');
const mobileGroup = ref<Group>('text');
const history = ref<string[]>([]);
const selected = computed(() => props.nodes.find((n) => n.id === selectedId.value));
const ordered = computed(() => {
  const byId = new Map(props.nodes.map((n) => [n.id, n]));
  return storyNodeOrder(props.nodes, props.order).map((id) => byId.get(id)!);
});
const grouped = computed(
  () => new Map(allGroups.map((g) => [g.id, ordered.value.filter((n) => groupOf(n) === g.id)])),
);
const context = ref<{ id: string; x: number; y: number }>();
const announcement = ref('');
const dragged = ref('');
const dropTarget = ref('');
const dropAfter = ref(false);
const videoFilter = ref('all');
const shownGroup = computed(() =>
  selected.value ? groupOf(selected.value) : expanded.value || mobileGroup.value,
);

watch(
  () => props.nodes.map((n) => n.id),
  (ids) => {
    history.value = history.value.filter((id) => ids.includes(id));
    if (selectedId.value && !ids.includes(selectedId.value)) selectedId.value = '';
    if (context.value && !ids.includes(context.value.id)) context.value = undefined;
    if (!props.nodes.some((node) => node.data.kind === 'audio')) {
      if (mobileGroup.value === 'audio') mobileGroup.value = 'text';
      if (expanded.value === 'audio') expanded.value = '';
    }
  },
  { immediate: true },
);
watch(selected, (node) => {
  if (!node) return;
  mobileGroup.value = groupOf(node);
  if (
    node.data.kind === 'video' &&
    ((videoFilter.value === 'ready' && !node.data.media) ||
      (videoFilter.value === 'empty' && node.data.media))
  )
    videoFilter.value = 'all';
});
function list(group: Group) {
  const items = grouped.value.get(group) || [];
  return group === 'video' && videoFilter.value !== 'all'
    ? items.filter((n) => (videoFilter.value === 'ready' ? !!n.data.media : !n.data.media))
    : items;
}
function visibleColumn(group: Group) {
  if (selected.value) return group === groupOf(selected.value);
  return (
    !expanded.value || group === expanded.value || (expanded.value !== 'text' && group === 'text')
  );
}
async function open(id: string, origin?: string) {
  if (!props.nodes.some((n) => n.id === id)) return;
  context.value = undefined;
  if (origin && origin !== id) history.value.push(origin);
  else history.value = [];
  selectedId.value = id;
  await nextTick();
  root.value
    ?.querySelector<HTMLButtonElement>('.story-detail-header button')
    ?.focus({ preventScroll: true });
}
async function close() {
  const id = selectedId.value;
  selectedId.value = '';
  history.value = [];
  context.value = undefined;
  await nextTick();
  Array.from(root.value?.querySelectorAll<HTMLElement>('[data-story-node]') || [])
    .find((el) => el.dataset.storyNode === id)
    ?.querySelector<HTMLButtonElement>('.story-card-title')
    ?.focus({ preventScroll: true });
}
async function back() {
  if (context.value) {
    context.value = undefined;
    return;
  }
  const previous = history.value.pop();
  if (previous && props.nodes.some((n) => n.id === previous)) {
    selectedId.value = previous;
    await nextTick();
    root.value
      ?.querySelector<HTMLButtonElement>('.story-detail-header button')
      ?.focus({ preventScroll: true });
  } else if (selected.value) await close();
  else expanded.value = '';
}
function changeGroup(event: Event) {
  const group = (event.target as HTMLSelectElement).value as Group;
  const node = list(group)[0];
  if (node) void open(node.id);
  else {
    selectedId.value = '';
    history.value = [];
    expanded.value = group;
    mobileGroup.value = group;
  }
}
function toggleExpanded(group: Group) {
  expanded.value = expanded.value === group ? '' : group;
  mobileGroup.value = group;
}
async function showMenu(event: MouseEvent, id: string) {
  event.preventDefault();
  context.value = {
    id,
    x: Math.max(8, Math.min(event.clientX, window.innerWidth - 220)),
    y: Math.max(60, Math.min(event.clientY, window.innerHeight - 265)),
  };
  await nextTick();
  root.value
    ?.querySelector<HTMLButtonElement>('.story-context-menu button:not(:disabled)')
    ?.focus();
}
function move(id: string, target: string, after = false) {
  if (props.readOnly || id === target) return;
  const node = props.nodes.find((n) => n.id === id),
    other = props.nodes.find((n) => n.id === target);
  if (!node || !other || groupOf(node) !== groupOf(other)) return;
  const ids = ordered.value.map((n) => n.id).filter((value) => value !== id);
  ids.splice(ids.indexOf(target) + (after ? 1 : 0), 0, id);
  emit('reorder', ids);
  announcement.value = node.data.title + '顺序已更新';
}
function sibling(id: string, offset: number) {
  const node = props.nodes.find((n) => n.id === id);
  if (!node) return;
  const items = grouped.value.get(groupOf(node)) || [];
  return items[items.findIndex((n) => n.id === id) + offset];
}
function moveBy(id: string, offset: number) {
  const target = sibling(id, offset);
  if (target) move(id, target.id, offset > 0);
  context.value = undefined;
}
function startDrag(event: DragEvent, id: string) {
  if (props.readOnly || !event.dataTransfer) {
    event.preventDefault();
    return;
  }
  dragged.value = id;
  event.dataTransfer.effectAllowed = 'move';
  event.dataTransfer.setData('text/plain', id);
}
function dragOver(event: DragEvent, node: CanvasNode) {
  const source = props.nodes.find((n) => n.id === dragged.value);
  if (props.readOnly || !source || groupOf(source) !== groupOf(node) || source.id === node.id)
    return;
  event.preventDefault();
  const bounds = (event.currentTarget as HTMLElement).getBoundingClientRect();
  dropTarget.value = node.id;
  dropAfter.value = event.clientY > bounds.top + bounds.height / 2;
}
function endDrag() {
  dragged.value = '';
  dropTarget.value = '';
}
function drop(event: DragEvent, id: string) {
  if (dropTarget.value !== id) return;
  event.preventDefault();
  move(dragged.value, id, dropAfter.value);
  endDrag();
}
function keydown(event: KeyboardEvent) {
  if (event.isComposing) return;
  if (event.key === 'Escape') {
    event.preventDefault();
    event.stopPropagation();
    void back();
  }
  if (!context.value) return;
  const duplicate = (event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 'd';
  if (duplicate || event.key === 'Delete' || event.key === 'Backspace') {
    event.preventDefault();
    event.stopPropagation();
    if (!props.readOnly) {
      if (duplicate) emit('duplicate', context.value.id);
      else emit('remove', context.value.id);
      context.value = undefined;
    }
    return;
  }
  if (event.key === 'Tab') {
    context.value = undefined;
    return;
  }
  if (['ArrowDown', 'ArrowUp', 'Home', 'End'].includes(event.key)) {
    event.preventDefault();
    const buttons = [
      ...root.value!.querySelectorAll<HTMLButtonElement>(
        '.story-context-menu button:not(:disabled)',
      ),
    ];
    const current = buttons.indexOf(event.target as HTMLButtonElement);
    const index =
      event.key === 'Home'
        ? 0
        : event.key === 'End'
          ? buttons.length - 1
          : (current + (event.key === 'ArrowDown' ? 1 : -1) + buttons.length) % buttons.length;
    buttons[index]?.focus();
  }
}
defineExpose({ back });
</script>

<template>
  <section
    ref="root"
    class="canvas-storyboard"
    :class="{
      'has-detail': selected,
      'has-expanded': expanded && !selected,
      'has-audio': groups.length === 4,
    }"
    aria-label="画布故事板"
    @keydown="keydown"
    @pointerdown="
      !($event.target as Element).closest('.story-context-menu') && (context = undefined)
    "
    @dragend="endDrag"
  >
    <nav class="story-mobile-tabs" aria-label="内容分类">
      <button
        v-for="group in groups"
        :key="group.id"
        :class="{ active: mobileGroup === group.id }"
        :aria-pressed="mobileGroup === group.id"
        @click="
          mobileGroup = group.id;
          selectedId = '';
          history = [];
          expanded = '';
        "
      >
        <UiIcon :name="group.icon" :size="16" weight="regular" />{{ group.label }}
      </button>
    </nav>
    <section
      v-for="group in groups"
      v-show="visibleColumn(group.id)"
      :key="group.id"
      class="story-column"
      :class="{
        'mobile-active': mobileGroup === group.id,
        'expanded-column': expanded === group.id,
        'media-column': group.id !== 'text',
      }"
      :aria-label="group.label + '列表'"
    >
      <header class="story-column-header">
        <select v-if="selected" :value="shownGroup" aria-label="切换内容分类" @change="changeGroup">
          <option v-for="item in groups" :key="item.id" :value="item.id">{{ item.label }}</option>
        </select>
        <h2 v-else>{{ group.label }}</h2>
        <select
          v-if="group.id === 'video'"
          v-model="videoFilter"
          class="story-video-filter"
          aria-label="筛选视频"
        >
          <option value="all">全部</option>
          <option value="ready">已有素材</option>
          <option value="empty">待创作</option>
        </select>
        <button
          class="story-icon-button"
          :aria-label="'添加' + group.label + '节点'"
          :title="'添加' + group.label"
          :disabled="readOnly"
          @click="emit('add', group.id)"
        >
          <UiIcon name="plus" :size="17" weight="regular" />
        </button>
        <button
          v-if="!selected"
          class="story-icon-button"
          :aria-label="(expanded === group.id ? '收起' : '展开') + group.label + '列表'"
          :title="expanded === group.id ? '返回三栏' : '展开列表'"
          @click="toggleExpanded(group.id)"
        >
          <UiIcon :name="expanded === group.id ? 'close' : 'expand'" :size="17" weight="regular" />
        </button>
      </header>
      <div class="story-column-scroll">
        <div v-if="!list(group.id).length" class="story-column-empty">
          <UiIcon :name="group.icon" :size="30" weight="regular" />
          <p>
            {{
              group.id === 'video' && videoFilter !== 'all'
                ? '没有符合条件的视频'
                : '还没有' + group.label
            }}
          </p>
          <button class="quiet-button" :disabled="readOnly" @click="emit('add', group.id)">
            <UiIcon name="plus" :size="15" weight="regular" />添加{{ group.label }}
          </button>
        </div>
        <div
          v-for="node in list(group.id)"
          :key="node.id"
          class="story-sort-item"
          :class="{
            'drop-before': dropTarget === node.id && !dropAfter,
            'drop-after': dropTarget === node.id && dropAfter,
            dragging: dragged === node.id,
          }"
          @dragover="dragOver($event, node)"
          @drop="drop($event, node.id)"
        >
          <StoryboardCard
            :node="node"
            :selected="selectedId === node.id"
            :references="references.get(node.id) || []"
            :read-only="readOnly"
            @open="open(node.id)"
            @reference="open($event, node.id)"
            @more="showMenu($event, node.id)"
            @drag="startDrag($event, node.id)"
          />
        </div>
      </div>
    </section>
    <StoryboardDetail
      v-if="selected"
      :key="selected.id"
      :node="selected"
      :references="references.get(selected.id) || []"
      :read-only="readOnly"
      :can-back="!!history.length"
      @checkpoint="emit('checkpoint')"
      @update="emit('update', selected!.id, $event)"
      @media="emit('media', selected!.id)"
      @generate="emit('generate')"
      @reference="open($event, selected!.id)"
      @remove-reference="emit('removeReference', selected!.id, $event)"
      @back="back"
      @close="close"
      @locate="emit('locate', selected!.id)"
      @more="showMenu($event, selected!.id)"
    />
    <div
      v-if="context"
      class="story-context-menu"
      role="menu"
      aria-label="故事板节点操作"
      :style="{ left: context.x + 'px', top: context.y + 'px' }"
    >
      <button role="menuitem" @click="open(context!.id)">
        <UiIcon name="edit" :size="16" weight="regular" />打开详情
      </button>
      <button
        role="menuitem"
        @click="
          emit('locate', context!.id);
          context = undefined;
        "
      >
        <UiIcon name="flow" :size="16" weight="regular" />在工作流中定位
      </button>
      <hr />
      <button
        role="menuitem"
        :disabled="readOnly || !sibling(context.id, -1)"
        @click="moveBy(context!.id, -1)"
      >
        <UiIcon name="up" :size="16" weight="regular" />上移
      </button>
      <button
        role="menuitem"
        :disabled="readOnly || !sibling(context.id, 1)"
        @click="moveBy(context!.id, 1)"
      >
        <UiIcon name="up" class="story-down" :size="16" weight="regular" />下移
      </button>
      <hr />
      <button
        role="menuitem"
        :disabled="readOnly"
        @click="
          emit('duplicate', context!.id);
          context = undefined;
        "
      >
        <UiIcon name="copy" :size="16" weight="regular" />创建副本
      </button>
      <button
        role="menuitem"
        class="danger"
        :disabled="readOnly"
        @click="
          emit('remove', context!.id);
          context = undefined;
        "
      >
        <UiIcon name="trash" :size="16" weight="regular" />删除节点
      </button>
    </div>
    <span class="sr-only" role="status">{{ announcement }}</span>
  </section>
</template>
