<script setup lang="ts">
import { ref } from 'vue'
import { Box, Edit, Plus, Refresh } from '@element-plus/icons-vue'
import UiAvatarUpload from '../components/ui/UiAvatarUpload.vue'
import UiDataTable from '../components/ui/UiDataTable.vue'
import UiEmptyState from '../components/ui/UiEmptyState.vue'
import UiFormDrawer from '../components/ui/UiFormDrawer.vue'
import UiMenuIcon from '../components/ui/UiMenuIcon.vue'
import UiOrgTree from '../components/ui/UiOrgTree.vue'
import UiOrgTreeSelect from '../components/ui/UiOrgTreeSelect.vue'
import UiPageHeader from '../components/ui/UiPageHeader.vue'
import UiStatusTag from '../components/ui/UiStatusTag.vue'
import UiToolbar from '../components/ui/UiToolbar.vue'
import UiUserIdentity from '../components/ui/UiUserIdentity.vue'
import type { OrgTreeNode, UserProfile } from '../types/system'

const drawerOpen = ref(false)
const selectedOrg = ref<number | null>(1)
const avatarFile = ref<File | null>(null)
const avatarPreview = ref<string | null>(null)
const demoUser: UserProfile = { id: 1, username: 'admin', displayName: '系统管理员', orgId: 1, orgName: '平台总部', avatarUrl: null, roles: ['超级管理员'], status: 1 }
const demoRows = [{ id: 1, name: '用户身份组件', status: 1 }, { id: 2, name: '组织树组件', status: 1 }]
const demoOrgs: OrgTreeNode[] = [{ id: 1, parentId: 0, orgCode: 'ROOT', orgName: '平台总部', sortNo: 0, status: 1, children: [{ id: 2, parentId: 1, orgCode: 'TECH', orgName: '技术中心', sortNo: 1, status: 1, children: [], users: [] }], users: [demoUser as never] }]
function submit() { drawerOpen.value = false }
</script>

<template>
  <section class="component-showcase-page">
    <UiPageHeader eyebrow="前端基础组件" title="组件示例" description="所有业务页面共享的交互和视觉组件集中展示。">
      <template #actions><el-button type="primary" @click="drawerOpen = true"><el-icon><Plus /></el-icon>打开表单抽屉</el-button></template>
    </UiPageHeader>
    <UiToolbar><el-input placeholder="示例查询条件" style="width:240px" /><template #actions><el-button><el-icon><Refresh /></el-icon>刷新</el-button><el-button type="primary">查询</el-button></template></UiToolbar>
    <div class="showcase-grid">
      <el-card shadow="never" class="surface-card showcase-card"><template #header><div class="card-heading"><div><span class="panel-kicker">身份标识</span><h3>用户身份</h3></div></div></template><UiUserIdentity :user="demoUser" /><p class="showcase-note">头像和姓名横向排列，悬浮查看用户详情。</p><UiAvatarUpload v-model="avatarFile" v-model:preview-url="avatarPreview" /></el-card>
      <el-card shadow="never" class="surface-card showcase-card"><template #header><div class="card-heading"><div><span class="panel-kicker">状态与图标</span><h3>状态和图标</h3></div></div></template><div class="showcase-inline"><UiStatusTag :value="1" :labels="{ '1': '启用' }" /><UiStatusTag :value="0" :labels="{ '0': '停用' }" /><UiMenuIcon name="setting" /><UiMenuIcon name="user" /><el-icon><Box /></el-icon><el-icon><Edit /></el-icon></div><UiEmptyState title="空状态示例" description="没有数据时使用统一的空状态组件。" /></el-card>
      <el-card shadow="never" class="surface-card showcase-card showcase-card--wide"><template #header><div class="card-heading"><div><span class="panel-kicker">组织能力</span><h3>组织树和组织选择器</h3></div></div></template><div class="showcase-org-layout"><UiOrgTree :nodes="demoOrgs" :selected-id="selectedOrg" @select="selectedOrg = $event.id" /><div><el-form label-position="top"><el-form-item label="所属组织"><UiOrgTreeSelect v-model="selectedOrg" :nodes="demoOrgs" /></el-form-item></el-form><p class="showcase-note">组织树可接入节点级新增用户、新增下级组织和编辑操作。</p></div></div></el-card>
      <el-card shadow="never" class="surface-card showcase-card showcase-card--wide"><template #header><div class="card-heading"><div><span class="panel-kicker">数据表格</span><h3>数据表格</h3></div></div></template><UiDataTable :data="demoRows" row-key="id" border><el-table-column prop="name" label="组件名称" /><el-table-column label="状态"><template #default="scope"><UiStatusTag :value="scope.row.status" :labels="{ '1': '已启用' }" /></template></el-table-column><el-table-column label="操作"><template #default><el-button link type="primary"><el-icon><Edit /></el-icon>编辑</el-button></template></el-table-column></UiDataTable></el-card>
    </div>
    <UiFormDrawer v-model="drawerOpen" title="表单抽屉示例" @submit="submit"><el-form label-position="top"><el-form-item label="组件名称"><el-input model-value="示例表单" /></el-form-item><el-form-item label="说明"><el-input type="textarea" model-value="统一的表单抽屉操作区。" /></el-form-item></el-form></UiFormDrawer>
  </section>
</template>