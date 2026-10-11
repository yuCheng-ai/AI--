<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue';
import type { Clip, Media, Shot } from '../../domain/types';
import UiIcon from '../UiIcon.vue';
import { api } from '../../api/client';
import { downloadFile, formatDuration } from '../../lib/format';
const props = defineProps<{
  shots: Shot[];
  clips: Clip[];
  editable: boolean;
  workspaceId: string;
}>();
const emit = defineEmits<{ update: [clips: Clip[]] }>();
const time = ref(0),
  playing = ref(false),
  media = ref<Media[]>([]),
  mediaError = ref(false),
  selected = ref('');
const total = computed(() => props.clips.reduce((sum, c) => sum + c.duration, 0));
const active = computed(() => {
  let end = 0;
  return (
    props.clips.find((c) => {
      end += c.duration;
      return time.value < end;
    }) || props.clips.at(-1)
  );
});
const shot = computed(() => props.shots.find((s) => s.id === active.value?.shotId));
const activeMedia = computed(() => media.value.find((m) => m.id === active.value?.mediaId));
const src = computed(() =>
  active.value?.mediaId
    ? api.mediaUrl(props.workspaceId, active.value.mediaId)
    : active.value?.image,
);
const video = ref<HTMLVideoElement>();
let frame = 0,
  lastTime = 0;
function pause() {
  playing.value = false;
  cancelAnimationFrame(frame);
  video.value?.pause();
}
function tick(now: number) {
  if (!playing.value) return;
  time.value += (now - lastTime) / 1000;
  lastTime = now;
  if (time.value >= total.value) {
    time.value = total.value;
    pause();
  } else frame = requestAnimationFrame(tick);
}
function play() {
  if (playing.value) {
    pause();
    return;
  }
  if (!total.value) return;
  if (time.value >= total.value) time.value = 0;
  playing.value = true;
  lastTime = performance.now();
  frame = requestAnimationFrame(tick);
  void video.value?.play().catch(() => {});
}
function select(clip: Clip) {
  pause();
  selected.value = clip.id;
  time.value = props.clips
    .slice(
      0,
      props.clips.findIndex((c) => c.id === clip.id),
    )
    .reduce((sum, c) => sum + c.duration, 0);
}
function addShots() {
  const existing = new Set(props.clips.map((c) => c.shotId));
  emit('update', [
    ...props.clips,
    ...props.shots
      .filter((s) => !existing.has(s.id))
      .map((s) => ({ id: crypto.randomUUID(), shotId: s.id, duration: s.durationSeconds })),
  ]);
}
function reorder(index: number, direction: number) {
  const copy = [...props.clips];
  const target = index + direction;
  if (target < 0 || target >= copy.length) return;
  [copy[index], copy[target]] = [copy[target]!, copy[index]!];
  pause();
  emit('update', copy);
}
function duration(id: string, value: string) {
  const n = Math.max(1, Math.min(60, Math.round(Number(value) || 1)));
  pause();
  emit(
    'update',
    props.clips.map((c) => (c.id === id ? { ...c, duration: n } : c)),
  );
}
watch(
  () => active.value?.id,
  () => {
    mediaError.value = false;
  },
);
watch(total, (value) => {
  if (time.value > value) time.value = value;
});
watch(
  () => [props.workspaceId, active.value?.mediaId] as const,
  async ([wid, id], _, onCleanup) => {
    let stale = false;
    onCleanup(() => {
      stale = true;
    });
    if (wid === 'demo' || !id || media.value.some((item) => item.id === id)) return;
    try {
      const item = await api.mediaItem(wid, id);
      if (!stale) media.value.push(item);
    } catch {
      if (!stale) mediaError.value = true;
    }
  },
  { immediate: true },
);
function exportTimeline() {
  downloadFile(
    '时间轴.json',
    JSON.stringify(
      { version: 1, type: 'storyboard-timeline', clips: props.clips, shots: props.shots },
      null,
      2,
    ),
    'application/json',
  );
}
function syncVideo() {
  if (video.value) {
    const before = props.clips
      .slice(
        0,
        props.clips.findIndex((c) => c.id === active.value?.id),
      )
      .reduce((sum, c) => sum + c.duration, 0);
    video.value.currentTime = Math.max(0, time.value - before);
    if (playing.value) void video.value.play().catch(() => {});
  }
}
onBeforeUnmount(pause);
</script>
<template>
  <section class="timeline-stage">
    <div class="stage-heading">
      <div>
        <h2>时间轴</h2>
        <p>排列镜头，预览故事的节奏。</p>
      </div>
      <button class="quiet-button" :disabled="!clips.length" @click="exportTimeline">
        <UiIcon name="export" :size="18" />导出编排
      </button>
    </div>
    <div class="timeline-preview">
      <video
        v-if="src && activeMedia?.contentType.startsWith('video/') && !mediaError"
        ref="video"
        :src="src"
        playsinline
        @loadedmetadata="syncVideo"
        @error="mediaError = true"
      /><img
        v-else-if="src && !mediaError"
        :src="src"
        alt="当前镜头参考画面"
        @error="mediaError = true"
      />
      <div v-else class="preview-caption">
        <UiIcon name="film" :size="40" />
        <h3>{{ shot?.title || '故事即将开场' }}</h3>
        <p>{{ shot?.description || '将分镜加入时间轴，开始排列镜头。' }}</p>
        <span v-if="mediaError" class="error-text">素材暂时无法读取</span>
      </div>
      <span class="preview-type">分镜预览</span>
    </div>
    <div class="playback">
      <button
        class="icon-button"
        :disabled="!clips.length"
        :aria-label="playing ? '暂停预览' : '播放预览'"
        @click="play"
      >
        <UiIcon :name="playing ? 'pause' : 'play'" weight="fill" /></button
      ><span
        >{{ formatDuration(time) }} <em>/ {{ formatDuration(total) }}</em></span
      ><input
        v-model.number="time"
        aria-label="预览进度"
        type="range"
        min="0"
        :max="Math.max(total, 1)"
        step="0.05"
        :disabled="!clips.length"
        @input="
          pause();
          syncVideo();
        "
      />
    </div>
    <div class="timeline-track-heading">
      <span
        >镜头轨道 <span class="muted small">{{ clips.length }} 个镜头</span></span
      ><button
        class="text-button"
        :disabled="!editable || !shots.some((s) => !clips.some((c) => c.shotId === s.id))"
        @click="addShots"
      >
        <UiIcon name="plus" :size="16" />加入分镜
      </button>
    </div>
    <div class="timeline-track">
      <p v-if="!clips.length" class="track-empty">先在分镜板创建镜头，再加入时间轴。</p>
      <article
        v-for="(clip, index) in clips"
        :key="clip.id"
        class="timeline-clip"
        :class="{ active: active?.id === clip.id, selected: selected === clip.id }"
      >
        <button class="clip-select" @click="select(clip)">
          <span class="small muted">{{ String(index + 1).padStart(2, '0') }}</span
          ><strong>{{ shots.find((s) => s.id === clip.shotId)?.title || '镜头' }}</strong>
        </button>
        <div class="clip-controls">
          <button
            class="icon-button"
            :disabled="!editable || index === 0"
            aria-label="镜头前移"
            @click="reorder(index, -1)"
          >
            <UiIcon name="previous" :size="16" /></button
          ><label
            ><input
              :value="clip.duration"
              :disabled="!editable"
              type="number"
              min="1"
              max="60"
              :aria-label="`镜头 ${index + 1} 时长`"
              @input="duration(clip.id, ($event.target as HTMLInputElement).value)"
            />s</label
          ><button
            class="icon-button"
            :disabled="!editable || index === clips.length - 1"
            aria-label="镜头后移"
            @click="reorder(index, 1)"
          >
            <UiIcon name="next" :size="16" />
          </button>
        </div>
      </article>
    </div>
    <p class="timeline-note">当前为素材与分镜编排预览，成片渲染尚未接入。</p>
  </section>
</template>
