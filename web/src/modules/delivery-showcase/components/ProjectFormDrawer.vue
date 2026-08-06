<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage } from 'element-plus'
import UiFormDrawer from '../../../components/ui/UiFormDrawer.vue'
import { departments, emptyDraft, owners } from '../mock'
import type { DeliveryProject, ProjectDraft } from '../types'

const props = defineProps<{ modelValue: boolean; project?: DeliveryProject | null }>()
const emit = defineEmits<{ 'update:modelValue': [value: boolean]; saved: [draft: ProjectDraft, project?: DeliveryProject | null] }>()
const formRef = ref<FormInstance>()
const saving = ref(false)
const form = reactive<ProjectDraft>(emptyDraft())
const dateRange = ref<[string, string] | null>(null)
const title = computed(() => props.project ? `编辑交付项目 · ${props.project.code}` : '新建交付项目')
const rules: FormRules<ProjectDraft> = {
  name: [{ required: true, message: '请输入项目名称', trigger: 'blur' }, { min: 4, max: 40, message: '项目名称应为 4-40 个字符', trigger: 'blur' }],
  owner: [{ required: true, message: '请选择负责人', trigger: 'change' }],
  department: [{ required: true, message: '请选择负责团队', trigger: 'change' }],
  description: [{ required: true, message: '请说明交付目标', trigger: 'blur' }]
}
watch(() => props.modelValue, open => {
  if (!open) return
  const value = props.project ? { name: props.project.name, type: props.project.type, priority: props.project.priority, owner: props.project.owner, department: props.project.department, startDate: props.project.startDate, endDate: props.project.endDate, budget: props.project.budget, description: props.project.description, members: [...props.project.members], deliveryMode: '常规发布' as const, dataMigration: props.project.type === '数据迁移' } : emptyDraft()
  Object.assign(form, value); dateRange.value = value.startDate && value.endDate ? [value.startDate, value.endDate] : null
})
watch(dateRange, value => { form.startDate = value?.[0] || ''; form.endDate = value?.[1] || '' })
async function submit() {
  if (!(await formRef.value?.validate().catch(() => false))) return
  if (!dateRange.value) { ElMessage.warning('请选择计划周期'); return }
  saving.value = true
  await new Promise(resolve => window.setTimeout(resolve, 700))
  saving.value = false; emit('saved', { ...form, members: [...form.members] }, props.project); emit('update:modelValue', false)
}
</script>

<template>
  <UiFormDrawer :model-value="modelValue" :title="title" width="min(640px, 94vw)" :loading="saving" @update:model-value="emit('update:modelValue', $event)" @submit="submit">
    <el-alert title="示范数据只保存在当前页面会话中" type="info" :closable="false" show-icon />
    <el-form ref="formRef" :model="form" :rules="rules" label-position="top" class="delivery-standard-form">
      <div class="delivery-form-section"><h4>基本信息</h4><div class="delivery-form-grid"><el-form-item label="项目名称" prop="name" class="is-wide"><el-input v-model="form.name" maxlength="40" show-word-limit placeholder="例如：统一支付能力升级" /></el-form-item><el-form-item label="项目类型"><el-select v-model="form.type"><el-option v-for="item in ['产品迭代', '系统建设', '数据迁移', '基础设施']" :key="item" :label="item" :value="item" /></el-select></el-form-item><el-form-item label="优先级"><el-segmented v-model="form.priority" :options="['P0', 'P1', 'P2']" /></el-form-item><el-form-item label="负责人" prop="owner"><el-select v-model="form.owner" filterable><el-option v-for="item in owners" :key="item" :label="item" :value="item" /></el-select></el-form-item><el-form-item label="负责团队" prop="department"><el-select v-model="form.department"><el-option v-for="item in departments" :key="item" :label="item" :value="item" /></el-select></el-form-item></div></div>
      <div class="delivery-form-section"><h4>交付计划</h4><div class="delivery-form-grid"><el-form-item label="计划周期" required class="is-wide"><el-date-picker v-model="dateRange" type="daterange" value-format="YYYY-MM-DD" start-placeholder="开始日期" end-placeholder="完成日期" /></el-form-item><el-form-item label="预算（万元）"><el-input-number v-model="form.budget" :min="0" :max="9999" :step="10" controls-position="right" /></el-form-item><el-form-item label="发布方式"><el-select v-model="form.deliveryMode"><el-option v-for="item in ['常规发布', '灰度发布', '双轨运行']" :key="item" :label="item" :value="item" /></el-select></el-form-item><el-form-item label="包含数据迁移"><el-switch v-model="form.dataMigration" /></el-form-item><el-form-item label="项目成员" class="is-wide"><el-select v-model="form.members" multiple filterable collapse-tags><el-option v-for="item in owners" :key="item" :label="item" :value="item" /></el-select></el-form-item><el-form-item label="交付目标" prop="description" class="is-wide"><el-input v-model="form.description" type="textarea" :rows="4" maxlength="200" show-word-limit /></el-form-item></div></div>
    </el-form>
  </UiFormDrawer>
</template>
