<script setup lang="ts">
import { computed, nextTick, reactive, ref, watch } from 'vue'
import { ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import UiFormDrawer from '../../../components/ui/UiFormDrawer.vue'
import type { CreateProjectCommand, ProjectSummary } from '../../../types/project-context'

const props = withDefaults(defineProps<{
  modelValue: boolean
  project?: ProjectSummary | null
  saving?: boolean
  error?: string
}>(), { project: null, saving: false, error: '' })
const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  submit: [command: CreateProjectCommand]
}>()

const formRef = ref<FormInstance>()
const form = reactive<CreateProjectCommand>({ projectCode: '', projectName: '' })
const initialSnapshot = ref('')
const title = computed(() => props.project ? `编辑项目 · ${props.project.projectCode}` : '新建项目')
const dirty = computed(() => props.modelValue && JSON.stringify(form) !== initialSnapshot.value)
const rules: FormRules<CreateProjectCommand> = {
  projectCode: [
    { required: true, message: '请输入项目编号', trigger: 'blur' },
    { max: 64, message: '项目编号不能超过 64 个字符', trigger: 'blur' },
    { pattern: /^[A-Za-z0-9][A-Za-z0-9._-]*$/, message: '项目编号只能包含字母、数字、点、下划线和短横线', trigger: 'blur' }
  ],
  projectName: [
    { required: true, message: '请输入项目名称', trigger: 'blur' },
    { max: 128, message: '项目名称不能超过 128 个字符', trigger: 'blur' }
  ]
}

watch(() => props.modelValue, open => {
  if (!open) return
  form.projectCode = props.project?.projectCode || ''
  form.projectName = props.project?.projectName || ''
  initialSnapshot.value = JSON.stringify(form)
  void nextTick(() => formRef.value?.clearValidate())
})

async function requestClose(value: boolean) {
  if (value) {
    emit('update:modelValue', true)
    return
  }
  if (props.saving) return
  if (dirty.value) {
    try {
      await ElMessageBox.confirm('尚未保存的项目资料将丢失。', '放弃未保存内容', {
        type: 'warning',
        confirmButtonText: '放弃修改',
        cancelButtonText: '继续编辑'
      })
    } catch {
      return
    }
  }
  emit('update:modelValue', false)
}

async function submit() {
  if (props.saving || !(await formRef.value?.validate().catch(() => false))) return
  emit('submit', { projectCode: form.projectCode.trim(), projectName: form.projectName.trim() })
}
</script>

<template>
  <UiFormDrawer
    :model-value="modelValue"
    :title="title"
    width="min(600px, calc(100vw - 24px))"
    :loading="saving"
    :confirm-text="project ? '保存修改' : '创建项目'"
    @update:model-value="requestClose"
    @submit="submit"
  >
    <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon class="project-form-alert" />
    <el-form ref="formRef" :model="form" :rules="rules" label-position="top" class="project-form">
      <div class="project-form__section">
        <h3>基本信息</h3>
        <el-form-item label="项目编号" prop="projectCode">
          <el-input
            v-model="form.projectCode"
            :disabled="Boolean(project)"
            maxlength="64"
            show-word-limit
            placeholder="例如：ASSET-LIBRARY"
          />
          <small>创建后不可修改，租户内唯一。</small>
        </el-form-item>
        <el-form-item label="项目名称" prop="projectName">
          <el-input v-model="form.projectName" maxlength="128" show-word-limit placeholder="请输入便于识别的项目名称" />
        </el-form-item>
      </div>
      <el-alert
        v-if="project?.status === 'ARCHIVED'"
        title="归档项目为只读状态，不能修改项目资料"
        type="warning"
        :closable="false"
        show-icon
      />
    </el-form>
  </UiFormDrawer>
</template>
