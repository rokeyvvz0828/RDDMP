<!--
  用途：数迁资产内容 - 汇报材料页
  说明：支持多维度筛选、单条/批量上传、编辑、下载、逻辑删除和回收站管理。
        桌面端使用表格展示，移动端使用卡片展示。
-->
<script setup lang="ts">
import '../../data-migration.css'
import { onMounted, onUnmounted, ref, computed, watch } from 'vue'
import { ElMessage, ElMessageBox, type UploadFile } from 'element-plus'
import { Delete, Download, Edit, Plus, Refresh, Search, UploadFilled, FolderOpened, View } from '@element-plus/icons-vue'
import UiDataTable from '../../../../components/ui/UiDataTable.vue'
import UiFormDrawer from '../../../../components/ui/UiFormDrawer.vue'
import UiToolbar from '../../../../components/ui/UiToolbar.vue'
import UiPagination from '../../../../components/ui/UiPagination.vue'
import UiPageHeader from '../../../../components/ui/UiPageHeader.vue'
import UiEmptyState from '../../../../components/ui/UiEmptyState.vue'
import UiFilePreview from '../../../../components/ui/UiFilePreview.vue'
import { useAuthStore } from '../../../../stores/auth'
import {
  listReportMaterials,
  uploadReportMaterial,
  batchUploadReportMaterials,
  updateReportMaterial,
  deleteReportMaterials,
  downloadReportMaterial,
  listReportRecycleBin,
  restoreReportMaterials,
  purgeReportMaterials,
  checkReportMd5,
  computeFileMd5 as computeFileMd5Api,
  getReportProjectOptions,
  type ReportMaterial,
  type ReportPageQuery
} from '../../../../api/data-migration'
import { uploadAttachment, getAttachment, getAttachmentDownload } from '../../../../api/attachments'
import { deleteFilePreview, uploadFilePreview } from '../../../../api/file-preview'

const authStore = useAuthStore()
const loading = ref(false)
const reports = ref<ReportMaterial[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(20)
const selectedIds = ref<number[]>([])
const actionBusy = ref(false)

// 文件预览
const filePreviewVisible = ref(false)
const filePreviewUrl = ref<string | null>(null)
const filePreviewName = ref('')
const filePreviewId = ref<string | null>(null)

async function cleanupFilePreview() {
  const previewId = filePreviewId.value
  filePreviewId.value = null
  filePreviewUrl.value = null
  if (!previewId) return
  try {
    await deleteFilePreview(previewId)
  } catch (error) {
    console.warn('清理临时预览文件失败', error)
  }
}

watch(filePreviewVisible, (visible) => {
  if (!visible) void cleanupFilePreview()
})

onUnmounted(() => {
  void cleanupFilePreview()
})

// 筛选条件
const filterProjectId = ref<number | null>(null)
const filterReportPeriod = ref<string>('')
const filterKeyword = ref('')

// 项目选项
const projectOptions = ref<Array<{ id: number; project_name: string }>>([])

// 汇报周期选项
const reportPeriodOptions = [
  { value: 'DAILY', label: '日报' },
  { value: 'WEEKLY', label: '周报' },
  { value: 'BIWEEKLY', label: '双周报' },
  { value: 'MONTHLY', label: '月报' },
  { value: 'IRREGULAR', label: '不定期汇报' }
]

// 上传抽屉
const uploadDrawerOpen = ref(false)
const uploadSaving = ref(false)
const uploadType = ref<'single' | 'batch'>('single')
const uploadProjectId = ref<number | null>(null)
const uploadReportPeriod = ref<string>('')
const uploadReportName = ref('')
const uploadReportDate = ref('')
const uploadKeywords = ref('')
const uploadMd5 = ref('')
const uploadFile = ref<File | null>(null)
const uploadFiles = ref<File[]>([])
const singleUploadRef = ref<InstanceType<typeof import('element-plus')['ElUpload']>>()

// 监听uploadFile变化，自动更新资料名称和MD5
watch(uploadFile, (newFile) => {
  if (newFile) {
    // 自动填充完整文件名（含扩展名）作为资料名称
    uploadReportName.value = newFile.name
    // 计算MD5
    computeFileMd5(newFile).then(md5 => {
      uploadMd5.value = md5
    })
  } else {
    uploadReportName.value = ''
    uploadMd5.value = ''
  }
})

// 编辑抽屉
const editDrawerOpen = ref(false)
const editSaving = ref(false)
const editId = ref<number | null>(null)
const editProjectId = ref<number | null>(null)
const editReportPeriod = ref<string>('')
const editReportName = ref('')
const editReportDate = ref('')
const editKeywords = ref('')
const editMd5 = ref('')
const editFile = ref<File | null>(null)

// 关键字标签输入
const newKeyword = ref('')
const newEditKeyword = ref('')

// 上传抽屉：添加关键字
function addKeyword() {
  const val = newKeyword.value.trim()
  if (!val) return
  const list = uploadKeywords.value ? uploadKeywords.value.split(',').map(s => s.trim()).filter(Boolean) : []
  if (!list.includes(val)) {
    list.push(val)
    uploadKeywords.value = list.join(',')
  }
  newKeyword.value = ''
}

// 上传抽屉：删除关键字
function removeKeyword(index: number) {
  const list = uploadKeywords.value.split(',').map(s => s.trim()).filter(Boolean)
  list.splice(index, 1)
  uploadKeywords.value = list.join(',')
}

// 编辑抽屉：添加关键字
function addEditKeyword() {
  const val = newEditKeyword.value.trim()
  if (!val) return
  const list = editKeywords.value ? editKeywords.value.split(',').map(s => s.trim()).filter(Boolean) : []
  if (!list.includes(val)) {
    list.push(val)
    editKeywords.value = list.join(',')
  }
  newEditKeyword.value = ''
}

// 编辑抽屉：删除关键字
function removeEditKeyword(index: number) {
  const list = editKeywords.value.split(',').map(s => s.trim()).filter(Boolean)
  list.splice(index, 1)
  editKeywords.value = list.join(',')
}

// 回收站
const activeTab = ref<'list' | 'recycle'>('list')
const recycleLoading = ref(false)
const recycleReports = ref<ReportMaterial[]>([])
const recycleTotal = ref(0)
const recycleCurrentPage = ref(1)
const recyclePageSize = ref(20)
const recycleSelectedIds = ref<number[]>([])

// 权限检查
const hasCreatePermission = computed(() => {
  return authStore.hasPermission('data-migration:content:reports:create') ||
         authStore.hasPermission('data-migration:write') ||
         authStore.hasPermission('data-migration:manage') ||
         authStore.hasPermission('system:admin')
})

const hasManagePermission = computed(() => {
  return authStore.hasPermission('data-migration:manage') ||
         authStore.hasPermission('system:admin')
})

const isAdmin = computed(() => {
  return authStore.hasPermission('data-migration:manage') ||
         authStore.hasPermission('system:admin')
})

function messageOf(error: unknown) {
  return error instanceof Error ? error.message : '操作失败，请稍后重试'
}

// 判断是否为用户主动取消操作（兼容不同 Element Plus 版本的取消错误格式）
function isUserCancel(error: unknown): boolean {
  if (error === 'cancel' || error === 'close') return true
  if (error instanceof Error && (error.message === 'cancel' || error.message === 'close')) return true
  if (typeof error === 'object' && error !== null) {
    const action = (error as Record<string, unknown>).action
    if (action === 'cancel' || action === 'close') return true
  }
  return false
}

// 加载项目选项
async function loadProjectOptions() {
  try {
    const response = await getReportProjectOptions()
    projectOptions.value = response.data.data ?? []
  } catch (e) {
    console.error('加载项目选项失败', e)
  }
}

// 加载汇报材料列表
async function loadReports() {
  loading.value = true
  selectedIds.value = []
  try {
    const params: ReportPageQuery = {
      page: currentPage.value,
      size: pageSize.value
    }
    if (filterProjectId.value) params.projectId = filterProjectId.value
    if (filterReportPeriod.value) params.reportPeriod = filterReportPeriod.value
    if (filterKeyword.value.trim()) params.keyword = filterKeyword.value.trim()

    const response = await listReportMaterials(params)
    const pageData = response.data.data
    reports.value = pageData?.records ?? []
    total.value = pageData?.total ?? 0
  } catch (e) {
    ElMessage.error(messageOf(e))
  } finally {
    loading.value = false
  }
}

// 加载回收站列表
async function loadRecycleBin() {
  recycleLoading.value = true
  recycleSelectedIds.value = []
  try {
    const params: ReportPageQuery = {
      page: recycleCurrentPage.value,
      size: recyclePageSize.value
    }
    if (filterProjectId.value) params.projectId = filterProjectId.value
    if (filterReportPeriod.value) params.reportPeriod = filterReportPeriod.value
    if (filterKeyword.value.trim()) params.keyword = filterKeyword.value.trim()

    const response = await listReportRecycleBin(params)
    const pageData = response.data.data
    recycleReports.value = pageData?.records ?? []
    recycleTotal.value = pageData?.total ?? 0
  } catch (e) {
    ElMessage.error(messageOf(e))
  } finally {
    recycleLoading.value = false
  }
}

// 切换Tab
function switchTab(tab: 'list' | 'recycle') {
  activeTab.value = tab
  if (tab === 'list') {
    loadReports()
  } else {
    loadRecycleBin()
  }
}

// 筛选查询
function handleSearch() {
  currentPage.value = 1
  recycleCurrentPage.value = 1
  if (activeTab.value === 'list') {
    loadReports()
  } else {
    loadRecycleBin()
  }
}

// 重置筛选
function handleReset() {
  filterProjectId.value = null
  filterReportPeriod.value = ''
  filterKeyword.value = ''
  handleSearch()
}

// 刷新
function handleRefresh() {
  if (activeTab.value === 'list') {
    loadReports()
  } else {
    loadRecycleBin()
  }
}

// 分页变化
function handlePageChange(page: number) {
  currentPage.value = page
  loadReports()
}

function handleSizeChange(size: number) {
  pageSize.value = size
  currentPage.value = 1
  loadReports()
}

function handleRecyclePageChange(page: number) {
  recycleCurrentPage.value = page
  loadRecycleBin()
}

function handleRecycleSizeChange(size: number) {
  recyclePageSize.value = size
  recycleCurrentPage.value = 1
  loadRecycleBin()
}

// 选择变化
function onSelectionChange(rows: ReportMaterial[]) {
  selectedIds.value = rows.map(row => row.id)
}

function onRecycleSelectionChange(rows: ReportMaterial[]) {
  recycleSelectedIds.value = rows.map(row => row.id)
}

// 打开单条上传抽屉
function openSingleUpload() {
  uploadType.value = 'single'
  uploadProjectId.value = null
  uploadReportPeriod.value = ''
  uploadReportName.value = ''
  uploadReportDate.value = ''
  uploadKeywords.value = ''
  uploadMd5.value = ''
  uploadFile.value = null
  uploadFiles.value = []
  uploadDrawerOpen.value = true
}

// 打开批量上传抽屉
function openBatchUpload() {
  uploadType.value = 'batch'
  uploadProjectId.value = null
  uploadReportPeriod.value = ''
  uploadFiles.value = []
  uploadDrawerOpen.value = true
}

// 文件选择变化
function onUploadFileChange(file: UploadFile) {
  if (uploadType.value === 'single') {
    // 获取原始文件对象
    const rawFile = file.raw ?? (file instanceof File ? file : null)
    uploadFile.value = rawFile
  }
}

// 超过文件数量限制时触发（重新选择文件）
function onUploadExceed(files: File[]) {
  if (uploadType.value === 'single' && files.length > 0) {
    const upload = singleUploadRef.value as any
    // 清除旧文件，再将新文件加入 el-upload 内部列表，保持视觉同步
    upload?.clearFiles()
    upload?.handleStart(files[0])
    uploadFile.value = files[0]
  }
}

// 单条上传：用户手动删除文件时清空关联字段
function onUploadRemove() {
  uploadFile.value = null
}

function onBatchFileChange(files: File[]) {
  uploadFiles.value = files
}

// 计算文件MD5
async function computeFileMd5(file: File): Promise<string> {
  try {
    return await computeFileMd5Api(file)
  } catch {
    // 如果计算失败，返回空字符串
    return ''
  }
}

// 保存单条上传
async function saveSingleUpload() {
  if (!uploadProjectId.value) {
    ElMessage.warning('请选择所属项目')
    return
  }
  if (!uploadReportPeriod.value) {
    ElMessage.warning('请选择汇报周期')
    return
  }
  if (!uploadReportName.value.trim()) {
    ElMessage.warning('请填写资料名称')
    return
  }
  if (!uploadFile.value) {
    ElMessage.warning('请选择要上传的文件')
    return
  }

  uploadSaving.value = true
  try {
    // 计算MD5（如果还没有计算）
    let md5 = uploadMd5.value
    if (!md5) {
      md5 = await computeFileMd5(uploadFile.value)
      uploadMd5.value = md5
    }

    // 校验MD5是否已存在
    if (md5) {
      const md5Available = await checkReportMd5(md5)
      if (!md5Available) {
        ElMessage.error('文件MD5已存在，不允许重复提交')
        return
      }
    }

    // 上传附件获取attachmentId
    const attachmentResponse = await uploadAttachment(uploadFile.value)
    const attachmentId = attachmentResponse.data?.data?.id

    if (!attachmentId) {
      ElMessage.error('附件上传失败')
      return
    }

    await uploadReportMaterial({
      projectId: uploadProjectId.value,
      reportPeriod: uploadReportPeriod.value,
      reportName: uploadReportName.value.trim(),
      reportDate: uploadReportDate.value || undefined,
      keywords: uploadKeywords.value.trim(),
      attachmentId,
      checksumMd5: md5 || undefined
    })

    ElMessage.success('上传成功')
    uploadDrawerOpen.value = false
    loadReports()
  } catch (e) {
    ElMessage.error(messageOf(e))
  } finally {
    uploadSaving.value = false
  }
}

// 保存批量上传
async function saveBatchUpload() {
  if (!uploadProjectId.value) {
    ElMessage.warning('请选择所属项目')
    return
  }
  if (!uploadReportPeriod.value) {
    ElMessage.warning('请选择汇报周期')
    return
  }
  if (uploadFiles.value.length === 0) {
    ElMessage.warning('请选择要上传的文件')
    return
  }

  uploadSaving.value = true
  try {
    // 上传所有附件获取attachmentIds
    const attachmentIds: number[] = []
    const checksumMd5s: string[] = []
    const batchMd5s = new Set<string>()
    for (const file of uploadFiles.value) {
      try {
        const md5 = await computeFileMd5(file)
        if (!md5) {
          throw new Error(`无法计算文件 ${file.name} 的 MD5`)
        }
        if (batchMd5s.has(md5)) {
          throw new Error(`文件 ${file.name} 与本批次其他文件内容重复`)
        }
        const md5Available = await checkReportMd5(md5)
        if (!md5Available) {
          throw new Error(`文件 ${file.name} 已存在，不允许重复上传`)
        }
        const response = await uploadAttachment(file)
        const attachmentId = response.data?.data?.id
        if (attachmentId) {
          attachmentIds.push(attachmentId)
          checksumMd5s.push(md5)
          batchMd5s.add(md5)
        } else {
          throw new Error(`文件 ${file.name} 上传失败`)
        }
      } catch (e) {
        ElMessage.error(`文件 ${file.name} 上传失败: ${messageOf(e)}`)
        return
      }
    }

    await batchUploadReportMaterials({
      projectId: uploadProjectId.value,
      reportPeriod: uploadReportPeriod.value,
      attachmentIds,
      checksumMd5s
    })

    ElMessage.success(`批量上传成功，共 ${uploadFiles.value.length} 个文件`)
    uploadDrawerOpen.value = false
    loadReports()

    // 批量上传成功后，提示用户补充元数据
    ElMessageBox.confirm(
      '批量上传成功！是否需要补充汇报日期和关键字索引？',
      '元数据补充',
      {
        confirmButtonText: '去补充',
        cancelButtonText: '稍后补充',
        type: 'info'
      }
    ).then(() => {
      // 打开批量编辑对话框
      openBatchEditDialog()
    }).catch(() => {
      // 用户选择稍后补充
    })
  } catch (e) {
    ElMessage.error(messageOf(e))
  } finally {
    uploadSaving.value = false
  }
}

// 批量编辑对话框
const batchEditDialogVisible = ref(false)
const batchEditItems = ref<Array<{
  id: number
  reportName: string
  reportDate: string
  keywords: string
}>>([])

function openBatchEditDialog() {
  // 这里需要从后端获取刚才上传的材料列表
  // 暂时使用空数据，实际应该调用API获取
  batchEditItems.value = []
  batchEditDialogVisible.value = true
}

async function saveBatchEdit() {
  // 批量更新元数据
  try {
    for (const item of batchEditItems.value) {
      await updateReportMaterial(item.id, {
        reportDate: item.reportDate || undefined,
        keywords: item.keywords || undefined
      })
    }
    ElMessage.success('批量更新成功')
    batchEditDialogVisible.value = false
    loadReports()
  } catch (e) {
    ElMessage.error(messageOf(e))
  }
}

// 下载汇报材料
async function downloadReport(row: ReportMaterial) {
  actionBusy.value = true
  try {
    const response = await downloadReportMaterial(row.id)
    let url = response.data.data
    if (!url) return
    // 后端返回的可能是附件 API 地址（需二次请求获取真实 URL），也可能是直链
    const match = url.match(/\/attachments\/(\d+)\/download/)
    if (match) {
      const res = await getAttachmentDownload(Number(match[1]))
      url = res.data.data?.downloadUrl || ''
    }
    if (url) {
      const a = document.createElement('a')
      a.href = url
      a.download = row.asset_name || ''
      a.style.display = 'none'
      document.body.appendChild(a)
      a.click()
      document.body.removeChild(a)
    }
  } catch (e) {
    ElMessage.error(messageOf(e))
  } finally {
    actionBusy.value = false
  }
}

// 预览汇报材料
async function previewReport(row: ReportMaterial) {
  if (!row.attachment_id) {
    ElMessage.warning('该材料暂无附件，无法预览')
    return
  }
  actionBusy.value = true
  try {
    await cleanupFilePreview()
    const [attachmentResponse, downloadResponse] = await Promise.all([
      getAttachment(row.attachment_id),
      getAttachmentDownload(row.attachment_id)
    ])
    const attachment = attachmentResponse.data.data
    const downloadUrl = downloadResponse.data.data?.downloadUrl
    if (!attachment || !downloadUrl) throw new Error('无法获取附件信息')

    const sourceResponse = await fetch(downloadUrl)
    if (!sourceResponse.ok) throw new Error('附件下载失败')
    const sourceBlob = await sourceResponse.blob()
    const sourceFile = new File([sourceBlob], attachment.fileName, {
      type: attachment.contentType || sourceBlob.type || 'application/octet-stream'
    })
    const preview = (await uploadFilePreview(sourceFile)).data.data
    if (!preview?.previewUrl || !preview.previewId) throw new Error('无法获取预览地址')

    filePreviewId.value = preview.previewId
    filePreviewUrl.value = preview.previewUrl
    filePreviewName.value = preview.fileName || attachment.fileName
    filePreviewVisible.value = true
  } catch (e) {
    // 预览失败时，提示用户并建议下载
    ElMessageBox.confirm(
      '预览服务暂时不可用，是否下载文件查看？',
      '预览失败',
      {
        confirmButtonText: '下载',
        cancelButtonText: '取消',
        type: 'warning',
      }
    ).then(() => {
      downloadReport(row)
    }).catch(() => {})
  } finally {
    actionBusy.value = false
  }
}

// 打开编辑抽屉
function openEdit(row: ReportMaterial) {
  editId.value = row.id
  editProjectId.value = row.project_id
  editReportPeriod.value = row.report_period
  editReportName.value = row.asset_name
  editReportDate.value = row.report_date || ''
  editKeywords.value = row.keywords || ''
  editMd5.value = row.checksum_md5 || ''
  editFile.value = null
  editDrawerOpen.value = true
}

// 文件选择变化（编辑）
function onEditFileChange(file: UploadFile) {
  editFile.value = file.raw ?? null
  if (editFile.value) {
    computeFileMd5(editFile.value).then(md5 => {
      editMd5.value = md5
    })
  }
}

// 保存编辑
async function saveEdit() {
  if (!editId.value) return

  if (!editReportName.value.trim()) {
    ElMessage.warning('请填写资料名称')
    return
  }
  editSaving.value = true
  try {
    const params: any = {
      reportName: editReportName.value.trim(),
      reportDate: editReportDate.value || undefined,
      keywords: editKeywords.value.trim()
    }

    if (editProjectId.value) params.projectId = editProjectId.value
    if (editReportPeriod.value) params.reportPeriod = editReportPeriod.value

    if (editFile.value) {
      // 上传附件获取attachmentId
      const attachmentResponse = await uploadAttachment(editFile.value)
      const attachmentId = attachmentResponse.data?.data?.id

      if (!attachmentId) {
        ElMessage.error('附件上传失败')
        return
      }

      params.attachmentId = attachmentId
      if (editMd5.value) params.checksumMd5 = editMd5.value
    }

    await updateReportMaterial(editId.value, params)
    ElMessage.success('编辑成功')
    editDrawerOpen.value = false
    loadReports()
  } catch (e) {
    ElMessage.error(messageOf(e))
  } finally {
    editSaving.value = false
  }
}

// 删除汇报材料
async function deleteReports() {
  if (selectedIds.value.length === 0) return

  try {
    await ElMessageBox.confirm(
      `确认将选中的 ${selectedIds.value.length} 个汇报材料移入回收站吗？`,
      '移入回收站',
      { type: 'warning' }
    )

    actionBusy.value = true
    await deleteReportMaterials(selectedIds.value)
    ElMessage.success('已移入回收站')
    loadReports()
  } catch (error) {
    if (!isUserCancel(error)) {
      ElMessage.error(messageOf(error))
    }
  } finally {
    actionBusy.value = false
  }
}

// 删除单个汇报材料
async function deleteSingleReport(row: ReportMaterial) {
  try {
    await ElMessageBox.confirm(
      `确认将"${row.asset_name}"移入回收站吗？`,
      '移入回收站',
      { type: 'warning' }
    )

    actionBusy.value = true
    await deleteReportMaterials([row.id])
    ElMessage.success('已移入回收站')
    loadReports()
  } catch (error) {
    if (!isUserCancel(error)) {
      ElMessage.error(messageOf(error))
    }
  } finally {
    actionBusy.value = false
  }
}

// 恢复汇报材料
async function restoreReports() {
  if (recycleSelectedIds.value.length === 0) return

  try {
    await ElMessageBox.confirm(
      `确认恢复选中的 ${recycleSelectedIds.value.length} 个汇报材料吗？`,
      '恢复汇报材料',
      { type: 'info' }
    )

    actionBusy.value = true
    await restoreReportMaterials(recycleSelectedIds.value)
    ElMessage.success('恢复成功')
    loadRecycleBin()
  } catch (error) {
    if (!isUserCancel(error)) {
      ElMessage.error(messageOf(error))
    }
  } finally {
    actionBusy.value = false
  }
}

// 恢复单个汇报材料
async function restoreSingleReport(row: ReportMaterial) {
  try {
    await ElMessageBox.confirm(
      `确认恢复"${row.asset_name}"吗？`,
      '恢复汇报材料',
      { type: 'info' }
    )

    actionBusy.value = true
    await restoreReportMaterials([row.id])
    ElMessage.success('恢复成功')
    loadRecycleBin()
  } catch (error) {
    if (!isUserCancel(error)) {
      ElMessage.error(messageOf(error))
    }
  } finally {
    actionBusy.value = false
  }
}

// 确认清理汇报材料
async function purgeReports() {
  if (recycleSelectedIds.value.length === 0) return

  try {
    await ElMessageBox.confirm(
      `确认彻底销毁选中的 ${recycleSelectedIds.value.length} 个汇报材料吗？此操作不可恢复，但会保留审计日志。`,
      '确认清理',
      { type: 'error', confirmButtonText: '彻底销毁', cancelButtonText: '取消' }
    )

    actionBusy.value = true
    await purgeReportMaterials(recycleSelectedIds.value)
    ElMessage.success('清理完成')
    loadRecycleBin()
  } catch (error) {
    if (!isUserCancel(error)) {
      ElMessage.error(messageOf(error))
    }
  } finally {
    actionBusy.value = false
  }
}

// 确认清理单个汇报材料
async function purgeSingleReport(row: ReportMaterial) {
  try {
    await ElMessageBox.confirm(
      `确认彻底销毁"${row.asset_name}"吗？此操作不可恢复，但会保留审计日志。`,
      '确认清理',
      { type: 'error', confirmButtonText: '彻底销毁', cancelButtonText: '取消' }
    )

    actionBusy.value = true
    await purgeReportMaterials([row.id])
    ElMessage.success('清理完成')
    loadRecycleBin()
  } catch (error) {
    if (!isUserCancel(error)) {
      ElMessage.error(messageOf(error))
    }
  } finally {
    actionBusy.value = false
  }
}

// 格式化汇报周期
function formatReportPeriod(period: string): string {
  const periodMap: Record<string, string> = {
    'DAILY': '日报',
    'WEEKLY': '周报',
    'BIWEEKLY': '双周报',
    'MONTHLY': '月报',
    'IRREGULAR': '不定期汇报'
  }
  return periodMap[period] || period
}

// 格式化文件大小
function formatFileSize(size?: number): string {
  if (!size) return '—'
  if (size < 1024) return size + ' B'
  if (size < 1024 * 1024) return (size / 1024).toFixed(1) + ' KB'
  return (size / (1024 * 1024)).toFixed(1) + ' MB'
}

// 检查是否可以编辑/删除
function canEditOrDelete(row: ReportMaterial): boolean {
  if (isAdmin.value) return true
  return row.owner_id === authStore.user?.id
}

// 初始化
onMounted(() => {
  loadProjectOptions()
  loadReports()
})
</script>

<template>
  <section class="dm-page-root">
    <!-- 页面标题 -->
    <UiPageHeader title="汇报材料">
      <template #actions>
        <el-button
          v-if="hasCreatePermission"
          type="primary"
          :disabled="loading || actionBusy"
          @click="openSingleUpload"
        >
          <el-icon><Plus /></el-icon>单条上传
        </el-button>
        <el-button
          v-if="hasCreatePermission"
          type="primary"
          plain
          :disabled="loading || actionBusy"
          @click="openBatchUpload"
        >
          <el-icon><UploadFilled /></el-icon>批量上传
        </el-button>
      </template>
    </UiPageHeader>

    <!-- Tab切换 -->
    <el-tabs v-model="activeTab" @tab-click="(tab: any) => switchTab(tab.paneName)">
      <el-tab-pane label="汇报材料" name="list">
        <!-- 筛选工具栏 -->
        <UiToolbar>
          <el-select
            v-model="filterProjectId"
            placeholder="选择项目"
            clearable
            filterable
            style="width: 200px"
          >
            <el-option
              v-for="project in projectOptions"
              :key="project.id"
              :label="project.project_name"
              :value="project.id"
            />
          </el-select>
          <el-select
            v-model="filterReportPeriod"
            placeholder="汇报周期"
            clearable
            style="width: 140px"
          >
            <el-option
              v-for="option in reportPeriodOptions"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
          <el-input
            v-model="filterKeyword"
            clearable
            placeholder="搜索资料名称或关键字"
            style="width: 240px"
            @keyup.enter="handleSearch"
          >
            <template #prefix><el-icon><Search /></el-icon></template>
          </el-input>
          <template #actions>
            <el-button :disabled="loading" @click="handleRefresh">
              <el-icon><Refresh /></el-icon>刷新
            </el-button>
            <el-button type="primary" :disabled="loading" @click="handleSearch">
              <el-icon><Search /></el-icon>查询
            </el-button>
            <el-button :disabled="loading" @click="handleReset">重置</el-button>
            <el-button
              v-if="selectedIds.length > 0"
              type="danger"
              plain
              :disabled="actionBusy"
              @click="deleteReports"
            >
              <el-icon><Delete /></el-icon>移入回收站 ({{ selectedIds.length }})
            </el-button>
          </template>
        </UiToolbar>

        <!-- 桌面端表格 -->
        <UiDataTable
          class="dm-desktop-table"
          :data="reports"
          :loading="loading"
          row-key="id"
          border
          empty-text="暂无汇报材料"
          @selection-change="onSelectionChange"
        >
          <el-table-column type="selection" width="46" />
          <el-table-column prop="id" label="ID" width="80" show-overflow-tooltip />
          <el-table-column prop="project_name" label="所属项目" min-width="150" show-overflow-tooltip />
          <el-table-column label="汇报周期" width="120">
            <template #default="{ row }">
              {{ formatReportPeriod(row.report_period) }}
            </template>
          </el-table-column>
          <el-table-column prop="asset_name" label="资料名称" min-width="200" show-overflow-tooltip />
          <el-table-column prop="asset_code" label="文件编号" min-width="150" show-overflow-tooltip />
          <el-table-column label="汇报日期" width="120">
            <template #default="{ row }">
              {{ row.report_date || '—' }}
            </template>
          </el-table-column>
          <el-table-column label="关键字索引" min-width="150" show-overflow-tooltip>
            <template #default="{ row }">
              {{ row.keywords || '—' }}
            </template>
          </el-table-column>
          <el-table-column label="文件大小" width="100">
            <template #default="{ row }">
              {{ formatFileSize(row.file_size) }}
            </template>
          </el-table-column>
          <el-table-column label="操作" width="260" fixed="right">
            <template #default="{ row }">
              <div class="dm-table-actions">
                <el-button
                  link
                  type="primary"
                  :disabled="actionBusy"
                  @click="previewReport(row)"
                >
                  <el-icon><View /></el-icon>预览
                </el-button>
                <el-button
                  link
                  type="primary"
                  :disabled="actionBusy"
                  @click="downloadReport(row)"
                >
                  <el-icon><Download /></el-icon>下载
                </el-button>
                <el-button
                  v-if="canEditOrDelete(row)"
                  link
                  type="primary"
                  :disabled="actionBusy"
                  @click="openEdit(row)"
                >
                  <el-icon><Edit /></el-icon>编辑
                </el-button>
                <el-button
                  v-if="canEditOrDelete(row)"
                  link
                  type="danger"
                  :disabled="actionBusy"
                  @click="deleteSingleReport(row)"
                >
                  <el-icon><Delete /></el-icon>删除
                </el-button>
              </div>
            </template>
          </el-table-column>
        </UiDataTable>

        <!-- 移动端卡片 -->
        <div class="dm-mobile-list" :class="{ 'is-loading': loading }">
          <article v-for="report in reports" :key="report.id">
            <header>
              <div>
                <strong>{{ report.asset_name }}</strong>
                <small>{{ formatReportPeriod(report.report_period) }}</small>
              </div>
              <el-tag size="small" type="info">{{ formatFileSize(report.file_size) }}</el-tag>
            </header>
            <dl>
              <dt>ID</dt>
              <dd>{{ report.id }}</dd>
              <dt>所属项目</dt>
              <dd>{{ report.project_name || '—' }}</dd>
              <dt>文件编号</dt>
              <dd>{{ report.asset_code || '—' }}</dd>
              <dt>汇报日期</dt>
              <dd>{{ report.report_date || '—' }}</dd>
              <dt>关键字索引</dt>
              <dd>{{ report.keywords || '—' }}</dd>
            </dl>
            <footer>
              <el-button
                link
                type="primary"
                :disabled="actionBusy"
                @click="previewReport(report)"
              >
                <el-icon><View /></el-icon>预览
              </el-button>
              <el-button
                link
                type="primary"
                :disabled="actionBusy"
                @click="downloadReport(report)"
              >
                <el-icon><Download /></el-icon>下载
              </el-button>
              <el-button
                v-if="canEditOrDelete(report)"
                link
                type="primary"
                :disabled="actionBusy"
                @click="openEdit(report)"
              >
                <el-icon><Edit /></el-icon>编辑
              </el-button>
              <el-button
                v-if="canEditOrDelete(report)"
                link
                type="danger"
                :disabled="actionBusy"
                @click="deleteSingleReport(report)"
              >
                <el-icon><Delete /></el-icon>删除
              </el-button>
            </footer>
          </article>
          <UiEmptyState v-if="!loading && reports.length === 0" description="暂无汇报材料" />
        </div>

        <!-- 分页 -->
        <div class="dm-table-footer">
          <span>共 {{ total }} 条</span>
          <UiPagination
            :page="currentPage"
            :page-size="pageSize"
            :total="total"
            :page-sizes="[20, 50, 100]"
            @update:page="handlePageChange"
            @update:page-size="handleSizeChange"
          />
        </div>
      </el-tab-pane>

      <!-- 回收站Tab（仅管理员可见） -->
      <el-tab-pane v-if="hasManagePermission" label="回收站" name="recycle">
        <!-- 回收站工具栏 -->
        <UiToolbar>
          <el-select
            v-model="filterProjectId"
            placeholder="选择项目"
            clearable
            filterable
            style="width: 200px"
          >
            <el-option
              v-for="project in projectOptions"
              :key="project.id"
              :label="project.project_name"
              :value="project.id"
            />
          </el-select>
          <el-select
            v-model="filterReportPeriod"
            placeholder="汇报周期"
            clearable
            style="width: 140px"
          >
            <el-option
              v-for="option in reportPeriodOptions"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
          <el-input
            v-model="filterKeyword"
            clearable
            placeholder="搜索资料名称或关键字"
            style="width: 240px"
            @keyup.enter="handleSearch"
          >
            <template #prefix><el-icon><Search /></el-icon></template>
          </el-input>
          <template #actions>
            <el-button :disabled="recycleLoading" @click="handleRefresh">
              <el-icon><Refresh /></el-icon>刷新
            </el-button>
            <el-button type="primary" :disabled="recycleLoading" @click="handleSearch">
              <el-icon><Search /></el-icon>查询
            </el-button>
            <el-button :disabled="recycleLoading" @click="handleReset">重置</el-button>
            <el-button
              v-if="recycleSelectedIds.length > 0"
              type="success"
              plain
              :disabled="actionBusy"
              @click="restoreReports"
            >
              <el-icon><FolderOpened /></el-icon>恢复 ({{ recycleSelectedIds.length }})
            </el-button>
            <el-button
              v-if="recycleSelectedIds.length > 0"
              type="danger"
              plain
              :disabled="actionBusy"
              @click="purgeReports"
            >
              <el-icon><Delete /></el-icon>彻底销毁 ({{ recycleSelectedIds.length }})
            </el-button>
          </template>
        </UiToolbar>

        <!-- 回收站表格 -->
        <UiDataTable
          class="dm-desktop-table"
          :data="recycleReports"
          :loading="recycleLoading"
          row-key="id"
          border
          empty-text="回收站为空"
          @selection-change="onRecycleSelectionChange"
        >
          <el-table-column type="selection" width="46" />
          <el-table-column prop="id" label="ID" width="80" show-overflow-tooltip />
          <el-table-column prop="project_name" label="所属项目" min-width="150" show-overflow-tooltip />
          <el-table-column label="汇报周期" width="120">
            <template #default="{ row }">
              {{ formatReportPeriod(row.report_period) }}
            </template>
          </el-table-column>
          <el-table-column prop="asset_name" label="资料名称" min-width="200" show-overflow-tooltip />
          <el-table-column label="汇报日期" width="120">
            <template #default="{ row }">
              {{ row.report_date || '—' }}
            </template>
          </el-table-column>
          <el-table-column label="删除时间" width="160">
            <template #default="{ row }">
              {{ row.deleted_at || '—' }}
            </template>
          </el-table-column>
          <el-table-column label="操作" width="160" fixed="right">
            <template #default="{ row }">
              <div class="dm-table-actions">
                <el-button
                  link
                  type="success"
                  :disabled="actionBusy"
                  @click="restoreSingleReport(row)"
                >
                  <el-icon><FolderOpened /></el-icon>恢复
                </el-button>
                <el-button
                  link
                  type="danger"
                  :disabled="actionBusy"
                  @click="purgeSingleReport(row)"
                >
                  <el-icon><Delete /></el-icon>彻底销毁
                </el-button>
              </div>
            </template>
          </el-table-column>
        </UiDataTable>

        <!-- 回收站分页 -->
        <div class="dm-table-footer">
          <span>共 {{ recycleTotal }} 条</span>
          <UiPagination
            :page="recycleCurrentPage"
            :page-size="recyclePageSize"
            :total="recycleTotal"
            :page-sizes="[20, 50, 100]"
            @update:page="handleRecyclePageChange"
            @update:page-size="handleRecycleSizeChange"
          />
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- 单条上传抽屉 -->
    <UiFormDrawer
      v-model="uploadDrawerOpen"
      :title="uploadType === 'single' ? '上传汇报材料' : '批量上传汇报材料'"
      :loading="uploadSaving"
      @submit="uploadType === 'single' ? saveSingleUpload() : saveBatchUpload()"
    >
      <el-form label-position="top">
        <el-form-item label="所属项目" required>
          <el-select
            v-model="uploadProjectId"
            placeholder="请选择项目"
            filterable
            style="width: 100%"
          >
            <el-option
              v-for="project in projectOptions"
              :key="project.id"
              :label="project.project_name"
              :value="project.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="汇报周期" required>
          <el-select
            v-model="uploadReportPeriod"
            placeholder="请选择汇报周期"
            style="width: 100%"
          >
            <el-option
              v-for="option in reportPeriodOptions"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
        </el-form-item>

        <!-- 单条上传特有字段 -->
        <template v-if="uploadType === 'single'">
          <el-form-item label="资料名称" required>
            <el-input v-model="uploadReportName" disabled placeholder="系统自动填充文件名" />
          </el-form-item>
          <el-form-item label="汇报日期">
            <el-date-picker
              v-model="uploadReportDate"
              type="date"
              placeholder="选择日期"
              value-format="YYYY-MM-DD"
              style="width: 100%"
            />
          </el-form-item>
          <el-form-item label="关键字索引">
            <div style="display: flex; flex-wrap: wrap; gap: 6px; margin-bottom: 6px;">
              <el-tag
                v-for="(kw, idx) in (uploadKeywords ? uploadKeywords.split(',').map(s => s.trim()).filter(Boolean) : [])"
                :key="idx"
                closable
                @close="removeKeyword(idx)"
              >{{ kw }}</el-tag>
            </div>
            <el-input v-model="newKeyword" placeholder="输入关键字后回车添加" @keyup.enter="addKeyword" @blur="addKeyword" />
          </el-form-item>
          <el-form-item label="MD5">
            <el-input v-model="uploadMd5" placeholder="系统自动计算" disabled />
          </el-form-item>
        </template>

        <!-- 文件上传 -->
        <el-form-item :label="uploadType === 'single' ? '源文件' : '源文件（多选）'" required>
          <el-upload
            v-if="uploadType === 'single'"
            ref="singleUploadRef"
            :auto-upload="false"
            :limit="1"
            drag
            style="width: 100%"
            @change="onUploadFileChange"
            @exceed="onUploadExceed"
            @remove="onUploadRemove"
          >
            <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
            <div class="el-upload__text">拖拽文件到此处，或 <em>点击选择</em></div>
          </el-upload>
          <el-upload
            v-else
            :auto-upload="false"
            :multiple="true"
            drag
            style="width: 100%"
            @change="(file: UploadFile) => onBatchFileChange([...uploadFiles, file.raw!].filter(Boolean) as File[])"
          >
            <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
            <div class="el-upload__text">拖拽文件到此处，或 <em>点击选择多个文件</em></div>
          </el-upload>
        </el-form-item>

        <!-- 批量上传提示 -->
        <el-alert
          v-if="uploadType === 'batch'"
          title="批量上传说明"
          type="info"
          :closable="false"
          show-icon
        >
          <template #default>
            <p>1. 选择多个文件后，系统将自动读取文件名作为资料名称。</p>
            <p>2. 汇报日期和关键字索引需要在上传后逐条编辑补充。</p>
            <p>3. 系统将自动计算文件MD5，防止重复提交。</p>
          </template>
        </el-alert>
      </el-form>
    </UiFormDrawer>

    <!-- 编辑抽屉 -->
    <UiFormDrawer
      v-model="editDrawerOpen"
      title="编辑汇报材料"
      :loading="editSaving"
      @submit="saveEdit"
    >
      <el-form label-position="top">
        <el-form-item label="ID">
          <el-input :model-value="editId" disabled />
        </el-form-item>
        <el-form-item label="所属项目">
          <el-select
            v-model="editProjectId"
            placeholder="请选择项目"
            filterable
            style="width: 100%"
          >
            <el-option
              v-for="project in projectOptions"
              :key="project.id"
              :label="project.project_name"
              :value="project.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="汇报周期">
          <el-select
            v-model="editReportPeriod"
            placeholder="请选择汇报周期"
            style="width: 100%"
          >
            <el-option
              v-for="option in reportPeriodOptions"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="资料名称" required>
          <el-input v-model="editReportName" disabled placeholder="资料名称不可修改" />
        </el-form-item>
        <el-form-item label="汇报日期">
          <el-date-picker
            v-model="editReportDate"
            type="date"
            placeholder="选择日期"
            value-format="YYYY-MM-DD"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="关键字索引">
          <div style="display: flex; flex-wrap: wrap; gap: 6px; margin-bottom: 6px;">
            <el-tag
              v-for="(kw, idx) in (editKeywords ? editKeywords.split(',').map(s => s.trim()).filter(Boolean) : [])"
              :key="idx"
              closable
              @close="removeEditKeyword(idx)"
            >{{ kw }}</el-tag>
          </div>
          <el-input v-model="newEditKeyword" placeholder="输入关键字后回车添加" @keyup.enter="addEditKeyword" @blur="addEditKeyword" />
        </el-form-item>
        <el-form-item label="MD5">
          <el-input v-model="editMd5" placeholder="系统自动计算" disabled />
        </el-form-item>
      </el-form>
    </UiFormDrawer>

    <!-- 批量编辑元数据对话框 -->
    <el-dialog
      v-model="batchEditDialogVisible"
      title="批量编辑元数据"
      width="800px"
      :close-on-click-modal="false"
    >
      <div class="batch-edit-hint">
        <el-alert
          type="info"
          :closable="false"
          show-icon
        >
          <template #default>
            <p>请为批量上传的文件补充汇报日期和关键字索引。</p>
            <p>汇报日期格式：YYYY-MM-DD，关键字用英文逗号分隔。</p>
          </template>
        </el-alert>
      </div>

      <el-table :data="batchEditItems" border style="width: 100%; margin-top: 16px;">
        <el-table-column prop="reportName" label="资料名称" min-width="200" show-overflow-tooltip />
        <el-table-column label="汇报日期" width="180">
          <template #default="{ row }">
            <el-date-picker
              v-model="row.reportDate"
              type="date"
              placeholder="选择日期"
              value-format="YYYY-MM-DD"
              style="width: 100%"
            />
          </template>
        </el-table-column>
        <el-table-column label="关键字索引" min-width="200">
          <template #default="{ row }">
            <el-input v-model="row.keywords" placeholder="多个关键字用英文逗号分隔" />
          </template>
        </el-table-column>
      </el-table>

      <template #footer>
        <el-button @click="batchEditDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveBatchEdit">保存</el-button>
      </template>
    </el-dialog>

    <!-- 文件预览对话框 -->
    <UiFilePreview
      v-model="filePreviewVisible"
      :url="filePreviewUrl"
      :file-name="filePreviewName"
    />
  </section>
</template>

<style scoped>
.dm-table-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.batch-edit-hint {
  margin-bottom: 16px;
}

.batch-edit-hint p {
  margin: 4px 0;
}

.dm-mobile-list article {
  margin-bottom: 12px;
}

.dm-mobile-list footer {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.dm-mobile-list dt {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-bottom: 4px;
}

.dm-mobile-list dd {
  margin: 0;
  font-size: 14px;
}
</style>
