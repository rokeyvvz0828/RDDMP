<script setup lang="ts">
import { computed, ref } from 'vue'
import { Position, VueFlow, type Edge, type Node } from '@vue-flow/core'
import '@vue-flow/core/dist/style.css'
import '@vue-flow/core/dist/theme-default.css'
import { Check, Close, Document, RefreshLeft } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox, ElNotification } from 'element-plus'
import { approvalEvents as seededEvents } from '../mock'
import type { ApprovalEvent, DeliveryProject } from '../types'

const props = defineProps<{ projects: DeliveryProject[] }>()
const selectedId = ref(props.projects[0]?.id || 1)
const actionOpen = ref(false)
const decision = ref<'同意' | '退回' | '驳回'>('同意')
const comment = ref('')
const submitting = ref(false)
const events = ref<ApprovalEvent[]>(seededEvents.map(item => ({ ...item })))
const selectedProject = computed(() => props.projects.find(item => item.id === selectedId.value) || props.projects[0])
const nodes = computed<Node[]>(() => [
  { id: 'start', position: { x: 70, y: 145 }, sourcePosition: Position.Right, targetPosition: Position.Left, data: { label: '提交交付申请\n林川 · 已完成' }, class: 'delivery-flow-node is-done' },
  { id: 'tech', position: { x: 330, y: 145 }, sourcePosition: Position.Right, targetPosition: Position.Left, data: { label: '技术负责人审批\n周宁 · 已同意' }, class: 'delivery-flow-node is-done' },
  { id: 'quality', position: { x: 590, y: 145 }, sourcePosition: Position.Right, targetPosition: Position.Left, data: { label: '质量负责人审批\n陈曦 · 待处理' }, class: 'delivery-flow-node is-current' },
  { id: 'release', position: { x: 850, y: 145 }, sourcePosition: Position.Right, targetPosition: Position.Left, data: { label: '版本窗口确认\n沈越 · 未到达' }, class: 'delivery-flow-node' },
  { id: 'end', position: { x: 1110, y: 145 }, sourcePosition: Position.Right, targetPosition: Position.Left, data: { label: '审批完成\n等待流转' }, class: 'delivery-flow-node' }
])
const edges: Edge[] = [
  { id: 'e1', source: 'start', target: 'tech', animated: false },
  { id: 'e2', source: 'tech', target: 'quality', animated: true },
  { id: 'e3', source: 'quality', target: 'release' },
  { id: 'e4', source: 'release', target: 'end' }
]

function openAction(type: '同意' | '退回' | '驳回') { decision.value = type; comment.value = ''; actionOpen.value = true }
async function submitAction() {
  if (decision.value !== '同意' && !comment.value.trim()) { ElMessage.warning(`${decision.value}时必须填写原因`); return }
  if (decision.value === '驳回') {
    await ElMessageBox.confirm(`驳回后“${selectedProject.value.name}”本次审批将终止，需要重新提交。`, '确认驳回', { type: 'warning', confirmButtonText: '确认驳回', cancelButtonText: '继续审批' })
  }
  submitting.value = true
  await new Promise(resolve => window.setTimeout(resolve, 650))
  const pending = events.value.find(item => item.action === '待审批')
  if (pending) Object.assign(pending, { action: decision.value, time: '刚刚', comment: comment.value || '同意按当前交付计划执行。' })
  submitting.value = false
  actionOpen.value = false
  ElNotification({ title: '审批已提交', message: `${selectedProject.value.code} 已${decision.value}，审批记录已更新。`, type: decision.value === '同意' ? 'success' : 'warning' })
}
function remind() { ElMessage.success('催办通知已发送给当前审批人') }
</script>

<template>
  <div class="delivery-approval-layout">
    <section class="delivery-panel delivery-approval-summary">
      <header class="delivery-panel__header"><div><span class="panel-kicker">待办审批</span><h3>交付准入审批</h3></div><el-select v-model="selectedId" style="width:260px"><el-option v-for="project in projects.slice(0, 5)" :key="project.id" :value="project.id" :label="`${project.code} · ${project.name}`" /></el-select></header>
      <div class="delivery-approval-context"><div><small>申请单号</small><strong>APR-{{ selectedProject.code.slice(4) }}</strong></div><div><small>申请项目</small><strong>{{ selectedProject.name }}</strong></div><div><small>当前节点</small><strong>质量负责人审批</strong></div><div><small>剩余时限</small><strong class="is-warning">06:42:18</strong></div></div>
    </section>

    <section class="delivery-panel delivery-flow-panel">
      <header class="delivery-panel__header"><div><span class="panel-kicker">流程图</span><h3>交付准入流程</h3></div><el-button @click="remind"><el-icon><RefreshLeft /></el-icon>催办</el-button></header>
      <div class="delivery-flow-canvas"><VueFlow :nodes="nodes" :edges="edges" fit-view-on-init :min-zoom=".55" :max-zoom="1.2" :nodes-draggable="false" :nodes-connectable="false" :elements-selectable="false" /></div>
    </section>

    <div class="delivery-approval-bottom">
      <section class="delivery-panel delivery-approval-timeline">
        <header class="delivery-panel__header"><div><span class="panel-kicker">审计记录</span><h3>审批时间线</h3></div><el-button link type="primary"><el-icon><Document /></el-icon>查看完整记录</el-button></header>
        <el-timeline><el-timeline-item v-for="event in events" :key="event.id" :timestamp="event.time" :type="event.action === '同意' ? 'success' : event.action === '待审批' ? 'primary' : event.action === '驳回' ? 'danger' : 'warning'" placement="top"><div class="delivery-audit-event"><strong>{{ event.node }} · {{ event.action }}</strong><span>{{ event.operator }}</span><p>{{ event.comment }}</p></div></el-timeline-item></el-timeline>
      </section>
      <section class="delivery-panel delivery-decision-panel">
        <header class="delivery-panel__header"><div><span class="panel-kicker">当前操作</span><h3>审批决策</h3></div></header>
        <el-alert title="审批会写入审计记录；驳回将终止本次申请。" type="info" :closable="false" show-icon />
        <div class="delivery-decision-actions"><el-button type="success" @click="openAction('同意')"><el-icon><Check /></el-icon>同意</el-button><el-button type="warning" @click="openAction('退回')"><el-icon><RefreshLeft /></el-icon>退回补充</el-button><el-button type="danger" plain @click="openAction('驳回')"><el-icon><Close /></el-icon>驳回</el-button></div>
      </section>
    </div>

    <el-dialog v-model="actionOpen" :title="`${decision}审批`" width="min(520px, 92vw)" destroy-on-close>
      <div class="delivery-dialog-context"><span>对象</span><strong>{{ selectedProject.code }} · {{ selectedProject.name }}</strong><span>影响</span><strong>{{ decision === '同意' ? '流转到版本窗口确认' : decision === '退回' ? '返回申请人补充材料' : '终止本次申请' }}</strong></div>
      <el-form label-position="top"><el-form-item :label="`${decision}意见`" :required="decision !== '同意'"><el-input v-model="comment" type="textarea" :rows="4" maxlength="200" show-word-limit :placeholder="decision === '同意' ? '可填写审批意见' : `请填写${decision}原因`" /></el-form-item></el-form>
      <template #footer><el-button @click="actionOpen = false">取消</el-button><el-button :type="decision === '驳回' ? 'danger' : 'primary'" :loading="submitting" @click="submitAction">提交{{ decision }}</el-button></template>
    </el-dialog>
  </div>
</template>
