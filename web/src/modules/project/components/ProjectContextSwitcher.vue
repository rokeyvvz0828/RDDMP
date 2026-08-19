<script setup lang="ts">
import { computed, watch } from 'vue'
import { FolderOpened, RefreshRight, WarningFilled } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '../../../stores/auth'
import { useProjectContextStore } from '../../../stores/project-context'

const auth = useAuthStore()
const projectContext = useProjectContextStore()
const selectedId = computed(() => projectContext.currentProjectId)
const placeholder = computed(() => {
  if (projectContext.loading) return '加载项目...'
  if (projectContext.error) return '项目加载失败'
  return projectContext.availableProjects.length ? '选择项目' : '暂无可用项目'
})

watch(() => auth.user ? `${auth.user.tenantId}:${auth.user.id}` : '', (identity, previousIdentity) => {
  if (identity === previousIdentity) return
  projectContext.bindCurrentUser()
}, { immediate: true })

watch(() => projectContext.selectionExpired, expired => {
  if (expired) ElMessage.warning('原项目已不可用，已切换到当前可访问项目')
})

function selectProject(value: string | number) {
  projectContext.select(Number(value))
}
</script>

<template>
  <div class="project-context-switcher" aria-label="当前项目">
    <el-icon class="project-context-switcher__icon"><FolderOpened /></el-icon>
    <el-tooltip :content="projectContext.currentProject?.projectName || placeholder" placement="bottom">
      <el-select
        :model-value="selectedId"
        :placeholder="placeholder"
        :loading="projectContext.loading || projectContext.refreshing"
        :disabled="projectContext.loading || !projectContext.availableProjects.length"
        aria-label="切换当前项目"
        @change="selectProject"
      >
        <el-option
          v-for="project in projectContext.availableProjects"
          :key="project.id"
          :label="`${project.projectCode} · ${project.projectName}`"
          :value="project.id"
        >
          <span class="project-context-switcher__option-name">{{ project.projectName }}</span>
          <small>{{ project.projectCode }}</small>
        </el-option>
      </el-select>
    </el-tooltip>
    <el-tooltip v-if="projectContext.error" content="项目加载失败，点击重试" placement="bottom">
      <el-button
        text
        circle
        type="danger"
        aria-label="重新加载项目"
        :loading="projectContext.refreshing"
        @click="projectContext.refresh().catch(() => undefined)"
      >
        <el-icon><RefreshRight v-if="projectContext.refreshing" /><WarningFilled v-else /></el-icon>
      </el-button>
    </el-tooltip>
  </div>
</template>
