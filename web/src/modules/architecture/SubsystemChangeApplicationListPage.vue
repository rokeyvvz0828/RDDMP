<script setup lang="ts">
import { computed } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
import UiPageHeader from '../../components/ui/UiPageHeader.vue'
import { useAuthStore } from '../../stores/auth'
import SubsystemChangeApplicationList from './components/SubsystemChangeApplicationList.vue'
import './architecture.css'

const auth = useAuthStore()
const router = useRouter()
const canApply = computed(() => auth.hasPermission('architecture:apply') || auth.hasPermission('architecture:manage'))

function create() {
  void router.push({ name: 'architecture-subsystem-change-application-new', query: { targetKind: 'PHYSICAL' } })
}
</script>

<template>
  <main class="architecture-page architecture-change-page">
    <UiPageHeader title="架构子系统变更工单" description="物理子系统的新增、变更、下线、重新启用、作废和替换都在审批流程中完成。">
      <template #actions>
        <div v-if="canApply" class="architecture-page__actions">
          <el-button type="primary" @click="create"><el-icon><Plus /></el-icon>申请物理子系统</el-button>
        </div>
      </template>
    </UiPageHeader>
    <SubsystemChangeApplicationList />
  </main>
</template>
