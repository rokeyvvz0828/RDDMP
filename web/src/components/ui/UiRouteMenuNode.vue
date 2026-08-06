<script setup lang="ts">
import { computed } from 'vue'
import UiMenuIcon from './UiMenuIcon.vue'
import type { RouteNode } from '../../types/auth'
defineOptions({ name: 'UiRouteMenuNode' })
const props = defineProps<{ node: RouteNode }>()
const path = computed(() => props.node.routePath?.startsWith('/') ? props.node.routePath : `/${props.node.routePath || ''}`)
</script>
<template>
  <el-sub-menu v-if="node.children?.length" :index="path"><template #title><UiMenuIcon :name="node.icon" /><span>{{ node.menuName }}</span></template><UiRouteMenuNode v-for="child in node.children" :key="child.id" :node="child" /></el-sub-menu>
  <el-menu-item v-else :index="path"><UiMenuIcon :name="node.icon" /><template #title>{{ node.menuName }}</template></el-menu-item>
</template>
