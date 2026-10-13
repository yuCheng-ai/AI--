<script setup lang="ts">
import { nextTick, ref } from 'vue';
import type { Message } from '../../domain/types';
import UiIcon from '../UiIcon.vue';
import { useAppStore } from '../../stores/app';
const props = defineProps<{
  messages: Message[];
  episodeNumber: number;
  stage: string;
  demo: boolean;
  editable: boolean;
}>();
const emit = defineEmits<{
  add: [message: Message];
  clear: [];
  insert: [text: string];
  close: [];
}>();
const app = useAppStore(),
  prompt = ref(''),
  conversation = ref<HTMLElement>(),
  attachment = ref<HTMLInputElement>();
async function send() {
  const text = prompt.value.trim();
  if (!text || !props.editable) return;
  emit('add', { id: crypto.randomUUID(), role: 'user', text });
  prompt.value = '';
  emit('add', {
    id: crypto.randomUUID(),
    role: 'assistant',
    text: '想法已保存在本集草稿中。工作室尚未连接生成模型，暂时无法生成内容。你可以继续编辑剧本，模型接入后再提交这条想法。',
  });
  await nextTick();
  conversation.value?.scrollTo({ top: conversation.value.scrollHeight, behavior: 'smooth' });
}
async function attach(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0];
  if (!file) return;
  try {
    if (file.size > 100000) throw new Error('参考文本请小于 100 KB。');
    const content = await file.text();
    prompt.value = `${prompt.value}\n参考：${file.name}\n${content.slice(0, 4000)}`.slice(0, 6000);
  } catch (e) {
    app.report(e);
  } finally {
    (event.target as HTMLInputElement).value = '';
  }
}
</script>
<template>
  <aside class="agent-panel" aria-label="智能体">
    <header class="agent-heading">
      <h2>智能体</h2>
      <div>
        <button
          class="icon-button"
          title="新对话"
          aria-label="新对话"
          :disabled="!editable || !messages.length"
          @click="emit('clear')"
        >
          <UiIcon name="plus" :size="24" /></button
        ><button
          class="icon-button agent-mobile-close"
          aria-label="收起智能体"
          @click="emit('close')"
        >
          <UiIcon name="close" />
        </button>
      </div>
    </header>
    <div class="agent-context">
      <span>第 {{ episodeNumber }} 集 · {{ stage }}</span
      ><span class="model-state">{{
        demo && messages.some((m) => m.sample) ? '示例对话' : '模型未连接'
      }}</span>
    </div>
    <div ref="conversation" class="agent-conversation">
      <div v-if="!messages.length" class="agent-empty">
        <UiIcon name="sparkle" :size="30" />
        <h3>一起，把故事打磨得更好。</h3>
        <p>写下灵感、修改意见或场景要求。当前模型尚未连接，想法会保存在本集草稿中。</p>
        <div class="prompt-ideas">
          <button :disabled="!editable" @click="prompt = '帮我梳理这一集的冲突与转折。'">
            梳理剧情冲突<UiIcon name="outward" :size="16" /></button
          ><button :disabled="!editable" @click="prompt = '开场增加一点悬念，保留当前场景。'">
            打磨故事开场<UiIcon name="outward" :size="16" />
          </button>
        </div>
      </div>
      <article
        v-for="message in messages"
        :key="message.id"
        class="agent-message"
        :class="message.role"
      >
        <div v-if="message.role === 'assistant'" class="agent-mark">
          <UiIcon name="sparkle" weight="fill" :size="20" />
        </div>
        <div class="message-body">
          <p>{{ message.text }}</p>
          <blockquote v-if="message.sample && message.role === 'assistant'">
            <p>“别相信那个替你开门的人。”</p>
            <button
              class="text-button coral-text"
              :disabled="!editable"
              @click="emit('insert', '别相信那个替你开门的人。')"
            >
              插入剧本<UiIcon name="outward" :size="17" />
            </button>
          </blockquote>
        </div>
      </article>
    </div>
    <form class="agent-composer input-surface" @submit.prevent="send">
      <textarea
        v-model="prompt"
        aria-label="给智能体的想法"
        placeholder="继续打磨这个故事…"
        :disabled="!editable"
        maxlength="6000"
        @keydown.enter.exact.prevent="!$event.isComposing && send()"
      />
      <div>
        <button
          type="button"
          class="icon-button"
          title="附加参考文本"
          aria-label="附加参考文本"
          :disabled="!editable"
          @click="attachment?.click()"
        >
          <UiIcon name="attachment" :size="23" /></button
        ><span class="composer-hint">{{
          prompt.trim() ? '保存想法 · Shift + Enter 换行' : '想法仅保存于本机'
        }}</span
        ><button class="send-button" aria-label="保存想法" :disabled="!prompt.trim() || !editable">
          <UiIcon name="up" :size="23" />
        </button>
      </div>
      <input ref="attachment" hidden type="file" accept=".txt,.md" @change="attach" />
    </form>
  </aside>
</template>
