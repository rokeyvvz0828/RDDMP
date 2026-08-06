<script setup lang="ts">
import { computed } from 'vue'
import { Delete, Edit, FolderAdd, Plus, UserFilled } from '@element-plus/icons-vue'
import UiUserIdentity from './UiUserIdentity.vue'
import UiStatusTag from './UiStatusTag.vue'
import type { OrgTreeNode } from '../../types/system'

const props = withDefaults(defineProps<{ nodes?: OrgTreeNode[]; loading?: boolean; selectedId?: number | null }>(), { nodes: () => [], loading: false, selectedId: null })
const emit = defineEmits<{ select: [node: OrgTreeNode]; 'add-user': [node: OrgTreeNode]; 'add-org': [node: OrgTreeNode]; 'edit-org': [node: OrgTreeNode]; 'delete-org': [node: OrgTreeNode] }>()
const selected = computed(() => props.selectedId)
</script>

<template>
  <div v-loading="loading" class="ui-org-tree">
    <el-tree :data="nodes" node-key="id" default-expand-all highlight-current :current-node-key="selected" :expand-on-click-node="false" empty-text="暂无组织" @node-click="emit('select', $event)">
      <template #default="{ data }">
        <div class="ui-org-tree__node">
          <div class="ui-org-tree__main"><el-icon><FolderAdd /></el-icon><span>{{ data.orgName }}</span><small>{{ data.users?.length || 0 }} 人</small></div>
          <div class="ui-org-tree__actions">
            <UiStatusTag :value="data.status" :labels="{ '0': '停用', '1': '启用' }" />
            <el-tooltip content="添加下级组织"><el-button text circle size="small" @click.stop="emit('add-org', data)"><el-icon><Plus /></el-icon></el-button></el-tooltip>
            <el-tooltip content="添加用户"><el-button text circle size="small" @click.stop="emit('add-user', data)"><el-icon><UserFilled /></el-icon></el-button></el-tooltip>
            <el-tooltip content="编辑组织"><el-button text circle size="small" @click.stop="emit('edit-org', data)"><el-icon><Edit /></el-icon></el-button></el-tooltip><el-tooltip content="删除组织"><el-button text circle size="small" type="danger" @click.stop="emit('delete-org', data)"><el-icon><Delete /></el-icon></el-button></el-tooltip>
          </div>
        </div>
      </template>
    </el-tree>
  </div>
</template>