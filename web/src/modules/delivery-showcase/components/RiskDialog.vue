<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { owners } from '../mock'
import type { DeliveryProject, DeliveryRisk } from '../types'

const props = defineProps<{ modelValue: boolean; project: DeliveryProject | null }>()
const emit = defineEmits<{ 'update:modelValue': [value: boolean]; saved: [risk: DeliveryRisk, project: DeliveryProject] }>()
const saving = ref(false)
const form = reactive({ title: '', level: '中' as DeliveryRisk['level'], owner: '', dueDate: '', response: '' })
watch(() => props.modelValue, open => { if (open) Object.assign(form, { title: '', level: '中', owner: props.project?.owner || '', dueDate: '', response: '' }) })
async function submit() {
  if (!props.project || !form.title.trim() || !form.owner || !form.dueDate) { ElMessage.warning('请完整填写风险、责任人和解决日期'); return }
  saving.value = true; await new Promise(resolve => window.setTimeout(resolve, 600)); saving.value = false
  emit('saved', { id: Date.now(), title: form.title, level: form.level, owner: form.owner, dueDate: form.dueDate, status: '待处理' }, props.project); emit('update:modelValue', false)
}
</script>

<template><el-dialog :model-value="modelValue" title="登记交付风险" width="min(560px, 92vw)" destroy-on-close @update:model-value="emit('update:modelValue', $event)"><div v-if="project" class="delivery-dialog-context"><span>所属项目</span><strong>{{ project.code }} · {{ project.name }}</strong><span>当前阶段</span><strong>{{ project.stage }}</strong></div><el-form label-position="top"><el-form-item label="风险描述" required><el-input v-model="form.title" placeholder="描述具体风险及影响" maxlength="80" show-word-limit /></el-form-item><div class="delivery-form-grid"><el-form-item label="风险等级"><el-segmented v-model="form.level" :options="['高', '中', '低']" /></el-form-item><el-form-item label="责任人" required><el-select v-model="form.owner" filterable><el-option v-for="item in owners" :key="item" :label="item" :value="item" /></el-select></el-form-item><el-form-item label="计划解决日期" required><el-date-picker v-model="form.dueDate" type="date" value-format="YYYY-MM-DD" /></el-form-item><el-form-item label="应对措施" class="is-wide"><el-input v-model="form.response" type="textarea" :rows="3" /></el-form-item></div></el-form><template #footer><el-button @click="emit('update:modelValue', false)">取消</el-button><el-button type="primary" :loading="saving" @click="submit">保存风险</el-button></template></el-dialog></template>
