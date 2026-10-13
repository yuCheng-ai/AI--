<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import {
  VueFlow,
  useVueFlow,
  getBezierPath,
  Position,
  type Connection,
  type NodeMouseEvent,
  type OnConnectStartParams,
  type ViewportTransform,
} from '@vue-flow/core';
import { Background } from '@vue-flow/background';
import { MiniMap } from '@vue-flow/minimap';
import '@vue-flow/core/dist/style.css';
import '@vue-flow/core/dist/theme-default.css';
import '@vue-flow/minimap/dist/style.css';
import '../styles/canvas.css';
import CreativeNode from '../components/canvas/CreativeNode.vue';
import FlowingEdge from '../components/canvas/FlowingEdge.vue';
import CanvasAssets from '../components/canvas/CanvasAssets.vue';
import CanvasStoryboard from '../components/canvas/CanvasStoryboard.vue';
import UiIcon from '../components/UiIcon.vue';
import UiDialog from '../components/UiDialog.vue';
import { useAppStore } from '../stores/app';
import { useCanvasDocument } from '../composables/useCanvasDocument';
import {
  blankBoard,
  boardSchema,
  canvasSchema,
  canvasTools,
  canConnectKinds,
  connectionRestriction,
  parseCanvas,
  storyNodeOrder,
  type CanvasData,
  type CanvasKind,
  type CanvasNode,
  type CanvasEdge,
} from '../domain/canvas';
import type { Media } from '../domain/types';
import { layoutCanvas } from '../domain/canvasLayout';
import { downloadFile } from '../lib/format';

const app = useAppStore();
const state = useCanvasDocument();
const { document: doc, ready, error, saving, undoStack, redoStack } = state;
const board = computed(
  () => doc.value.boards.find((b) => b.id === doc.value.activeId) || doc.value.boards[0]!,
);
const nodes = ref<CanvasNode[]>([]),
  edges = ref<CanvasEdge[]>([]);
const referencesByNode = computed(() => {
  const byId = new Map(nodes.value.map((node) => [node.id, node]));
  const references = new Map<string, CanvasNode[]>();
  for (const edge of edges.value) {
    const source = byId.get(edge.source);
    if (!source || edge.source === edge.target || !byId.has(edge.target)) continue;
    const inputs = references.get(edge.target) || [];
    // Only direct incoming nodes are references; keep connection order and deduplicate sources.
    if (!inputs.some((node) => node.id === source.id)) inputs.push(source);
    references.set(edge.target, inputs);
  }
  return references;
});
const root = ref<HTMLElement>(),
  popover = ref<HTMLElement>(),
  importInput = ref<HTMLInputElement>();
const flow = useVueFlow();
const zoom = computed(() => flow.viewport.value.zoom),
  mode = ref<'select' | 'hand'>('select'),
  view = ref<'flow' | 'board'>('flow');
const storyboard = ref<InstanceType<typeof CanvasStoryboard>>();
const storySelectedId = ref('');
const spacePressed = ref(false);
const panning = computed(() => mode.value === 'hand' || spacePressed.value);
const menu = ref(''),
  panel = ref(''),
  dialog = ref(''),
  filter = ref(''),
  mediaTarget = ref('');
const renameDraft = ref('');
let renaming = false;
const placement = ref<{ x: number; y: number }>(),
  contextPoint = ref<{ x: number; y: number }>();
const contextNodeIds = ref<string[]>([]);
type ConnectionOrigin = {
  nodeId: string;
  handleId: string | null;
  type: 'source' | 'target';
  boardId: string;
  point: { x: number; y: number };
};
let activeConnection: ConnectionOrigin | undefined;
const pendingConnection = ref<ConnectionOrigin>();
const branchTools = computed(() => {
  const origin = pendingConnection.value;
  const node = nodes.value.find((item) => item.id === origin?.nodeId);
  if (!origin || !node) return [];
  return canvasTools.map((tool) => {
    const source = origin.type === 'source' ? node.data.kind : tool.kind;
    const target = origin.type === 'source' ? tool.kind : node.data.kind;
    const enabled = canConnectKinds(source, target);
    return {
      ...tool,
      enabled,
      reason: enabled ? undefined : connectionRestriction(source, target),
    };
  });
});
const branchPreview = computed(() => {
  const origin = pendingConnection.value;
  if (menu.value !== 'branch' || !origin || !placement.value) return '';
  const { x, y, zoom } = flow.viewport.value;
  return getBezierPath({
    sourceX: origin.point.x * zoom + x,
    sourceY: origin.point.y * zoom + y,
    sourcePosition: origin.type === 'source' ? Position.Right : Position.Left,
    targetX: placement.value.x * zoom + x,
    targetY: placement.value.y * zoom + y,
    targetPosition: origin.type === 'source' ? Position.Left : Position.Right,
  })[0];
});
watch(
  menu,
  (value) => {
    if (value !== 'branch') pendingConnection.value = undefined;
    if (!value) {
      placement.value = undefined;
      contextPoint.value = undefined;
    }
  },
  { flush: 'sync' },
);
const selected = computed(() => flow.getSelectedNodes.value);
const flowingNodeIds = computed(
  () => new Set(view.value === 'flow' ? selected.value.map((n) => n.id) : []),
);
const listed = computed(() =>
  nodes.value.filter((n) => n.data!.title.toLowerCase().includes(filter.value.toLowerCase())),
);
const writable = computed(() => ready.value && !error.value);
let swapping = false;
let flowReady = false;
flow.onInit(() => {
  flowReady = true;
  void flow.setViewport(board.value.viewport);
});

function syncGraph() {
  if (swapping || !ready.value) return;
  const value = boardSchema.parse({ ...board.value, nodes: nodes.value, edges: edges.value });
  if (JSON.stringify(value.nodes) !== JSON.stringify(board.value.nodes))
    board.value.nodes = value.nodes;
  if (JSON.stringify(value.edges) !== JSON.stringify(board.value.edges))
    board.value.edges = value.edges;
  const order = storyNodeOrder(nodes.value, board.value.storyOrder);
  if (JSON.stringify(order) !== JSON.stringify(board.value.storyOrder))
    board.value.storyOrder = order;
}
watch([nodes, edges], syncGraph, { deep: true, flush: 'sync' });
watch(
  [doc, () => doc.value.activeId, ready],
  async () => {
    swapping = true;
    nodes.value = structuredClone(
      board.value.nodes.map((n) => ({
        ...n,
        position: { ...n.position },
        data: JSON.parse(JSON.stringify(n.data)),
      })),
    );
    edges.value = board.value.edges.map((e) => ({ ...e }));
    swapping = false;
    menu.value = '';
    activeConnection = undefined;
    panel.value = '';
    mediaTarget.value = '';
    await nextTick();
    if (flowReady) await flow.setViewport(board.value.viewport);
  },
  { immediate: true },
);
function checkpoint() {
  if (writable.value) {
    syncGraph();
    state.checkpoint();
  }
}
function undo() {
  if (writable.value) {
    syncGraph();
    state.undo();
  }
}
function redo() {
  if (writable.value) {
    syncGraph();
    state.redo();
  }
}
function centerPosition() {
  const rect = root.value?.getBoundingClientRect();
  return flow.project({ x: (rect?.width || 1200) / 2 - 180, y: (rect?.height || 800) / 2 - 160 });
}
async function selectNodes(ids: string[]) {
  await nextTick();
  flow.setNodes(nodes.value);
  flow.removeSelectedElements();
  flow.addSelectedNodes(flow.getNodes.value.filter((n) => ids.includes(n.id)));
}
async function addNode(kind: CanvasKind, data?: Partial<CanvasData>, reference?: ConnectionOrigin) {
  if (!writable.value) return;
  if (nodes.value.length >= 200) return app.notify('每个画布最多 200 个节点，请新建画布。', true);
  if (reference) {
    const origin = nodes.value.find((n) => n.id === reference.nodeId);
    if (reference.boardId !== board.value.id || !origin) {
      menu.value = '';
      return app.notify('原节点已不存在，请重新拖出连线。', true);
    }
    const source = reference.type === 'source' ? origin.data.kind : kind;
    const target = reference.type === 'source' ? kind : origin.data.kind;
    if (!canConnectKinds(source, target))
      return app.notify(connectionRestriction(source, target), true);
    if (edges.value.length >= 500) return app.notify('每个画布最多 500 条连线。', true);
  }
  checkpoint();
  const tool = canvasTools.find((t) => t.kind === kind)!;
  const titles = new Set(nodes.value.map((n) => n.data.title));
  let number = 1;
  while (titles.has(tool.label + ' ' + number)) number++;
  const position = placement.value || centerPosition();
  const offset = nodes.value.some(
    (n) => Math.abs(n.position.x - position.x) < 10 && Math.abs(n.position.y - position.y) < 10,
  )
    ? 40
    : 0;
  nodes.value.forEach((n) => (n.selected = false));
  const node: CanvasNode = {
    id: crypto.randomUUID(),
    type: 'creative',
    position: { x: position.x + offset, y: position.y + offset },
    selected: true,
    data: {
      kind,
      title: tool.label + ' ' + number,
      text: '',
      prompt: '',
      ratio: '16:9',
      duration: '5',
      ...data,
    },
  };
  nodes.value.push(node);
  if (reference) {
    // Keep the new node and its reference edge in the same undo step.
    edges.value.push({
      id: crypto.randomUUID(),
      source: reference.type === 'source' ? reference.nodeId : node.id,
      target: reference.type === 'source' ? node.id : reference.nodeId,
      sourceHandle: reference.type === 'source' ? reference.handleId : null,
      targetHandle: reference.type === 'target' ? reference.handleId : null,
    });
  }
  menu.value = '';
  placement.value = undefined;
  contextPoint.value = undefined;
  if (view.value === 'board') storySelectedId.value = node.id;
  await selectNodes([node.id]);
  return node;
}
function updateNode(id: string, patch: Partial<CanvasData>) {
  if (!writable.value) return;
  const n = nodes.value.find((n) => n.id === id);
  if (n?.data) n.data = { ...n.data, ...patch };
}
function removeNodes(ids?: string[]) {
  const targets = ids ?? selected.value.map((n) => n.id);
  const removeSelectedEdges = ids === undefined;
  if (
    !writable.value ||
    (!targets.length && !(removeSelectedEdges && edges.value.some((e) => e.selected)))
  )
    return;
  checkpoint();
  // Remove incident edges first so the persisted graph always has valid endpoints.
  edges.value = edges.value.filter(
    (e) =>
      !targets.includes(e.source) &&
      !targets.includes(e.target) &&
      !(removeSelectedEdges && e.selected),
  );
  nodes.value = nodes.value.filter((n) => !targets.includes(n.id));
  menu.value = '';
}
async function duplicate(ids = selected.value.map((n) => n.id)) {
  if (!writable.value || !ids.length) return;
  menu.value = '';
  const originals = nodes.value.filter((n) => ids.includes(n.id));
  if (nodes.value.length + originals.length > 200) return app.notify('节点数量达到上限。', true);
  checkpoint();
  const mapping = new Map<string, string>();
  const copies = originals.map((n) => {
    const id = crypto.randomUUID();
    mapping.set(n.id, id);
    return {
      id,
      type: 'creative',
      position: { x: n.position.x + 48, y: n.position.y + 48 },
      selected: true,
      data: {
        ...JSON.parse(JSON.stringify(n.data)),
        title: (n.data!.title + ' 副本').slice(0, 80),
      },
    };
  });
  const connections = edges.value
    .filter((e) => mapping.has(e.source) && mapping.has(e.target))
    .map((e) => ({
      id: crypto.randomUUID(),
      source: mapping.get(e.source)!,
      target: mapping.get(e.target)!,
    }));
  if (edges.value.length + connections.length > 500) return app.notify('连线数量达到上限。', true);
  nodes.value.forEach((n) => (n.selected = false));
  nodes.value.push(...copies);
  edges.value.push(...connections);
  await selectNodes(copies.map((n) => n.id));
  if (view.value === 'board' && copies[0]) storySelectedId.value = copies[0].id;
}
function reorderStory(ids: string[]) {
  if (!writable.value) return;
  const order = storyNodeOrder(nodes.value, ids);
  if (JSON.stringify(order) === JSON.stringify(board.value.storyOrder)) return;
  checkpoint();
  board.value.storyOrder = order;
}
function isValidConnection(connection: Connection): boolean {
  const source = nodes.value.find((node) => node.id === connection.source);
  const target = nodes.value.find((node) => node.id === connection.target);
  return (
    !!source &&
    !!target &&
    source.id !== target.id &&
    canConnectKinds(source.data.kind, target.data.kind) &&
    !edges.value.some((edge) => edge.source === source.id && edge.target === target.id)
  );
}
function connect(connection: Connection) {
  activeConnection = undefined;
  if (!writable.value || !isValidConnection(connection)) return;
  if (edges.value.length >= 500) return app.notify('每个画布最多 500 条连线。', true);
  checkpoint();
  edges.value.push({ id: crypto.randomUUID(), ...connection });
}
function removeReference(target: string, source: string) {
  if (!writable.value || !edges.value.some((e) => e.source === source && e.target === target))
    return;
  checkpoint();
  edges.value = edges.value.filter((e) => e.source !== source || e.target !== target);
}
function startConnection(params: OnConnectStartParams & { event?: MouseEvent | TouchEvent }) {
  menu.value = '';
  activeConnection = undefined;
  if (!writable.value || !params.nodeId || !params.handleType) return;
  const handle =
    params.event?.target instanceof Element
      ? params.event.target.closest('.vue-flow__handle')
      : null;
  const bounds = handle?.getBoundingClientRect();
  const rect = root.value?.getBoundingClientRect();
  if (!bounds || !rect) return;
  activeConnection = {
    nodeId: params.nodeId,
    handleId: params.handleId,
    type: params.handleType,
    boardId: board.value.id,
    point: flow.project({
      x: bounds.left + bounds.width / 2 - rect.left,
      y: bounds.top + bounds.height / 2 - rect.top,
    }),
  };
}
async function endConnection(event?: MouseEvent | TouchEvent) {
  const origin = activeConnection;
  activeConnection = undefined;
  if (!origin || !event || !writable.value || origin.boardId !== board.value.id) return;
  const pointer = 'changedTouches' in event ? event.changedTouches[0] : event;
  if (!pointer) return;
  const target = document.elementFromPoint(pointer.clientX, pointer.clientY);
  if (!target?.classList.contains('vue-flow__pane') || !root.value?.contains(target)) return;
  const rect = root.value.getBoundingClientRect();
  const point = { x: pointer.clientX - rect.left, y: pointer.clientY - rect.top };
  placement.value = flow.project(point);
  pendingConnection.value = origin;
  contextPoint.value = point;
  menu.value = 'branch';
  await positionContextMenu();
}
watch(
  () => flow.viewport.value,
  () => {
    if (menu.value === 'branch') menu.value = '';
  },
  { deep: true },
);
watch([view, writable], () => {
  activeConnection = undefined;
  if (menu.value === 'branch') menu.value = '';
});
function saveViewport(value: ViewportTransform) {
  if (ready.value && !swapping && !error.value) board.value.viewport = { ...value };
}
watch(() => ({ ...flow.viewport.value }), saveViewport);
function fit() {
  menu.value = '';
  if (nodes.value.length) void flow.fitView({ padding: 0.08, maxZoom: 1, duration: 200 });
  else void flow.setViewport({ x: 0, y: 0, zoom: 1 });
}
function arrange() {
  if (!writable.value || !nodes.value.length) return;
  const positions = layoutCanvas(nodes.value, edges.value);
  if (
    !nodes.value.some((n) => {
      const point = positions.get(n.id)!;
      return n.position.x !== point.x || n.position.y !== point.y;
    })
  )
    return fit();
  checkpoint();
  nodes.value.forEach((n) => (n.position = positions.get(n.id)!));
  void nextTick(fit);
}
function toggleMenu(name: string) {
  if (name === 'boards') renameDraft.value = board.value.name;
  menu.value = menu.value === name ? '' : name;
  placement.value = undefined;
  contextPoint.value = undefined;
}
function dismissMenu(event: PointerEvent) {
  if (!(event.target as Element).closest('.canvas-popover,.canvas-menu-trigger')) menu.value = '';
}
function openAt(event: MouseEvent) {
  if (!writable.value || !(event.target as Element).closest('.vue-flow__pane')) return;
  event.preventDefault();
  const rect = root.value!.getBoundingClientRect();
  placement.value = flow.project({ x: event.clientX - rect.left, y: event.clientY - rect.top });
  contextPoint.value = {
    x: Math.max(8, Math.min(event.clientX - rect.left, rect.width - 230)),
    y: Math.max(64, Math.min(event.clientY - rect.top, rect.height - 370)),
  };
  menu.value = 'add';
}
async function openNodeMenu({ event, node }: NodeMouseEvent) {
  event.preventDefault();
  event.stopPropagation();
  if (!writable.value || !(event instanceof MouseEvent)) return;
  const ids = selected.value.some((item) => item.id === node.id)
    ? selected.value.map((item) => item.id)
    : [node.id];
  await selectNodes(ids);
  contextNodeIds.value = ids;
  placement.value = undefined;
  const rect = root.value!.getBoundingClientRect();
  contextPoint.value = { x: event.clientX - rect.left, y: event.clientY - rect.top };
  menu.value = 'node';
  await positionContextMenu();
}
async function positionContextMenu() {
  await nextTick();
  if (!popover.value || !contextPoint.value || !root.value) return;
  const rect = root.value.getBoundingClientRect();
  const bounds = popover.value.getBoundingClientRect();
  contextPoint.value = {
    x: Math.max(8, Math.min(contextPoint.value.x, rect.width - bounds.width - 8)),
    y: Math.max(8, Math.min(contextPoint.value.y, rect.height - bounds.height - 8)),
  };
  popover.value
    ?.querySelector<HTMLButtonElement>('button:not(:disabled)')
    ?.focus({ preventScroll: true });
}
function navigateContextMenu(event: KeyboardEvent) {
  if (menu.value !== 'node' && menu.value !== 'branch') return;
  if (event.key === 'Tab') {
    menu.value = '';
    return;
  }
  if (!['ArrowDown', 'ArrowUp', 'Home', 'End'].includes(event.key)) return;
  event.preventDefault();
  const items = Array.from(
    popover.value!.querySelectorAll<HTMLButtonElement>('button:not(:disabled)'),
  );
  if (!items.length) return;
  const current = items.indexOf(event.target as HTMLButtonElement);
  const index =
    event.key === 'Home'
      ? 0
      : event.key === 'End'
        ? items.length - 1
        : (current + (event.key === 'ArrowDown' ? 1 : -1) + items.length) % items.length;
  items[index]?.focus();
}
function newBoard() {
  if (!writable.value) return;
  if (doc.value.boards.length >= 20) return app.notify('最多保存 20 个画布，请先导出备份。', true);
  checkpoint();
  const next = blankBoard('画布 ' + (doc.value.boards.length + 1));
  doc.value.boards.push(next);
  doc.value.activeId = next.id;
  view.value = 'flow';
}
function rename(event: Event) {
  const value = (event.target as HTMLInputElement).value.trim();
  if (!value || !writable.value || value === board.value.name) return;
  if (!renaming) {
    checkpoint();
    renaming = true;
  }
  board.value.name = value;
}
function openAssets(id = '') {
  menu.value = '';
  mediaTarget.value = id;
  panel.value = panel.value === 'assets' && !id ? '' : 'assets';
}
async function useMedia(item: Media) {
  if (!writable.value) return;
  const kind = item.contentType.split('/')[0] as CanvasKind;
  if (!['image', 'video', 'audio'].includes(kind))
    return app.notify('画布支持图片、视频和音频素材。', true);
  const data = {
    media: {
      id: item.id,
      workspaceId: app.workspaceId,
      fileName: item.fileName,
      contentType: item.contentType,
    },
  };
  const target = nodes.value.find((n) => n.id === mediaTarget.value);
  if (target) {
    checkpoint();
    updateNode(target.id, data);
  } else await addNode(kind, { ...data, title: item.fileName.slice(0, 80) });
  panel.value = '';
  mediaTarget.value = '';
}
async function focusNode(id: string) {
  view.value = 'flow';
  await selectNodes([id]);
  await nextTick();
  void flow.fitView({ nodes: [id], maxZoom: 1, padding: 0.3, duration: 200 });
}
function exportCanvas(raw = false) {
  syncGraph();
  downloadFile(
    raw ? '画布原始备份.json' : board.value.name + '.json',
    raw ? state.rawBackup() : JSON.stringify(canvasSchema.parse(doc.value), null, 2),
    'application/json',
  );
  menu.value = '';
}
async function importCanvas(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0];
  if (!file) return;
  try {
    if (!writable.value) throw new Error('请先解决保存问题。');
    if (file.size > 4_000_000) throw new Error('画布文件不能超过 4 MB。');
    const imported = parseCanvas(await file.text());
    if (doc.value.boards.length + imported.boards.length > 20)
      throw new Error('导入后超过 20 个画布，请拆分文件。');
    checkpoint();
    const boards = imported.boards.map((b) => ({
      ...b,
      id: crypto.randomUUID(),
      name: (b.name + ' · 导入').slice(0, 50),
    }));
    doc.value.boards.push(...boards);
    doc.value.activeId = boards[0]!.id;
    view.value = 'flow';
    app.notify('画布已导入，现有内容已保留');
  } catch (e) {
    app.notify(
      e instanceof Error && !e.message.startsWith('[') ? e.message : '画布文件格式不正确。',
      true,
    );
  } finally {
    (event.target as HTMLInputElement).value = '';
  }
}
function keydown(event: KeyboardEvent) {
  if (event.defaultPrevented || event.isComposing || dialog.value || app.loginOpen) return;
  if (event.key === 'Escape') {
    event.preventDefault();
    if (!menu.value && !panel.value && view.value === 'board') {
      void storyboard.value?.back();
      return;
    }
    activeConnection = undefined;
    menu.value = '';
    panel.value = '';
    spacePressed.value = false;
    if (event.target instanceof HTMLElement) event.target.blur();
    root.value?.focus({ preventScroll: true });
    return;
  }
  if (
    (event.target as Element)?.closest('input,textarea,select,[contenteditable="true"],dialog') ||
    dialog.value ||
    app.loginOpen
  )
    return;
  const mod = event.metaKey || event.ctrlKey;
  if (
    event.code === 'Space' &&
    !mod &&
    view.value === 'flow' &&
    !(event.target as Element)?.closest('button,a,[role="button"]')
  ) {
    event.preventDefault();
    spacePressed.value = true;
    return;
  }
  if (mod && event.key.toLowerCase() === 'z') {
    event.preventDefault();
    event.shiftKey ? redo() : undo();
  } else if (mod && event.key.toLowerCase() === 'd') {
    event.preventDefault();
    void duplicate(view.value === 'board' ? [storySelectedId.value].filter(Boolean) : undefined);
  } else if (view.value === 'flow' && mod && event.key.toLowerCase() === 'a') {
    event.preventDefault();
    void selectNodes(nodes.value.map((n) => n.id));
  } else if (view.value === 'flow' && mod && event.key === '0') {
    event.preventDefault();
    fit();
  } else if (mod && event.key.toLowerCase() === 's') {
    event.preventDefault();
    state.flush();
  } else if (event.key === 'Backspace' || event.key === 'Delete') {
    event.preventDefault();
    removeNodes(view.value === 'board' ? [storySelectedId.value].filter(Boolean) : undefined);
  } else if (view.value === 'flow' && !mod && event.key.toLowerCase() === 'v')
    mode.value = 'select';
  else if (view.value === 'flow' && !mod && event.key.toLowerCase() === 'h') mode.value = 'hand';
  else if (!mod && event.key.toLowerCase() === 't') void addNode('text');
}
function releasePan(event?: KeyboardEvent) {
  if (!event || event.code === 'Space') spacePressed.value = false;
}
onMounted(() => {
  window.addEventListener('keydown', keydown);
  window.addEventListener('keyup', releasePan);
  window.addEventListener('blur', () => releasePan(), { signal: panEvents.signal });
});
const panEvents = new AbortController();
onBeforeUnmount(() => {
  syncGraph();
  window.removeEventListener('keydown', keydown);
  window.removeEventListener('keyup', releasePan);
  panEvents.abort();
});
</script>

<template>
  <main
    ref="root"
    class="creative-canvas"
    :class="{ 'edges-hidden': !doc.showEdges, 'is-panning': panning }"
    tabindex="-1"
    @pointerdown="dismissMenu"
  >
    <VueFlow
      v-if="ready"
      v-show="view === 'flow'"
      v-model:nodes="nodes"
      v-model:edges="edges"
      :min-zoom="0.2"
      :max-zoom="2"
      :default-viewport="board.viewport"
      :snap-to-grid="doc.snap"
      :snap-grid="[16, 16]"
      :delete-key-code="null"
      :nodes-draggable="writable && !panning"
      :node-drag-threshold="5"
      :nodes-connectable="writable && !panning"
      :elements-selectable="!panning"
      :pan-on-drag="panning ? true : [1, 2]"
      :pan-activation-key-code="null"
      :selection-key-code="panning ? null : true"
      :zoom-on-double-click="false"
      :elevate-edges-on-select="false"
      :default-edge-options="{ type: 'default', interactionWidth: 20 }"
      @connect="connect"
      @connect-start="startConnection"
      @connect-end="endConnection"
      @node-drag-start="checkpoint"
      @selection-drag-start="checkpoint"
      @viewport-change-end="saveViewport"
      @pane-context-menu="openAt"
      @node-context-menu="openNodeMenu"
      @dblclick="openAt"
    >
      <Background :gap="16" :size="1" pattern-color="#393939" />
      <template #edge-default="props">
        <FlowingEdge
          v-bind="props"
          :active="flowingNodeIds.has(props.source) || flowingNodeIds.has(props.target)"
        />
      </template>
      <MiniMap
        v-if="doc.minimap"
        pannable
        zoomable
        :width="180"
        :height="115"
        node-color="#5a5551"
        mask-color="#131313b8"
      />
      <template #node-creative="props"
        ><CreativeNode
          :id="props.id"
          :data="props.data"
          :selected="props.selected"
          :read-only="!writable"
          :references="referencesByNode.get(props.id) || []"
          :validate-connection="isValidConnection"
          @checkpoint="checkpoint"
          @update="updateNode(props.id, $event)"
          @media="openAssets(props.id)"
          @generate="dialog = 'model'"
          @focus-reference="focusNode"
          @remove-reference="removeReference(props.id, $event)"
      /></template>
    </VueFlow>
    <svg v-if="branchPreview" class="canvas-branch-preview" aria-hidden="true">
      <path :d="branchPreview" />
    </svg>

    <header class="canvas-top-left">
      <div class="canvas-floating canvas-breadcrumb">
        <RouterLink to="/" class="canvas-brand" title="返回首页" aria-label="返回首页"
          ><UiIcon name="film" :size="23" weight="regular"
        /></RouterLink>
        <span class="canvas-workspace" :title="app.workspace?.name || '映序工作室'">{{
          app.workspace?.name || '映序工作室'
        }}</span
        ><i class="canvas-divider" />
        <button
          class="canvas-menu-trigger canvas-board-trigger"
          :aria-expanded="menu === 'boards'"
          @click="toggleMenu('boards')"
        >
          {{ board.name }}<UiIcon name="down" :size="12" weight="regular" />
        </button>
      </div>
      <div class="canvas-floating canvas-view-tabs" aria-label="画布视图">
        <button
          :class="{ active: view === 'flow' }"
          :aria-pressed="view === 'flow'"
          aria-label="工作流视图"
          title="工作流"
          @click="view = 'flow'"
        >
          <UiIcon name="flow" :size="18" weight="regular" /></button
        ><button
          :class="{ active: view === 'board' }"
          :aria-pressed="view === 'board'"
          aria-label="故事板视图"
          title="故事板"
          @click="view = 'board'"
        >
          <UiIcon name="grid" :size="18" weight="regular" />
        </button>
      </div>
    </header>
    <div class="canvas-top-right">
      <span
        class="canvas-save-status"
        :class="{ error: !!error }"
        :title="error || '保存在当前浏览器，导出可备份或迁移'"
        role="status"
        ><UiIcon
          :name="error ? 'warning' : saving ? 'loading' : 'check'"
          :size="14"
          weight="regular"
        /><span>{{ error ? '保存受阻' : saving ? '保存中' : '已存本机' }}</span></span
      >
      <button
        class="canvas-floating canvas-menu-trigger canvas-file-button"
        title="画布文件"
        aria-label="画布文件"
        :aria-expanded="menu === 'file'"
        @click="toggleMenu('file')"
      >
        <UiIcon name="export" :size="18" weight="regular" />
      </button>
      <button
        class="canvas-avatar"
        :title="app.user ? '当前用户' : '登录工作室'"
        aria-label="工作室账号"
        @click="app.user ? (dialog = 'account') : (app.loginOpen = true)"
      >
        <UiIcon name="user" :size="17" weight="regular" />
      </button>
    </div>
    <div v-if="error" class="canvas-save-error" role="alert">
      <span>{{ error }}</span
      ><button @click="exportCanvas()">导出当前副本</button
      ><button @click="exportCanvas(true)">原始备份</button>
    </div>
    <div v-if="!ready" class="canvas-loading">
      <UiIcon name="loading" class="spin" weight="regular" />正在打开画布
    </div>

    <div v-if="ready && !nodes.length && view === 'flow'" class="canvas-welcome">
      <p><UiIcon name="cursor" :size="19" weight="regular" />双击画布，自由连接你的灵感</p>
      <div class="canvas-quick-start">
        <button
          v-for="tool in [...canvasTools.slice(1), canvasTools[0]!]"
          :key="tool.kind"
          :disabled="!writable"
          @click="addNode(tool.kind)"
        >
          <span class="canvas-quick-icon"
            ><UiIcon :name="tool.icon" :size="21" weight="regular" /></span
          >{{ tool.kind === 'text' ? '自由文本' : tool.label + '创作'
          }}<UiIcon name="plus" :size="14" class="quick-plus" weight="regular" />
        </button>
      </div>
    </div>
    <CanvasStoryboard
      v-if="ready"
      v-show="view === 'board'"
      ref="storyboard"
      :key="(app.user?.id || 'guest') + ':' + app.workspaceId + ':' + board.id"
      v-model:selected-id="storySelectedId"
      :nodes="nodes"
      :order="board.storyOrder"
      :references="referencesByNode"
      :read-only="!writable"
      @add="addNode($event)"
      @checkpoint="checkpoint"
      @update="updateNode"
      @media="openAssets"
      @generate="dialog = 'model'"
      @locate="focusNode"
      @duplicate="duplicate([$event])"
      @remove="removeNodes([$event])"
      @remove-reference="removeReference"
      @reorder="reorderStory"
    />

    <div
      v-if="menu"
      ref="popover"
      class="canvas-popover"
      :class="'canvas-menu-' + menu"
      :role="menu === 'node' || menu === 'branch' ? 'menu' : undefined"
      :aria-label="
        menu === 'node' ? '节点操作' : menu === 'branch' ? '选择连接节点类型' : undefined
      "
      @keydown="navigateContextMenu"
      @contextmenu.prevent
      :style="
        (menu === 'add' || menu === 'node' || menu === 'branch') && contextPoint
          ? {
              left: contextPoint.x + 'px',
              top: contextPoint.y + 'px',
              bottom: 'auto',
              transform: 'none',
            }
          : undefined
      "
    >
      <template v-if="menu === 'branch'">
        <h3>{{ pendingConnection?.type === 'source' ? '引用此节点创建' : '添加参考节点' }}</h3>
        <button
          v-for="tool in branchTools"
          :key="tool.kind"
          role="menuitem"
          :disabled="!writable || !tool.enabled"
          :title="tool.reason"
          @click="addNode(tool.kind, undefined, pendingConnection)"
        >
          <UiIcon :name="tool.icon" :size="18" weight="regular" />{{ tool.label }}
        </button>
      </template>
      <template v-if="menu === 'node'">
        <h3 v-if="contextNodeIds.length > 1">已选 {{ contextNodeIds.length }} 个节点</h3>
        <button role="menuitem" :disabled="!writable" @click="duplicate(contextNodeIds)">
          <UiIcon name="copy" :size="17" weight="regular" />创建副本<small>⌘ / Ctrl D</small>
        </button>
        <hr role="separator" />
        <button role="menuitem" :disabled="!writable" @click="removeNodes(contextNodeIds)">
          <UiIcon name="trash" :size="17" weight="regular" />删除<small>⌫ / Delete</small>
        </button>
      </template>
      <template v-if="menu === 'add'"
        ><h3>添加节点<span>NODES</span></h3>
        <button
          v-for="tool in canvasTools"
          :key="tool.kind"
          :disabled="!writable"
          @click="addNode(tool.kind)"
        >
          <UiIcon :name="tool.icon" :size="18" weight="regular" />{{ tool.label
          }}<small v-if="tool.kind === 'text'">T</small>
        </button>
        <hr />
        <h3>添加资源</h3>
        <button @click="openAssets()">
          <UiIcon name="folder" :size="18" weight="regular" />从素材库选择</button
        ><button :disabled="!writable" @click="importInput?.click()">
          <UiIcon name="import" :size="18" weight="regular" />导入画布文件
        </button></template
      >
      <template v-if="menu === 'boards'"
        ><h3>
          我的画布<span>{{ doc.boards.length }} / 20</span>
        </h3>
        <div class="canvas-board-options">
          <button
            v-for="item in doc.boards"
            :key="item.id"
            :class="{ active: item.id === board.id }"
            @click="
              doc.activeId = item.id;
              menu = '';
            "
          >
            <UiIcon name="flow" :size="16" weight="regular" /><span>{{ item.name }}</span
            ><UiIcon v-if="item.id === board.id" name="check" :size="15" weight="regular" />
          </button>
        </div>
        <hr />
        <label class="canvas-rename"
          >当前画布名称<input
            v-model="renameDraft"
            maxlength="50"
            aria-label="当前画布名称"
            :disabled="!writable"
            @focus="renaming = false"
            @input="rename"
            @blur="renameDraft = board.name" /></label
        ><button :disabled="!writable" @click="newBoard">
          <UiIcon name="plus" :size="18" weight="regular" />新建画布
        </button></template
      >
      <template v-if="menu === 'file'"
        ><h3>画布文件</h3>
        <button @click="exportCanvas()">
          <UiIcon name="export" :size="17" weight="regular" />导出全部画布</button
        ><button
          :disabled="!writable"
          @click="
            importInput?.click();
            menu = '';
          "
        >
          <UiIcon name="import" :size="17" weight="regular" />导入画布
        </button>
        <hr />
        <p class="canvas-menu-note">
          草稿保存在本机浏览器。导出 JSON 文件，可备份并在其他设备导入。
        </p></template
      >
      <template v-if="menu === 'zoom'"
        ><h3>
          视图<span>{{ Math.round(zoom * 100) }}%</span>
        </h3>
        <button @click="flow.zoomIn()">
          <UiIcon name="plus" :size="16" weight="regular" />放大</button
        ><button @click="flow.zoomOut()">
          <UiIcon name="minus" :size="16" weight="regular" />缩小</button
        ><button @click="fit">
          <UiIcon name="expand" :size="16" weight="regular" />适合屏幕<small>⌘ 0</small>
        </button>
        <hr />
        <button
          v-for="value in [0.5, 1, 2]"
          :key="value"
          @click="
            flow.zoomTo(value);
            menu = '';
          "
        >
          {{ value * 100 }}%<UiIcon
            v-if="Math.abs(zoom - value) < 0.01"
            name="check"
            :size="15"
            weight="regular"
          /></button
      ></template>
    </div>
    <input
      ref="importInput"
      type="file"
      hidden
      accept=".json,application/json"
      @change="importCanvas"
    />

    <CanvasAssets
      v-if="panel === 'assets'"
      :key="mediaTarget"
      :kind="nodes.find((n) => n.id === mediaTarget)?.data?.kind"
      @close="panel = ''"
      @select="useMedia"
    />
    <aside v-if="panel === 'nodes'" class="canvas-drawer canvas-node-list" aria-label="节点管理">
      <header>
        <div>
          <h2>画布节点</h2>
          <span>{{ nodes.length }} 个节点 · {{ edges.length }} 条连接</span>
        </div>
        <button class="icon-button" aria-label="关闭节点管理" @click="panel = ''">
          <UiIcon name="close" weight="regular" />
        </button>
      </header>
      <div class="canvas-asset-search input-surface">
        <UiIcon name="search" :size="17" weight="regular" /><input
          v-model="filter"
          aria-label="搜索节点"
          placeholder="搜索节点"
        />
      </div>
      <div v-if="!listed.length" class="canvas-panel-empty">
        <UiIcon name="flow" :size="30" weight="regular" />
        <p>{{ filter ? '没有匹配的节点' : '从第一个节点开始' }}</p>
      </div>
      <button
        v-for="node in listed"
        :key="node.id"
        class="canvas-node-list-item"
        :class="{ active: node.selected }"
        @click="focusNode(node.id)"
      >
        <UiIcon :name="canvasTools.find((t) => t.kind === node.data?.kind)!.icon" :size="20" /><span
          ><strong>{{ node.data?.title }}</strong
          ><small>{{
            node.data?.text || canvasTools.find((t) => t.kind === node.data?.kind)!.label + '节点'
          }}</small></span
        ><UiIcon name="outward" :size="16" weight="regular" />
      </button>
    </aside>

    <div v-if="view === 'flow'" class="canvas-bottom-left" aria-label="视图控制">
      <button
        title="节点管理"
        aria-label="节点管理"
        :class="{ active: panel === 'nodes' }"
        @click="panel = panel === 'nodes' ? '' : 'nodes'"
      >
        <UiIcon name="rows" :size="17" weight="regular" /><span class="asset-manager-label"
          >节点管理</span
        ></button
      ><i class="canvas-divider" />
      <button
        title="整理画布"
        aria-label="整理画布"
        :disabled="!writable || !nodes.length"
        @click="arrange"
      >
        <UiIcon name="grid" :size="17" weight="regular" />
      </button>
      <button
        title="小地图"
        aria-label="切换小地图"
        :aria-pressed="doc.minimap"
        :class="{ active: doc.minimap }"
        @click="doc.minimap = !doc.minimap"
      >
        <UiIcon name="map" :size="17" weight="regular" />
      </button>
      <button
        :title="doc.showEdges ? '隐藏普通连线（保留选中节点的连线）' : '显示普通连线'"
        aria-label="切换节点连线"
        :aria-pressed="doc.showEdges"
        :class="{ active: doc.showEdges }"
        @click="doc.showEdges = !doc.showEdges"
      >
        <span class="canvas-toggle-icon" :class="{ 'is-off': !doc.showEdges }" aria-hidden="true">
          <UiIcon name="link" :size="17" weight="regular" />
        </span>
      </button>
      <button
        :title="doc.snap ? '关闭网格吸附' : '开启网格吸附'"
        aria-label="网格吸附"
        :aria-pressed="doc.snap"
        :class="{ active: doc.snap }"
        @click="doc.snap = !doc.snap"
      >
        <span class="canvas-toggle-icon" :class="{ 'is-off': !doc.snap }" aria-hidden="true">
          <UiIcon name="magnet" :size="17" weight="regular" />
        </span>
      </button>
      <button
        class="canvas-menu-trigger canvas-zoom-trigger"
        :aria-expanded="menu === 'zoom'"
        aria-label="缩放选项"
        @click="toggleMenu('zoom')"
      >
        {{ Math.round(zoom * 100) }}%<UiIcon name="down" :size="11" weight="regular" />
      </button>
    </div>
    <div class="canvas-dock-wrap">
      <div class="canvas-dock" aria-label="画布工具">
        <button
          class="canvas-menu-trigger canvas-add"
          :class="{ open: menu === 'add' }"
          :disabled="!writable"
          :aria-expanded="menu === 'add'"
          title="添加节点"
          aria-label="添加节点"
          @click="toggleMenu('add')"
        >
          <UiIcon name="plus" :size="24" weight="regular" />
        </button>
        <button
          v-if="view === 'flow'"
          :class="{ active: mode === 'select' }"
          :aria-pressed="mode === 'select'"
          title="选择 · V"
          aria-label="选择工具"
          @click="mode = 'select'"
        >
          <UiIcon name="cursor" :size="21" weight="regular" />
        </button>
        <button
          v-if="view === 'flow'"
          :class="{ active: mode === 'hand' }"
          :aria-pressed="mode === 'hand'"
          title="移动 · H"
          aria-label="移动工具"
          @click="mode = 'hand'"
        >
          <UiIcon name="hand" :size="21" weight="regular" />
        </button>
        <button
          :class="{ active: panel === 'assets' }"
          title="素材库"
          aria-label="素材库"
          @click="openAssets()"
        >
          <UiIcon name="folder" :size="21" weight="regular" /></button
        ><i class="canvas-divider" />
        <button
          :disabled="!undoStack.length || !writable"
          title="撤销 · ⌘ Z"
          aria-label="撤销"
          @click="undo"
        >
          <UiIcon name="undo" :size="19" weight="regular" />
        </button>
        <button
          :disabled="!redoStack.length || !writable"
          title="重做 · ⇧ ⌘ Z"
          aria-label="重做"
          @click="redo"
        >
          <UiIcon name="redo" :size="19" weight="regular" /></button
        ><i class="canvas-divider" />
        <button title="快捷键与操作" aria-label="快捷键与操作" @click="dialog = 'shortcuts'">
          <UiIcon name="keyboard" :size="21" weight="regular" />
        </button>
      </div>
      <button
        class="canvas-assistant"
        title="生成能力"
        aria-label="生成能力"
        @click="dialog = 'model'"
      >
        <UiIcon name="sparkle" :size="23" weight="regular" />
      </button>
    </div>
    <div v-if="selected.length > 1 && view === 'flow'" class="canvas-selection-bar">
      <span>已选 {{ selected.length }} 个节点</span>
    </div>
    <span v-if="nodes.length && view === 'flow'" class="canvas-corner-hint"
      >空格拖动画布 · 双击添加节点</span
    >

    <UiDialog
      :open="!!dialog"
      :title="
        dialog === 'shortcuts' ? '快捷键与操作' : dialog === 'account' ? '当前工作室' : '生成能力'
      "
      @close="dialog = ''"
    >
      <div v-if="dialog === 'shortcuts'" class="canvas-shortcuts">
        <p>从节点右侧连接点拖到另一节点左侧，建立参考关系。</p>
        <p>连线拖到空白处松开，选择类型即可创建并连接新节点。</p>
        <p>右键节点可创建副本或删除；多选后可批量操作。</p>
        <dl>
          <div
            v-for="item in [
              ['选择 / 框选', 'V'],
              ['移动画布', 'H / 空格 + 拖动'],
              ['新建文本', 'T'],
              ['复制选中节点', '⌘ / Ctrl + D'],
              ['撤销', '⌘ / Ctrl + Z'],
              ['重做', '⇧ + ⌘ / Ctrl + Z'],
              ['适合屏幕', '⌘ / Ctrl + 0'],
              ['删除选中节点 / 连线', 'Delete'],
              ['多选节点', 'Shift + 点击'],
            ]"
            :key="item[0]"
          >
            <dt>{{ item[0] }}</dt>
            <dd>{{ item[1] }}</dd>
          </div>
        </dl>
      </div>
      <div v-else-if="dialog === 'account'" class="canvas-capability">
        <UiIcon name="user" :size="34" weight="regular" />
        <h3>{{ app.workspace?.name || '映序工作室' }}</h3>
        <p>画布草稿保存在当前用户的本机浏览器中，素材由工作空间统一管理。</p>
        <RouterLink class="quiet-button" to="/">返回工作室</RouterLink>
      </div>
      <div v-else class="canvas-capability">
        <span class="canvas-capability-icon"
          ><UiIcon name="sparkle" :size="30" weight="regular"
        /></span>
        <h3>先把灵感连接起来</h3>
        <p>可以编辑提示词、设置画面比例与时长，并用连线组织参考素材。</p>
        <div class="canvas-model-state"><span class="status-dot" />尚未连接生成模型</div>
        <p class="muted small">
          图片、视频、音频和剧本生成将在模型服务接入后开放。当前不会提交生成任务。
        </p>
        <button class="quiet-button" @click="dialog = ''">继续编辑画布</button>
      </div>
    </UiDialog>
  </main>
</template>
