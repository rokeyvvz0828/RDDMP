<!--
  用途：数据迁移页面的「当前项目」上下文闸门
  说明：当无法从全局项目上下文解析出可访问的当前项目时（尚未选择项目、项目列表加载失败、
        当前项目不可访问），统一展示加载/提示/错误状态并阻断业务查询；不展示上一个项目的数据，
        也不回退到历史项目。页面负责把非 ready 状态传入并处理重试回调。
-->
<script setup lang="ts">
import { computed } from 'vue'
import type { ProjectScopeState as ScopeState } from '../composables/useProjectScope'
import { useProjectScope } from '../composables/useProjectScope'

const props = defineProps<{ state: ScopeState; description?: string }>()
defineEmits<{ (event: 'retry'): void }>()

const scope = useProjectScope()
/** 优先使用页面传入的说明，否则展示上下文解析得到的真实错误文案。 */
const detail = computed(() => props.description || scope.errorText.value)
</script>

<template>
  <section v-if="state === 'loading'" v-loading="true" class="dm-state-panel" aria-live="polite" />
  <section v-else-if="state === 'error'" class="dm-state-panel">
    <el-result icon="error" title="当前项目信息加载失败" :sub-title="detail || '未能获取可访问的项目列表，请重试。'">
      <template #extra><el-button type="primary" @click="$emit('retry')">重新加载</el-button></template>
    </el-result>
  </section>
  <section v-else-if="state === 'unavailable'" class="dm-state-panel">
    <el-result icon="warning" title="当前项目暂不可用" :sub-title="detail || '当前全局项目可能已归档或你已失去访问权限，请在顶部项目切换器中重新选择项目。'">
      <template #extra><el-button @click="$emit('retry')">重新加载项目</el-button></template>
    </el-result>
  </section>
  <section v-else class="dm-state-panel">
    <el-result icon="info" title="尚未选择项目" sub-title="请先在页面右上角、通知中心左侧的项目切换器中选择项目，再查看本模块数据。" />
  </section>
</template>
