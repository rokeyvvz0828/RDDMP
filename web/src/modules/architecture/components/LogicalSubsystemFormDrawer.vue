<script setup lang="ts">
import { computed, nextTick, reactive, ref, watch } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage, ElMessageBox } from 'element-plus'
import UiFormDrawer from '../../../components/ui/UiFormDrawer.vue'
import { apiErrorMessage } from '../../../api/error'
import { createLogicalSubsystem, loadOrganizationOptions, loadParameterOptions, loadUserOptions, updateLogicalSubsystem } from '../api'
import { useLatestOptions } from '../useLatestOptions'
import { cancelled, canonicalOptionCode, normalizeText } from '../utils'
import type { LogicalSubsystem, LogicalSubsystemCommand, ParameterOption } from '../types'

const props = defineProps<{ modelValue: boolean; record?: LogicalSubsystem | null }>()
const emit = defineEmits<{ 'update:modelValue': [value: boolean]; saved: [record: LogicalSubsystem] }>()
const formRef = ref<FormInstance>()
const drawerKey = ref(0)
const allowConfirmedClose = ref(false)
const saving = ref(false)
const baseline = ref('')
const deploymentPlatforms = ref<ParameterOption[]>([])
const systemTypes = ref<ParameterOption[]>([])
const ownerships = ref<ParameterOption[]>([])
const form = reactive<LogicalSubsystemCommand>(emptyForm())
const organizations = useLatestOptions(keyword => loadOrganizationOptions('logical-subsystem', keyword))
const users = useLatestOptions(keyword => loadUserOptions('logical-subsystem', keyword))
const title = computed(() => props.record ? `编辑逻辑子系统 · ${props.record.code}` : '新建逻辑子系统')
const dirty = computed(() => JSON.stringify(form) !== baseline.value)

const rules: FormRules<LogicalSubsystemCommand> = {
  code: [
    { required: true, message: '请输入系统编号', trigger: 'blur' },
    { pattern: /^[A-Z0-9_-]{2,32}$/, message: '编号为 2-32 位大写字母、数字、连字符或下划线', trigger: 'blur' }
  ],
  shortName: [{ required: true, message: '请输入系统简称', trigger: 'blur' }, { max: 100, message: '简称最多 100 个字符', trigger: 'blur' }],
  name: [{ required: true, message: '请输入系统名称', trigger: 'blur' }, { max: 200, message: '名称最多 200 个字符', trigger: 'blur' }],
  businessOrgId: [{ required: true, message: '请选择所属事业群', trigger: 'change' }],
  contactUserId: [{ required: true, message: '请选择联系人', trigger: 'change' }],
  description: [{ max: 2000, message: '系统描述最多 2000 个字符', trigger: 'blur' }],
  remark: [{ max: 1000, message: '备注最多 1000 个字符', trigger: 'blur' }]
}

function emptyForm(): LogicalSubsystemCommand {
  return { code: '', shortName: '', name: '', businessOrgId: null, deploymentPlatformCode: null, systemTypeCode: null, systemOwnershipCode: null, contactUserId: null, description: null, remark: null }
}

async function initialize() {
  const value = props.record
    ? { code: props.record.code, shortName: props.record.shortName, name: props.record.name, businessOrgId: props.record.businessOrgId, deploymentPlatformCode: props.record.deploymentPlatformCode, systemTypeCode: props.record.systemTypeCode, systemOwnershipCode: props.record.systemOwnershipCode, contactUserId: props.record.contactUserId, description: props.record.description, remark: props.record.remark }
    : emptyForm()
  Object.assign(form, value)
  await nextTick()
  formRef.value?.clearValidate()
  baseline.value = JSON.stringify(form)
  const results = await Promise.allSettled([
    organizations.loadNow(), users.loadNow(),
    loadParameterOptions('logical-subsystem', 'ARCH_DEPLOYMENT_PLATFORM'),
    loadParameterOptions('logical-subsystem', 'ARCH_SYSTEM_TYPE'),
    loadParameterOptions('logical-subsystem', 'ARCH_SYSTEM_OWNERSHIP')
  ])
  if (results[2].status === 'fulfilled') deploymentPlatforms.value = results[2].value
  if (results[3].status === 'fulfilled') systemTypes.value = results[3].value
  if (results[4].status === 'fulfilled') ownerships.value = results[4].value
  const beforeCanonical = JSON.stringify(form)
  form.deploymentPlatformCode = canonicalOptionCode(deploymentPlatforms.value, form.deploymentPlatformCode)
  form.systemTypeCode = canonicalOptionCode(systemTypes.value, form.systemTypeCode)
  form.systemOwnershipCode = canonicalOptionCode(ownerships.value, form.systemOwnershipCode)
  if (baseline.value === beforeCanonical) baseline.value = JSON.stringify(form)
  if (results.some(item => item.status === 'rejected')) ElMessage.warning('部分选项加载失败，可关闭后重试')
}

watch(() => props.modelValue, open => { if (open) void initialize() })

function normalize() {
  form.code = form.code.trim().toUpperCase()
  form.shortName = form.shortName.trim()
  form.name = form.name.trim()
  form.description = normalizeText(form.description)
  form.remark = normalizeText(form.remark)
}

async function submit() {
  if (saving.value) return
  normalize()
  if (!(await formRef.value?.validate().catch(() => false))) return
  saving.value = true
  try {
    const saved = props.record
      ? await updateLogicalSubsystem(props.record.id, { ...form })
      : await createLogicalSubsystem({ ...form })
    baseline.value = JSON.stringify(form)
    ElMessage.success(props.record ? '逻辑子系统已更新' : '逻辑子系统已创建')
    emit('saved', saved)
    emit('update:modelValue', false)
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '逻辑子系统保存失败'))
  } finally {
    saving.value = false
  }
}

async function requestClose(value: boolean) {
  if (!value && saving.value) return
  if (!value && allowConfirmedClose.value) {
    allowConfirmedClose.value = false
    emit('update:modelValue', false)
    return
  }
  if (value || !dirty.value) { emit('update:modelValue', value); return }
  try {
    await ElMessageBox.confirm('当前表单有未保存修改，关闭后这些修改将丢失。', '放弃修改', { type: 'warning', confirmButtonText: '放弃并关闭', cancelButtonText: '继续编辑' })
    allowConfirmedClose.value = true
    emit('update:modelValue', false)
  } catch (error) {
    if (!cancelled(error)) {
      ElMessage.error('关闭表单失败')
      return
    }
    // Element Plus 已开始关闭内部抽屉；重建本模块包装实例，保留响应式草稿并恢复可见。
    drawerKey.value += 1
  }
}
function organizationsVisible(open: boolean) { if (open) void organizations.loadNow() }
function usersVisible(open: boolean) { if (open) void users.loadNow() }
</script>

<template>
  <UiFormDrawer :key="drawerKey" :model-value="modelValue" :title="title" width="min(680px, calc(100vw - 24px))" :loading="saving" @update:model-value="requestClose" @submit="submit">
    <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
      <section class="architecture-form-section">
        <h3>基本信息</h3>
        <div class="architecture-form-grid">
          <el-form-item label="系统编号" prop="code"><el-input v-model="form.code" maxlength="32" placeholder="例如：LOG_CHANNEL" @blur="form.code = form.code.trim().toUpperCase()" /></el-form-item>
          <el-form-item label="系统简称" prop="shortName"><el-input v-model="form.shortName" maxlength="100" /></el-form-item>
          <el-form-item label="系统名称" prop="name" class="is-wide"><el-input v-model="form.name" maxlength="200" /></el-form-item>
          <el-form-item label="所属事业群" prop="businessOrgId">
            <el-select v-model="form.businessOrgId" filterable remote clearable :remote-method="organizations.search" :loading="organizations.loading.value" placeholder="搜索组织名称或路径" @visible-change="organizationsVisible">
              <el-option v-for="item in organizations.options.value" :key="item.id" :label="item.pathLabel" :value="item.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="联系人" prop="contactUserId">
            <el-select v-model="form.contactUserId" filterable remote clearable :remote-method="users.search" :loading="users.loading.value" placeholder="搜索姓名、用户名或电话" @visible-change="usersVisible">
              <el-option v-for="item in users.options.value" :key="item.id" :label="`${item.displayName}（${item.username}）`" :value="item.id" />
            </el-select>
          </el-form-item>
        </div>
      </section>
      <section class="architecture-form-section">
        <h3>系统分类</h3>
        <div class="architecture-form-grid">
          <el-form-item label="部署平台"><el-select v-model="form.deploymentPlatformCode" clearable><el-option v-for="item in deploymentPlatforms" :key="item.code" :label="item.label" :value="item.code" /></el-select></el-form-item>
          <el-form-item label="系统类型"><el-select v-model="form.systemTypeCode" clearable><el-option v-for="item in systemTypes" :key="item.code" :label="item.label" :value="item.code" /></el-select></el-form-item>
          <el-form-item label="系统归属"><el-select v-model="form.systemOwnershipCode" clearable><el-option v-for="item in ownerships" :key="item.code" :label="item.label" :value="item.code" /></el-select></el-form-item>
        </div>
      </section>
      <section class="architecture-form-section">
        <h3>补充说明</h3>
        <div class="architecture-form-grid">
          <el-form-item label="系统描述" prop="description" class="is-wide"><el-input v-model="form.description" type="textarea" :rows="4" maxlength="2000" show-word-limit /></el-form-item>
          <el-form-item label="备注" prop="remark" class="is-wide"><el-input v-model="form.remark" type="textarea" :rows="3" maxlength="1000" show-word-limit /></el-form-item>
        </div>
      </section>
    </el-form>
  </UiFormDrawer>
</template>
