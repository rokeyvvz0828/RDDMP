<script setup lang="ts">
import { computed, nextTick, reactive, ref, watch } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage, ElMessageBox } from 'element-plus'
import UiFormDrawer from '../../../components/ui/UiFormDrawer.vue'
import { apiErrorMessage } from '../../../api/error'
import {
  createPhysicalSubsystem,
  loadLogicalSubsystemOptions,
  loadOrganizationOptions,
  loadParameterOptions,
  loadUserOptions,
  updatePhysicalSubsystem
} from '../api'
import { useLatestOptions } from '../useLatestOptions'
import { cancelled, normalizeText } from '../utils'
import type { LogicalSubsystemOption, OrganizationOption, ParameterOption, PhysicalSubsystem, PhysicalSubsystemCommand, UserOption } from '../types'

const props = defineProps<{ modelValue: boolean; record?: PhysicalSubsystem | null }>()
const emit = defineEmits<{ 'update:modelValue': [value: boolean]; saved: [record: PhysicalSubsystem] }>()
const formRef = ref<FormInstance>()
const drawerKey = ref(0)
const allowConfirmedClose = ref(false)
const saving = ref(false)
const baseline = ref('')
const invalidTeamRequiresReselection = ref(false)
const runtimes = ref<ParameterOption[]>([])
const levels = ref<ParameterOption[]>([])
const frameworks = ref<ParameterOption[]>([])
const form = reactive<PhysicalSubsystemCommand>(emptyForm())
const organizations = useLatestOptions(keyword => loadOrganizationOptions('physical-subsystem', keyword))
const users = useLatestOptions(keyword => loadUserOptions('physical-subsystem', keyword))
const logicalSubsystems = useLatestOptions(loadLogicalSubsystemOptions)
const title = computed(() => props.record ? `编辑物理子系统 · ${props.record.code}` : '新建物理子系统')
const dirty = computed(() => JSON.stringify(form) !== baseline.value)

const selectedLogicalFallback = computed<LogicalSubsystemOption | null>(() => {
  if (!props.record || logicalSubsystems.options.value.some(item => item.id === props.record?.logicalSubsystemId)) return null
  return { id: props.record.logicalSubsystemId, code: props.record.logicalSubsystemCode, name: props.record.logicalSubsystemName }
})
const selectedTeamFallback = computed<OrganizationOption | null>(() => {
  if (!props.record?.responsibleTeamValid || organizations.options.value.some(item => item.id === props.record?.responsibleTeamOrgId)) return null
  return { id: props.record.responsibleTeamOrgId, name: props.record.responsibleTeamDisplayName, parentId: null, pathLabel: props.record.responsibleTeamDisplayName }
})
const selectedOwnerFallback = computed<UserOption | null>(() => userFallback(props.record?.ownerUserId, props.record?.ownerDisplayName))
const selectedContactFallback = computed<UserOption | null>(() => userFallback(props.record?.contactUserId, props.record?.contactDisplayName, props.record?.contactPhone))

const rules: FormRules<PhysicalSubsystemCommand> = {
  code: [
    { required: true, message: '请输入系统编号', trigger: 'blur' },
    { pattern: /^[A-Z0-9_-]{2,32}$/, message: '编号为 2-32 位大写字母、数字、连字符或下划线', trigger: 'blur' }
  ],
  shortName: [{ required: true, message: '请输入系统简称', trigger: 'blur' }, { max: 100, message: '简称最多 100 个字符', trigger: 'blur' }],
  name: [{ required: true, message: '请输入系统名称', trigger: 'blur' }, { max: 200, message: '名称最多 200 个字符', trigger: 'blur' }],
  logicalSubsystemId: [{ required: true, message: '请选择所属逻辑子系统', trigger: 'change' }],
  businessGroupName: [{ max: 100, message: '所属事业群最多 100 个字符', trigger: 'blur' }],
  responsibleTeamOrgId: [{ required: true, message: '请选择负责团队', trigger: 'change' }],
  description: [{ max: 2000, message: '系统描述最多 2000 个字符', trigger: 'blur' }],
  remark: [{ max: 1000, message: '备注最多 1000 个字符', trigger: 'blur' }]
}

function emptyForm(): PhysicalSubsystemCommand {
  return { code: '', shortName: '', name: '', logicalSubsystemId: null, businessGroupName: null, responsibleTeamOrgId: null, runtimeCode: null, systemLevelCode: null, developmentFrameworkCode: null, ownerUserId: null, contactUserId: null, description: null, remark: null }
}

function userFallback(id?: number | null, name?: string | null, phone?: string | null): UserOption | null {
  if (!id || users.options.value.some(item => item.id === id)) return null
  return { id, displayName: name || `用户 #${id}`, username: '历史引用', phone: phone || null }
}

async function initialize() {
  invalidTeamRequiresReselection.value = Boolean(props.record && !props.record.responsibleTeamValid)
  const value = props.record
    ? { code: props.record.code, shortName: props.record.shortName, name: props.record.name, logicalSubsystemId: props.record.logicalSubsystemId, businessGroupName: props.record.businessGroupName, responsibleTeamOrgId: props.record.responsibleTeamValid ? props.record.responsibleTeamOrgId : null, runtimeCode: props.record.runtimeCode, systemLevelCode: props.record.systemLevelCode, developmentFrameworkCode: props.record.developmentFrameworkCode, ownerUserId: props.record.ownerUserId, contactUserId: props.record.contactUserId, description: props.record.description, remark: props.record.remark }
    : emptyForm()
  Object.assign(form, value)
  await nextTick()
  formRef.value?.clearValidate()
  baseline.value = JSON.stringify(form)
  const results = await Promise.allSettled([
    organizations.loadNow(), users.loadNow(), logicalSubsystems.loadNow(),
    loadParameterOptions('physical-subsystem', 'ARCH_RUNTIME'),
    loadParameterOptions('physical-subsystem', 'ARCH_SYSTEM_LEVEL'),
    loadParameterOptions('physical-subsystem', 'ARCH_DEVELOPMENT_FRAMEWORK')
  ])
  if (results[3].status === 'fulfilled') runtimes.value = results[3].value
  if (results[4].status === 'fulfilled') levels.value = results[4].value
  if (results[5].status === 'fulfilled') frameworks.value = results[5].value
  if (results.some(item => item.status === 'rejected')) ElMessage.warning('部分选项加载失败，可关闭后重试')
}

watch(() => props.modelValue, open => { if (open) void initialize() })
watch(() => form.responsibleTeamOrgId, value => { if (value) invalidTeamRequiresReselection.value = false })

function normalize() {
  form.code = form.code.trim().toUpperCase()
  form.shortName = form.shortName.trim()
  form.name = form.name.trim()
  form.businessGroupName = normalizeText(form.businessGroupName)
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
      ? await updatePhysicalSubsystem(props.record.id, { ...form })
      : await createPhysicalSubsystem({ ...form })
    baseline.value = JSON.stringify(form)
    ElMessage.success(props.record ? '物理子系统已更新' : '物理子系统已创建')
    emit('saved', saved)
    emit('update:modelValue', false)
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '物理子系统保存失败'))
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
function logicalSubsystemsVisible(open: boolean) { if (open) void logicalSubsystems.loadNow() }
</script>

<template>
  <UiFormDrawer :key="drawerKey" :model-value="modelValue" :title="title" width="min(720px, calc(100vw - 24px))" :loading="saving" @update:model-value="requestClose" @submit="submit">
    <el-alert v-if="invalidTeamRequiresReselection" class="architecture-form-alert" type="warning" :closable="false" show-icon title="原负责团队已失效，保存前必须重新选择当前有效团队。" />
    <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
      <section class="architecture-form-section">
        <h3>基本信息</h3>
        <div class="architecture-form-grid">
          <el-form-item label="系统编号" prop="code"><el-input v-model="form.code" maxlength="32" placeholder="例如：PHY_PORTAL" @blur="form.code = form.code.trim().toUpperCase()" /></el-form-item>
          <el-form-item label="系统简称" prop="shortName"><el-input v-model="form.shortName" maxlength="100" /></el-form-item>
          <el-form-item label="系统名称" prop="name" class="is-wide"><el-input v-model="form.name" maxlength="200" /></el-form-item>
          <el-form-item label="所属逻辑子系统" prop="logicalSubsystemId">
            <el-select v-model="form.logicalSubsystemId" filterable remote clearable :remote-method="logicalSubsystems.search" :loading="logicalSubsystems.loading.value" placeholder="搜索逻辑系统编号或名称" @visible-change="logicalSubsystemsVisible">
              <el-option v-if="selectedLogicalFallback" :key="selectedLogicalFallback.id" :label="`${selectedLogicalFallback.name}（${selectedLogicalFallback.code}）`" :value="selectedLogicalFallback.id" />
              <el-option v-for="item in logicalSubsystems.options.value" :key="item.id" :label="`${item.name}（${item.code}）`" :value="item.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="所属事业群" prop="businessGroupName"><el-input v-model="form.businessGroupName" clearable maxlength="100" placeholder="可选，填写事业群名称" /></el-form-item>
          <el-form-item label="负责团队" prop="responsibleTeamOrgId" class="is-wide">
            <el-select v-model="form.responsibleTeamOrgId" filterable remote clearable :remote-method="organizations.search" :loading="organizations.loading.value" placeholder="必选，搜索组织名称或路径" @visible-change="organizationsVisible">
              <el-option v-if="selectedTeamFallback" :key="selectedTeamFallback.id" :label="selectedTeamFallback.pathLabel" :value="selectedTeamFallback.id" />
              <el-option v-for="item in organizations.options.value" :key="item.id" :label="item.pathLabel" :value="item.id" />
            </el-select>
          </el-form-item>
        </div>
      </section>
      <section class="architecture-form-section">
        <h3>系统分类与人员</h3>
        <div class="architecture-form-grid">
          <el-form-item label="系统运行时间"><el-select v-model="form.runtimeCode" clearable><el-option v-for="item in runtimes" :key="item.code" :label="item.label" :value="item.code" /></el-select></el-form-item>
          <el-form-item label="系统级别"><el-select v-model="form.systemLevelCode" clearable><el-option v-for="item in levels" :key="item.code" :label="item.label" :value="item.code" /></el-select></el-form-item>
          <el-form-item label="开发平台框架"><el-select v-model="form.developmentFrameworkCode" clearable><el-option v-for="item in frameworks" :key="item.code" :label="item.label" :value="item.code" /></el-select></el-form-item>
          <el-form-item label="负责人">
            <el-select v-model="form.ownerUserId" filterable remote clearable :remote-method="users.search" :loading="users.loading.value" placeholder="可选，搜索姓名、用户名或电话" @visible-change="usersVisible">
              <el-option v-if="selectedOwnerFallback" :key="selectedOwnerFallback.id" :label="selectedOwnerFallback.displayName" :value="selectedOwnerFallback.id" />
              <el-option v-for="item in users.options.value" :key="item.id" :label="`${item.displayName}（${item.username}）`" :value="item.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="联系人" class="is-wide">
            <el-select v-model="form.contactUserId" filterable remote clearable :remote-method="users.search" :loading="users.loading.value" placeholder="可选，搜索姓名、用户名或电话" @visible-change="usersVisible">
              <el-option v-if="selectedContactFallback" :key="selectedContactFallback.id" :label="selectedContactFallback.displayName" :value="selectedContactFallback.id" />
              <el-option v-for="item in users.options.value" :key="item.id" :label="`${item.displayName}（${item.username}）`" :value="item.id" />
            </el-select>
          </el-form-item>
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
