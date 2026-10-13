import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { useAppStore } from '../stores/app';
import { blankCanvas, canvasSchema, parseCanvas, type CanvasDocument } from '../domain/canvas';

export function useCanvasDocument() {
  const app = useAppStore();
  const document = ref<CanvasDocument>(blankCanvas());
  const ready = ref(false),
    error = ref(''),
    saving = ref(false);
  const undoStack = ref<string[]>([]),
    redoStack = ref<string[]>([]);
  const key = computed(
    () => 'yingxu:canvas:v2:' + (app.user?.id || 'guest') + ':' + (app.workspaceId || 'local'),
  );
  let activeKey = '',
    lastRaw: string | null = null,
    timer: ReturnType<typeof setTimeout> | undefined;
  const serialize = () => JSON.stringify(canvasSchema.parse(document.value));
  function flush() {
    clearTimeout(timer);
    if (!ready.value || !activeKey || error.value) return;
    try {
      if (localStorage.getItem(activeKey) !== lastRaw)
        throw new Error('另一个标签页更新了画布。请先导出当前副本，再刷新读取最新内容。');
      const raw = serialize();
      if (raw.length > 4_000_000) throw new Error('画布内容已达到本机保存上限，请导出备份。');
      localStorage.setItem(activeKey, raw);
      lastRaw = raw;
    } catch (e) {
      error.value = e instanceof Error ? e.message : '本机保存失败，请导出备份。';
    }
    saving.value = false;
  }
  watch(
    [key, () => app.loading],
    ([nextKey, loading]) => {
      if (loading) return;
      flush();
      ready.value = false;
      activeKey = nextKey;
      error.value = '';
      undoStack.value = [];
      redoStack.value = [];
      try {
        lastRaw = localStorage.getItem(nextKey);
        const raw = lastRaw ?? localStorage.getItem(nextKey.replace(':v2:', ':v1:'));
        document.value = raw ? parseCanvas(raw) : blankCanvas();
        if (!document.value.boards.some((b) => b.id === document.value.activeId))
          document.value.activeId = document.value.boards[0]!.id;
      } catch {
        document.value = blankCanvas();
        error.value = '画布草稿无法读取，原始数据已保留。请导出原始备份后处理。';
      }
      ready.value = true;
    },
    { immediate: true, flush: 'sync' },
  );
  watch(
    document,
    () => {
      if (!ready.value || error.value) return;
      saving.value = true;
      clearTimeout(timer);
      timer = setTimeout(flush, 300);
    },
    { deep: true },
  );
  function checkpoint() {
    const value = serialize();
    if (undoStack.value.at(-1) !== value) undoStack.value = [...undoStack.value.slice(-39), value];
    redoStack.value = [];
  }
  function undo() {
    const value = undoStack.value.pop();
    if (value) {
      redoStack.value.push(serialize());
      document.value = parseCanvas(value);
    }
  }
  function redo() {
    const value = redoStack.value.pop();
    if (value) {
      undoStack.value.push(serialize());
      document.value = parseCanvas(value);
    }
  }
  function storage(event: StorageEvent) {
    if (event.key === activeKey && event.newValue !== lastRaw) {
      error.value = '另一个标签页更新了画布。请先导出当前副本，再刷新读取最新内容。';
      clearTimeout(timer);
      saving.value = false;
    }
  }
  function rawBackup() {
    return (
      localStorage.getItem(activeKey) ??
      localStorage.getItem(activeKey.replace(':v2:', ':v1:')) ??
      serialize()
    );
  }
  onMounted(() => {
    window.addEventListener('pagehide', flush);
    window.addEventListener('storage', storage);
  });
  onBeforeUnmount(() => {
    flush();
    window.removeEventListener('pagehide', flush);
    window.removeEventListener('storage', storage);
  });
  return {
    document,
    ready,
    error,
    saving,
    checkpoint,
    undo,
    redo,
    undoStack,
    redoStack,
    flush,
    rawBackup,
  };
}
