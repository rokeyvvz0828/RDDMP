<script setup lang="ts">
import { computed, reactive, watch } from 'vue'
import type { RoleOption } from '../../types/system'
import type { WorkflowEdgeModel, WorkflowNodeModel, WorkflowScopeType } from '../../api/workflow'

interface UserOption { id: number; username: string; display_name: string; org_name?: string }
const props = defineProps<{ node: WorkflowNodeModel | null; edge: WorkflowEdgeModel | null; nodes: WorkflowNodeModel[]; users: UserOption[]; roles: RoleOption[]; scopeType: WorkflowScopeType; readonly?: boolean }>()
const projectScoped = computed(() => props.scopeType === 'PROJECT')
const templateScoped = computed(() => props.scopeType === 'TEMPLATE')
const emit = defineEmits<{ update: [node: WorkflowNodeModel]; updateEdge: [edge: WorkflowEdgeModel] }>()
const draft = reactive<WorkflowNodeModel>({ id: '', type: 'APPROVAL', label: '', position: { x: 0, y: 0 }, config: {} })
const edgeDraft = reactive<WorkflowEdgeModel>({ id: '', source: '', target: '', label: '', condition: '', default: false })
const edgeSourceLabel = computed(() => props.nodes.find(node => node.id === edgeDraft.source)?.label || edgeDraft.source)
const edgeTargetLabel = computed(() => props.nodes.find(node => node.id === edgeDraft.target)?.label || edgeDraft.target)
const edgeSourceNode = computed(() => props.nodes.find(node => node.id === edgeDraft.source) || null)
const isConditionalEdge = computed(() => edgeSourceNode.value?.type === 'CONDITION')
function syncDraft(node: WorkflowNodeModel | null) {
  if (!node) return
  draft.id = node.id
  draft.type = node.type
  draft.label = node.label
  draft.position = { ...node.position }
  draft.config = JSON.parse(JSON.stringify(node.config || {}))
  if (node.type === 'APPROVAL') draft.config.signatureRequired = Boolean(draft.config.signatureRequired)
}
function syncEdge(edge: WorkflowEdgeModel | null) {
  if (!edge) return
  edgeDraft.id = edge.id
  edgeDraft.source = edge.source
  edgeDraft.target = edge.target
  edgeDraft.label = edge.label || ''
  edgeDraft.condition = edge.condition || ''
  edgeDraft.default = Boolean(edge.default) || edgeSourceNode.value?.config?.defaultEdgeId === edge.id
}
function update() { if (props.node && !props.readonly) emit('update', JSON.parse(JSON.stringify(draft)) as WorkflowNodeModel) }
function updateEdge() {
  if (!props.edge || props.readonly) return
  const next = JSON.parse(JSON.stringify(edgeDraft)) as WorkflowEdgeModel
  if (!isConditionalEdge.value) { next.condition = null; next.default = false }
  if (next.default) next.condition = null
  emit('updateEdge', next)
}
watch(() => props.node, syncDraft, { deep: true, immediate: true })
watch(() => props.edge, syncEdge, { deep: true, immediate: true })
</script>
<template>
  <aside class="workflow-inspector">
    <template v-if="edge">
      <div class="workflow-inspector__heading"><span class="panel-kicker">连线配置</span><strong>{{ edgeSourceLabel }} → {{ edgeTargetLabel }}</strong><small>连线方向表示流程流转方向</small></div>
      <el-form label-position="top" size="default">
        <el-form-item label="连线名称"><el-input v-model="edgeDraft.label" maxlength="64" show-word-limit :disabled="readonly" @change="updateEdge" /></el-form-item>
        <template v-if="isConditionalEdge">
          <el-form-item label="条件表达式"><el-input v-model="edgeDraft.condition" :disabled="readonly || Boolean(edgeDraft.default)" @input="updateEdge" /></el-form-item>
          <el-form-item label="分支类型"><el-switch v-model="edgeDraft.default" :disabled="readonly" active-text="默认分支" inactive-text="条件分支" @change="updateEdge" /></el-form-item>
          <el-alert v-if="edgeDraft.default" type="info" :closable="false" show-icon title="默认分支不填写条件表达式。" />
          <el-alert v-else type="warning" :closable="false" show-icon title="条件网关的非默认分支必须填写 ${...} 条件表达式。" />
        </template>
        <el-alert v-else type="info" :closable="false" show-icon title="普通连线无需配置条件表达式。" />
      </el-form>
    </template>
    <template v-else-if="node">
      <div class="workflow-inspector__heading"><span class="panel-kicker">节点配置</span><strong>{{ node.type === 'APPROVAL' ? '用户任务' : node.type === 'CC' ? '抄送节点' : node.type === 'CONDITION' ? '条件网关' : node.type === 'PARALLEL_SPLIT' ? '并行分支网关' : node.type === 'PARALLEL_JOIN' ? '并行汇聚网关' : node.type === 'START' ? '开始事件' : '结束事件' }}</strong></div>
      <el-form label-position="top" size="default">
        <el-form-item label="节点名称" required><el-input v-model="draft.label" maxlength="64" show-word-limit :disabled="readonly || node.type === 'START' || node.type === 'END'" @change="update" /></el-form-item>
        <template v-if="node.type === 'APPROVAL'">
          <el-form-item label="审批人来源" required><el-radio-group v-model="draft.config.assigneeType" :disabled="readonly" @change="update"><template v-if="projectScoped"><el-radio-button value="PROJECT_MEMBER">项目成员</el-radio-button><el-radio-button value="PROJECT_ROLE">项目角色</el-radio-button></template><template v-else-if="templateScoped"><el-radio-button value="TEMPLATE_PLACEHOLDER">项目配置时指定</el-radio-button></template><template v-else><el-radio-button value="USER">指定用户</el-radio-button><el-radio-button value="ROLE">指定角色</el-radio-button></template><el-radio-button value="STARTER">发起人</el-radio-button></el-radio-group></el-form-item>
          <el-form-item v-if="['USER', 'PROJECT_MEMBER'].includes(String(draft.config.assigneeType))" :label="projectScoped ? '项目审批成员' : '审批用户'" required><el-select v-model="draft.config.assigneeIds" multiple filterable collapse-tags :disabled="readonly" placeholder="请选择审批人员" @change="update"><el-option v-for="user in users" :key="user.id" :label="`${user.display_name}（${user.username}）`" :value="user.id" /></el-select></el-form-item>
          <el-form-item v-if="['ROLE', 'PROJECT_ROLE'].includes(String(draft.config.assigneeType))" :label="projectScoped ? '项目审批角色' : '审批角色'" required><el-select v-model="draft.config.assigneeIds" multiple filterable collapse-tags :disabled="readonly" placeholder="请选择审批角色" @change="update"><el-option v-for="role in roles" :key="role.id" :label="role.role_name" :value="role.id" /></el-select></el-form-item>
          <el-form-item label="审批规则" required><el-radio-group v-model="draft.config.mode" :disabled="readonly" @change="update"><el-radio value="ANY">任一人同意</el-radio><el-radio value="ALL">全部同意</el-radio></el-radio-group></el-form-item>
          <el-form-item label="无审批人时"><el-radio-group v-model="draft.config.emptyAssigneeAction" :disabled="readonly" @change="update"><el-radio value="ERROR">启动时报错</el-radio><el-radio value="WAIT">等待补充人员</el-radio></el-radio-group></el-form-item>
        </template>
        <template v-else-if="node.type === 'CC'"><el-form-item label="抄送人员" required><el-tag v-if="templateScoped" effect="plain" type="info">项目配置时指定</el-tag><el-select v-else v-model="draft.config.userIds" multiple filterable collapse-tags :disabled="readonly" placeholder="请选择抄送用户" @change="update"><el-option v-for="user in users" :key="user.id" :label="`${user.display_name}（${user.username}）`" :value="user.id" /></el-select></el-form-item></template>
        <template v-else-if="node.type === 'CONDITION'"><el-alert type="info" :closable="false" show-icon title="条件写在出边上，并设置一条默认分支。" /></template>
        <template v-else-if="node.type === 'PARALLEL_SPLIT'"><el-alert type="info" :closable="false" show-icon title="该网关至少连接两个分支。" /></template>
        <template v-else-if="node.type === 'PARALLEL_JOIN'"><el-alert type="info" :closable="false" show-icon title="该网关等待进入的并行分支全部汇聚。" /></template>
        <el-alert v-else type="info" :closable="false" show-icon title="开始和结束事件无需配置。" />
      </el-form>
    </template>
    <el-empty v-else description="请选择节点或连线" :image-size="64" />
  </aside>
</template>
