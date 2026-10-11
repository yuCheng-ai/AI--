<script setup lang="ts">
import { ref } from 'vue';
import type { Character, Media } from '../../domain/types';
import UiIcon from '../UiIcon.vue';
import UiDialog from '../UiDialog.vue';
import MediaPicker from '../MediaPicker.vue';
import { api } from '../../api/client';
const props = defineProps<{ characters: Character[]; editable: boolean; workspaceId: string }>();
const emit = defineEmits<{ update: [characters: Character[]] }>();
const open = ref(false),
  editing = ref<Character | null>(null),
  name = ref(''),
  role = ref('主角'),
  description = ref(''),
  picker = ref<Character | null>(null);
function edit(character?: Character) {
  editing.value = character || null;
  name.value = character?.name || '';
  role.value = character?.role || '主角';
  description.value = character?.description || '';
  open.value = true;
}
function save() {
  if (!name.value.trim()) return;
  const item = {
    ...editing.value,
    id: editing.value?.id || crypto.randomUUID(),
    name: name.value.trim(),
    role: role.value,
    description: description.value.trim(),
  };
  emit(
    'update',
    editing.value
      ? props.characters.map((c) => (c.id === item.id ? item : c))
      : [...props.characters, item],
  );
  open.value = false;
}
function select(media: Media) {
  if (picker.value)
    emit(
      'update',
      props.characters.map((c) => (c.id === picker.value!.id ? { ...c, mediaId: media.id } : c)),
    );
  picker.value = null;
}
</script>
<template>
  <section class="stage-content">
    <div class="stage-heading">
      <div>
        <h2>角色</h2>
        <p>确定人物，保持每一幕的角色一致。</p>
      </div>
      <button class="quiet-button" :disabled="!editable" @click="edit()">
        <UiIcon name="plus" />添加角色
      </button>
    </div>
    <div v-if="!characters.length" class="empty-state tall">
      <UiIcon name="users" :size="36" />
      <h3>故事里，会遇见谁？</h3>
      <p>添加人物设定与参考图，为分镜做好准备。</p>
      <button class="primary" :disabled="!editable" @click="edit()">创建第一个角色</button>
    </div>
    <div v-else class="character-grid">
      <article v-for="character in characters" :key="character.id" class="character-card">
        <div class="character-top">
          <span class="role-tag">{{ character.role }}</span
          ><button
            class="icon-button"
            :disabled="!editable"
            :aria-label="`编辑角色 ${character.name}`"
            @click="edit(character)"
          >
            <UiIcon name="edit" :size="18" />
          </button>
        </div>
        <button class="character-reference" :disabled="!editable" @click="picker = character">
          <img
            v-if="character.mediaId"
            :src="api.mediaUrl(workspaceId, character.mediaId)"
            :alt="character.name"
          /><template v-else
            ><UiIcon name="user" :size="40" /><span
              ><UiIcon name="plus" :size="14" />选择参考图</span
            ></template
          >
        </button>
        <h3>{{ character.name }}</h3>
        <p>{{ character.description || '还没有角色描述' }}</p>
      </article>
    </div>
  </section>
  <UiDialog :open="open" :title="editing ? '编辑角色' : '添加角色'" @close="open = false"
    ><form class="form-stack" @submit.prevent="save">
      <label>角色名称<input v-model="name" required maxlength="80" /></label
      ><label
        >角色定位<select v-model="role">
          <option>主角</option>
          <option>配角</option>
          <option>其他</option>
        </select></label
      ><label
        >人物设定<textarea
          v-model="description"
          rows="6"
          maxlength="4000"
          placeholder="外貌、性格、关系，以及贯穿故事的动机…"
        /></label
      ><button class="primary">保存角色</button>
    </form></UiDialog
  ><MediaPicker
    :open="!!picker"
    :workspace-id="workspaceId"
    images-only
    @close="picker = null"
    @select="select"
  />
</template>
