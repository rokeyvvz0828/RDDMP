<!--
  用途：数迁基础资料 - 项目清单页（项目信息管理）
  说明：以表格展示项目（编号/名称/描述），支持增删改查；
        编号仅允许字母与数字、自动转大写、创建后不可修改；名称不允许重复且不超过 100 字符；
        视觉与交互对齐“用户管理”页：UiToolbar + UiDataTable + UiFormDrawer + Element Plus 反馈。
-->
<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Delete, Edit, Plus, Refresh, Search } from '@element-plus/icons-vue'
import UiDataTable from '../../../../components/ui/UiDataTable.vue'
import UiFormDrawer from '../../../../components/ui/UiFormDrawer.vue'
import UiToolbar from '../../../../components/ui/UiToolbar.vue'
import { apiErrorMessage } from '../../../../api/error'
import { createDataMigrationProject, deleteDataMigrationProject, listDataMigrationProjects, updateDataMigrationProject, type DataMigrationProject } from '../../../../api/data-migration'

const loading = ref(false)
const saving = ref(false)
const projects = ref<DataMigrationProject[]>([])
const keyword = ref('')
const drawerOpen = ref(false)
const editingId = ref<number | null>(null)
const formRef = ref<FormInstance>()

const form = reactive({ projectCode: '', projectName: '', description: '' })

const rules: FormRules = {
  projectCode: [
    { required: true, message: '请输入项目编号', trigger: 'blur' },
    { pattern: /^[A-Za-z0-9]+$/, message: '仅允许英文字母和数字', trigger: 'blur' }
  ],
  projectName: [
    { required: true, message: '请输入项目名称', trigger: 'blur' },
    { max: 100, message: '长度不能超过 100 个字符', trigger: 'blur' }
  ],
  description: [{ max: 500, message: '长度不能超过 500 个字符', trigger: 'blur' }]
}

function messageOf(error: unknown) {
  return error instanceof Error ? error.message : '操作失败，请稍后重试'
}

async function load() {
  loading.value = true
  try {
    projects.value = (await listDataMigrationProjects({ keyword: keyword.value || undefined })).data.data ?? []
  } catch (e) { ElMessage.error(messageOf(e)) }
  finally { loading.value = false }
}

function onCodeInput() {
  form.projectCode = form.projectCode.toUpperCase()
}

function openCreate() {
  editingId.value = null
  form.projectCode = ''
  form.projectName = ''
  form.description = ''
  drawerOpen.value = true
}

function openEdit(row: DataMigrationProject) {
  editingId.value = row.id
  form.projectCode = String(row.project_code || '')
  form.projectName = String(row.project_name || '')
  form.description = String(row.description ?? '')
  drawerOpen.value = true
}

async function save() {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    if (editingId.value) {
      await updateDataMigrationProject(editingId.value, { projectName: form.projectName.trim(), description: form.description.trim() })
      ElMessage.success('更新成功')
    } else {
      await createDataMigrationProject({ projectCode: form.projectCode.trim().toUpperCase(), projectName: form.projectName.trim(), description: form.description.trim() })
      ElMessage.success('创建成功')
    }
    drawerOpen.value = false
    await load()
  } catch (e) { ElMessage.error(apiErrorMessage(e, '保存失败，请检查字段和权限')) }
  finally { saving.value = false }
}

async function removeProject(row: DataMigrationProject) {
  try {
    await ElMessageBox.confirm(`删除项目「${row.project_name}」后将不再显示该项目，确认继续吗？`, '删除确认', { type: 'warning' })
    await deleteDataMigrationProject(row.id)
    ElMessage.success('删除成功')
    await load()
  } catch (error) {
    const action = (error as { action?: string }).action
    if (action !== 'cancel' && action !== 'close') ElMessage.error(apiErrorMessage(error, '删除失败'))
  }
}

onMounted(load)
</script>

<template>
  <section class="projects-page">
    <UiToolbar>
      <el-input v-model="keyword" clearable placeholder="搜索项目编号或名称" style="width: 240px" @keyup.enter="load">
        <template #prefix><el-icon><Search /></el-icon></template>
      </el-input>
      <template #actions>
        <el-button :disabled="loading" @click="load"><el-icon><Refresh /></el-icon>刷新</el-button>
        <el-button type="primary" :disabled="loading" @click="load"><el-icon><Search /></el-icon>查询</el-button>
        <el-button type="primary" @click="openCreate"><el-icon><Plus /></el-icon>新建项目</el-button>
      </template>
    </UiToolbar>

    <UiDataTable :data="projects" :loading="loading" row-key="id" border empty-text="暂无项目">
      <el-table-column prop="project_code" label="项目编号" min-width="150" />
      <el-table-column prop="project_name" label="项目名称" min-width="180" />
      <el-table-column prop="description" label="项目描述" min-width="220" show-overflow-tooltip>
        <template #default="scope">{{ scope.row.description || '-' }}</template>
      </el-table-column>
      <el-table-column label="操作" width="150" fixed="right">
        <template #default="scope">
          <el-button link type="primary" @click="openEdit(scope.row)"><el-icon><Edit /></el-icon>编辑</el-button>
          <el-button link type="danger" @click="removeProject(scope.row)"><el-icon><Delete /></el-icon>删除</el-button>
        </template>
      </el-table-column>
    </UiDataTable>

    <UiFormDrawer v-model="drawerOpen" :title="editingId ? '编辑项目' : '新建项目'" :loading="saving" @submit="save">
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <el-form-item label="项目编号" prop="projectCode">
          <el-input v-model="form.projectCode" :disabled="editingId != null" placeholder="仅限字母和数字，自动转为大写" maxlength="64" @input="onCodeInput" />
        </el-form-item>
        <el-form-item label="项目名称" prop="projectName">
          <el-input v-model="form.projectName" placeholder="不能与已有项目重复" maxlength="100" show-word-limit />
        </el-form-item>
        <el-form-item label="项目描述" prop="description">
          <el-input v-model="form.description" type="textarea" :rows="4" placeholder="选填" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
    </UiFormDrawer>
  </section>
</template>

<style scoped>
.projects-page { min-width: 0; }
</style>
