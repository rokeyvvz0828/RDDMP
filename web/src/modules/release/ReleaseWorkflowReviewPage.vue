<script setup lang="ts">
import { onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import { getWorkflowTaskContext } from '../../api/workflow'
import './release-prototype.css'

const route = useRoute()
const router = useRouter()

function safeBusinessPath(path: string) {
  return path.startsWith('/release/applications/') && !path.startsWith('//') && !/[\\\r\n]/.test(path)
}

onMounted(async () => {
  const rawTaskId = String(route.params.taskId || '')
  if (/^\d+$/.test(rawTaskId)) {
    try {
      const context = (await getWorkflowTaskContext(Number(rawTaskId))).data.data
      if (safeBusinessPath(context.action_path)) {
        const resolved = router.resolve(context.action_path)
        await router.replace({ path: resolved.path, query: { ...resolved.query, taskId: rawTaskId }, hash: resolved.hash })
        return
      }
    } catch {
      // Invalid, stale and unauthorized links all fail closed.
    }
  }
  ElMessage.warning('审核链接已失效或当前账号无权处理，请从任务中心重新进入。')
  await router.replace('/dashboard')
})
</script>

<template>
  <main class="release-review-page"><div class="release-review-redirect"><el-skeleton :rows="4" animated /></div></main>
</template>
