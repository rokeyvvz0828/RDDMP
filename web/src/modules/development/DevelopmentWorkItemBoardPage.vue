<script setup lang="ts">
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowLeft } from '@element-plus/icons-vue'
import UiPageHeader from '../../components/ui/UiPageHeader.vue'
import UiEmptyState from '../../components/ui/UiEmptyState.vue'
import { useProjectContextStore } from '../../stores/project-context'
import WorkItemsView from './components/WorkItemsView.vue'
import './development.css'
const project = useProjectContextStore(), router = useRouter()
onMounted(() => { void project.initialize() })
</script>

<template>
  <div class="development-page"><UiPageHeader title="工作项看板"><template #actions><el-button :icon="ArrowLeft" @click="router.push('/development/tasks')">开发任务</el-button></template></UiPageHeader><WorkItemsView v-if="project.currentRef" :project-ref="project.currentRef" /><UiEmptyState v-else title="请选择项目" :description="project.error || '暂无可访问项目'" /></div>
</template>
