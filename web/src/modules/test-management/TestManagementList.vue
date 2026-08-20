<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { Delete, Edit, Plus, Refresh, Search } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { useRoute } from 'vue-router'
import UiDataTable from '../../components/ui/UiDataTable.vue'
import UiEmptyState from '../../components/ui/UiEmptyState.vue'
import UiFormDrawer from '../../components/ui/UiFormDrawer.vue'
import UiPageHeader from '../../components/ui/UiPageHeader.vue'
import UiToolbar from '../../components/ui/UiToolbar.vue'
import { resolveTestManagementPage } from './catalog'
import './test-management.css'

interface PlaceholderRecord {
  id: number
  name: string
  description: string
  updatedAt: string
}

const route = useRoute()
const pageInfo = computed(() => resolveTestManagementPage(String(route.params.domain ?? ''), String(route.params.section ?? '')))
const records = ref<PlaceholderRecord[]>([])
const keyword = ref('')
const page = ref(1)
const pageSize = ref(10)
const refreshing = ref(false)
const drawerOpen = ref(false)
const editingId = ref<number | null>(null)
const formRef = ref<FormInstance>()
const form = reactive({ name: '', description: '' })
const rules: FormRules = { name: [{ required: true, message: '请输入名称', trigger: 'blur' }] }

const filtered = computed(() => {
  const query = keyword.value.trim().toLowerCase()
  return query
    ? records.value.filter(item => `${item.name}${item.description}`.toLowerCase().includes(query))
    : records.value
})
const rows = computed(() => filtered.value.slice((page.value - 1) * pageSize.value, page.value * pageSize.value))
const drawerTitle = computed(() => `${editingId.value === null ? '新增' : '编辑'}${pageInfo.value?.title ?? '记录'}`)

watch([keyword, pageSize], () => { page.value = 1 })

function resetForm() {
  editingId.value = null
  form.name = ''
  form.description = ''
  formRef.value?.clearValidate()
}

function openCreate() {
  resetForm()
  drawerOpen.value = true
}

function openEdit(record: PlaceholderRecord) {
  editingId.value = record.id
  form.name = record.name
  form.description = record.description
  drawerOpen.value = true
}

async function save() {
  if (!await formRef.value?.validate().catch(() => false)) return
  const updatedAt = new Intl.DateTimeFormat('zh-CN', { dateStyle: 'medium', timeStyle: 'short', hour12: false }).format(new Date())
  if (editingId.value === null) {
    records.value.unshift({ id: Date.now(), name: form.name.trim(), description: form.description.trim(), updatedAt })
    ElMessage.success('已新增临时记录')
  } else {
    const record = records.value.find(item => item.id === editingId.value)
    if (record) Object.assign(record, { name: form.name.trim(), description: form.description.trim(), updatedAt })
    ElMessage.success('已保存修改')
  }
  drawerOpen.value = false
}

async function remove(record: PlaceholderRecord) {
  const confirmed = await ElMessageBox.confirm(`确认删除“${record.name}”？当前为模块骨架，操作仅影响本次页面会话。`, '删除确认', {
    type: 'warning',
    confirmButtonText: '删除',
    cancelButtonText: '取消'
  }).then(() => true).catch(() => false)
  if (!confirmed) return
  records.value = records.value.filter(item => item.id !== record.id)
  if (page.value > 1 && !rows.value.length) page.value -= 1
  ElMessage.success('已删除临时记录')
}

async function refresh() {
  refreshing.value = true
  await new Promise(resolve => window.setTimeout(resolve, 300))
  refreshing.value = false
  ElMessage.success('列表已刷新')
}

function clearSearch() {
  keyword.value = ''
}
</script>

<template>
  <section v-if="pageInfo" class="test-management-page">
    <UiPageHeader
      eyebrow="测试管理"
      :title="pageInfo.title"
      :description="`${pageInfo.domain} · 当前为模块骨架，数据仅保存在本次页面会话中。`"
    >
      <template #actions>
        <el-button type="primary" @click="openCreate"><el-icon><Plus /></el-icon>新增</el-button>
      </template>
    </UiPageHeader>

    <UiToolbar>
      <el-input v-model="keyword" clearable class="test-management-search" placeholder="搜索名称或说明" @keyup.enter="page = 1">
        <template #prefix><el-icon><Search /></el-icon></template>
      </el-input>
      <template #actions>
        <el-tag type="info" effect="plain">临时数据</el-tag>
        <el-tooltip content="刷新列表">
          <el-button circle :loading="refreshing" aria-label="刷新列表" @click="refresh"><el-icon><Refresh /></el-icon></el-button>
        </el-tooltip>
      </template>
    </UiToolbar>

    <UiDataTable class="test-management-table" :data="rows" row-key="id" border>
      <el-table-column prop="name" label="名称" min-width="220" />
      <el-table-column prop="description" label="说明" min-width="280"><template #default="scope"><span class="test-management-muted">{{ scope.row.description || '—' }}</span></template></el-table-column>
      <el-table-column prop="updatedAt" label="更新时间" width="180" />
      <el-table-column label="操作" width="150" fixed="right"><template #default="scope">
        <el-button link type="primary" @click="openEdit(scope.row)"><el-icon><Edit /></el-icon>编辑</el-button>
        <el-button link type="danger" @click="remove(scope.row)"><el-icon><Delete /></el-icon>删除</el-button>
      </template></el-table-column>
    </UiDataTable>

    <div class="test-management-mobile-list">
      <article v-for="record in rows" :key="record.id">
        <header><strong>{{ record.name }}</strong><small>{{ record.updatedAt }}</small></header>
        <p>{{ record.description || '暂无说明' }}</p>
        <footer>
          <el-button link type="primary" @click="openEdit(record)"><el-icon><Edit /></el-icon>编辑</el-button>
          <el-button link type="danger" @click="remove(record)"><el-icon><Delete /></el-icon>删除</el-button>
        </footer>
      </article>
    </div>

    <UiEmptyState
      v-if="!rows.length"
      :title="keyword ? '没有匹配的记录' : `暂无${pageInfo.title}数据`"
      :description="keyword ? '请调整关键词后重试。' : '这是通用空白列表，可先新增一条临时记录体验交互。'"
    >
      <template #action>
        <el-button v-if="keyword" @click="clearSearch">清空搜索</el-button>
        <el-button v-else type="primary" @click="openCreate"><el-icon><Plus /></el-icon>新增记录</el-button>
      </template>
    </UiEmptyState>

    <div v-if="filtered.length > pageSize" class="test-management-pagination">
      <el-pagination v-model:current-page="page" v-model:page-size="pageSize" :total="filtered.length" :page-sizes="[10, 20, 50]" layout="total, sizes, prev, pager, next" />
    </div>

    <UiFormDrawer v-model="drawerOpen" :title="drawerTitle" width="min(520px, 92vw)" @submit="save">
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <el-form-item label="名称" prop="name"><el-input v-model="form.name" maxlength="100" show-word-limit placeholder="请输入名称" /></el-form-item>
        <el-form-item label="说明" prop="description"><el-input v-model="form.description" type="textarea" :rows="5" maxlength="500" show-word-limit placeholder="请输入说明（选填）" /></el-form-item>
      </el-form>
    </UiFormDrawer>
  </section>

  <el-result v-else icon="warning" title="页面配置不存在" sub-title="请从测试管理菜单重新进入。">
    <template #extra><router-link to="/dashboard"><el-button type="primary">返回首页</el-button></router-link></template>
  </el-result>
</template>
