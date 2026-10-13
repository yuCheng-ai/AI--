<script setup lang="ts">
import { computed } from 'vue';
import { BaseEdge, getBezierPath, type BezierEdgeProps } from '@vue-flow/core';

defineOptions({ inheritAttrs: false });
const props = defineProps<BezierEdgeProps & { active: boolean }>();
// Keep the path ordered source → target for both incoming and outgoing edges.
const path = computed(() => getBezierPath(props)[0]);
</script>

<template>
  <g class="canvas-edge" :class="{ 'is-flowing': active }">
    <BaseEdge
      :id="id"
      :path="path"
      :marker-start="markerStart"
      :marker-end="markerEnd"
      :interaction-width="interactionWidth"
    />
    <path v-if="active" class="canvas-edge-flow" :d="path" aria-hidden="true" />
  </g>
</template>
