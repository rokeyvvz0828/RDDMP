<script setup lang="ts">
import { reactive, watch } from 'vue'
import type { RoleOption } from '../../types/system'
import type { WorkflowNodeModel } from '../../api/workflow'
interface UserOption { id: number; username: string; display_name: string; org_name?: string }
const props = defineProps<{ node: WorkflowNodeModel | null; users: UserOption[]; roles: RoleOption[] }>()
const emit = defineEmits<{ update: [node: WorkflowNodeModel] }>()
const draft = reactive<WorkflowNodeModel>({ id: '', type: 'APPROVAL', label: '', position: { x: 0, y: 0 }, config: {} })
function syncDraft(node: WorkflowNodeModel | null) { if (!node) return; draft.id = node.id; draft.type = node.type; draft.label = node.label; draft.position = { ...node.position }; draft.config = JSON.parse(JSON.stringify(node.config || {})) }
function update() { if (props.node) emit('update', JSON.parse(JSON.stringify(draft)) as WorkflowNodeModel) }
watch(() => props.node, syncDraft, { deep: true, immediate: true })
</script>
<template>
  <aside class="workflow-inspector">
    <template v-if="node">
      <div class="workflow-inspector__heading"><span class="panel-kicker">节点配置</span><strong>{{ node.type === 'APPROVAL' ? '审批节点' : node.type === 'CC' ? '抄送节点' : node.type === 'CONDITION' ? '条件网关' : node.type === 'PARALLEL_SPLIT' ? '并行分支' : node.type === 'PARALLEL_JOIN' ? '并行汇聚' : node.type === 'START' ? '开始节点' : '结束节点' }}</strong></div>
      <el-form label-position="top" size="default">
        <el-form-item label="节点名称" required><el-input v-model="draft.label" maxlength="64" show-word-limit @change="update" /></el-form-item>
        <template v-if="node.type === 'APPROVAL'">
          <el-form-item label="审批人来源" required>
            <el-radio-group v-model="draft.config.assigneeType" @change="update">
              <el-radio-button value="USER">指定用户</el-radio-button><el-radio-button value="ROLE">指定角色</el-radio-button><el-radio-button value="STARTER">发起人</el-radio-button><el-radio-button value="ORG_OWNER">组织负责人</el-radio-button><el-radio-button value="FORM_FIELD">表单字段</el-radio-button><el-radio-button value="EXPRESSION">表达式</el-radio-button>
            </el-radio-group>
          </el-form-item>
          <el-form-item v-if="draft.config.assigneeType === 'USER'" label="审批用户" required><el-select v-model="draft.config.assigneeIds" multiple filterable collapse-tags placeholder="请选择审批用户" @change="update"><el-option v-for="user in users" :key="user.id" :label="`${user.display_name}（${user.username}）`" :value="user.id" /></el-select></el-form-item>
          <el-form-item v-if="draft.config.assigneeType === 'ROLE'" label="审批角色" required><el-select v-model="draft.config.assigneeIds" multiple filterable collapse-tags placeholder="请选择审批角色" @change="update"><el-option v-for="role in roles" :key="role.id" :label="role.role_name" :value="role.id" /></el-select></el-form-item>
          <el-form-item v-if="draft.config.assigneeType === 'FORM_FIELD'" label="用户字段名" required><el-input v-model="draft.config.fieldName" placeholder="例如 managerId" @change="update" /></el-form-item>
          <el-form-item v-if="draft.config.assigneeType === 'EXPRESSION'" label="审批人表达式" required><el-input v-model="draft.config.expression" placeholder="例如 ${approverId}" @change="update" /></el-form-item>
          <el-form-item label="审批规则" required><el-radio-group v-model="draft.config.mode" @change="update"><el-radio value="ANY">任一人同意</el-radio><el-radio value="ALL">全部同意</el-radio><el-radio value="PERCENT">按比例同意</el-radio></el-radio-group></el-form-item>
          <el-form-item v-if="draft.config.mode === 'PERCENT'" label="通过比例"><el-input-number v-model="draft.config.percentage" :min="1" :max="100" :step="5" @change="update" /></el-form-item>
          <el-form-item label="无审批人时"><el-radio-group v-model="draft.config.emptyAssigneeAction" @change="update"><el-radio value="ERROR">启动时报错</el-radio><el-radio value="WAIT">等待补充人员</el-radio></el-radio-group></el-form-item>
          <el-divider content-position="left">多实例审批</el-divider>
          <el-form-item label="启用多实例"><el-switch v-model="draft.config.multiInstance" @change="update" /></el-form-item>
          <template v-if="draft.config.multiInstance"><el-form-item label="人员集合变量" required><el-input v-model="draft.config.collectionVariable" placeholder="例如 approvers" @change="update" /></el-form-item><el-form-item label="审批顺序"><el-radio-group v-model="draft.config.sequential" @change="update"><el-radio :value="false">并行</el-radio><el-radio :value="true">串行</el-radio></el-radio-group></el-form-item></template>
        </template>
        <template v-else-if="node.type === 'CC'">
          <el-form-item label="抄送用户" required><el-select v-model="draft.config.userIds" multiple filterable collapse-tags placeholder="请选择抄送用户" @change="update"><el-option v-for="user in users" :key="user.id" :label="`${user.display_name}（${user.username}）`" :value="user.id" /></el-select></el-form-item>
        </template>
        <template v-else-if="node.type === 'CONDITION'"><el-alert type="info" :closable="false" title="请在连线上配置 ${变量 条件}，并设置一条默认分支。" /></template>
        <template v-else-if="node.type === 'PARALLEL_SPLIT'"><el-alert type="info" :closable="false" title="该节点至少连接两个分支，所有分支会同时执行。" /></template>
        <template v-else-if="node.type === 'PARALLEL_JOIN'"><el-alert type="info" :closable="false" title="该节点等待所有进入的并行分支汇聚。" /></template>
        <el-alert v-else type="info" :closable="false" show-icon title="开始和结束节点无需配置审批人。" />
      </el-form>
    </template>
    <el-empty v-else description="请选择一个节点" :image-size="64" />
  </aside>
</template>