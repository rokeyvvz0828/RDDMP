<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { EditPen, Plus, Refresh, Search, View } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import UiDataTable from '../../components/ui/UiDataTable.vue'
import UiEmptyState from '../../components/ui/UiEmptyState.vue'
import UiPageHeader from '../../components/ui/UiPageHeader.vue'
import UiStatusTag from '../../components/ui/UiStatusTag.vue'
import UiToolbar from '../../components/ui/UiToolbar.vue'
import { apiErrorMessage } from '../../api/error'
import { useAuthStore } from '../../stores/auth'
import {
  changePlanTemplateStatus,
  createPlanTemplate,
  listPlanTemplates,
  listPlanTemplateVersions,
  publishPlanTemplate,
  updatePlanTemplate
} from './planApi'
import type { PlanTemplateView, TemplateStatus, TemplateVersionView } from './planApi'
import './architecture.css'

const router = useRouter()
const auth = useAuthStore()
const canView = computed(() => ['architecture:plan-template:view', 'architecture:plan-template:manage', 'architecture:view', 'architecture:manage'].some(permission => auth.hasPermission(permission)))
const canManage = computed(() => auth.hasPermission('architecture:plan-template:manage') || auth.hasPermission('architecture:manage'))
const forbidden = ref(false)

const loading = ref(false)
const loadError = ref('')
const rows = ref<PlanTemplateView[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)
const filters = reactive({ keyword: '', status: '' as TemplateStatus | '' })

async function load() {
  loading.value = true
  loadError.value = ''
  forbidden.value = false
  try {
    const result = await listPlanTemplates({ keyword: filters.keyword || undefined, status: filters.status || undefined, page: page.value, size: pageSize.value })
    rows.value = result.records
    total.value = result.total
  } catch (error) {
    const message = apiErrorMessage(error, '加载失败')
    if (/403|权限/.test(message)) {
      forbidden.value = true
    } else {
      loadError.value = message
    }
  } finally {
    loading.value = false
  }
}

function reset() {
  filters.keyword = ''
  filters.status = ''
  load()
}

onMounted(load)

const statusLabels: Record<TemplateStatus, string> = { DRAFT: '草稿', ACTIVE: '已启用', INACTIVE: '已停用' }
const statusTones: Record<TemplateStatus, 'primary' | 'success' | 'warning' | 'danger' | 'info'> = { DRAFT: 'warning', ACTIVE: 'success', INACTIVE: 'info' }

// ---------- 新建/编辑模板 ----------
const editVisible = ref(false)
const editSaving = ref(false)
const editForm = reactive({ id: 0, name: '', description: '', rowVersion: 0 })

function openCreate() {
  editForm.id = 0
  editForm.name = ''
  editForm.description = ''
  editForm.rowVersion = 0
  editVisible.value = true
}

function openEdit(row: PlanTemplateView) {
  editForm.id = row.id
  editForm.name = row.name
  editForm.description = row.description || ''
  editForm.rowVersion = row.rowVersion
  editVisible.value = true
}

async function saveTemplate() {
  if (!editForm.name.trim()) {
    ElMessage.warning('请输入模板名称')
    return
  }
  editSaving.value = true
  try {
    if (editForm.id === 0) {
      const created = await createPlanTemplate({ name: editForm.name.trim(), description: editForm.description.trim() || null })
      ElMessage.success('模板已创建，正在打开结构编辑…')
      editVisible.value = false
      openEditor(created.id)
    } else {
      await updatePlanTemplate(editForm.id, {
        name: editForm.name.trim(),
        description: editForm.description.trim() || null,
        rowVersion: editForm.rowVersion
      })
      ElMessage.success('模板已保存')
      editVisible.value = false
      await load()
    }
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, "操作失败"))
  } finally {
    editSaving.value = false
  }
}

// ---------- 状态与发布 ----------
async function toggleStatus(row: PlanTemplateView) {
  const target = row.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE'
  try {
    await changePlanTemplateStatus(row.id, target)
    ElMessage.success(target === 'ACTIVE' ? '模板已启用' : '模板已停用')
    await load()
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, "操作失败"))
  }
}

const publishVisible = ref(false)
const publishSaving = ref(false)
const publishTemplateId = ref(0)
const publishNote = ref('')

function openPublish(row: PlanTemplateView) {
  publishTemplateId.value = row.id
  publishNote.value = ''
  publishVisible.value = true
}

async function doPublish() {
  publishSaving.value = true
  try {
    const version = await publishPlanTemplate(publishTemplateId.value, publishNote.value.trim() || null)
    ElMessage.success(`已发布版本 v${version.versionNo}`)
    publishVisible.value = false
    await load()
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, "操作失败"))
  } finally {
    publishSaving.value = false
  }
}

// ---------- 进入结构编辑页 ----------
function openEditor(id: number) {
  router.push({ name: 'architecture-plan-template-edit', params: { id } })
}

// ---------- 版本历史 ----------
const versionsVisible = ref(false)
const versions = ref<TemplateVersionView[]>([])
const versionsLoading = ref(false)

async function openVersions(row: PlanTemplateView) {
  versionsVisible.value = true
  versionsLoading.value = true
  try {
    versions.value = await listPlanTemplateVersions(row.id)
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, "操作失败"))
  } finally {
    versionsLoading.value = false
  }
}

function snapshotSummary(contentJson: string) {
  try {
    const stages = JSON.parse(contentJson) as Array<{ stageName: string; tasks: Array<{ name: string; checkItems: unknown[] }> }>
    return stages.map(stage => `${stage.stageName}(${stage.tasks.map(task => `${task.name}[${task.checkItems.length}项]`).join('、')})`).join('；')
  } catch {
    return '结构快照（JSON）'
  }
}
</script>

<template>
  <main class="architecture-page">
    <UiPageHeader title="搭建计划模板" description="维护可复用的计划结构并发布版本；已发布版本不可原地改写">
      <template #actions>
        <el-button v-if="canManage" type="primary" @click="openCreate"><el-icon><Plus /></el-icon>新建模板</el-button>
      </template>
    </UiPageHeader>

    <UiToolbar>
      <el-input v-model="filters.keyword" placeholder="模板名称" clearable style="width: 220px" @keyup.enter="load" />
      <el-select v-model="filters.status" placeholder="状态" clearable style="width: 140px">
        <el-option v-for="(label, key) in statusLabels" :key="key" :label="label" :value="key" />
      </el-select>
      <el-button type="primary" @click="load"><el-icon><Search /></el-icon>查询</el-button>
      <el-button @click="reset">重置</el-button>
      <template #actions>
        <el-tooltip content="刷新列表">
          <el-button circle :loading="loading" aria-label="刷新计划模板列表" @click="load"><el-icon><Refresh /></el-icon></el-button>
        </el-tooltip>
      </template>
    </UiToolbar>

    <section v-if="auth.token && !auth.user" v-loading="true" class="architecture-state-panel" aria-label="正在确认访问权限" />
    <section v-else-if="!canView || forbidden" class="architecture-state-panel">
      <el-result icon="warning" title="暂无搭建计划模板查看权限" sub-title="请申请 architecture:plan-template:view 或 manage 权限。" />
    </section>
    <section v-else-if="loadError" class="architecture-state-panel">
      <el-result icon="error" title="计划模板加载失败" :sub-title="loadError">
        <template #extra><el-button type="primary" @click="load">重新加载</el-button></template>
      </el-result>
    </section>

    <template v-else>
      <UiDataTable v-if="rows.length || loading" class="architecture-desktop-table" :data="rows" :loading="loading" row-key="id" border>
        <el-table-column label="模板" min-width="220">
          <template #default="{ row }">
            <button type="button" class="architecture-table-identity" @click="openEditor(row.id)">
              <strong>{{ row.name }}</strong>
              <small>{{ row.description || '暂无说明' }}</small>
            </button>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <UiStatusTag :value="row.status" :labels="statusLabels" :tone="statusTones[row.status as TemplateStatus]" />
          </template>
        </el-table-column>
        <el-table-column label="已发布版本" width="100" align="center">
          <template #default="{ row }">v{{ row.latestVersionNo }}</template>
        </el-table-column>
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <div class="architecture-table-actions">
              <el-button link type="primary" @click="openEditor(row.id)"><el-icon><View /></el-icon>结构编辑</el-button>
              <el-button v-if="canManage" link type="primary" @click="openEdit(row)"><el-icon><EditPen /></el-icon>编辑</el-button>
              <el-button v-if="canManage" link type="primary" @click="openPublish(row)">发布新版本</el-button>
              <el-dropdown v-if="canManage">
                <el-button link>更多</el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item @click="openVersions(row)">版本历史</el-dropdown-item>
                    <el-dropdown-item :disabled="row.status === 'DRAFT'" @click="toggleStatus(row)">
                      {{ row.status === 'ACTIVE' ? '停用模板' : '启用模板' }}
                    </el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>
          </template>
        </el-table-column>
      </UiDataTable>

      <div v-if="rows.length || loading" v-loading="loading" class="architecture-mobile-list" :class="{ 'is-loading': loading }">
        <article v-for="row in rows" :key="row.id">
          <header>
            <div><strong>{{ row.name }}</strong><small>v{{ row.latestVersionNo }}</small></div>
            <UiStatusTag :value="row.status" :labels="statusLabels" :tone="statusTones[row.status as TemplateStatus]" />
          </header>
          <dl>
            <div><dt>说明</dt><dd>{{ row.description || '—' }}</dd></div>
          </dl>
          <footer>
            <el-button link type="primary" @click="openEditor(row.id)"><el-icon><View /></el-icon>结构编辑</el-button>
            <el-button v-if="canManage" link type="primary" @click="openPublish(row)">发布新版本</el-button>
            <el-dropdown v-if="canManage">
              <el-button link>更多</el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item @click="openEdit(row)">编辑</el-dropdown-item>
                  <el-dropdown-item @click="openVersions(row)">版本历史</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </footer>
        </article>
      </div>

      <UiEmptyState v-if="!loading && !rows.length" title="暂无计划模板" description="当前筛选下没有计划模板记录。">
        <template #action>
          <el-button v-if="canManage" type="primary" @click="openCreate">新建模板</el-button>
          <el-button v-else @click="reset">清空筛选</el-button>
        </template>
      </UiEmptyState>

      <nav v-if="rows.length || page > 1" class="architecture-change-pagination" aria-label="计划模板分页">
        <span>第 {{ page }} 页</span>
        <div>
          <el-button :disabled="page <= 1 || loading" @click="page--; load()">上一页</el-button>
          <el-button :disabled="page * pageSize >= total || loading" @click="page++; load()">下一页</el-button>
        </div>
      </nav>
    </template>

    <!-- 新建/编辑模板 -->
    <el-dialog v-model="editVisible" :title="editForm.id === 0 ? '新建计划模板' : '编辑计划模板'" width="min(480px, 96vw)">
      <el-form label-width="90px">
        <el-form-item label="模板名称" required>
          <el-input v-model="editForm.name" maxlength="200" placeholder="如：SIT 环境搭建模板" />
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="editForm.description" type="textarea" :rows="3" maxlength="2000" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="editSaving" @click="saveTemplate">保存</el-button>
      </template>
    </el-dialog>

    <!-- 发布 -->
    <el-dialog v-model="publishVisible" title="发布新版本" width="min(460px, 96vw)">
      <p class="plan-template-publish-tip">发布后生成不可变版本快照，后续模板调整不会影响已创建的计划。</p>
      <el-input v-model="publishNote" placeholder="发布说明（可选）" maxlength="1000" />
      <template #footer>
        <el-button @click="publishVisible = false">取消</el-button>
        <el-button type="primary" :loading="publishSaving" @click="doPublish">确认发布</el-button>
      </template>
    </el-dialog>

    <!-- 版本历史 -->
    <el-drawer v-model="versionsVisible" size="min(680px, 96vw)" title="版本历史" destroy-on-close>
      <div v-loading="versionsLoading">
        <el-table :data="versions" size="small">
          <el-table-column label="版本" width="80" align="center">
            <template #default="{ row }">v{{ row.versionNo }}</template>
          </el-table-column>
          <el-table-column prop="publishedAt" label="发布时间" width="170" />
          <el-table-column prop="note" label="说明" min-width="120" show-overflow-tooltip />
          <el-table-column label="结构摘要" min-width="240">
            <template #default="{ row }">{{ snapshotSummary(row.contentJson) }}</template>
          </el-table-column>
        </el-table>
        <el-empty v-if="versions.length === 0" description="尚无已发布版本" />
      </div>
    </el-drawer>
  </main>
</template>

<style scoped>
.plan-template-publish-tip {
  color: var(--muted);
  margin: 0 0 12px;
  font-size: 13px;
}
</style>
