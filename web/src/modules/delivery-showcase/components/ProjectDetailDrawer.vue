<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue'
import { Delete, Document, Download, Edit, Paperclip, Upload, View } from '@element-plus/icons-vue'
import { ElMessage, type UploadFile } from 'element-plus'
import { deleteFilePreview, uploadFilePreview, type FilePreviewResult } from '../../../api/file-preview'
import { apiErrorMessage } from '../../../api/error'
import UiFilePreview from '../../../components/ui/UiFilePreview.vue'
import { useAuthStore } from '../../../stores/auth'
import type { DeliveryProject } from '../types'

interface DemoAttachment {
  name: string
  assetUrl: string
  contentType: string
  owner: string
  date: string
  size: string
  sourceFile?: File
  uploaded?: boolean
}

const props = defineProps<{ modelValue: boolean; project: DeliveryProject | null }>()
const emit = defineEmits<{ 'update:modelValue': [value: boolean]; edit: [project: DeliveryProject] }>()
const authStore = useAuthStore()
const tab = ref('overview')
const previewResult = ref<FilePreviewResult | null>(null)
const previewDialogOpen = ref(false)
const previewingFile = ref<string | null>(null)
let cleanupTimer: number | undefined
const attachmentAccept = '.pdf,.doc,.docx,.xls,.xlsx,.ppt,.pptx,.txt,.csv,.png,.jpg,.jpeg'
const allowedAttachmentExtensions = new Set(attachmentAccept.split(',').map(value => value.slice(1)))
const maxAttachmentSize = 50 * 1024 * 1024
const attachments = ref<DemoAttachment[]>([
  { name: '交付范围说明-v2.1.pdf', assetUrl: `${import.meta.env.BASE_URL}demo-files/delivery-scope.pdf`, contentType: 'application/pdf', owner: '林川', date: '2026-08-05', size: '186 KB' },
  { name: '项目实施计划.xlsx', assetUrl: `${import.meta.env.BASE_URL}demo-files/project-plan.xlsx`, contentType: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet', owner: '林川', date: '2026-08-05', size: '6 KB' },
  { name: '投产回退预案.docx', assetUrl: `${import.meta.env.BASE_URL}demo-files/rollback-plan.docx`, contentType: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document', owner: '林川', date: '2026-08-05', size: '8 KB' }
])

watch(() => props.modelValue, open => {
  if (open) tab.value = 'overview'
  else previewDialogOpen.value = false
})

watch(previewDialogOpen, open => {
  if (cleanupTimer) window.clearTimeout(cleanupTimer)
  if (!open) {
    cleanupTimer = window.setTimeout(() => {
      cleanupTimer = undefined
      void cleanupPreview()
    }, 350)
  }
})

onBeforeUnmount(() => {
  if (cleanupTimer) window.clearTimeout(cleanupTimer)
  attachments.value.filter(file => file.uploaded).forEach(file => URL.revokeObjectURL(file.assetUrl))
  void cleanupPreview()
})

function download(name: string) { ElMessage.success(`${name} 已加入下载队列`) }

function downloadAttachment(file: DemoAttachment) {
  const link = document.createElement('a')
  link.href = file.assetUrl
  link.download = file.name
  document.body.appendChild(link)
  link.click()
  link.remove()
  download(file.name)
}

function uploadAttachment(uploadFile: UploadFile) {
  const file = uploadFile.raw
  if (!file) return
  const extension = file.name.includes('.') ? file.name.split('.').pop()?.toLowerCase() || '' : ''
  if (!allowedAttachmentExtensions.has(extension)) {
    ElMessage.warning('该文件类型暂不支持上传和预览')
    return
  }
  if (file.size > maxAttachmentSize) {
    ElMessage.warning('单个附件不能超过 50 MB')
    return
  }
  if (attachments.value.some(item => item.name === file.name)) {
    ElMessage.warning('附件列表中已存在同名文件')
    return
  }
  attachments.value.unshift({
    name: file.name,
    assetUrl: URL.createObjectURL(file),
    contentType: file.type || 'application/octet-stream',
    owner: authStore.user?.displayName || authStore.user?.username || '当前用户',
    date: currentDate(),
    size: formatFileSize(file.size),
    sourceFile: file,
    uploaded: true
  })
  ElMessage.success(`${file.name} 上传成功`)
}

function removeAttachment(file: DemoAttachment) {
  if (!file.uploaded) return
  attachments.value = attachments.value.filter(item => item !== file)
  URL.revokeObjectURL(file.assetUrl)
  ElMessage.success(`${file.name} 已删除`)
}

async function previewAttachment(attachment: DemoAttachment) {
  if (previewingFile.value) return
  previewingFile.value = attachment.name
  try {
    if (cleanupTimer) {
      window.clearTimeout(cleanupTimer)
      cleanupTimer = undefined
    }
    await cleanupPreview()
    let file = attachment.sourceFile
    if (!file) {
      const response = await fetch(attachment.assetUrl)
      if (!response.ok) throw new Error(`演示文件加载失败：${response.status}`)
      file = new File([await response.blob()], attachment.name, { type: attachment.contentType })
    }
    previewResult.value = (await uploadFilePreview(file)).data.data
    previewDialogOpen.value = true
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '文件预览失败，请检查预览服务后重试'))
  } finally {
    previewingFile.value = null
  }
}

async function cleanupPreview() {
  const result = previewResult.value
  if (!result) return
  previewResult.value = null
  try {
    await deleteFilePreview(result.previewId)
  } catch {
    // Temporary preview cleanup is best effort and should not block the drawer.
  }
}

function currentDate() {
  const date = new Date()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${date.getFullYear()}-${month}-${day}`
}

function formatFileSize(bytes: number) {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${Math.ceil(bytes / 1024)} KB`
  return `${Math.round(bytes / 1024 / 1024)} MB`
}
</script>

<template>
  <el-drawer :model-value="modelValue" :title="project ? `${project.code} · 项目详情` : '项目详情'" size="min(760px, 96vw)" class="delivery-detail-drawer" @update:model-value="emit('update:modelValue', $event)">
    <template v-if="project">
      <div class="delivery-detail-heading"><div><el-tag :type="project.status === '有风险' ? 'danger' : project.status === '已完成' ? 'success' : 'primary'" effect="plain">{{ project.status }}</el-tag><h2>{{ project.name }}</h2><p>{{ project.description }}</p></div><el-button type="primary" @click="emit('edit', project)"><el-icon><Edit /></el-icon>编辑项目</el-button></div>
      <el-tabs v-model="tab" class="delivery-detail-tabs">
        <el-tab-pane label="概览" name="overview"><el-descriptions :column="2" border><el-descriptions-item label="项目类型">{{ project.type }}</el-descriptions-item><el-descriptions-item label="优先级">{{ project.priority }}</el-descriptions-item><el-descriptions-item label="负责人">{{ project.owner }}</el-descriptions-item><el-descriptions-item label="负责团队">{{ project.department }}</el-descriptions-item><el-descriptions-item label="计划周期">{{ project.startDate }} 至 {{ project.endDate }}</el-descriptions-item><el-descriptions-item label="预算">{{ project.budget }} 万元</el-descriptions-item><el-descriptions-item label="当前阶段">{{ project.stage }}</el-descriptions-item><el-descriptions-item label="总体进度"><el-progress :percentage="project.progress" /></el-descriptions-item><el-descriptions-item label="项目成员" :span="2"><el-tag v-for="member in project.members" :key="member" class="delivery-member-tag" effect="plain">{{ member }}</el-tag></el-descriptions-item></el-descriptions></el-tab-pane>
        <el-tab-pane :label="`里程碑 ${project.milestones.length}`" name="milestones"><el-timeline><el-timeline-item v-for="item in project.milestones" :key="item.id" :timestamp="`${item.start} - ${item.end}`" :type="item.status === '已完成' ? 'success' : item.status === '延期' ? 'danger' : item.status === '进行中' ? 'primary' : 'info'" placement="top"><div class="delivery-milestone-detail"><strong>{{ item.name }}</strong><span>{{ item.stage }} · {{ item.owner }}</span><el-progress :percentage="item.progress" :stroke-width="6" /></div></el-timeline-item></el-timeline></el-tab-pane>
        <el-tab-pane :label="`风险 ${project.risks.length}`" name="risks"><div v-if="project.risks.length" class="delivery-risk-list"><article v-for="risk in project.risks" :key="risk.id"><el-tag :type="risk.level === '高' ? 'danger' : risk.level === '中' ? 'warning' : 'info'">{{ risk.level }}风险</el-tag><div><strong>{{ risk.title }}</strong><small>责任人 {{ risk.owner }} · 计划解决 {{ risk.dueDate }}</small></div><span>{{ risk.status }}</span></article></div><el-empty v-else description="当前项目暂无登记风险" /></el-tab-pane>
        <el-tab-pane :label="`附件 ${attachments.length}`" name="files">
          <div class="delivery-file-toolbar">
            <span>共 {{ attachments.length }} 个附件</span>
            <el-upload :auto-upload="false" :show-file-list="false" :accept="attachmentAccept" :on-change="uploadAttachment">
              <el-button type="primary" :icon="Upload">上传附件</el-button>
            </el-upload>
          </div>
          <div class="delivery-file-list">
            <div v-for="file in attachments" :key="file.name" class="delivery-file-item">
              <el-icon class="delivery-file-item__icon"><Paperclip /></el-icon>
              <div class="delivery-file-item__meta">
                <strong>{{ file.name }}</strong>
                <small>{{ file.owner }} · {{ file.date }} · {{ file.size }}</small>
              </div>
              <div class="delivery-file-item__actions">
                <el-tooltip content="在线预览" placement="top">
                  <el-button
                    :icon="View"
                    circle
                    plain
                    type="primary"
                    :loading="previewingFile === file.name"
                    :disabled="Boolean(previewingFile) && previewingFile !== file.name"
                    :aria-label="`预览${file.name}`"
                    @click="previewAttachment(file)"
                  />
                </el-tooltip>
                <el-tooltip content="下载附件" placement="top">
                  <el-button :icon="Download" circle plain :aria-label="`下载${file.name}`" @click="downloadAttachment(file)" />
                </el-tooltip>
                <el-tooltip v-if="file.uploaded" content="删除附件" placement="top">
                  <el-button :icon="Delete" circle plain type="danger" :disabled="Boolean(previewingFile)" :aria-label="`删除${file.name}`" @click="removeAttachment(file)" />
                </el-tooltip>
              </div>
            </div>
          </div>
        </el-tab-pane>
        <el-tab-pane label="动态" name="activity"><el-timeline><el-timeline-item timestamp="今天 09:42" type="primary"><strong>林川更新了研发阶段进度</strong><p class="delivery-muted">总体进度由 58% 更新为 {{ project.progress }}%</p></el-timeline-item><el-timeline-item timestamp="昨天 16:20" type="warning"><strong>顾言登记了一项交付风险</strong><p class="delivery-muted">已指派责任人并设置解决日期</p></el-timeline-item><el-timeline-item timestamp="08-02 11:15" type="success"><strong>周宁完成技术方案审批</strong></el-timeline-item></el-timeline></el-tab-pane>
      </el-tabs>
    </template>
    <template #footer><div class="delivery-drawer-footer"><el-button @click="emit('update:modelValue', false)">关闭</el-button><el-button v-if="project" @click="download('项目详情.pdf')"><el-icon><Document /></el-icon>导出详情</el-button></div></template>
  </el-drawer>
  <UiFilePreview v-model="previewDialogOpen" :url="previewResult?.previewUrl || null" :file-name="previewResult?.fileName || '文件预览'" />
</template>
