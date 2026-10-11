<script setup lang="ts">
import { ref } from 'vue';
import type { Clip, Media, Shot, ShotInput } from '../../domain/types';
import UiIcon from '../UiIcon.vue';
import UiDialog from '../UiDialog.vue';
import MediaPicker from '../MediaPicker.vue';
import { api, ApiError } from '../../api/client';
const props = defineProps<{
  shots: Shot[];
  clips: Clip[];
  editable: boolean;
  workspaceId: string;
  save: (input: ShotInput, existing?: Shot) => Promise<void>;
}>();
const emit = defineEmits<{ updateClips: [clips: Clip[]]; timeline: [] }>();
const open = ref(false),
  editing = ref<Shot>(),
  title = ref(''),
  description = ref(''),
  dialogue = ref(''),
  duration = ref(5),
  busy = ref(false),
  error = ref(''),
  picker = ref<Shot | null>(null);
function edit(shot?: Shot) {
  editing.value = shot;
  title.value = shot?.title || '';
  description.value = shot?.description || '';
  dialogue.value = shot?.dialogue || '';
  duration.value = shot?.durationSeconds || 5;
  error.value = '';
  open.value = true;
}
async function submit() {
  busy.value = true;
  error.value = '';
  try {
    await props.save(
      {
        title: title.value.trim(),
        description: description.value,
        dialogue: dialogue.value,
        durationSeconds: Number(duration.value),
      },
      editing.value,
    );
    open.value = false;
  } catch (e) {
    error.value =
      e instanceof ApiError && e.status === 409
        ? '镜头已被其他人修改，当前内容未覆盖。请保留你的修改，关闭对话框并刷新后重新编辑。'
        : (e as Error).message;
  } finally {
    busy.value = false;
  }
}
function reference(shot: Shot) {
  return props.clips.find((clip) => clip.shotId === shot.id);
}
function select(media: Media) {
  const shot = picker.value;
  if (!shot) return;
  const clip = reference(shot);
  const image = media.contentType.startsWith('image/')
    ? api.mediaUrl(props.workspaceId, media.id)
    : undefined;
  emit(
    'updateClips',
    clip
      ? props.clips.map((c) => (c.id === clip.id ? { ...c, mediaId: media.id, image } : c))
      : [
          ...props.clips,
          {
            id: crypto.randomUUID(),
            shotId: shot.id,
            duration: shot.durationSeconds,
            mediaId: media.id,
            image,
          },
        ],
  );
  picker.value = null;
}
</script>
<template>
  <section class="stage-content">
    <div class="stage-heading">
      <div>
        <h2>分镜板</h2>
        <p>把每一幕，变成清晰的画面。</p>
      </div>
      <div class="row-actions">
        <button class="text-button" :disabled="!shots.length" @click="emit('timeline')">
          查看时间轴<UiIcon name="right" :size="17" /></button
        ><button class="quiet-button" :disabled="!editable" @click="edit()">
          <UiIcon name="plus" />添加镜头
        </button>
      </div>
    </div>
    <div v-if="!shots.length" class="empty-state tall">
      <UiIcon name="film" :size="36" />
      <h3>从第一个镜头开始</h3>
      <p>写下画面、台词和时长，再为镜头添加参考素材。</p>
      <button class="primary" :disabled="!editable" @click="edit()">添加镜头</button>
    </div>
    <div v-else class="shot-grid">
      <article v-for="(shot, index) in shots" :key="shot.id" class="shot-card">
        <button
          class="shot-image"
          :disabled="!editable"
          :aria-label="`选择镜头 ${index + 1} 参考素材`"
          @click="picker = shot"
        >
          <img v-if="reference(shot)?.image" :src="reference(shot)!.image" alt="镜头参考画面" />
          <div v-else-if="reference(shot)?.mediaId" class="attached-media">
            <UiIcon name="image" :size="30" /><span>已关联素材</span>
          </div>
          <div v-else class="shot-empty">
            <UiIcon name="plus" :size="26" /><span>添加参考素材</span>
          </div>
          <span class="shot-number">{{ String(index + 1).padStart(2, '0') }}</span
          ><span class="shot-duration">{{ shot.durationSeconds }}s</span>
        </button>
        <div class="shot-info">
          <h3>{{ shot.title }}</h3>
          <p>{{ shot.description }}</p>
          <div class="shot-bottom">
            <span v-if="workspaceId !== 'demo'">修订 {{ shot.revision }}</span
            ><span v-else>示例分镜</span
            ><button class="text-button" :disabled="!editable" @click="edit(shot)">
              编辑<UiIcon name="edit" :size="16" />
            </button>
          </div>
        </div>
      </article>
    </div>
  </section>
  <UiDialog :open="open" :title="editing ? '编辑镜头' : '添加镜头'" @close="!busy && (open = false)"
    ><form class="form-stack" @submit.prevent="submit">
      <label>镜头名称<input v-model="title" maxlength="120" required /></label
      ><label
        >画面描述<textarea
          v-model="description"
          rows="4"
          maxlength="12000"
          placeholder="景别、动作、机位、光线…"
        /></label
      ><label>台词<textarea v-model="dialogue" rows="2" maxlength="8000" /></label
      ><label
        >时长（秒）<input
          v-model.number="duration"
          type="number"
          min="1"
          max="60"
          step="1"
          required
      /></label>
      <p v-if="error" role="alert" class="error-text">{{ error }}</p>
      <button class="primary" :disabled="busy">{{ busy ? '正在保存' : '保存镜头' }}</button>
    </form></UiDialog
  ><MediaPicker
    :open="!!picker"
    :workspace-id="workspaceId"
    @close="picker = null"
    @select="select"
  />
</template>
