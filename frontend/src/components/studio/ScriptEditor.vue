<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue';
import { EditorContent, useEditor, type JSONContent } from '@tiptap/vue-3';
import StarterKit from '@tiptap/starter-kit';
import Placeholder from '@tiptap/extension-placeholder';
import UiIcon from '../UiIcon.vue';
import { scriptFromText } from '../../domain/examples';
import { useAppStore } from '../../stores/app';
const props = defineProps<{ content: JSONContent; editable: boolean; confirmed: boolean }>();
const emit = defineEmits<{ update: [content: JSONContent]; confirm: []; count: [count: number] }>();
const app = useAppStore(),
  input = ref<HTMLInputElement>();
const editor = useEditor({
  content: props.content,
  editable: props.editable,
  extensions: [
    StarterKit.configure({ heading: { levels: [2, 3] }, link: false }),
    Placeholder.configure({ placeholder: '写下故事的第一幕，或导入已有剧本…' }),
  ],
  editorProps: {
    attributes: {
      'aria-label': '剧本编辑器',
      role: 'textbox',
      'aria-multiline': 'true',
      spellcheck: 'false',
    },
  },
  onUpdate: ({ editor }) => {
    emit('update', editor.getJSON());
    emit('count', editor.getText().replace(/\s/g, '').length);
  },
  onCreate: ({ editor }) => emit('count', editor.getText().replace(/\s/g, '').length),
});
const isEmpty = computed(() => !editor.value || editor.value.isEmpty);
watch(
  () => props.editable,
  (value) => editor.value?.setEditable(value),
);
async function importFile(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0];
  if (!file) return;
  try {
    if (file.size > 1_000_000) throw new Error('剧本文本最大支持 1 MB。');
    const value = await file.text();
    if (!value.trim()) throw new Error('文件中没有可导入的文字。');
    // Append imported text so an existing manuscript is never silently replaced.
    const document = scriptFromText(value);
    if (editor.value?.isEmpty) editor.value.commands.setContent(document);
    else editor.value?.chain().focus('end').insertContent(document.content!).run();
    app.notify('剧本已导入，原有内容已保留');
  } catch (e) {
    app.report(e);
  } finally {
    (event.target as HTMLInputElement).value = '';
  }
}
function insert(text: string) {
  if (props.editable)
    editor.value
      ?.chain()
      .focus('end')
      .insertContent({ type: 'paragraph', content: [{ type: 'text', text }] })
      .run();
}
defineExpose({ insert });
onBeforeUnmount(() => editor.value?.destroy());
</script>
<template>
  <div class="script-stage">
    <div v-if="editor" class="editor-toolbar">
      <div class="format-actions">
        <select
          aria-label="段落样式"
          title="场景标题会自动显示 01、02… 编号；正文不编号"
          :disabled="!editable"
          :value="editor.isActive('heading', { level: 2 }) ? 'scene' : 'text'"
          @change="
            ($event.target as HTMLSelectElement).value === 'scene'
              ? editor.chain().focus().setHeading({ level: 2 }).run()
              : editor.chain().focus().setParagraph().run()
          "
        >
          <option value="text">正文</option>
          <option value="scene">场景标题</option></select
        ><span class="divider" /><button
          class="icon-button"
          aria-label="加粗"
          :disabled="!editable"
          :aria-pressed="editor.isActive('bold')"
          @click="editor.chain().focus().toggleBold().run()"
        >
          <UiIcon name="bold" /></button
        ><button
          class="icon-button"
          aria-label="斜体"
          :disabled="!editable"
          :aria-pressed="editor.isActive('italic')"
          @click="editor.chain().focus().toggleItalic().run()"
        >
          <UiIcon name="italic" /></button
        ><span class="divider" /><button
          class="icon-button"
          aria-label="对白格式"
          :disabled="!editable"
          :aria-pressed="editor.isActive('blockquote')"
          @click="editor.chain().focus().toggleBlockquote().run()"
        >
          <UiIcon name="align" /></button
        ><button
          class="icon-button"
          aria-label="项目列表"
          :disabled="!editable"
          :aria-pressed="editor.isActive('bulletList')"
          @click="editor.chain().focus().toggleBulletList().run()"
        >
          <UiIcon name="list" /></button
        ><span class="divider" /><button
          class="icon-button"
          aria-label="撤销"
          :disabled="!editable || !editor.can().undo()"
          @click="editor.chain().focus().undo().run()"
        >
          <UiIcon name="undo" /></button
        ><button
          class="icon-button"
          aria-label="重做"
          :disabled="!editable || !editor.can().redo()"
          @click="editor.chain().focus().redo().run()"
        >
          <UiIcon name="redo" />
        </button>
      </div>
      <button class="text-button import-button" :disabled="!editable" @click="input?.click()">
        <UiIcon name="import" />导入剧本</button
      ><input
        ref="input"
        type="file"
        accept=".txt,.md,text/plain,text/markdown"
        hidden
        @change="importFile"
      />
    </div>
    <div class="script-scroll editor-surface"><EditorContent :editor="editor" /></div>
    <footer class="editor-footer">
      <slot name="status" /><button
        class="primary"
        :disabled="!editable || isEmpty"
        @click="emit('confirm')"
      >
        {{ confirmed ? '继续到角色' : '确认剧本' }}<UiIcon name="right" />
      </button>
    </footer>
  </div>
</template>
