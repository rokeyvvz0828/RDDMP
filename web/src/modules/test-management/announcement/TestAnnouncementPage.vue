<!--
文件：web/src/modules/test-management/announcement/TestAnnouncementPage.vue
说明：测试公告板页面或交互组件。
用途：承载用户可见的加载、空、失败、提交和交互状态。
作者：hengguan
-->
<script setup lang="ts">
// 关键逻辑：页面只消费现有全局项目上下文；当前测试大类、项目和实体选择共同决定请求范围，前端显隐不替代服务端校验。
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, shallowRef, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Bottom, Delete, Download, Edit, List, Reading, Top, View } from '@element-plus/icons-vue'
import { Editor as WangEditor, Toolbar } from '@wangeditor/editor-for-vue'
import '@wangeditor/editor/dist/css/style.css'
import { useProjectContextStore } from '../../../stores/project-context'
import UiDataTable from '../../../components/ui/UiDataTable.vue'
import UiEmptyState from '../../../components/ui/UiEmptyState.vue'
import UiFilePreview from '../../../components/ui/UiFilePreview.vue'
import UiPageHeader from '../../../components/ui/UiPageHeader.vue'
import { getAttachmentDownload, getAttachmentPreview, uploadAttachment } from '../../../api/attachments'
import { announcementDetail, currentAnnouncements, deleteAnnouncement, listAnnouncementProjects, listAnnouncements, pinAnnouncement, saveAnnouncement, type TestAnnouncement, type TestAnnouncementAttachment, type TestDomain, type TestProjectOption } from '../api'
import './announcement.css'
import './announcement-editor.css'

type AnnouncementForm = { id?: number; title: string; content_html: string; pinned: boolean; attachment_ids: number[]; inline_attachment_ids: number[]; attachments: TestAnnouncementAttachment[] }

const route = useRoute()
const context = useProjectContextStore()
const domain = computed(() => String(route.params.domain) as TestDomain)
const domainName = computed(() => ({ 'application-assembly': '应用组装测试', 'user-testing': '用户测试', 'non-functional': '非功能测试', security: '安全测试' }[domain.value] || '测试管理'))
const projects = ref<TestProjectOption[]>([])
const tab = ref<'current' | 'manage'>('current')
const current = ref<TestAnnouncement[]>([])
const rows = ref<TestAnnouncement[]>([])
const total = ref(0)
const page = ref(1)
const loading = ref(false)
const saving = ref(false)
const manageAllowed = ref(true)
const editorVisible = ref(false)
const previewVisible = ref(false)
const previewUrl = ref<string | null>(null)
const previewName = ref('附件预览')
const dirty = ref(false)
const hydratingEditor = ref(false)
const wangEditor = shallowRef<any>()
const editorHtml = ref('')
const filters = reactive({ keyword: '', publisher: '', from: '', to: '' })
const form = reactive<AnnouncementForm>({ title: '', content_html: '', pinned: false, attachment_ids: [], inline_attachment_ids: [], attachments: [] })
const projectId = computed(() => projects.value.find((item) => item.project_code === context.currentRef)?.id)
const hasProject = computed(() => Boolean(projectId.value))
const toolbarConfig = { toolbarKeys: ['headerSelect', 'blockquote', '|', 'bold', 'underline', 'italic', 'through', 'sup', 'sub', 'clearStyle', '|', 'color', 'bgColor', 'fontSize', 'fontFamily', 'lineHeight', '|', 'bulletedList', 'numberedList', 'justifyLeft', 'justifyCenter', 'justifyRight', 'justifyJustify', 'indent', 'delIndent', '|', 'insertLink', 'insertTable', 'uploadImage', 'divider', 'codeBlock', '|', 'undo', 'redo', 'fullScreen'] }
const editorConfig = { placeholder: '请输入公告正文，支持标题、颜色、字体、排版、表格、链接和图片…', MENU_CONF: { uploadImage: { async customUpload(file: File, insert: (url: string, alt?: string, href?: string) => void) { await upload(file, true, insert) } } } }

function requestError(error: any) { ElMessage.error(error?.response?.data?.message || '请求失败，请稍后重试') }
function plainText(value?: string) { if (!value) return ''; const container = document.createElement('div'); container.innerHTML = value; return (container.textContent || container.innerText || '').replace(/\s+/g, ' ').trim() }
function summary(item: TestAnnouncement) { return plainText(item.summary || item.content_html) || '—' }
function fileAttachments(item?: Pick<TestAnnouncement, 'attachments'>) { return (item?.attachments || []).filter((attachment) => attachment.attachmentType === 'FILE') }
function formatFileSize(size?: number) { if (!size) return '0 KB'; return size < 1024 * 1024 ? `${Math.max(1, Math.round(size / 1024))} KB` : `${(size / 1024 / 1024).toFixed(1)} MB` }
function formatTime(value?: string) { if (!value) return '—'; const date = new Date(value.replace(' ', 'T')); if (Number.isNaN(date.getTime())) return value; const pad = (number: number) => String(number).padStart(2, '0'); return `${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}` }

async function load() {
  if (!projectId.value) return
  loading.value = true
  try { current.value = (await currentAnnouncements(domain.value, projectId.value)).data.data || []; if (tab.value === 'manage') await loadList() } catch (error) { requestError(error) } finally { loading.value = false }
}
async function loadList() {
  if (!projectId.value) return
  try { const result = (await listAnnouncements(domain.value, { projectId: projectId.value, page: page.value, size: 20, ...filters })).data.data; rows.value = result.records || []; total.value = result.total || 0; manageAllowed.value = true } catch (error: any) { if (error?.response?.status === 403) { manageAllowed.value = false; tab.value = 'current'; return }; requestError(error) }
}
function fillForm(item?: TestAnnouncement) {
  Object.assign(form, { id: item?.id, title: item?.title || '', content_html: item?.content_html || '', pinned: Boolean(item?.pinned), attachment_ids: fileAttachments(item).map((attachment) => attachment.id), inline_attachment_ids: (item?.attachments || []).filter((attachment) => attachment.attachmentType === 'INLINE').map((attachment) => attachment.id), attachments: [...(item?.attachments || [])] })
  editorHtml.value = form.content_html
}
function openNew() { fillForm(); dirty.value = false; editorVisible.value = true }
async function edit(item: TestAnnouncement) {
  if (!projectId.value) return
  try { fillForm((await announcementDetail(domain.value, projectId.value, item.id)).data.data); dirty.value = false; editorVisible.value = true } catch (error) { requestError(error) }
}
async function hydrate(instance = wangEditor.value) { if (!instance) return; hydratingEditor.value = true; editorHtml.value = form.content_html; instance.setHtml(form.content_html || ''); await nextTick(); hydratingEditor.value = false; dirty.value = false }
function created(instance: any) { wangEditor.value = instance; void hydrate(instance) }
function changed(instance: any) { const value = instance.getHtml(); editorHtml.value = value; form.content_html = value; if (!hydratingEditor.value) dirty.value = true }
async function save() {
  if (!projectId.value) return
  if (!form.title.trim() || !plainText(form.content_html)) { ElMessage.warning('请填写公告标题和正文'); return }
  saving.value = true
  try { await saveAnnouncement(domain.value, projectId.value, { id: form.id, title: form.title.trim(), content_html: form.content_html, pinned: form.pinned, attachment_ids: form.attachment_ids, inline_attachment_ids: form.inline_attachment_ids }); editorVisible.value = false; dirty.value = false; await load(); ElMessage.success(form.id ? '公告已更新' : '公告已发布') } catch (error) { requestError(error) } finally { saving.value = false }
}
async function toggle(item: TestAnnouncement) { if (!projectId.value) return; try { await pinAnnouncement(domain.value, projectId.value, item.id, !item.pinned); await load(); ElMessage.success(item.pinned ? '已取消置顶' : '已置顶') } catch (error) { requestError(error) } }
async function remove(item: TestAnnouncement) {
  if (!projectId.value) return
  try { await ElMessageBox.confirm(`确认删除公告「${item.title}」？删除后其附件将一并删除且不可恢复。`, '删除确认', { type: 'warning', confirmButtonText: '确认删除', confirmButtonClass: 'el-button--danger' }); await deleteAnnouncement(domain.value, projectId.value, item.id); await load(); ElMessage.success('公告已删除') } catch (error: any) { if (error !== 'cancel' && error !== 'close') requestError(error) }
}
async function upload(file: File, inline = false, insert?: (url: string, alt?: string, href?: string) => void) {
  if (file.size > 50 * 1024 * 1024) { ElMessage.warning('单个附件不能超过 50 MB'); return }
  try { const attachment = (await uploadAttachment(file)).data.data; (inline ? form.inline_attachment_ids : form.attachment_ids).push(attachment.id); form.attachments.push({ id: attachment.id, fileName: file.name, fileSize: file.size, attachmentType: inline ? 'INLINE' : 'FILE' }); if (inline && insert) { const preview = (await getAttachmentPreview(attachment.id)).data.data.previewUrl; insert(preview, file.name, preview) }; dirty.value = true; ElMessage.success(inline ? '图片已插入正文' : '附件已添加') } catch (error) { requestError(error) }
}
function removeAttachment(attachment: TestAnnouncementAttachment) { const ids = attachment.attachmentType === 'INLINE' ? form.inline_attachment_ids : form.attachment_ids; const index = ids.indexOf(attachment.id); if (index >= 0) ids.splice(index, 1); form.attachments = form.attachments.filter((item) => item.id !== attachment.id); dirty.value = true }
async function preview(attachment: TestAnnouncementAttachment) { try { previewName.value = attachment.fileName; previewUrl.value = (await getAttachmentPreview(attachment.id)).data.data.previewUrl; previewVisible.value = true } catch (error) { requestError(error) } }
async function download(attachment: TestAnnouncementAttachment) { try { const url = (await getAttachmentDownload(attachment.id)).data.data.downloadUrl; const anchor = document.createElement('a'); anchor.href = url; anchor.download = attachment.fileName; anchor.rel = 'noopener'; anchor.target = '_blank'; document.body.appendChild(anchor); anchor.click(); anchor.remove() } catch (error) { requestError(error) } }
async function requestClose(done: () => void) { if (saving.value) return; if (dirty.value) try { await ElMessageBox.confirm('正文或附件尚未保存，确认关闭吗？', '未保存的修改', { type: 'warning' }) } catch { return }; done() }
function closed() { wangEditor.value?.destroy(); wangEditor.value = undefined; dirty.value = false }

watch(() => context.currentRef, () => { page.value = 1; void load() })
watch(tab, () => void load())
onMounted(async () => { await context.initialize(); try { projects.value = (await listAnnouncementProjects(domain.value)).data.data || []; await load() } catch (error) { requestError(error) } })
onBeforeUnmount(() => wangEditor.value?.destroy())
</script>

<template>
  <section class="announcement-page">
    <UiPageHeader eyebrow="测试管理" :title="`${domainName} · 测试公告板`" />
    <template v-if="hasProject">
      <nav class="announcement-module-nav" aria-label="测试公告板视图">
        <button type="button" :class="{ active: tab === 'current' }" @click="tab = 'current'"><el-icon><Reading /></el-icon><span>当前公告</span></button>
        <button v-if="manageAllowed" type="button" :class="{ active: tab === 'manage' }" @click="tab = 'manage'"><el-icon><List /></el-icon><span>公告管理</span></button>
      </nav>
      <section v-show="tab === 'current'" v-loading="loading" class="announcement-panel announcement-current">
          <UiEmptyState v-if="!current.length" title="暂无公告" description="当前项目尚未发布测试公告"><el-button v-if="manageAllowed" type="primary" @click="openNew">发布第一条公告</el-button></UiEmptyState>
          <article v-for="(item, index) in current" :key="item.id" class="announcement-card" :class="{ pinned: item.pinned }">
            <h3><el-tag v-if="item.pinned" type="primary">置顶</el-tag><el-tag v-else-if="index === 0">最新公告</el-tag><span>{{ item.title }}</span></h3>
            <div class="announcement-rich-content" v-html="item.content_html" />
            <section v-if="fileAttachments(item).length" class="announcement-attachments"><h4>附件（{{ fileAttachments(item).length }}）</h4><div v-for="attachment in fileAttachments(item)" :key="attachment.id" class="attachment-row"><span class="attachment-name">{{ attachment.fileName }}</span><span>{{ formatFileSize(attachment.fileSize) }}</span><span>{{ formatTime(attachment.createdAt) }}</span><div class="attachment-actions"><el-tooltip content="在线预览" placement="top"><el-button circle plain type="primary" aria-label="在线预览附件" @click="preview(attachment)"><el-icon><View /></el-icon></el-button></el-tooltip><el-tooltip content="下载附件" placement="top"><el-button circle plain type="primary" aria-label="下载附件" @click="download(attachment)"><el-icon><Download /></el-icon></el-button></el-tooltip></div></div></section>
            <div class="announcement-meta"><span>发布时间：{{ formatTime(item.published_at) }}</span><span v-if="item.last_edited_at">最后编辑：{{ formatTime(item.last_edited_at) }}</span></div>
          </article>
      </section>
      <section v-if="manageAllowed" v-show="tab === 'manage'" class="announcement-panel announcement-management">
        <div class="announce-toolbar"><el-input v-model="filters.keyword" clearable placeholder="输入标题关键字" @keyup.enter="page = 1; loadList()" /><el-input v-model="filters.publisher" clearable placeholder="发布人" @keyup.enter="page = 1; loadList()" /><el-date-picker v-model="filters.from" value-format="YYYY-MM-DD" type="date" placeholder="开始日期" /><el-date-picker v-model="filters.to" value-format="YYYY-MM-DD" type="date" placeholder="结束日期" /><el-button @click="page = 1; loadList()">查询</el-button><el-button @click="Object.assign(filters, { keyword: '', publisher: '', from: '', to: '' }); page = 1; loadList()">重置</el-button><el-button type="primary" @click="openNew">发布公告</el-button></div>
        <UiDataTable :data="rows" :loading="loading" row-key="id" border class="announcement-table">
          <el-table-column prop="title" label="标题" width="260" class-name="announcement-title-column" header-align="center" sortable resizable><template #default="{ row }"><el-tag v-if="row.pinned" type="primary" size="small">置顶</el-tag><span class="announcement-title-cell">{{ row.title }}</span></template></el-table-column>
          <el-table-column label="正文摘要" min-width="160" header-align="center"><template #default="{ row }"><p class="announcement-summary">{{ summary(row) }}</p></template></el-table-column>
          <el-table-column prop="publisher_name" label="发布人" width="84" header-align="center" align="center" sortable resizable><template #default="{ row }">{{ row.publisher_name || '—' }}</template></el-table-column>
          <el-table-column prop="published_at" label="发布时间" width="132" header-align="center" align="center" sortable resizable><template #default="{ row }">{{ formatTime(row.published_at) }}</template></el-table-column>
          <el-table-column prop="pinned" label="是否置顶" width="96" header-align="center" align="center" sortable resizable><template #default="{ row }"><el-tag :type="row.pinned ? 'primary' : 'info'">{{ row.pinned ? '置顶' : '否' }}</el-tag></template></el-table-column>
          <el-table-column prop="attachment_count" label="附件数" width="80" header-align="center" align="center" sortable resizable><template #default="{ row }">{{ row.attachment_count || 0 }}</template></el-table-column>
          <el-table-column label="操作" width="84" header-align="center" fixed="right" :resizable="false"><template #default="{ row }"><div class="announcement-table-actions"><el-tooltip content="编辑公告" placement="top"><el-button link type="primary" aria-label="编辑公告" @click="edit(row)"><el-icon><Edit /></el-icon></el-button></el-tooltip><el-tooltip :content="row.pinned ? '取消置顶' : '置顶公告'" placement="top"><el-button link type="primary" :aria-label="row.pinned ? '取消置顶' : '置顶公告'" @click="toggle(row)"><el-icon><Bottom v-if="row.pinned" /><Top v-else /></el-icon></el-button></el-tooltip><el-tooltip content="删除公告" placement="top"><el-button link type="danger" aria-label="删除公告" @click="remove(row)"><el-icon><Delete /></el-icon></el-button></el-tooltip></div></template></el-table-column>
          <template #footer><el-pagination v-model:current-page="page" :total="total" :page-size="20" layout="total, prev, pager, next" @current-change="loadList" /></template>
        </UiDataTable>
      </section>
    </template>
    <UiEmptyState v-else title="请先选择项目" description="请使用顶部全局项目选择器后查看公告内容" />
    <el-dialog v-model="editorVisible" width="920px" destroy-on-close :close-on-click-modal="false" :before-close="requestClose" @closed="closed">
      <template #header>{{ form.id ? '编辑公告' : '发布公告' }}</template>
      <el-form label-position="top" class="announcement-editor-form"><el-form-item label="公告标题" required><el-input v-model="form.title" maxlength="100" show-word-limit placeholder="请输入公告标题" @input="dirty = true" /></el-form-item><el-form-item label="正文" required><div class="wang-editor"><Toolbar :editor="wangEditor" :default-config="toolbarConfig" mode="default" /><WangEditor v-model="editorHtml" :default-config="editorConfig" mode="default" @on-created="created" @on-change="changed" /></div></el-form-item><el-form-item label="附件"><el-upload :show-file-list="false" :auto-upload="false" @change="(file: any) => upload(file.raw)"><el-button>上传附件</el-button></el-upload><p class="attachment-hint">单个附件不超过 50 MB；正文图片请使用编辑器工具栏上传。</p><div v-if="fileAttachments(form).length" class="editor-attachment-list"><div v-for="attachment in fileAttachments(form)" :key="attachment.id" class="attachment-row"><span class="attachment-name">{{ attachment.fileName }}</span><span>{{ formatFileSize(attachment.fileSize) }}</span><el-button link type="danger" @click="removeAttachment(attachment)">移除</el-button></div></div></el-form-item><el-form-item><el-checkbox v-model="form.pinned" @change="dirty = true">置顶公告</el-checkbox></el-form-item></el-form>
      <template #footer><el-button :disabled="saving" @click="requestClose(() => { editorVisible = false })">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存并发布</el-button></template>
    </el-dialog>
    <UiFilePreview v-model="previewVisible" :url="previewUrl" :file-name="previewName" />
  </section>
</template>
