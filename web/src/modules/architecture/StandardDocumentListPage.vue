<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { Document, Plus, Refresh, Search, UploadFilled, View } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { UploadFile, UploadInstance } from 'element-plus'
import { useAuthStore } from '../../stores/auth'
import { apiErrorMessage } from '../../api/error'
import { getAttachmentDownload, getAttachmentPreview, uploadAttachment } from '../../api/attachments'
import UiDataTable from '../../components/ui/UiDataTable.vue'
import UiEmptyState from '../../components/ui/UiEmptyState.vue'
import UiPageHeader from '../../components/ui/UiPageHeader.vue'
import UiStatusTag from '../../components/ui/UiStatusTag.vue'
import UiToolbar from '../../components/ui/UiToolbar.vue'
import {
  bindStandardAttachment,
  createStandardDocument,
  deleteStandardAttachment,
  deleteStandardDocument,
  getStandardDocument,
  listStandardAttachments,
  listStandardCategories,
  listStandardDocuments,
  listStandardVersions,
  offlineStandardDocument,
  publishStandardDocument,
  updateStandardDocument
} from './api'
import type {
  AttachmentItemView,
  StandardCategory,
  StandardDocumentDetail,
  StandardDocumentStatus,
  StandardDocumentSummary,
  StandardVersion
} from './types'
import { formatDateTime, httpStatus, normalizeText } from './utils'
import './architecture.css'

const auth = useAuthStore()
const rows = ref<StandardDocumentSummary[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)
const loading = ref(false)
const loadError = ref('')
const forbidden = ref(false)
const categories = ref<StandardCategory[]>([])
const filters = reactive({ title: '', categoryCode: '', status: '' as StandardDocumentStatus | '' })

const canView = computed(() => auth.hasPermission('architecture:standard:view') || auth.hasPermission('architecture:standard:manage'))
const canManage = computed(() => auth.hasPermission('architecture:standard:manage'))

const statusLabels: Record<StandardDocumentStatus, string> = { DRAFT: '草稿', PUBLISHED: '已发布', OFFLINE: '已下线' }

// ---------- 详情抽屉 ----------
const detailOpen = ref(false)
const detailLoading = ref(false)
const detail = ref<StandardDocumentDetail | null>(null)
const versions = ref<StandardVersion[]>([])
const attachments = ref<AttachmentItemView[]>([])
const versionOpen = ref(false)

async function openDetail(row: StandardDocumentSummary) {
  detailOpen.value = true
  detailLoading.value = true
  detail.value = null
  versions.value = []
  attachments.value = []
  try {
    const [doc, versionList, attachmentList] = await Promise.all([
      getStandardDocument(row.id),
      listStandardVersions(row.id),
      listStandardAttachments(row.id)
    ])
    detail.value = doc
    versions.value = versionList
    attachments.value = attachmentList
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '加载文档详情失败'))
  } finally {
    detailLoading.value = false
  }
}

async function previewAttachmentItem(item: AttachmentItemView) {
  try {
    const result = (await getAttachmentPreview(item.id)).data.data
    window.open(result.previewUrl, '_blank', 'noopener')
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '预览失败'))
  }
}

async function downloadAttachmentItem(item: AttachmentItemView) {
  try {
    const result = (await getAttachmentDownload(item.id)).data.data
    window.open(result.downloadUrl, '_blank', 'noopener')
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '下载失败'))
  }
}

// ---------- 编辑弹窗 ----------
const editorOpen = ref(false)
const editorSaving = ref(false)
const editing = ref<StandardDocumentDetail | null>(null)
const form = reactive({ title: '', categoryCode: '', summary: '', content: '' })
let editorRowVersion = 0

function openCreate() {
  editing.value = null
  form.title = ''
  form.categoryCode = categories.value[0]?.code || ''
  form.summary = ''
  form.content = ''
  editorRowVersion = 0
  editorOpen.value = true
}

function openEdit(doc: StandardDocumentDetail) {
  editing.value = doc
  form.title = doc.title
  form.categoryCode = doc.categoryCode
  form.summary = doc.summary || ''
  form.content = doc.content || ''
  editorRowVersion = doc.rowVersion
  editorOpen.value = true
}

async function saveEditor() {
  if (!form.title.trim()) { ElMessage.warning('请填写标题'); return }
  if (!form.categoryCode) { ElMessage.warning('请选择类别'); return }
  editorSaving.value = true
  try {
    const payload = { title: form.title.trim(), categoryCode: form.categoryCode, summary: normalizeText(form.summary), content: normalizeText(form.content) }
    if (editing.value) {
      await updateStandardDocument(editing.value.id, { ...payload, rowVersion: editorRowVersion })
    } else {
      await createStandardDocument(payload)
    }
    editorOpen.value = false
    ElMessage.success(editing.value ? '文档已保存' : '草稿已创建')
    await load()
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '保存失败'))
  } finally {
    editorSaving.value = false
  }
}

// ---------- 发布/下线/删除 ----------
async function publish(row: StandardDocumentSummary) {
  try {
    await ElMessageBox.confirm(`确认发布《${row.title}》？发布后将追加新版本快照。`, '发布架构规范', { type: 'info' })
  } catch { return }
  try {
    const detailDoc = await getStandardDocument(row.id)
    await publishStandardDocument(row.id, detailDoc.rowVersion)
    ElMessage.success('发布成功')
    await load()
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '发布失败'))
  }
}

async function offline(row: StandardDocumentSummary) {
  try {
    await ElMessageBox.confirm(`确认下线《${row.title}》？已发布版本历史保留。`, '下线架构规范', { type: 'warning' })
  } catch { return }
  try {
    const detailDoc = await getStandardDocument(row.id)
    await offlineStandardDocument(row.id, detailDoc.rowVersion)
    ElMessage.success('已下线')
    await load()
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '下线失败'))
  }
}

async function remove(row: StandardDocumentSummary) {
  try {
    await ElMessageBox.confirm(`确认删除草稿《${row.title}》？删除不可恢复。`, '删除草稿', { type: 'warning' })
  } catch { return }
  try {
    const detailDoc = await getStandardDocument(row.id)
    await deleteStandardDocument(row.id, detailDoc.rowVersion)
    ElMessage.success('已删除')
    await load()
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '删除失败'))
  }
}

// ---------- 附件 ----------
const uploadRef = ref<UploadInstance>()
const uploading = ref(false)

async function onAttachmentUploaded(file: UploadFile) {
  const raw = file.raw
  if (!raw || !detail.value) return
  uploading.value = true
  try {
    const item = (await uploadAttachment(raw)).data.data
    await bindStandardAttachment(detail.value.id, item.id)
    ElMessage.success('附件已上传')
    attachments.value = await listStandardAttachments(detail.value.id)
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '上传失败'))
  } finally {
    uploading.value = false
    if (uploadRef.value) uploadRef.value.clearFiles()
  }
}

async function removeAttachment(item: AttachmentItemView) {
  if (!detail.value) return
  try {
    await ElMessageBox.confirm(`确认移除附件《${item.fileName}》？`, '移除附件', { type: 'warning' })
  } catch { return }
  try {
    await deleteStandardAttachment(detail.value.id, item.id)
    ElMessage.success('已移除')
    attachments.value = await listStandardAttachments(detail.value.id)
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '移除失败'))
  }
}

// ---------- 列表 ----------
async function load() {
  if (!canView.value) return
  loading.value = true
  loadError.value = ''
  try {
    const result = await listStandardDocuments({
      page: page.value, size: pageSize.value,
      title: filters.title || undefined,
      categoryCode: filters.categoryCode || undefined,
      status: filters.status || undefined
    })
    rows.value = result.records
    total.value = result.total
  } catch (error) {
    if (httpStatus(error) === 403) { forbidden.value = true } else { loadError.value = apiErrorMessage(error, '加载失败') }
  } finally {
    loading.value = false
  }
}

async function loadCategories() {
  try {
    categories.value = await listStandardCategories()
  } catch { /* 类别加载失败不阻断列表 */ }
}

function resetFilters() {
  filters.title = ''
  filters.categoryCode = ''
  filters.status = ''
  page.value = 1
  load()
}

watch([page, pageSize], load)
onMounted(async () => {
  await Promise.all([loadCategories(), load()])
})

function categoryLabel(code: string) {
  return categories.value.find(item => item.code === code)?.label || code
}
</script>

<template>
  <div class="architecture-page">
    <UiPageHeader eyebrow="ARCHITECTURE" title="架构规范" description="按受控类别发布和维护架构规范文档，技术人员按权限在线查阅及预览附件。">
      <template #actions>
        <el-button v-if="canManage" type="primary" :icon="Plus" @click="openCreate">发布规范</el-button>
      </template>
    </UiPageHeader>

    <UiToolbar>
      <template #filters>
        <el-input v-model="filters.title" placeholder="搜索标题" clearable style="width:220px" @keyup.enter="page = 1; load()" />
        <el-select v-model="filters.categoryCode" placeholder="类别" clearable style="width:160px">
          <el-option v-for="category in categories" :key="category.code" :label="category.label" :value="category.code" />
        </el-select>
        <el-select v-model="filters.status" placeholder="状态" clearable style="width:140px">
          <el-option label="草稿" value="DRAFT" />
          <el-option label="已发布" value="PUBLISHED" />
          <el-option label="已下线" value="OFFLINE" />
        </el-select>
        <el-button :icon="Search" @click="page = 1; load()">查询</el-button>
        <el-button :icon="Refresh" @click="resetFilters">重置</el-button>
      </template>
    </UiToolbar>

    <UiEmptyState v-if="forbidden" title="无访问权限" description="当前账号缺少架构规范查阅权限，请联系管理员授权。">
      <template #action><el-button @click="$router.push('/dashboard')">返回首页</el-button></template>
    </UiEmptyState>

    <UiEmptyState v-else-if="loadError" title="加载失败" :description="loadError">
      <template #action><el-button :icon="Refresh" @click="load">重试</el-button></template>
    </UiEmptyState>

    <UiDataTable v-else-if="canView" :data="rows" :loading="loading" :empty-text="'暂无架构规范文档'">
      <el-table-column label="标题" min-width="220">
        <template #default="{ row }"><a class="standard-title-link" @click="openDetail(row)">{{ row.title }}</a></template>
      </el-table-column>
      <el-table-column label="类别" width="130">
        <template #default="{ row }">{{ categoryLabel(row.categoryCode) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="110">
        <template #default="{ row }">
          <UiStatusTag :value="row.status" :labels="statusLabels" :tone="row.status === 'PUBLISHED' ? 'success' : row.status === 'OFFLINE' ? 'info' : 'warning'" />
        </template>
      </el-table-column>
      <el-table-column label="版本" width="80">
        <template #default="{ row }">{{ row.currentVersion > 0 ? `v${row.currentVersion}` : '—' }}</template>
      </el-table-column>
      <el-table-column label="发布人" width="120">
        <template #default="{ row }">{{ row.publishedByName || '—' }}</template>
      </el-table-column>
      <el-table-column label="发布时间" width="170">
        <template #default="{ row }">{{ formatDateTime(row.publishedAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="220" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" :icon="View" @click="openDetail(row)">详情</el-button>
          <template v-if="canManage">
            <el-button v-if="row.status === 'DRAFT' || row.status === 'OFFLINE'" link type="success" @click="publish(row)">发布</el-button>
            <el-button v-if="row.status === 'PUBLISHED'" link type="warning" @click="offline(row)">下线</el-button>
            <el-button v-if="row.status !== 'OFFLINE'" link type="primary" @click="getStandardDocument(row.id).then(doc => openEdit(doc))">编辑</el-button>
            <el-button v-if="row.status === 'DRAFT'" link type="danger" @click="remove(row)">删除</el-button>
          </template>
        </template>
      </el-table-column>
      <template #footer>
        <el-pagination v-model:current-page="page" v-model:page-size="pageSize" :total="total"
                       layout="total, sizes, prev, pager, next" :page-sizes="[10, 20, 50]" />
      </template>
    </UiDataTable>

    <!-- 详情抽屉 -->
    <el-drawer v-model="detailOpen" size="min(640px, 92vw)" :title="detail?.title || '架构规范详情'" v-loading="detailLoading">
      <template v-if="detail">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="类别">{{ categoryLabel(detail.categoryCode) }}</el-descriptions-item>
          <el-descriptions-item label="状态"><UiStatusTag :value="detail.status" :labels="statusLabels" /></el-descriptions-item>
          <el-descriptions-item label="当前版本">{{ detail.currentVersion > 0 ? `v${detail.currentVersion}` : '未发布' }}</el-descriptions-item>
          <el-descriptions-item label="发布人">{{ detail.publishedByName || '—' }}</el-descriptions-item>
          <el-descriptions-item label="发布时间">{{ formatDateTime(detail.publishedAt) }}</el-descriptions-item>
          <el-descriptions-item label="创建人">{{ detail.createdByName || '—' }}</el-descriptions-item>
        </el-descriptions>
        <el-divider content-position="left">摘要</el-divider>
        <p class="standard-detail-text">{{ detail.summary || '—' }}</p>
        <el-divider content-position="left">正文</el-divider>
        <pre class="standard-detail-text standard-detail-pre">{{ detail.content || '—' }}</pre>
        <el-divider content-position="left">附件</el-divider>
        <div class="standard-attachments">
          <div v-for="item in attachments" :key="item.id" class="standard-attachment-item">
            <el-icon><Document /></el-icon>
            <span class="standard-attachment-name" @click="previewAttachmentItem(item)">{{ item.fileName }}</span>
            <span class="standard-attachment-meta">{{ formatDateTime(item.createdAt) }}</span>
            <el-button link type="primary" @click="downloadAttachmentItem(item)">下载</el-button>
            <el-button v-if="canManage" link type="danger" @click="removeAttachment(item)">移除</el-button>
          </div>
          <el-empty v-if="attachments.length === 0" description="暂无附件" :image-size="60" />
          <el-upload v-if="canManage" ref="uploadRef" :auto-upload="false" :show-file-list="false" :on-change="onAttachmentUploaded">
            <el-button :icon="UploadFilled" :loading="uploading">上传附件（PDF 等文件仅作附件格式）</el-button>
          </el-upload>
        </div>
        <el-divider content-position="left">版本历史</el-divider>
        <el-button link type="primary" @click="versionOpen = true">查看 {{ versions.length }} 个版本快照</el-button>
      </template>
    </el-drawer>

    <!-- 版本快照弹窗 -->
    <el-dialog v-model="versionOpen" title="发布版本快照（不可变）" width="min(720px, 92vw)">
      <el-timeline v-if="versions.length">
        <el-timeline-item v-for="version in versions" :key="version.id" :timestamp="`${formatDateTime(version.publishedAt)} · ${version.publishedByName || '—'}`" placement="top">
          <el-card shadow="never">
            <strong>v{{ version.versionNo }} · {{ version.title }}</strong>
            <p class="standard-detail-text">{{ version.summary || '—' }}</p>
            <pre class="standard-detail-text standard-detail-pre">{{ version.content || '—' }}</pre>
          </el-card>
        </el-timeline-item>
      </el-timeline>
      <el-empty v-else description="尚未发布过版本" />
    </el-dialog>

    <!-- 编辑弹窗 -->
    <el-dialog v-model="editorOpen" :title="editing ? '编辑架构规范' : '发布架构规范'" width="min(720px, 94vw)" :close-on-click-modal="false">
      <el-form label-position="top">
        <el-form-item label="标题" required>
          <el-input v-model="form.title" maxlength="200" show-word-limit placeholder="规范标题" />
        </el-form-item>
        <el-form-item label="类别" required>
          <el-select v-model="form.categoryCode" style="width:100%">
            <el-option v-for="category in categories" :key="category.code" :label="category.label" :value="category.code" />
          </el-select>
        </el-form-item>
        <el-form-item label="摘要">
          <el-input v-model="form.summary" type="textarea" :rows="3" maxlength="2000" show-word-limit />
        </el-form-item>
        <el-form-item label="正文">
          <el-input v-model="form.content" type="textarea" :rows="10" placeholder="规范正文内容" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editorOpen = false">取消</el-button>
        <el-button type="primary" :loading="editorSaving" @click="saveEditor">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>
