<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { departments, emptyDraft, owners } from '../mock'
import type { ProjectDraft } from '../types'

const props = defineProps<{ modelValue: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [value: boolean]; saved: [draft: ProjectDraft] }>()
const step = ref(0)
const saving = ref(false)
const form = reactive<ProjectDraft>(emptyDraft())
const dateRange = ref<[string, string] | null>(null)
watch(() => props.modelValue, open => { if (open) { Object.assign(form, emptyDraft()); dateRange.value = null; step.value = 0 } })
function next() {
  if (step.value === 0 && (!form.name.trim() || !form.owner || !form.department)) { ElMessage.warning('请完整填写项目名称、负责人和负责团队'); return }
  if (step.value === 1 && !dateRange.value) { ElMessage.warning('请选择计划周期'); return }
  if (dateRange.value) { form.startDate = dateRange.value[0]; form.endDate = dateRange.value[1] }
  step.value = Math.min(3, step.value + 1)
}
async function submit() { saving.value = true; await new Promise(resolve => window.setTimeout(resolve, 850)); saving.value = false; emit('saved', { ...form, members: [...form.members] }); emit('update:modelValue', false) }
</script>

<template>
  <el-dialog :model-value="modelValue" title="分步新建交付项目" width="min(900px, 96vw)" top="5vh" destroy-on-close class="delivery-wizard-dialog" @update:model-value="emit('update:modelValue', $event)">
    <el-steps :active="step" finish-status="success" align-center><el-step title="基本信息" /><el-step title="计划与资源" /><el-step title="交付策略" /><el-step title="确认提交" /></el-steps>
    <div class="delivery-wizard-body">
      <el-form v-if="step === 0" label-position="top" class="delivery-form-grid"><el-form-item label="项目名称" required class="is-wide"><el-input v-model="form.name" placeholder="请输入项目名称" maxlength="40" show-word-limit /></el-form-item><el-form-item label="项目类型"><el-select v-model="form.type"><el-option v-for="item in ['产品迭代', '系统建设', '数据迁移', '基础设施']" :key="item" :label="item" :value="item" /></el-select></el-form-item><el-form-item label="优先级"><el-radio-group v-model="form.priority"><el-radio-button value="P0">P0</el-radio-button><el-radio-button value="P1">P1</el-radio-button><el-radio-button value="P2">P2</el-radio-button></el-radio-group></el-form-item><el-form-item label="负责人" required><el-select v-model="form.owner" filterable><el-option v-for="item in owners" :key="item" :label="item" :value="item" /></el-select></el-form-item><el-form-item label="负责团队" required><el-select v-model="form.department"><el-option v-for="item in departments" :key="item" :label="item" :value="item" /></el-select></el-form-item></el-form>
      <el-form v-else-if="step === 1" label-position="top" class="delivery-form-grid"><el-form-item label="计划周期" required class="is-wide"><el-date-picker v-model="dateRange" type="daterange" value-format="YYYY-MM-DD" start-placeholder="开始日期" end-placeholder="完成日期" /></el-form-item><el-form-item label="项目预算（万元）"><el-input-number v-model="form.budget" :min="0" :max="9999" controls-position="right" /></el-form-item><el-form-item label="项目成员" class="is-wide"><el-select v-model="form.members" multiple filterable><el-option v-for="item in owners" :key="item" :label="item" :value="item" /></el-select></el-form-item></el-form>
      <el-form v-else-if="step === 2" label-position="top"><el-form-item label="发布方式"><el-radio-group v-model="form.deliveryMode"><el-radio value="常规发布" border>常规发布</el-radio><el-radio value="灰度发布" border>灰度发布</el-radio><el-radio value="双轨运行" border>双轨运行</el-radio></el-radio-group></el-form-item><el-form-item label="数据迁移"><el-switch v-model="form.dataMigration" active-text="包含迁移" inactive-text="不包含迁移" /></el-form-item><el-form-item label="交付目标"><el-input v-model="form.description" type="textarea" :rows="5" maxlength="200" show-word-limit placeholder="描述业务目标、范围和可验证结果" /></el-form-item><el-form-item label="附件"><el-upload drag action="#" :auto-upload="false" multiple><div class="delivery-upload-text">拖放方案或清单到此处，或点击选择文件</div><template #tip><div class="el-upload__tip">示范模式不会上传文件，单个文件不超过 20MB</div></template></el-upload></el-form-item></el-form>
      <div v-else class="delivery-review"><el-result icon="success" title="信息已准备完成" sub-title="提交后将创建项目并进入交付申请流程。" /><el-descriptions :column="2" border><el-descriptions-item label="项目名称">{{ form.name }}</el-descriptions-item><el-descriptions-item label="项目类型">{{ form.type }}</el-descriptions-item><el-descriptions-item label="负责人">{{ form.owner }}</el-descriptions-item><el-descriptions-item label="负责团队">{{ form.department }}</el-descriptions-item><el-descriptions-item label="计划周期">{{ form.startDate }} 至 {{ form.endDate }}</el-descriptions-item><el-descriptions-item label="发布方式">{{ form.deliveryMode }}</el-descriptions-item><el-descriptions-item label="交付目标" :span="2">{{ form.description || '暂未填写' }}</el-descriptions-item></el-descriptions></div>
    </div>
    <template #footer><el-button @click="emit('update:modelValue', false)">取消</el-button><el-button v-if="step > 0" @click="step--">上一步</el-button><el-button v-if="step < 3" type="primary" @click="next">下一步</el-button><el-button v-else type="primary" :loading="saving" @click="submit">创建并提交</el-button></template>
  </el-dialog>
</template>
