<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, Edit, Plus, Refresh, Search, SwitchButton, User } from '@element-plus/icons-vue'
import UiAvatarUpload from '../components/ui/UiAvatarUpload.vue'
import UiDataTable from '../components/ui/UiDataTable.vue'
import UiFormDrawer from '../components/ui/UiFormDrawer.vue'
import UiMenuIcon from '../components/ui/UiMenuIcon.vue'
import UiOrgTree from '../components/ui/UiOrgTree.vue'
import UiOrgTreeSelect from '../components/ui/UiOrgTreeSelect.vue'
import UiPageHeader from '../components/ui/UiPageHeader.vue'
import UiStatusTag from '../components/ui/UiStatusTag.vue'
import UiToolbar from '../components/ui/UiToolbar.vue'
import UiUserIdentity from '../components/ui/UiUserIdentity.vue'
import { menuIconOptions } from '../components/ui/menu-icons'
import { createSystem, deleteSystem, getOrgTree, getRoleOptions, getUserRoles, listSystem, saveUserRoles, updateSystem, updateSystemStatus, uploadUserAvatar } from '../api/system'
import type { OrgTreeNode, SystemResource, SystemRow, UserProfile } from '../types/system'
import { apiErrorMessage } from '../api/error'
import { formatDateOnly } from '../utils/date'

type Field = { key: string; label: string; required?: boolean; type?: 'number' }
type ResourceMeta = { title: string; description: string; columns: Array<{ prop: string; label: string; minWidth?: number }>; fields: Field[] }

const route = useRoute()
const router = useRouter()
const resource = computed(() => String(route.params.section || 'users') as SystemResource)
const metadata: Partial<Record<SystemResource, ResourceMeta>> = {
  users: { title: '用户管理', description: '维护平台账号、组织归属和头像信息。', columns: [{ prop: 'username', label: '账号' }, { prop: 'display_name', label: '姓名' }, { prop: 'email', label: '邮箱' }, { prop: 'org_name', label: '所属组织' }, { prop: 'status', label: '状态' }, { prop: 'last_login_at', label: '最近登录' }], fields: [{ key: 'username', label: '账号', required: true }, { key: 'password', label: '初始密码', required: true }, { key: 'display_name', label: '姓名', required: true }, { key: 'email', label: '邮箱' }, { key: 'org_id', label: '所属组织', type: 'number' }] },
  roles: { title: '角色权限', description: '维护角色编码、名称和授权边界。', columns: [{ prop: 'role_code', label: '角色编码' }, { prop: 'role_name', label: '角色名称' }, { prop: 'status', label: '状态' }, { prop: 'created_at', label: '创建时间' }], fields: [{ key: 'role_code', label: '角色编码', required: true }, { key: 'role_name', label: '角色名称', required: true }] },
  orgs: { title: '组织架构', description: '维护组织层级，并从组织节点管理所属用户。', columns: [], fields: [{ key: 'org_code', label: '组织编码', required: true }, { key: 'org_name', label: '组织名称', required: true }, { key: 'parent_id', label: '上级组织', type: 'number' }, { key: 'sort_no', label: '排序', type: 'number' }] },
  menus: { title: '菜单路由', description: '维护菜单树、路由地址、权限编码和菜单图标。', columns: [{ prop: 'menu_name', label: '菜单名称' }, { prop: 'menu_type', label: '类型' }, { prop: 'icon', label: '图标' }, { prop: 'route_path', label: '路由地址' }, { prop: 'permission_code', label: '权限编码' }, { prop: 'sort_no', label: '排序' }, { prop: 'status', label: '状态' }], fields: [{ key: 'menu_name', label: '菜单名称', required: true }, { key: 'menu_type', label: '菜单类型', required: true }, { key: 'icon', label: '菜单图标' }, { key: 'route_path', label: '路由地址' }, { key: 'component_path', label: '组件键' }, { key: 'permission_code', label: '权限编码' }, { key: 'parent_id', label: '上级菜单 ID', type: 'number' }, { key: 'sort_no', label: '排序', type: 'number' }] },
}

const current = computed(() => metadata[resource.value] || metadata.users!)
const isMenuResource = computed(() => resource.value === 'menus')
const isOrgResource = computed(() => resource.value === 'orgs')
const isUserResource = computed(() => resource.value === 'users')
type MenuTreeRow = SystemRow & { children: MenuTreeRow[] }
const rows = ref<SystemRow[]>([])
const orgUsers = ref<SystemRow[]>([])
const orgTree = ref<OrgTreeNode[]>([])
const selectedOrgId = ref<number | null>(null)
const loading = ref(false)
const orgUsersLoading = ref(false)
const saving = ref(false)
const keyword = ref('')
const page = ref(1)
const size = ref(20)
const total = ref(0)
const drawerOpen = ref(false)
const editingId = ref<number | null>(null)
const form = reactive<Record<string, string | number | null>>({})
const avatarFile = ref<File | null>(null)
const avatarPreview = ref<string | null>(null)
const roleOptions = ref<Array<{ id: number; role_code: string; role_name: string }>>([])
const selectedRoleIds = ref<number[]>([])
const roleDialogOpen = ref(false)
const roleUser = ref<UserProfile | null>(null)
const roleLoading = ref(false)
const roleSaving = ref(false)

const excludedOrgIds = computed<number[]>(() => {
  if (!isOrgResource.value || !editingId.value) return []
  const ids = new Set<number>([editingId.value])
  const visit = (items: OrgTreeNode[]) => items.forEach(node => {
    if (ids.has(node.parentId)) ids.add(node.id)
    visit(node.children || [])
  })
  visit(orgTree.value)
  return [...ids]
})

const menuTree = computed<MenuTreeRow[]>(() => {
  if (!isMenuResource.value) return []
  const nodes = new Map<number, MenuTreeRow>()
  rows.value.forEach(row => nodes.set(row.id, { ...row, children: [] } as MenuTreeRow))
  const roots: MenuTreeRow[] = []
  nodes.forEach(node => { const parent = nodes.get(Number(node.parent_id || 0)); if (parent) parent.children.push(node); else roots.push(node) })
  const sortNodes = (items: MenuTreeRow[]) => { items.sort((a, b) => Number(a.sort_no || 0) - Number(b.sort_no || 0) || a.id - b.id); items.forEach(item => sortNodes(item.children)) }
  sortNodes(roots)
  return roots
})

function resetForm() {
  Object.keys(form).forEach(key => delete form[key])
  current.value.fields.forEach(field => { form[field.key] = field.type === 'number' ? 0 : '' })
  avatarFile.value = null
  avatarPreview.value = null
  selectedRoleIds.value = []
}

async function loadRoleOptions() {
  if (roleOptions.value.length) return
  try { roleOptions.value = (await getRoleOptions()).data.data } catch { /* role selection remains empty when the operator lacks role read permission */ }
}

async function loadOrgTree() {
  const response = await getOrgTree()
  orgTree.value = response.data.data
  if (!selectedOrgId.value && orgTree.value[0]) selectedOrgId.value = orgTree.value[0].id
  if (selectedOrgId.value) await loadOrgUsers(selectedOrgId.value)
}

async function loadOrgUsers(orgId: number) {
  orgUsersLoading.value = true
  try {
    const response = await listSystem('users', { page: 1, size: 1000, keyword: keyword.value || undefined, orgId })
    orgUsers.value = response.data.data.records
  } finally { orgUsersLoading.value = false }
}

async function load() {
  loading.value = true
  try {
    if (isOrgResource.value) { await loadOrgTree(); return }
    if (isUserResource.value && !orgTree.value.length) orgTree.value = (await getOrgTree()).data.data
    const queryOrgId = route.query.orgId ? Number(route.query.orgId) : undefined
    const response = await listSystem(resource.value, { page: page.value, size: isMenuResource.value ? 1000 : size.value, keyword: keyword.value || undefined, orgId: isUserResource.value ? (selectedOrgId.value || queryOrgId) || undefined : undefined })
    rows.value = response.data.data.records
    total.value = response.data.data.total
  } catch (error) { ElMessage.error(apiErrorMessage(error, '加载数据失败，请检查权限和服务状态')) } finally { loading.value = false }
}

function openCreate() { editingId.value = null; resetForm(); if (isUserResource.value && route.query.orgId) form.org_id = Number(route.query.orgId); if (isOrgResource.value && route.query.parentId) form.parent_id = Number(route.query.parentId); drawerOpen.value = true }
function openCreateUser(node: OrgTreeNode) { router.push({ path: '/system/users', query: { orgId: String(node.id), create: '1' } }) }
function openCreateOrg(node?: OrgTreeNode) { if (!isOrgResource.value) return; editingId.value = null; resetForm(); form.parent_id = node?.id || 0; drawerOpen.value = true }
async function openEdit(row: SystemRow) { editingId.value = row.id; resetForm(); current.value.fields.forEach(field => { const value = row[field.key]; if (value !== undefined && value !== null) form[field.key] = field.type === 'number' ? Number(value) : String(value) }); if (isUserResource.value) avatarPreview.value = row.avatar_url ? String(row.avatar_url) : null; drawerOpen.value = true }
async function openRoleMaintenance(row: SystemRow) {
  roleUser.value = asUser(row)
  selectedRoleIds.value = []
  roleDialogOpen.value = true
  roleLoading.value = true
  try {
    await loadRoleOptions()
    selectedRoleIds.value = (await getUserRoles(row.id)).data.data
  } catch (error) {
    roleDialogOpen.value = false
    ElMessage.error(apiErrorMessage(error, '加载用户角色失败，请检查权限和服务状态'))
  } finally {
    roleLoading.value = false
  }
}
async function saveRoles() {
  if (!roleUser.value) return
  roleSaving.value = true
  try {
    await saveUserRoles(roleUser.value.id, selectedRoleIds.value)
    ElMessage.success('角色保存成功')
    roleDialogOpen.value = false
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '角色保存失败，请检查角色权限'))
  } finally {
    roleSaving.value = false
  }
}
function openEditOrg(node: OrgTreeNode) { openEdit({ id: node.id, org_code: node.orgCode, org_name: node.orgName, parent_id: node.parentId === node.id ? 0 : node.parentId, sort_no: node.sortNo, status: node.status }) }

async function save() {
  const missing = current.value.fields.find(field => field.required && !(editingId.value && field.key === 'password') && !String(form[field.key] ?? '').trim())
  if (missing) { ElMessage.warning(`请填写${missing.label}`); return }
  saving.value = true
  try {
    let saved: SystemRow
    if (editingId.value) saved = (await updateSystem(resource.value, editingId.value, { ...form })).data.data
    else saved = (await createSystem(resource.value, { ...form, status: 1 })).data.data
    if (isUserResource.value && avatarFile.value) await uploadUserAvatar(saved.id, avatarFile.value)
    ElMessage.success(editingId.value ? '更新成功' : '创建成功')
    drawerOpen.value = false
    await load()
    if (isOrgResource.value) await loadOrgTree()
  } catch (error) { ElMessage.error(apiErrorMessage(error, '保存失败，请检查字段和权限')) } finally { saving.value = false }
}

async function removeRow(row: SystemRow) {
  try {
    await ElMessageBox.confirm('删除' + current.value.title + '后将不再显示该记录，确认继续吗？', '删除确认', { type: 'warning' })
    await deleteSystem(resource.value, row.id)
    ElMessage.success('删除成功')
    await load()
  } catch (error) {
    const action = (error as { action?: string }).action
    if (action !== 'cancel' && action !== 'close') ElMessage.error(apiErrorMessage(error, '删除失败'))
  }
}

async function toggleStatus(row: SystemRow) {
  const next = row.status === 1 ? 0 : 1
  try {
    await ElMessageBox.confirm(`确认${next === 1 ? '启用' : '停用'}该记录吗？`, '变更状态', { type: 'warning' })
    await updateSystemStatus(resource.value, row.id, next)
    ElMessage.success('状态已更新')
    await load()
  } catch (error) {
    const action = (error as { action?: string }).action
    if (action !== 'cancel' && action !== 'close') ElMessage.error(apiErrorMessage(error, '状态更新失败'))
  }
}

function cellText(row: SystemRow, prop: string) { const value = row[prop]; return ['created_at', 'updated_at', 'last_login_at'].includes(prop) ? formatDateOnly(value) : value === null || value === undefined || value === '' ? '-' : String(value) }
function menuTypeLabel(value: unknown) { return String(value) === 'directory' ? '目录' : '菜单' }
function onPageChange(value: number) { page.value = value; load() }
function onSizeChange(value: number) { size.value = value; page.value = 1; load() }
function onOrgSelect(node: OrgTreeNode) { selectedOrgId.value = node.id; loadOrgUsers(node.id) }
function asUser(row: SystemRow): UserProfile { return { id: row.id, username: String(row.username || ''), displayName: String(row.display_name || ''), orgId: Number(row.org_id || 0), orgName: row.org_name ? String(row.org_name) : null, avatarUrl: row.avatar_url ? String(row.avatar_url) : null, status: Number(row.status ?? 1) } }

watch(resource, () => { page.value = 1; keyword.value = ''; selectedOrgId.value = null; load() })
watch(() => route.query.create, value => { if (value === '1' && isUserResource.value) nextTick(openCreate) }, { immediate: true })
onMounted(load)
</script>

<template>
  <section class="system-resource-page">
    <UiPageHeader eyebrow="系统管理" :title="current.title" :description="current.description">
      <template #actions><el-button type="primary" @click="openCreate"><el-icon><Plus /></el-icon>{{ isOrgResource ? '新建组织' : '新建' }}</el-button></template>
    </UiPageHeader>
    <UiToolbar>
      <el-input v-model="keyword" clearable placeholder="搜索名称或编码" style="width:240px" @keyup.enter="load"><template #prefix><el-icon><Search /></el-icon></template></el-input>
      <template #actions><el-button @click="load"><el-icon><Refresh /></el-icon>刷新</el-button><el-button type="primary" @click="load"><el-icon><Search /></el-icon>查询</el-button></template>
    </UiToolbar>

    <div v-if="isOrgResource" class="org-management">
      <UiOrgTree :nodes="orgTree" :loading="loading" :selected-id="selectedOrgId" @select="onOrgSelect" @add-user="openCreateUser" @add-org="openCreateOrg" @edit-org="openEditOrg" @delete-org="removeRow({ id: $event.id } as SystemRow)" />
      <div class="org-users-panel">
        <div class="org-users-panel__heading"><div><span class="panel-kicker">组织成员</span><h3>当前组织用户</h3></div><el-button type="primary" plain @click="selectedOrgId && openCreateUser({ id: selectedOrgId } as OrgTreeNode)"><el-icon><Plus /></el-icon>添加用户</el-button></div>
        <UiDataTable :data="orgUsers" :loading="orgUsersLoading" row-key="id" border empty-text="当前组织暂无用户">
          <el-table-column label="用户" min-width="220"><template #default="scope"><UiUserIdentity :user="asUser(scope.row)" /></template></el-table-column>
          <el-table-column prop="username" label="账号" min-width="150" />
          <el-table-column prop="email" label="邮箱" min-width="200" />
          <el-table-column label="状态" width="100"><template #default="scope"><UiStatusTag :value="scope.row.status" :labels="{ '0': '停用', '1': '启用' }" /></template></el-table-column>
          <el-table-column label="操作" width="150"><template #default="scope"><el-button link type="primary" @click="router.push({ path: '/system/users', query: { edit: String(scope.row.id) } })"><el-icon><Edit /></el-icon>编辑</el-button></template></el-table-column>
        </UiDataTable>
      </div>
    </div>

    <div v-else-if="isMenuResource" v-loading="loading" class="ui-surface-card menu-tree-card">
      <el-tree :data="menuTree" node-key="id" default-expand-all :expand-on-click-node="false" empty-text="暂无菜单">
        <template #default="{ data }"><div class="menu-tree-node"><div class="menu-tree-node__main"><UiMenuIcon :name="String(data.icon || '')" /><div class="menu-tree-node__content"><strong>{{ data.menu_name }}</strong><div><span class="menu-tree-node__id">菜单 ID: {{ data.id }}</span><span>{{ menuTypeLabel(data.menu_type) }}</span><span>{{ data.route_path || '无路由' }}</span></div></div></div><div class="menu-tree-node__actions"><UiStatusTag :value="data.status" :labels="{ '0': '停用', '1': '启用' }" /><el-button link type="primary" @click="openEdit(data)"><el-icon><Edit /></el-icon>编辑</el-button><el-button link type="danger" @click="removeRow(data)"><el-icon><Delete /></el-icon>删除</el-button><el-button link :type="data.status === 1 ? 'warning' : 'success'" @click="toggleStatus(data)"><el-icon><SwitchButton /></el-icon>{{ data.status === 1 ? '停用' : '启用' }}</el-button></div></div></template>
      </el-tree>
      <div class="menu-tree-card__footer">共 {{ rows.length }} 个菜单</div>
    </div>

    <UiDataTable v-else :data="rows" :loading="loading" row-key="id" border>
      <template v-if="isUserResource"><el-table-column label="用户" min-width="220"><template #default="scope"><UiUserIdentity :user="asUser(scope.row)" /></template></el-table-column><el-table-column prop="username" label="账号" min-width="140" /><el-table-column prop="email" label="邮箱" min-width="200" /><el-table-column prop="org_name" label="所属组织" min-width="160" /><el-table-column label="状态" width="100"><template #default="scope"><UiStatusTag :value="scope.row.status" :labels="{ '0': '停用', '1': '启用' }" /></template></el-table-column></template>
      <template v-else><el-table-column v-for="column in current.columns" :key="column.prop" :prop="column.prop" :label="column.label" :min-width="column.minWidth || 130"><template #default="scope"><UiStatusTag v-if="column.prop === 'status'" :value="scope.row[column.prop]" :labels="{ '0': '停用', '1': '启用' }" /><span v-else-if="column.prop === 'icon'"><UiMenuIcon :name="String(scope.row[column.prop] || '')" /></span><span v-else>{{ cellText(scope.row, column.prop) }}</span></template></el-table-column></template>
      <el-table-column label="操作" :width="isUserResource ? 390 : 320" fixed="right"><template #default="scope"><el-button v-if="isUserResource" link type="primary" @click="openRoleMaintenance(scope.row)"><el-icon><User /></el-icon>角色</el-button><el-button v-if="resource === 'roles'" link type="primary" @click="router.push('/system/role-permissions')">权限</el-button><el-button link type="primary" @click="openEdit(scope.row)"><el-icon><Edit /></el-icon>编辑</el-button><el-button link type="danger" @click="removeRow(scope.row)"><el-icon><Delete /></el-icon>删除</el-button><el-button link :type="scope.row.status === 1 ? 'warning' : 'success'" @click="toggleStatus(scope.row)"><el-icon><SwitchButton /></el-icon>{{ scope.row.status === 1 ? '停用' : '启用' }}</el-button></template></el-table-column>
      <template #footer><el-pagination v-model:current-page="page" v-model:page-size="size" layout="total, sizes, prev, pager, next" :total="total" @current-change="onPageChange" @size-change="onSizeChange" /></template>
    </UiDataTable>

    <UiFormDrawer v-model="drawerOpen" :title="editingId ? `编辑${current.title}` : `新建${current.title}`" :loading="saving" @submit="save">
      <el-form label-position="top">
        <el-form-item v-if="isUserResource" label="头像"><UiAvatarUpload v-model="avatarFile" v-model:preview-url="avatarPreview" /></el-form-item>
        <el-form-item v-for="field in current.fields" :key="field.key" :label="field.label" :required="field.required">
          <UiOrgTreeSelect v-if="(isUserResource && field.key === 'org_id') || (isOrgResource && field.key === 'parent_id')" :model-value="form[field.key] ? Number(form[field.key]) : null" :nodes="orgTree" :exclude-ids="isOrgResource && editingId ? excludedOrgIds : []" :placeholder="field.label" @update:model-value="form[field.key] = $event" />
          <el-select v-else-if="isMenuResource && field.key === 'icon'" v-model="form[field.key]" clearable filterable placeholder="选择菜单图标" style="width:100%"><el-option v-for="item in menuIconOptions" :key="item.key" :label="item.label" :value="item.key"><span class="menu-icon-option"><UiMenuIcon :name="item.key" /><span>{{ item.label }}</span></span></el-option></el-select>
          <el-input v-else-if="field.type !== 'number'" v-model="form[field.key]" :type="field.key === 'password' ? 'password' : 'text'" :show-password="field.key === 'password'" />
          <el-input-number v-else v-model="form[field.key]" :min="0" style="width:100%" />
        </el-form-item>
      </el-form>
    </UiFormDrawer>

    <el-dialog v-model="roleDialogOpen" title="维护用户角色" width="460px" :close-on-click-modal="false" destroy-on-close>
      <div v-if="roleUser" class="role-maintenance">
        <div class="role-maintenance__user"><UiUserIdentity :user="roleUser" :show-profile="false" /><span>请选择该用户关联的角色</span></div>
        <el-select v-model="selectedRoleIds" multiple filterable clearable :loading="roleLoading" placeholder="请选择角色" style="width:100%">
          <el-option v-for="role in roleOptions" :key="role.id" :label="role.role_name" :value="role.id" />
        </el-select>
      </div>
      <template #footer><el-button @click="roleDialogOpen = false">取消</el-button><el-button type="primary" :loading="roleSaving" :disabled="roleLoading" @click="saveRoles">保存角色</el-button></template>
    </el-dialog>
  </section>
</template>
