<script setup lang="ts">
import { computed } from 'vue'
import { CopyDocument, WarningFilled } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import UiStatusTag from '../../../components/ui/UiStatusTag.vue'
import type { ReleaseApplicationStatusCode, ReleaseHistoricalApplicationDto } from '../../../api/release'

const props = defineProps<{
  modelValue: boolean
  conflicts: ReleaseHistoricalApplicationDto[]
  resolving?: boolean
  currentUserId?: number
  canCancelReview?: boolean
  cancellingCode?: string
  preflight?: boolean
  refreshError?: string
}>()
const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  choose: [action: 'CANCEL_OLD' | 'EDIT_OLD' | 'CREATE_NEW', conflict?: ReleaseHistoricalApplicationDto]
  cancel: [conflict: ReleaseHistoricalApplicationDto]
}>()

const hasInReview = computed(() => props.conflicts.some(item => item.application.status === 'IN_REVIEW'))
const continueDisabledReason = computed(() => {
  if (props.refreshError) return '审批状态刷新失败，请等待自动重试或取消后重新操作'
  if (hasInReview.value) return '前一申请仍在审批中，请先取消'
  return ''
})

const statusLabels: Record<ReleaseApplicationStatusCode, string> = {
  DRAFT: '草稿', IN_REVIEW: '审批中', RETURNED: '已退回', WITHDRAWN: '已撤回',
  CANCELLED: '已取消', RELEASED: '制品准出'
}
const versionLabels = { REGULAR: '常规版本', URGENT: '紧急版本', EMERGENCY: '应急版本' } as const
function statusTone(value: ReleaseApplicationStatusCode) {
  return value === 'RELEASED' ? 'success' : value === 'IN_REVIEW' ? 'primary'
    : value === 'RETURNED' || value === 'WITHDRAWN' ? 'warning' : 'info'
}
function minute(value?: string) { return value ? value.replace('T', ' ').slice(0, 16) : '-' }
function canCancelReview(item: ReleaseHistoricalApplicationDto) {
  return Boolean(props.preflight)
    && item.application.status === 'IN_REVIEW'
    && item.application.requesterId === props.currentUserId
    && Boolean(props.canCancelReview)
}
async function copyCode(code: string) {
  await navigator.clipboard?.writeText(code)
  ElMessage.success(`已复制 ${code}`)
}
</script>

<template>
  <el-dialog :model-value="modelValue" class="release-conflict-dialog" title="发现同窗口重复申请" width="min(920px, 95vw)" top="5vh" :close-on-click-modal="false" @update:model-value="emit('update:modelValue', $event)">
    <el-alert
      :title="hasInReview ? '前一申请仍在审批中，取消完成前不能保存草稿或提交审批。' : '请核对历史申请后继续。以下展示原申请单全部业务信息，不包含附件。'"
      :type="hasInReview ? 'error' : 'warning'"
      :closable="false"
      show-icon
    />
    <el-alert v-if="refreshError" class="release-conflict-refresh-error" :title="refreshError" type="error" :closable="false" show-icon />
    <div class="release-conflict-list">
      <article v-for="item in conflicts" :key="item.application.applicationCode">
        <el-icon class="release-conflict-list__icon"><WarningFilled /></el-icon>
        <div>
          <header>
            <strong>{{ item.application.applicationCode }}</strong>
            <UiStatusTag :value="statusLabels[item.application.status]" :tone="statusTone(item.application.status)" />
          </header>
          <dl>
            <div><dt>申请单号</dt><dd><button class="release-conflict-code-button" type="button" @click="copyCode(item.application.applicationCode)">{{ item.application.applicationCode }} <el-icon><CopyDocument /></el-icon></button></dd></div>
            <div><dt>投产窗口</dt><dd>{{ item.application.windowCode ? `${item.application.windowCode} · ${item.application.windowName}` : '应急版本待归入窗口' }}</dd></div>
            <div><dt>物理子系统</dt><dd>{{ item.application.subsystemCode }} · {{ item.application.subsystemName }}</dd></div>
            <div><dt>版本类型 / 特征</dt><dd>{{ versionLabels[item.application.versionType] }} / {{ item.application.characteristic === 'ADDITIONAL' ? '追加申请' : '普通申请' }}</dd></div>
            <div><dt>申请人 / 部门</dt><dd>{{ item.application.requesterName }} / {{ item.application.requesterDepartment || '-' }}</dd></div>
            <div><dt>申请时间</dt><dd>{{ minute(item.application.createdAt) }}</dd></div>
            <div><dt>需求编号</dt><dd>{{ item.application.requirementCodes.join('、') || '无' }}</dd></div>
            <div><dt>流程编码</dt><dd>{{ item.application.workflowCode }}</dd></div>
            <div class="is-wide"><dt>全部交付单元</dt><dd>{{ item.application.deliveries.map(unit => `${unit.deliveryUnitCode} · ${unit.deliveryUnitName}（${unit.artifactVersion}）`).join('；') }}</dd></div>
            <div v-if="item.application.fileMedia.length" class="is-wide"><dt>全部文件介质</dt><dd class="release-path-list"><code v-for="file in item.application.fileMedia" :key="file.id">{{ file.filePath }}</code></dd></div>
            <div class="is-wide"><dt>本次内容变化</dt><dd>{{ item.versionChanges.map(change => change.deliveryUnitCode === 'FILE' ? `文件路径：${change.currentVersion}` : `${change.deliveryUnitName}：${change.previousVersion} -> ${change.currentVersion}`).join('；') || '内容未变化' }}</dd></div>
            <div class="is-wide"><dt>申请说明</dt><dd>{{ item.application.description || '无' }}</dd></div>
            <div v-if="item.application.urgentReason" class="is-wide"><dt>紧急原因</dt><dd>{{ item.application.urgentReason }}</dd></div>
            <div v-if="item.application.emergencyDescription" class="is-wide"><dt>应急说明</dt><dd>{{ item.application.emergencyDescription }}</dd></div>
          </dl>
          <div v-if="canCancelReview(item) || (!preflight && (item.allowedActions.includes('CANCEL_OLD') || item.allowedActions.includes('EDIT_OLD')))" class="release-conflict-actions">
            <el-button
              v-if="canCancelReview(item)"
              type="danger"
              plain
              :loading="cancellingCode === item.application.applicationCode"
              :disabled="resolving || Boolean(cancellingCode)"
              @click="emit('cancel', item)"
            >{{ cancellingCode === item.application.applicationCode ? '取消处理中' : '取消申请' }}</el-button>
            <el-button v-if="!preflight && item.allowedActions.includes('CANCEL_OLD')" type="danger" plain :disabled="resolving" @click="emit('choose', 'CANCEL_OLD', item)">取消旧申请</el-button>
            <el-button v-if="!preflight && item.allowedActions.includes('EDIT_OLD')" type="primary" plain :disabled="resolving" @click="emit('choose', 'EDIT_OLD', item)">修改旧申请</el-button>
          </div>
        </div>
      </article>
    </div>
    <template #footer>
      <div class="release-conflict-footer">
        <el-button :disabled="resolving" @click="emit('update:modelValue', false)">取消</el-button>
        <el-tooltip :disabled="!continueDisabledReason" :content="continueDisabledReason" placement="top">
          <span class="release-conflict-continue-wrap">
            <el-button type="primary" :loading="resolving" :disabled="Boolean(continueDisabledReason)" @click="emit('choose', 'CREATE_NEW')">继续申请</el-button>
          </span>
        </el-tooltip>
      </div>
    </template>
  </el-dialog>
</template>
