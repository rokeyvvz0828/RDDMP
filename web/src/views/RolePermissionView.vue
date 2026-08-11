<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Check, Refresh } from '@element-plus/icons-vue'
import UiMenuIcon from '../components/ui/UiMenuIcon.vue'
import UiToolbar from '../components/ui/UiToolbar.vue'
import { getPermissionCatalog, getRoleOptions, getRolePermissions, saveRolePermissions } from '../api/system'
import type { PermissionMenu, RoleOption } from '../types/system'
import { apiErrorMessage } from '../api/error'

const roles = ref<RoleOption[]>([])
const flatMenus = ref<PermissionMenu[]>([])
const selectedRoleId = ref<number | null>(null)
const checkedIds = ref<number[]>([])
const loading = ref(false)
const saving = ref(false)

const menuTree = computed<PermissionMenu[]>(() => {
  const nodes = new Map<number, PermissionMenu>()
  flatMenus.value.forEach(item => nodes.set(item.id, { ...item, children: [] }))
  const roots: PermissionMenu[] = []
  nodes.forEach(node => {
    const parent = nodes.get(Number(node.parent_id || 0))
    if (parent) parent.children?.push(node)
    else roots.push(node)
  })
  const sort = (items: PermissionMenu[]) => {
    items.sort((a, b) => a.sort_no - b.sort_no || a.id - b.id)
    items.forEach(item => sort(item.children || []))
  }
  sort(roots)
  return roots
})

const selectedRole = computed(() => roles.value.find(role => role.id === selectedRoleId.value))

function isChecked(actionId: number) {
  return checkedIds.value.includes(actionId)
}

function toggleAction(actionId: number, value: boolean) {
  if (value && !checkedIds.value.includes(actionId)) checkedIds.value = [...checkedIds.value, actionId]
  if (!value) checkedIds.value = checkedIds.value.filter(id => id !== actionId)
}

function toggleMenu(menu: PermissionMenu, value: boolean) {
  menu.actions.forEach(action => toggleAction(action.id, value))
}

async function load() {
  loading.value = true
  try {
    const [roleResponse, catalogResponse] = await Promise.all([getRoleOptions(), getPermissionCatalog()])
    roles.value = roleResponse.data.data
    flatMenus.value = catalogResponse.data.data.menus
    if (!selectedRoleId.value || !roles.value.some(role => role.id === selectedRoleId.value)) selectedRoleId.value = roles.value[0]?.id || null
    await loadRolePermissions()
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '权限目录加载失败，请检查权限和服务状态'))
  } finally {
    loading.value = false
  }
}

async function loadRolePermissions() {
  if (!selectedRoleId.value) {
    checkedIds.value = []
    return
  }
  try {
    checkedIds.value = (await getRolePermissions(selectedRoleId.value)).data.data.permissionIds
  } catch (error) {
    checkedIds.value = []
    ElMessage.error(apiErrorMessage(error, '角色权限加载失败'))
  }
}

async function save() {
  if (!selectedRoleId.value) {
    ElMessage.warning('请先选择角色')
    return
  }
  saving.value = true
  try {
    await saveRolePermissions(selectedRoleId.value, checkedIds.value)
    ElMessage.success('角色权限已保存')
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '角色权限保存失败'))
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<template>
  <section class="role-permission-page">
    <UiToolbar class="role-permission-toolbar"><div class="ui-toolbar__filters"><span>当前角色</span><el-select v-model="selectedRoleId" filterable placeholder="请选择角色" style="width:280px" @change="loadRolePermissions"><el-option v-for="role in roles" :key="role.id" :label="role.role_name" :value="role.id" /></el-select><el-tag v-if="selectedRole" type="info">{{ selectedRole.role_code }}</el-tag><small>勾选动作后保存，父级菜单会自动保留用于路由展示。</small></div><template #actions><el-button :loading="loading" @click="load"><el-icon><Refresh /></el-icon>刷新</el-button><el-button type="primary" :loading="saving" @click="save"><el-icon><Check /></el-icon>保存权限</el-button></template></UiToolbar>
    <el-card v-loading="loading" class="permission-tree-card" shadow="never">
      <el-tree :data="menuTree" node-key="id" default-expand-all :expand-on-click-node="false" empty-text="暂无菜单权限目录">
        <template #default="{ data }">
          <div class="permission-tree-node">
            <div class="permission-tree-node__name"><UiMenuIcon :name="String(data.icon || '')" /><span>{{ data.menu_name }}</span><small>{{ data.route_path || '目录' }}</small></div>
            <div class="permission-tree-node__actions">
              <el-checkbox v-for="action in data.actions" :key="action.id" :model-value="isChecked(action.id)" @click.stop @update:model-value="toggleAction(action.id, Boolean($event))">{{ action.permission_name }}</el-checkbox>
              <el-button v-if="data.actions.length" text circle title="全选当前菜单" @click.stop="toggleMenu(data, true)"><el-icon><Check /></el-icon></el-button>
            </div>
          </div>
        </template>
      </el-tree>
    </el-card>
  </section>
</template>
