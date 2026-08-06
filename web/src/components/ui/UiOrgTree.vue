<script setup lang="ts">
import { computed } from 'vue'
import { Delete, Edit, FolderAdd, MoreFilled, Plus, UserFilled } from '@element-plus/icons-vue'
import type { OrgTreeNode } from '../../types/system'

const props = withDefaults(defineProps<{ nodes?: OrgTreeNode[]; loading?: boolean; selectedId?: number | null }>(), { nodes: () => [], loading: false, selectedId: null })
const emit = defineEmits<{ select: [node: OrgTreeNode]; 'add-user': [node: OrgTreeNode]; 'add-org': [node: OrgTreeNode]; 'edit-org': [node: OrgTreeNode]; 'delete-org': [node: OrgTreeNode] }>()
const selected = computed(() => props.selectedId)

type OrgAction = 'add-user' | 'edit-org' | 'delete-org'

function handleAction(command: OrgAction, node: OrgTreeNode) {
  if (command === 'add-user') emit('add-user', node)
  if (command === 'edit-org') emit('edit-org', node)
  if (command === 'delete-org') emit('delete-org', node)
}
</script>

<template>
  <div v-loading="loading" class="ui-org-tree">
    <el-tree :data="nodes" node-key="id" default-expand-all highlight-current :current-node-key="selected" :expand-on-click-node="false" empty-text="暂无组织" @node-click="emit('select', $event)">
      <template #default="{ data }">
        <div class="ui-org-tree__node">
          <div class="ui-org-tree__main">
            <el-icon><FolderAdd /></el-icon>
            <div class="ui-org-tree__content">
              <strong :title="data.orgName">{{ data.orgName }}</strong>
              <div class="ui-org-tree__meta">
                <span>{{ data.users?.length || 0 }} 位成员</span>
                <i aria-hidden="true">·</i>
                <span :class="{ 'is-disabled': data.status !== 1 }">{{ data.status === 1 ? '启用' : '停用' }}</span>
              </div>
            </div>
          </div>
          <div class="ui-org-tree__actions">
            <el-tooltip content="添加下级组织"><el-button text circle size="small" aria-label="添加下级组织" @click.stop="emit('add-org', data)"><el-icon><Plus /></el-icon></el-button></el-tooltip>
            <el-dropdown trigger="click" @click.stop @command="handleAction($event, data)">
              <el-button text circle size="small" aria-label="更多组织操作" @click.stop><el-icon><MoreFilled /></el-icon></el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="add-user" :icon="UserFilled">添加用户</el-dropdown-item>
                  <el-dropdown-item command="edit-org" :icon="Edit">编辑组织</el-dropdown-item>
                  <el-dropdown-item command="delete-org" :icon="Delete" divided class="ui-org-tree__danger-action">删除组织</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
        </div>
      </template>
    </el-tree>
  </div>
</template>
