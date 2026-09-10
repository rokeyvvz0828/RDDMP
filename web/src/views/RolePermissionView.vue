<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, Edit, Plus, Refresh, Search, SwitchButton } from '@element-plus/icons-vue'
import UiDataTable from '../components/ui/UiDataTable.vue'
import UiFormDrawer from '../components/ui/UiFormDrawer.vue'
import UiPagination from '../components/ui/UiPagination.vue'
import UiStatusTag from '../components/ui/UiStatusTag.vue'
import { type UiTreeOption } from '../components/ui/UiTreeSelect.vue'
import UiToolbar from '../components/ui/UiToolbar.vue'
import { createPermission, deletePermission, getPermissionCatalog, listPermissions, updatePermission, updatePermissionStatus } from '../api/system'
import type { PermissionMenu, PermissionPayload, PermissionRecord } from '../types/system'
import { apiErrorMessage } from '../api/error'

type PermissionCatalogMenu = PermissionMenu & { status?: number }

const rows = ref<PermissionRecord[]>([])
const menus = ref<PermissionCatalogMenu[]>([])
const keyword = ref('')
const status = ref<number | undefined>()
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)
const loading = ref(false)
const loadError = ref('')
const saving = ref(false)
const drawerOpen = ref(false)
const editingId = ref<number | null>(null)
const form = reactive<PermissionPayload>({ menu_id: 0, action_code: '', permission_code: '', permission_name: '', status: 1 })

const menuTree = computed(() => {
  const nodes = new Map<number, PermissionMenu>()
  menus.value.forEach(menu => {
    if (Number(menu.status) === 0) return
    const id = Number(menu.id)
    if (!Number.isFinite(id)) return
    nodes.set(id, {
      ...menu,
      id,
      parent_id: Number(menu.parent_id || 0),
      sort_no: Number(menu.sort_no || 0),
      children: []
    })
  })
  const roots: PermissionMenu[] = []
  nodes.forEach(node => {
    const parent = nodes.get(node.parent_id)
    if (parent && parent.id !== node.id) parent.children!.push(node)
    else roots.push(node)
  })
  const sort = (items: PermissionMenu[]) => {
    items.sort((left, right) => left.sort_no - right.sort_no || left.id - right.id)
    items.forEach(item => sort(item.children || []))
  }
  sort(roots)
  return roots
})

const expandedMenuIds = computed(() => {
  const activeMenus = new Map<number, PermissionMenu>()
  menus.value.forEach(menu => {
    if (Number(menu.status) !== 0) activeMenus.set(Number(menu.id), menu)
  })
  const expanded: number[] = []
  const visited = new Set<number>()
  let menu = activeMenus.get(form.menu_id)
  while (menu) {
    const parentId = Number(menu.parent_id || 0)
    if (!parentId || visited.has(parentId)) break
    const parent = activeMenus.get(parentId)
    if (!parent) break
    expanded.push(parentId)
    visited.add(parentId)
    menu = parent
  }
  return expanded
})

const menuOptions = computed<UiTreeOption[]>(() => {
  const mapNode = (menu: PermissionMenu): UiTreeOption => ({
    value: menu.id,
    label: menu.menu_name,
    disabled: menu.menu_type === 'catalog',
    children: (menu.children || []).map(mapNode)
  })
  return menuTree.value.map(mapNode)
})

const menuTreeSelectProps = {
  value: 'value',
  label: 'label',
  children: 'children',
  disabled: 'disabled'
}

function resetForm() {
  Object.assign(form, { menu_id: 0, action_code: '', permission_code: '', permission_name: '', status: 1 })
}

async function loadMenus() {
  menus.value = (await getPermissionCatalog()).data.data.menus || []
}

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    const response = await listPermissions({ page: page.value, size: pageSize.value, keyword: keyword.value.trim() || undefined, status: status.value })
    rows.value = response.data.data.records || []
    total.value = response.data.data.total || 0
  } catch (error) {
    rows.value = []
    total.value = 0
    loadError.value = apiErrorMessage(error, '权限目录加载失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

function search() { page.value = 1; void load() }
function openCreate() { editingId.value = null; resetForm(); drawerOpen.value = true }
function openEdit(row: PermissionRecord) {
  editingId.value = row.id
  Object.assign(form, { menu_id: row.menu_id, action_code: row.action_code, permission_code: row.permission_code, permission_name: row.permission_name, status: row.status })
  drawerOpen.value = true
}

async function save() {
  if (!form.menu_id || !form.permission_name.trim() || !form.action_code.trim() || !form.permission_code.trim()) {
    ElMessage.warning('请完整填写所属菜单、权限名称、动作编码和权限编码')
    return
  }
  saving.value = true
  try {
    if (editingId.value) await updatePermission(editingId.value, { menu_id: form.menu_id, permission_name: form.permission_name.trim(), status: form.status })
    else await createPermission({ ...form, action_code: form.action_code.trim(), permission_code: form.permission_code.trim(), permission_name: form.permission_name.trim() })
    drawerOpen.value = false
    await load()
    ElMessage.success('权限已保存')
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '权限保存失败'))
  } finally {
    saving.value = false
  }
}

async function toggleStatus(row: PermissionRecord) {
  const next = row.status === 1 ? 0 : 1
  try {
    await ElMessageBox.confirm(`确认${next === 1 ? '启用' : '停用'}权限“${row.permission_name}”吗？`, '状态确认', { type: 'warning' })
    await updatePermissionStatus(row.id, next)
    await load()
    ElMessage.success(`权限已${next === 1 ? '启用' : '停用'}`)
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(apiErrorMessage(error, '权限状态更新失败'))
  }
}

async function remove(row: PermissionRecord) {
  try {
    await ElMessageBox.confirm(`删除后无法恢复，确认删除权限“${row.permission_name}”吗？`, '删除权限', { type: 'warning' })
    await deletePermission(row.id)
    if (rows.value.length === 1 && page.value > 1) page.value -= 1
    await load()
    ElMessage.success('权限已删除')
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(apiErrorMessage(error, '权限删除失败'))
  }
}

function updatePage(value: number) { page.value = value; void load() }
function updatePageSize(value: number) { pageSize.value = value; page.value = 1; void load() }

onMounted(async () => {
  try { await loadMenus() } catch (error) { ElMessage.error(apiErrorMessage(error, '菜单目录加载失败')) }
  await load()
})
</script>

<template>
  <section class="permission-catalog-page">
    <UiToolbar class="permission-catalog-toolbar">
      <el-input v-model="keyword" clearable placeholder="权限名称、编码或所属菜单" @keyup.enter="search"><template #prefix><el-icon><Search /></el-icon></template></el-input>
      <el-select v-model="status" clearable placeholder="全部状态" @change="search"><el-option label="启用" :value="1" /><el-option label="停用" :value="0" /></el-select>
      <template #actions><el-button :loading="loading" @click="load"><el-icon><Refresh /></el-icon>刷新</el-button><el-button type="primary" @click="openCreate"><el-icon><Plus /></el-icon>新增权限</el-button></template>
    </UiToolbar>

    <el-alert v-if="loadError" :title="loadError" type="error" show-icon :closable="false" class="permission-catalog-error"><template #default><el-button link type="primary" @click="load">重新加载</el-button></template></el-alert>

    <UiDataTable :data="rows" :loading="loading" row-key="id" border empty-text="暂无权限数据">
      <el-table-column prop="permission_name" label="权限名称" min-width="160" />
      <el-table-column prop="permission_code" label="权限编码" min-width="250" show-overflow-tooltip />
      <el-table-column prop="action_code" label="动作编码" min-width="120" />
      <el-table-column prop="menu_name" label="所属菜单" min-width="160" />
      <el-table-column label="状态" width="100"><template #default="scope"><UiStatusTag :value="scope.row.status" :labels="{ '0': '停用', '1': '启用' }" /></template></el-table-column>
      <el-table-column label="操作" width="220" fixed="right"><template #default="scope"><el-button link type="primary" @click="openEdit(scope.row)"><el-icon><Edit /></el-icon>编辑</el-button><el-button link :type="scope.row.status === 1 ? 'warning' : 'success'" @click="toggleStatus(scope.row)"><el-icon><SwitchButton /></el-icon>{{ scope.row.status === 1 ? '停用' : '启用' }}</el-button><el-button link type="danger" @click="remove(scope.row)"><el-icon><Delete /></el-icon>删除</el-button></template></el-table-column>
      <template #footer><UiPagination :total="total" :page="page" :page-size="pageSize" @update:page="updatePage" @update:page-size="updatePageSize" /></template>
    </UiDataTable>

    <UiFormDrawer v-model="drawerOpen" :title="editingId ? '编辑权限' : '新增权限'" width="560px" :loading="saving" @submit="save">
      <el-form label-position="top" @submit.prevent="save">
        <el-form-item label="所属菜单" required><el-tree-select :key="`permission-menu-${drawerOpen}-${editingId || 'new'}-${form.menu_id}`" :model-value="form.menu_id || null" :data="menuOptions" :props="menuTreeSelectProps" check-strictly clearable filterable node-key="value" :render-after-expand="false" :default-expanded-keys="expandedMenuIds" placeholder="请选择菜单" style="width:100%" @update:model-value="form.menu_id = Number($event) || 0"><template #default="{ data }"><span>{{ data.label }}</span></template></el-tree-select></el-form-item>
        <el-form-item label="权限名称" required><el-input v-model="form.permission_name" maxlength="64" show-word-limit /></el-form-item>
        <el-form-item label="动作编码" required><el-input v-model="form.action_code" :disabled="Boolean(editingId)" maxlength="64" placeholder="例如 read、create、update" /></el-form-item>
        <el-form-item label="权限编码" required><el-input v-model="form.permission_code" :disabled="Boolean(editingId)" maxlength="128" placeholder="例如 project:plan:list:read" /></el-form-item>
        <el-form-item label="状态"><el-switch v-model="form.status" :active-value="1" :inactive-value="0" active-text="启用" inactive-text="停用" /></el-form-item>
      </el-form>
    </UiFormDrawer>
  </section>
</template>
