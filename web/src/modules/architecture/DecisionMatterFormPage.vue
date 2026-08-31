<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ArrowLeft } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../../stores/auth'
import { apiErrorMessage } from '../../api/error'
import UiPageHeader from '../../components/ui/UiPageHeader.vue'
import { createDecisionMatter, getDecisionMatter, updateDecisionMatter } from './api'
import type { DecisionMatterDetail } from './types'
import { httpStatus } from './utils'
import './architecture.css'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()
const editingId = computed(() => route.params.id ? Number(route.params.id) : null)
const loading = ref(false)
const saving = ref(false)
const forbidden = ref(false)
const loadError = ref('')
const form = reactive({ title: '', problem: '' })
let rowVersion = 0

const canEdit = computed(() => ['architecture:decision:propose', 'architecture:decision:review', 'architecture:decision:manage'].some(p => auth.hasPermission(p)))

onMounted(async () => {
  if (!editingId.value) return
  loading.value = true
  try {
    const matter = await getDecisionMatter(editingId.value) as DecisionMatterDetail
    form.title = matter.title
    form.problem = matter.problem
    rowVersion = matter.rowVersion
  } catch (error) {
    if (httpStatus(error) === 403) { forbidden.value = true } else { loadError.value = apiErrorMessage(error, '加载失败') }
  } finally {
    loading.value = false
  }
})

async function save() {
  if (!form.title.trim()) { ElMessage.warning('请填写标题'); return }
  if (!form.problem.trim()) { ElMessage.warning('请填写问题或困难描述'); return }
  if (!canEdit.value) { ElMessage.warning('当前账号无权提交事项'); return }
  saving.value = true
  try {
    if (editingId.value) {
      await updateDecisionMatter(editingId.value, { rowVersion, title: form.title.trim(), problem: form.problem.trim() })
    } else {
      await createDecisionMatter({ title: form.title.trim(), problem: form.problem.trim() })
    }
    ElMessage.success(editingId.value ? '事项已保存' : '事项已提交')
    router.push('/architecture/decisions')
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '保存失败'))
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div class="architecture-page">
    <el-button :icon="ArrowLeft" link @click="router.push('/architecture/decisions')">返回事项列表</el-button>
    <UiPageHeader eyebrow="ARCHITECTURE DECISION" :title="editingId ? '编辑架构决策事项' : '提交架构决策事项'"
                  description="围绕一个明确问题或困难提交事项；方案、影响分析与争议点可以后续协作补齐。" />

    <UiEmptyState v-if="forbidden" title="无访问权限" description="当前账号缺少提交权限，请联系管理员授权。" />
    <UiEmptyState v-else-if="loadError" title="加载失败" :description="loadError" />

    <el-card v-else shadow="never" class="ui-surface-card" v-loading="loading">
      <el-form label-position="top" class="decision-form">
        <el-form-item label="事项标题" required>
          <el-input v-model="form.title" maxlength="300" show-word-limit placeholder="一句话概括问题或困难" />
        </el-form-item>
        <el-form-item label="问题或困难描述" required>
          <el-input v-model="form.problem" type="textarea" :rows="8" placeholder="描述背景、现状与需要架构组决策的问题；方案、影响分析、争议点可以后续补充" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="ui-toolbar__actions">
          <el-button @click="router.push('/architecture/decisions')">取消</el-button>
          <el-button type="primary" :loading="saving" @click="save">{{ editingId ? '保存' : '提交事项' }}</el-button>
        </div>
      </template>
    </el-card>
  </div>
</template>
