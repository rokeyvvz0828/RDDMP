<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { Box, Delete, Document, Edit, Plus, Refresh, Upload, View } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox, type UploadFile } from 'element-plus'
import { deleteFilePreview, getFilePreviewCapabilities, uploadFilePreview, type FilePreviewCapabilities, type FilePreviewResult } from '../api/file-preview'
import { apiErrorMessage } from '../api/error'
import UiAvatarUpload from '../components/ui/UiAvatarUpload.vue'
import UiDataTable from '../components/ui/UiDataTable.vue'
import UiEmptyState from '../components/ui/UiEmptyState.vue'
import UiFormDrawer from '../components/ui/UiFormDrawer.vue'
import UiFilePreview from '../components/ui/UiFilePreview.vue'
import UiMenuIcon from '../components/ui/UiMenuIcon.vue'
import UiOrgTree from '../components/ui/UiOrgTree.vue'
import UiOrgTreeSelect from '../components/ui/UiOrgTreeSelect.vue'
import UiStatusTag from '../components/ui/UiStatusTag.vue'
import UiToolbar from '../components/ui/UiToolbar.vue'
import UiUserIdentity from '../components/ui/UiUserIdentity.vue'
import type { OrgTreeNode, UserProfile } from '../types/system'

const drawerOpen = ref(false)
const selectedOrg = ref<number | null>(1)
const avatarFile = ref<File | null>(null)
const avatarPreview = ref<string | null>(null)
const previewCapabilities = ref<FilePreviewCapabilities | null>(null)
const previewCapabilitiesLoading = ref(true)
const previewFile = ref<File | null>(null)
const previewResult = ref<FilePreviewResult | null>(null)
const previewDialogOpen = ref(false)
const previewSubmitting = ref(false)
const previewError = ref('')
const demoUser: UserProfile = { id: 1, username: 'admin', displayName: '系统管理员', orgId: 1, orgName: '平台总部', avatarUrl: null, roles: ['超级管理员'], status: 1 }
const demoRows = [{ id: 1, name: '用户身份组件', status: 1 }, { id: 2, name: '组织树组件', status: 1 }]
const demoOrgs: OrgTreeNode[] = [{ id: 1, parentId: 0, orgCode: 'ROOT', orgName: '平台总部', sortNo: 0, status: 1, children: [{ id: 2, parentId: 1, orgCode: 'TECH', orgName: '技术中心', sortNo: 1, status: 1, children: [], users: [] }], users: [demoUser as never] }]
function submit() { drawerOpen.value = false }

const previewAccept = computed(() => previewCapabilities.value?.allowedExtensions.map(value => `.${value}`).join(',') || '')
const previewLimit = computed(() => formatFileSize(previewCapabilities.value?.maxFileSizeBytes || 0))

onMounted(loadPreviewCapabilities)

async function loadPreviewCapabilities() {
  previewCapabilitiesLoading.value = true
  previewError.value = ''
  try {
    previewCapabilities.value = (await getFilePreviewCapabilities()).data.data
  } catch (error) {
    previewError.value = responseMessage(error, '文件预览配置加载失败')
  } finally {
    previewCapabilitiesLoading.value = false
  }
}

function selectPreviewFile(uploadFile: UploadFile) {
  const file = uploadFile.raw
  if (!file || !previewCapabilities.value) return
  previewError.value = ''
  const extension = file.name.includes('.') ? file.name.split('.').pop()?.toLowerCase() || '' : ''
  if (!previewCapabilities.value.allowedExtensions.includes(extension)) {
    previewFile.value = null
    previewError.value = '该文件类型不在预览白名单中'
    return
  }
  if (file.size > previewCapabilities.value.maxFileSizeBytes) {
    previewFile.value = null
    previewError.value = `文件大小不能超过 ${previewLimit.value}`
    return
  }
  previewFile.value = file
}

async function submitPreview() {
  if (!previewFile.value || previewSubmitting.value) return
  previewSubmitting.value = true
  previewError.value = ''
  try {
    previewResult.value = (await uploadFilePreview(previewFile.value)).data.data
    previewDialogOpen.value = true
  } catch (error) {
    previewError.value = responseMessage(error, '文件上传或预览地址生成失败')
  } finally {
    previewSubmitting.value = false
  }
}

async function removePreview() {
  if (!previewResult.value) return
  await ElMessageBox.confirm(`将删除临时文件“${previewResult.value.fileName}”，删除后不能继续预览。`, '删除临时文件', {
    confirmButtonText: '删除',
    cancelButtonText: '取消',
    type: 'warning'
  })
  try {
    await deleteFilePreview(previewResult.value.previewId)
    previewDialogOpen.value = false
    previewResult.value = null
    previewFile.value = null
    ElMessage.success('临时文件已删除')
  } catch (error) {
    previewError.value = responseMessage(error, '临时文件删除失败')
  }
}

function responseMessage(error: unknown, fallback: string) {
  const candidate = error as { response?: { data?: { message?: unknown } } }
  const message = candidate.response?.data?.message
  return typeof message === 'string' && message.trim() ? message : apiErrorMessage(error, fallback)
}

function formatFileSize(bytes: number) {
  if (bytes <= 0) return '-'
  if (bytes < 1024 * 1024) return `${Math.ceil(bytes / 1024)} KB`
  return `${Math.round(bytes / 1024 / 1024)} MB`
}
</script>

<template>
  <section class="component-showcase-page">
    <UiToolbar><el-input placeholder="示例查询条件" style="width:240px" /><template #actions><el-button><el-icon><Refresh /></el-icon>刷新</el-button><el-button type="primary">查询</el-button><el-button type="primary" @click="drawerOpen = true"><el-icon><Plus /></el-icon>打开表单抽屉</el-button></template></UiToolbar>
    <div class="showcase-grid">
      <el-card shadow="never" class="surface-card showcase-card"><template #header><div class="card-heading"><div><span class="panel-kicker">身份标识</span><h3>用户身份</h3></div></div></template><UiUserIdentity :user="demoUser" /><p class="showcase-note">头像和姓名横向排列，悬浮查看用户详情。</p><UiAvatarUpload v-model="avatarFile" v-model:preview-url="avatarPreview" /></el-card>
      <el-card shadow="never" class="surface-card showcase-card"><template #header><div class="card-heading"><div><span class="panel-kicker">状态与图标</span><h3>状态和图标</h3></div></div></template><div class="showcase-inline"><UiStatusTag :value="1" :labels="{ '1': '启用' }" /><UiStatusTag :value="0" :labels="{ '0': '停用' }" /><UiMenuIcon name="setting" /><UiMenuIcon name="user" /><el-icon><Box /></el-icon><el-icon><Edit /></el-icon></div><UiEmptyState title="空状态示例" description="没有数据时使用统一的空状态组件。" /></el-card>
      <el-card shadow="never" class="surface-card showcase-card showcase-card--wide"><template #header><div class="card-heading"><div><span class="panel-kicker">组织能力</span><h3>组织树和组织选择器</h3></div></div></template><div class="showcase-org-layout"><UiOrgTree :nodes="demoOrgs" :selected-id="selectedOrg" @select="selectedOrg = $event.id" /><div><el-form label-position="top"><el-form-item label="所属组织"><UiOrgTreeSelect v-model="selectedOrg" :nodes="demoOrgs" /></el-form-item></el-form><p class="showcase-note">组织树可接入节点级新增用户、新增下级组织和编辑操作。</p></div></div></el-card>
      <el-card shadow="never" class="surface-card showcase-card showcase-card--wide"><template #header><div class="card-heading"><div><span class="panel-kicker">数据表格</span><h3>数据表格</h3></div></div></template><UiDataTable :data="demoRows" row-key="id" border><el-table-column prop="name" label="组件名称" /><el-table-column label="状态"><template #default="scope"><UiStatusTag :value="scope.row.status" :labels="{ '1': '已启用' }" /></template></el-table-column><el-table-column label="操作"><template #default><el-button link type="primary"><el-icon><Edit /></el-icon>编辑</el-button></template></el-table-column></UiDataTable></el-card>
      <el-card shadow="never" class="surface-card showcase-card showcase-card--wide">
        <template #header><div class="card-heading"><div><span class="panel-kicker">文档能力</span><h3>文件在线预览</h3></div><el-tag v-if="previewCapabilities" :type="previewCapabilities.enabled ? 'success' : 'info'">{{ previewCapabilities.enabled ? '服务可用' : '未启用' }}</el-tag></div></template>
        <div v-loading="previewCapabilitiesLoading" class="file-preview-showcase">
          <el-alert v-if="previewError" :title="previewError" type="error" show-icon :closable="false" />
          <template v-if="previewCapabilities">
            <div class="file-preview-showcase__policy">
              <span>单文件上限 {{ previewLimit }}</span>
              <div class="file-preview-showcase__types">
                <el-tag v-for="extension in previewCapabilities.allowedExtensions.slice(0, 10)" :key="extension" size="small" effect="plain">{{ extension.toUpperCase() }}</el-tag>
                <span v-if="previewCapabilities.allowedExtensions.length > 10">+{{ previewCapabilities.allowedExtensions.length - 10 }}</span>
              </div>
            </div>
            <div v-if="previewCapabilities.enabled" class="file-preview-showcase__actions">
              <el-upload :auto-upload="false" :show-file-list="false" :accept="previewAccept" :disabled="Boolean(previewResult)" :on-change="selectPreviewFile">
                <el-button :icon="Upload" :disabled="Boolean(previewResult)">选择文件</el-button>
              </el-upload>
              <div v-if="previewFile" class="file-preview-showcase__file">
                <el-icon><Document /></el-icon>
                <div><strong>{{ previewFile.name }}</strong><span>{{ formatFileSize(previewFile.size) }}</span></div>
              </div>
              <div class="file-preview-showcase__commands">
                <el-button v-if="!previewResult" type="primary" :icon="View" :loading="previewSubmitting" :disabled="!previewFile" @click="submitPreview">上传并预览</el-button>
                <el-button v-else type="primary" :icon="View" @click="previewDialogOpen = true">打开预览</el-button>
                <el-tooltip v-if="previewResult" content="删除临时文件" placement="top"><el-button :icon="Delete" circle type="danger" plain aria-label="删除临时文件" @click="removePreview" /></el-tooltip>
              </div>
            </div>
            <el-empty v-else description="文件预览服务未启用"><el-button type="primary" :icon="Refresh" @click="loadPreviewCapabilities">重新检测</el-button></el-empty>
          </template>
        </div>
      </el-card>
    </div>
    <UiFormDrawer v-model="drawerOpen" title="表单抽屉示例" @submit="submit"><el-form label-position="top"><el-form-item label="组件名称"><el-input model-value="示例表单" /></el-form-item><el-form-item label="说明"><el-input type="textarea" model-value="统一的表单抽屉操作区。" /></el-form-item></el-form></UiFormDrawer>
    <UiFilePreview v-model="previewDialogOpen" :url="previewResult?.previewUrl || null" :file-name="previewResult?.fileName || '文件预览'" />
  </section>
</template>
