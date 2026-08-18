<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowDown, Download, Plus, Refresh, UploadFilled } from '@element-plus/icons-vue'
import UiDataTable from '../components/ui/UiDataTable.vue'
import UiFormDrawer from '../components/ui/UiFormDrawer.vue'
import UiStatusTag from '../components/ui/UiStatusTag.vue'
import UiToolbar from '../components/ui/UiToolbar.vue'
import UiPagination from '../components/ui/UiPagination.vue'
import UiEmptyState from '../components/ui/UiEmptyState.vue'
import { apiErrorMessage } from '../api/error'
import { listSystem } from '../api/system'
import {
  addProjectMember,
  confirmImport,
  createBaseline,
  createDifference,
  createLegacy,
  createProject,
  createSystem,
  deleteDifference,
  deleteLegacy,
  deleteProject,
  deleteSystem,
  differenceChanges,
  downloadTemplate,
  fetchRequirementEnums,
  legacyChanges,
  legacyStageLogs,
  listBaselineItems,
  listBaselines,
  listDifferences,
  listLegacy,
  listProjectMembers,
  listProjects,
  listSystems,
  previewImport,
  removeProjectMember,
  reviewResult,
  stageTransition,
  submitReview,
  updateDifference,
  updateLegacy,
  updateProject,
  updateSystem
} from '../api/requirements'
import type {
  BaselineItem,
  ChangeLogRow,
  ImportPreviewReport,
  LegacyRequirement,
  ProjectMember,
  RequirementBaseline,
  RequirementDifference,
  RequirementEnums,
  RequirementProject,
  RequirementSystem,
  StageLogRow
} from '../types/requirements'

const route = useRoute()
const router = useRouter()
const section = computed(() => String(route.params.section || 'new-project'))
const enums = ref<RequirementEnums | null>(null)
const options = computed(() => enums.value?.options || {})
const fieldLabels = computed(() => enums.value?.fieldLabels || {})

const changeTypeLabels: Record<string, string> = {
  CREATE: '新增', UPDATE: '修改', DELETE: '删除', SUBMIT_REVIEW: '提交评审',
  REVIEW_PASS: '评审通过', REVIEW_RETURN: '评审退回', BASELINE: '纳入基线',
  STAGE_TRANSITION: '阶段推进', IMPORT: '导入'
}

function label(field: string | null | undefined) {
  return field ? fieldLabels.value[field] || field : ''
}

async function loadEnums() {
  try {
    enums.value = (await fetchRequirementEnums()).data.data
    if (section.value === 'new-project') await loadSystems()
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '枚举加载失败'))
  }
}

watch(section, () => {
  if (section.value === 'new-project') loadProjects()
  if (section.value === 'legacy') loadLegacy()
  if (section.value === 'systems') loadSystems()
})

onMounted(() => {
  void loadEnums()
  if (section.value === 'new-project') loadProjects()
  if (section.value === 'legacy') loadLegacy()
  if (section.value === 'systems') loadSystems()
})

// ---------------- 新建项目 ----------------
const projects = ref<RequirementProject[]>([])
const projectsLoading = ref(false)
const selectedProject = ref<RequirementProject | null>(null)
const selectedProjectId = ref<number | null>(null)
const projectFormVisible = ref(false)
const projectSaving = ref(false)
const projectForm = reactive<Record<string, unknown>>({ project_code: '', project_name: '', project_type: '0~1 新建', start_time: '', status: '进行中', description: '' })

async function loadProjects() {
  projectsLoading.value = true
  try {
    projects.value = (await listProjects()).data.data
    if (projects.value.length) {
      const keep = projects.value.find(project => project.id === selectedProjectId.value)
      selectedProjectId.value = keep ? keep.id : projects.value[0].id
      selectedProject.value = projects.value.find(project => project.id === selectedProjectId.value) || null
      await loadDifferences()
    } else {
      selectedProjectId.value = null
      selectedProject.value = null
    }
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '项目加载失败'))
  } finally {
    projectsLoading.value = false
  }
}

function openProjectCreate() {
  Object.assign(projectForm, { project_code: '', project_name: '', project_type: '0~1 新建', start_time: '', status: '进行中', description: '' })
  projectFormVisible.value = true
}

function openProjectEdit(row: RequirementProject) {
  Object.assign(projectForm, row)
  projectFormVisible.value = true
}

async function saveProject() {
  projectSaving.value = true
  try {
    let createdId: number | null = null
    if (projectForm.id) {
      await updateProject(Number(projectForm.id), projectForm)
    } else {
      createdId = (await createProject(projectForm)).data.data.id
    }
    if (createdId) {
      selectedProjectId.value = createdId
    }
    projectFormVisible.value = false
    ElMessage.success('项目已保存')
    await loadProjects()
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '项目保存失败'))
  } finally {
    projectSaving.value = false
  }
}

function onProjectChange(id: number) {
  selectedProject.value = projects.value.find(project => project.id === id) || null
  if (selectedProject.value) {
    differencePage.value = 1
    void loadDifferences()
  }
}

function projectCommand(command: string) {
  if (!selectedProject.value) return
  if (command === 'edit') openProjectEdit(selectedProject.value)
  if (command === 'delete') void removeProject(selectedProject.value)
}

async function removeProject(row: RequirementProject) {
  try {
    await ElMessageBox.confirm(`确认删除项目「${row.project_name}」？`, '删除确认', { type: 'warning' })
    await deleteProject(row.id)
    ElMessage.success('项目已删除')
    await loadProjects()
  } catch (error) {
    if (error === 'cancel' || error === 'close') return
    ElMessage.error(apiErrorMessage(error, '项目删除失败'))
  }
}

// 项目成员
const memberDialogVisible = ref(false)
const members = ref<ProjectMember[]>([])
const memberUserId = ref<number | null>(null)
const userOptions = ref<Array<{ id: number; display_name?: string; username?: string }>>([])

async function openMembers(project: RequirementProject) {
  selectedProject.value = project
  memberUserId.value = null
  try {
    members.value = (await listProjectMembers(project.id)).data.data
    const page = (await listSystem('users', { page: 1, size: 100 })).data.data
    userOptions.value = (page as { records: Array<{ id: number; display_name?: string; username?: string }> }).records || []
    memberDialogVisible.value = true
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '成员加载失败'))
  }
}

async function addMember() {
  if (!selectedProject.value || !memberUserId.value) return
  try {
    await addProjectMember(selectedProject.value.id, { userId: memberUserId.value })
    members.value = (await listProjectMembers(selectedProject.value.id)).data.data
    ElMessage.success('成员已添加')
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '成员添加失败'))
  }
}

async function removeMember(row: ProjectMember) {
  try {
    await removeProjectMember(row.id)
    members.value = members.value.filter(item => item.id !== row.id)
    ElMessage.success('成员已移除')
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '成员移除失败'))
  }
}

// 差异清单
const differences = ref<RequirementDifference[]>([])
const differencesTotal = ref(0)
const differencePage = ref(1)
const differenceSize = ref(20)
const differencesLoading = ref(false)
const diffFilters = reactive<{ reviewStatus: string; devStatus: string; testStatus: string; keyword: string }>({ reviewStatus: '', devStatus: '', testStatus: '', keyword: '' })

async function loadDifferences() {
  if (!selectedProject.value) return
  differencesLoading.value = true
  try {
    const page = (await listDifferences({
      projectId: selectedProject.value.id,
      ...diffFilters,
      page: differencePage.value,
      size: differenceSize.value
    })).data.data
    differences.value = page.records
    differencesTotal.value = page.total
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '差异清单加载失败'))
  } finally {
    differencesLoading.value = false
  }
}

const diffFormVisible = ref(false)
const diffSaving = ref(false)
const diffForm = reactive<Record<string, unknown>>({})

function openDiffCreate() {
  const blank: Record<string, unknown> = { name: '', business_conglomerate: '', business_section: '', business_group: '', requirement_no: '', category: '', system_id: undefined, jinke_practice: '', difference_type: '', monshang_practice: '', difference_desc: '', monshang_dept: '', monshang_analyst: '', jinke_analyst: '', adapt_mode: '', handle_status: '', coord_group: '', solution: '', is_special: '否', decision_level: '', decision_conclusion: '', monshang_confirm_dept: '', jinke_confirmer: '', dev_status: '未开始', test_status: '未开始' }
  Object.keys(diffForm).forEach(key => delete diffForm[key])
  Object.assign(diffForm, blank)
  diffFormVisible.value = true
}

function openDiffEdit(row: RequirementDifference) {
  Object.keys(diffForm).forEach(key => delete diffForm[key])
  Object.assign(diffForm, { id: row.id }, row)
  diffFormVisible.value = true
}

async function saveDifference() {
  if (!selectedProject.value) return
  diffSaving.value = true
  try {
    if (diffForm.id) {
      await updateDifference(Number(diffForm.id), diffForm)
    } else {
      await createDifference(selectedProject.value.id, diffForm)
    }
    diffFormVisible.value = false
    ElMessage.success('差异已保存')
    await loadDifferences()
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '差异保存失败'))
  } finally {
    diffSaving.value = false
  }
}

async function removeDifference(row: RequirementDifference) {
  try {
    await ElMessageBox.confirm(`确认删除差异「${row.name}」？`, '删除确认', { type: 'warning' })
    await deleteDifference(row.id)
    ElMessage.success('差异已删除')
    await loadDifferences()
  } catch (error) {
    if (error === 'cancel' || error === 'close') return
    ElMessage.error(apiErrorMessage(error, '差异删除失败'))
  }
}

async function submitDifferenceReview(row: RequirementDifference) {
  try {
    await submitReview(row.id)
    ElMessage.success('已提交评审（评审处理接入后由审批结果回写）')
    await loadDifferences()
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '提交评审失败'))
  }
}

const reviewDialogVisible = ref(false)
const reviewSaving = ref(false)
const reviewTarget = ref<RequirementDifference | null>(null)
const reviewForm = reactive<{ decision: 'APPROVE' | 'RETURN'; comment: string }>({ decision: 'APPROVE', comment: '' })

function openReview(row: RequirementDifference) {
  reviewTarget.value = row
  reviewForm.decision = 'APPROVE'
  reviewForm.comment = ''
  reviewDialogVisible.value = true
}

async function saveReview() {
  if (!reviewTarget.value) return
  reviewSaving.value = true
  try {
    await reviewResult(reviewTarget.value.id, reviewForm)
    reviewDialogVisible.value = false
    ElMessage.success('评审处理完成')
    await loadDifferences()
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '评审处理失败'))
  } finally {
    reviewSaving.value = false
  }
}

// 基线
const baselineDialogVisible = ref(false)
const baselines = ref<RequirementBaseline[]>([])
const baselineItems = ref<BaselineItem[]>([])
const baselineDetailVisible = ref(false)

async function openBaselines() {
  if (!selectedProject.value) return
  try {
    baselines.value = (await listBaselines(selectedProject.value.id)).data.data
    baselineDialogVisible.value = true
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '基线加载失败'))
  }
}

async function formBaseline() {
  if (!selectedProject.value) return
  try {
    await ElMessageBox.confirm(`确认对项目「${selectedProject.value.project_name}」形成基线？形成后差异整体锁定。`, '形成基线', { type: 'warning' })
    const result = (await createBaseline(selectedProject.value.id)).data.data
    ElMessage.success(`基线已形成：${result.baseline_no}`)
    await loadProjects()
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '形成基线失败'))
  }
}

async function showBaselineItems(row: RequirementBaseline) {
  try {
    baselineItems.value = (await listBaselineItems(row.id)).data.data
    baselineDetailVisible.value = true
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '基线明细加载失败'))
  }
}

// 修改记录
const changeLogDialogVisible = ref(false)
const changeLogs = ref<ChangeLogRow[]>([])
const changeLogTitle = ref('修改记录')

async function openChangeLogs(bizType: 'NEW_PROJECT_DIFF' | 'LEGACY_REQUIREMENT', bizId: number, title: string) {
  changeLogTitle.value = title
  try {
    changeLogs.value = bizType === 'NEW_PROJECT_DIFF'
      ? (await differenceChanges(bizId)).data.data
      : (await legacyChanges(bizId)).data.data
    changeLogDialogVisible.value = true
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '修改记录加载失败'))
  }
}

// 导入
const importDialogVisible = ref(false)
const importReport = ref<ImportPreviewReport | null>(null)
const importFile = ref<File | null>(null)
const importing = ref(false)

function openImport() {
  importReport.value = null
  importFile.value = null
  importDialogVisible.value = true
}

async function onImportFile(file: File) {
  if (!selectedProject.value) return
  importing.value = true
  try {
    importReport.value = (await previewImport('DIFF', selectedProject.value.id, file)).data.data
    importFile.value = file
    ElMessage.success(`校验完成：成功 ${importReport.value.successRows} 行，错误 ${importReport.value.errorRows} 行`)
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '导入文件校验失败'))
  } finally {
    importing.value = false
  }
}

async function confirmImportRows() {
  if (!importReport.value) return
  importing.value = true
  try {
    const result = (await confirmImport({
      bizType: 'DIFF',
      projectId: selectedProject.value?.id,
      fileName: importFile.value?.name,
      rows: importReport.value.rows
    })).data.data
    ElMessage.success(`导入完成：${result.successRows} 行`)
    importDialogVisible.value = false
    await loadDifferences()
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '确认导入失败'))
  } finally {
    importing.value = false
  }
}

// ---------------- 存量项目 ----------------
const legacyRows = ref<LegacyRequirement[]>([])
const legacyTotal = ref(0)
const legacyPage = ref(1)
const legacySize = ref(20)
const legacyLoading = ref(false)
const legacyFilters = reactive<{ businessGroup: string; stage: string; stageStatus: string; keyword: string }>({ businessGroup: '', stage: '', stageStatus: '', keyword: '' })

async function loadLegacy() {
  legacyLoading.value = true
  try {
    const page = (await listLegacy({
      businessGroup: legacyFilters.businessGroup || undefined,
      stage: legacyFilters.stage || undefined,
      stageStatus: legacyFilters.stageStatus || undefined,
      keyword: legacyFilters.keyword || undefined,
      page: legacyPage.value,
      size: legacySize.value
    })).data.data
    legacyRows.value = page.records
    legacyTotal.value = page.total
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '存量需求加载失败'))
  } finally {
    legacyLoading.value = false
  }
}

const legacyFormVisible = ref(false)
const legacySaving = ref(false)
const legacyForm = reactive<Record<string, unknown>>({})

function openLegacyCreate() {
  Object.keys(legacyForm).forEach(key => delete legacyForm[key])
  Object.assign(legacyForm, {
    legacy_doc_name: '', requirement_no: '', requirement_name: '', content_summary: '',
    propose_dept: '', proposer: '', monshang_ba: '', monshang_architect: '',
    requirement_received_date: '', requirement_type: '', regulation_category: '',
    business_group: '', sub_group: '', jinke_contact: '', need_jinke_arch_decision: '否',
    jinke_architect: '', unified_managed: '是', requirement_status: '需求分析', remark: '',
    change_involved: '否', change_info: '', change_review_conclusion: '',
    change_conclusion_status: '', change_remark: '', not_project_developed: '否'
  })
  legacyFormVisible.value = true
}

function openLegacyEdit(row: LegacyRequirement) {
  Object.keys(legacyForm).forEach(key => delete legacyForm[key])
  Object.assign(legacyForm, { id: row.id }, row)
  legacyFormVisible.value = true
}

async function saveLegacy() {
  legacySaving.value = true
  try {
    if (legacyForm.id) {
      await updateLegacy(Number(legacyForm.id), legacyForm)
    } else {
      await createLegacy(legacyForm)
    }
    legacyFormVisible.value = false
    ElMessage.success('存量需求已保存')
    await loadLegacy()
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '存量需求保存失败'))
  } finally {
    legacySaving.value = false
  }
}

async function removeLegacy(row: LegacyRequirement) {
  try {
    await ElMessageBox.confirm(`确认删除存量需求「${row.requirement_name}」？`, '删除确认', { type: 'warning' })
    await deleteLegacy(row.id)
    ElMessage.success('存量需求已删除')
    await loadLegacy()
  } catch (error) {
    if (error === 'cancel' || error === 'close') return
    ElMessage.error(apiErrorMessage(error, '存量需求删除失败'))
  }
}

const stageDialogVisible = ref(false)
const stageSaving = ref(false)
const stageTarget = ref<LegacyRequirement | null>(null)
const stageForm = reactive<{ stage: string; action: 'START' | 'COMPLETE' | 'BACK'; comment: string }>({ stage: 'PROPOSE', action: 'START', comment: '' })
const stageLogs = ref<StageLogRow[]>([])
const stageLogDialogVisible = ref(false)

function openStage(row: LegacyRequirement) {
  stageTarget.value = row
  stageForm.stage = row.current_stage
  stageForm.action = 'START'
  stageForm.comment = ''
  stageDialogVisible.value = true
}

async function saveStage() {
  if (!stageTarget.value) return
  stageSaving.value = true
  try {
    await stageTransition(stageTarget.value.id, stageForm)
    stageDialogVisible.value = false
    ElMessage.success('阶段已推进')
    await loadLegacy()
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '阶段推进失败'))
  } finally {
    stageSaving.value = false
  }
}

async function showStageLogs(row: LegacyRequirement) {
  try {
    stageLogs.value = (await legacyStageLogs(row.id)).data.data
    stageLogDialogVisible.value = true
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '阶段记录加载失败'))
  }
}

// ---------------- 系统清单 ----------------
const systems = ref<RequirementSystem[]>([])
const systemsLoading = ref(false)
const systemFormVisible = ref(false)
const systemSaving = ref(false)
const systemForm = reactive<Record<string, unknown>>({})

async function loadSystems() {
  systemsLoading.value = true
  try {
    systems.value = (await listSystems()).data.data
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '系统清单加载失败'))
  } finally {
    systemsLoading.value = false
  }
}

function openSystemCreate() {
  Object.keys(systemForm).forEach(key => delete systemForm[key])
  Object.assign(systemForm, { system_code: '', system_name: '', english_name: '', conglomerate: '', status: '启用', logical_subsystem_code: '', logical_subsystem_name: '', business_component_code: '', business_component_name: '', business_domain: '', product_view: '', launch_point: '', category: '', introduction: '', disaster_level: '', source_type: '' })
  systemFormVisible.value = true
}

function openSystemEdit(row: RequirementSystem) {
  Object.keys(systemForm).forEach(key => delete systemForm[key])
  Object.assign(systemForm, { id: row.id }, row)
  systemFormVisible.value = true
}

async function saveSystem() {
  systemSaving.value = true
  try {
    if (systemForm.id) {
      await updateSystem(Number(systemForm.id), systemForm)
    } else {
      await createSystem(systemForm)
    }
    systemFormVisible.value = false
    ElMessage.success('系统已保存')
    await loadSystems()
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '系统保存失败'))
  } finally {
    systemSaving.value = false
  }
}

async function removeSystem(row: RequirementSystem) {
  try {
    await ElMessageBox.confirm(`确认删除系统「${row.system_name}」？`, '删除确认', { type: 'warning' })
    await deleteSystem(row.id)
    ElMessage.success('系统已删除')
    await loadSystems()
  } catch (error) {
    if (error === 'cancel' || error === 'close') return
    ElMessage.error(apiErrorMessage(error, '系统删除失败'))
  }
}

function stageLabel(stage: string) {
  const map = options.value.legacyStageLabelMap as unknown as Record<string, string> | undefined
  return map?.[stage] || stage
}

function canEditDiff(row: RequirementDifference) {
  return row.review_status === '待评审' || row.review_status === '已退回'
}
</script>

<template>
  <section class="requirements-page">
    <el-tabs :model-value="section" class="requirements-tabs" @tab-change="(name: string) => router.replace('/requirements/' + name)">
      <el-tab-pane label="新建项目" name="new-project" />
      <el-tab-pane label="存量项目" name="legacy" />
      <el-tab-pane label="系统清单" name="systems" />
    </el-tabs>

    <!-- 新建项目 -->
    <div v-if="section === 'new-project'" class="req-section">
      <UiToolbar>
        <el-select v-model="selectedProjectId" filterable placeholder="选择项目" class="req-project-select" @change="onProjectChange">
          <el-option v-for="project in projects" :key="project.id" :label="`${project.project_code} ${project.project_name}`" :value="project.id" />
        </el-select>
        <span v-if="selectedProject" class="muted">已评审 {{ selectedProject.reviewed_count || 0 }} / {{ selectedProject.difference_count || 0 }}</span>
        <template #actions>
          <el-button @click="loadProjects"><el-icon><Refresh /></el-icon>刷新</el-button>
          <el-button type="primary" @click="openProjectCreate"><el-icon><Plus /></el-icon>新建项目</el-button>
          <el-dropdown v-if="selectedProject" trigger="click" @command="projectCommand">
            <el-button>项目操作<el-icon class="el-icon--right"><ArrowDown /></el-icon></el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="edit">编辑项目</el-dropdown-item>
                <el-dropdown-item command="delete" divided>删除项目</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
          <el-button v-if="selectedProject" @click="openMembers(selectedProject)">成员</el-button>
          <el-button v-if="selectedProject" @click="openBaselines()">基线</el-button>
          <el-button v-if="selectedProject" type="success" @click="formBaseline">形成基线</el-button>
        </template>
      </UiToolbar>

      <template v-if="selectedProject">
        <UiToolbar>
          <span class="muted">差异清单（{{ differencesTotal }} 条）</span>
          <template #actions>
            <el-select v-model="diffFilters.reviewStatus" placeholder="差异状态" clearable style="width: 130px" @change="loadDifferences">
              <el-option v-for="item in options.reviewStatuses || []" :key="item" :label="item" :value="item" />
            </el-select>
            <el-select v-model="diffFilters.devStatus" placeholder="开发状态" clearable style="width: 130px" @change="loadDifferences">
              <el-option v-for="item in options.devStatuses || []" :key="item" :label="item" :value="item" />
            </el-select>
            <el-input v-model="diffFilters.keyword" placeholder="名称/需求编号" clearable style="width: 180px" @keyup.enter="loadDifferences" @clear="loadDifferences" />
            <el-button @click="loadDifferences"><el-icon><Refresh /></el-icon>查询</el-button>
            <el-button @click="downloadTemplate('DIFF')"><el-icon><Download /></el-icon>模板下载</el-button>
            <el-button @click="openImport"><el-icon><UploadFilled /></el-icon>导入</el-button>
            <el-button type="primary" @click="openDiffCreate"><el-icon><Plus /></el-icon>新增差异</el-button>
            <el-button type="success" @click="formBaseline">形成基线</el-button>
          </template>
        </UiToolbar>
        <UiDataTable :data="differences" :loading="differencesLoading" row-key="id" border>
          <el-table-column prop="seq_no" label="序号" width="60" />
          <el-table-column prop="requirement_no" label="需求编号" min-width="130" />
          <el-table-column prop="name" label="名称" min-width="220" show-overflow-tooltip />
          <el-table-column prop="category" label="分类" width="90" />
          <el-table-column prop="difference_type" label="差异类型" min-width="150" show-overflow-tooltip />
          <el-table-column prop="business_group" label="业务组" width="100" />
          <el-table-column label="差异状态" width="110"><template #default="scope"><UiStatusTag :value="scope.row.review_status" /></template></el-table-column>
          <el-table-column label="开发状态" width="100"><template #default="scope"><UiStatusTag :value="scope.row.dev_status" /></template></el-table-column>
          <el-table-column label="测试状态" width="100"><template #default="scope"><UiStatusTag :value="scope.row.test_status" /></template></el-table-column>
          <el-table-column label="操作" width="330" fixed="right">
            <template #default="scope">
              <el-button link type="primary" @click="openChangeLogs('NEW_PROJECT_DIFF', scope.row.id, `修改记录：${scope.row.name}`)">修改记录</el-button>
              <el-button v-if="canEditDiff(scope.row)" link type="primary" @click="openDiffEdit(scope.row)">编辑</el-button>
              <el-button v-if="canEditDiff(scope.row)" link type="danger" @click="removeDifference(scope.row)">删除</el-button>
              <el-button v-if="scope.row.review_status === '待评审' || scope.row.review_status === '已退回'" link type="warning" @click="submitDifferenceReview(scope.row)">提交评审</el-button>
              <el-button v-if="scope.row.review_status === '评审中'" link type="success" @click="openReview(scope.row)">评审处理</el-button>
            </template>
          </el-table-column>
        </UiDataTable>
        <UiPagination v-model:page="differencePage" v-model:page-size="differenceSize" :total="differencesTotal" @update:page="loadDifferences" @update:page-size="loadDifferences" />
      </template>
      <UiEmptyState v-else title="暂无项目" description="先创建一个新建项目，再维护需求差异清单。" />
    </div>

    <!-- 存量项目 -->
    <div v-if="section === 'legacy'" class="req-section">
      <UiToolbar>
        <el-input v-model="legacyFilters.keyword" placeholder="需求名称/编号" clearable style="width: 200px" @keyup.enter="loadLegacy" @clear="loadLegacy" />
        <el-input v-model="legacyFilters.businessGroup" placeholder="业务组" clearable style="width: 150px" @keyup.enter="loadLegacy" @clear="loadLegacy" />
        <el-select v-model="legacyFilters.stage" placeholder="当前阶段" clearable style="width: 150px" @change="loadLegacy"><el-option v-for="stage in options.legacyStages || []" :key="stage" :label="stageLabel(stage)" :value="stage" /></el-select>
        <template #actions>
          <el-button @click="loadLegacy"><el-icon><Refresh /></el-icon>查询</el-button>
          <el-button @click="downloadTemplate('LEGACY')"><el-icon><Download /></el-icon>模板下载</el-button>
          <el-button type="primary" @click="openLegacyCreate"><el-icon><Plus /></el-icon>新增存量需求</el-button>
        </template>
      </UiToolbar>
      <UiDataTable :data="legacyRows" :loading="legacyLoading" row-key="id" border>
        <el-table-column prop="requirement_no" label="需求编号" min-width="180" />
        <el-table-column prop="requirement_name" label="需求名称" min-width="220" show-overflow-tooltip />
        <el-table-column prop="business_group" label="业务组" width="110" />
        <el-table-column label="当前阶段" width="120"><template #default="scope"><UiStatusTag :value="stageLabel(scope.row.current_stage)" /></template></el-table-column>
        <el-table-column label="需求状态" width="120"><template #default="scope"><span>{{ scope.row.requirement_status || '-' }}</span></template></el-table-column>
        <el-table-column label="来源" width="90"><template #default="scope"><span>{{ scope.row.source === 'IMPORT' ? '导入' : '在线填写' }}</span></template></el-table-column>
        <el-table-column label="操作" width="300" fixed="right">
          <template #default="scope">
            <el-button link type="primary" @click="openChangeLogs('LEGACY_REQUIREMENT', scope.row.id, `修改记录：${scope.row.requirement_name}`)">修改记录</el-button>
            <el-button link type="primary" @click="showStageLogs(scope.row)">阶段记录</el-button>
            <el-button link type="primary" @click="openStage(scope.row)">阶段推进</el-button>
            <el-button link type="primary" @click="openLegacyEdit(scope.row)">编辑</el-button>
            <el-button link type="danger" @click="removeLegacy(scope.row)">删除</el-button>
          </template>
        </el-table-column>
      </UiDataTable>
      <UiPagination v-model:page="legacyPage" v-model:page-size="legacySize" :total="legacyTotal" @update:page="loadLegacy" @update:page-size="loadLegacy" />
    </div>

    <!-- 系统清单 -->
    <div v-if="section === 'systems'" class="req-section">
      <UiToolbar>
        <span class="muted">系统清单主数据（新建项目与存量项目共用）</span>
        <template #actions>
          <el-button @click="loadSystems"><el-icon><Refresh /></el-icon>刷新</el-button>
          <el-button type="primary" @click="openSystemCreate"><el-icon><Plus /></el-icon>新增系统</el-button>
        </template>
      </UiToolbar>
      <UiDataTable :data="systems" :loading="systemsLoading" row-key="id" border>
        <el-table-column prop="system_code" label="系统编号" min-width="120" />
        <el-table-column prop="system_name" label="系统名称" min-width="200" />
        <el-table-column prop="english_name" label="英文简称" min-width="120" />
        <el-table-column prop="conglomerate" label="事业群" min-width="130" />
        <el-table-column prop="business_domain" label="业务领域" min-width="120" />
        <el-table-column prop="source_type" label="引入/保留" width="110" />
        <el-table-column label="状态" width="100"><template #default="scope"><UiStatusTag :value="scope.row.status" /></template></el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="scope">
            <el-button link type="primary" @click="openSystemEdit(scope.row)">编辑</el-button>
            <el-button link type="danger" @click="removeSystem(scope.row)">删除</el-button>
          </template>
        </el-table-column>
      </UiDataTable>
    </div>

    <!-- 项目表单 -->
    <UiFormDrawer v-model="projectFormVisible" :title="projectForm.id ? '编辑项目' : '新建项目'" :loading="projectSaving" width="min(560px, calc(100vw - 24px))" @submit="saveProject">
      <el-form label-position="top">
        <el-form-item label="项目编码" required><el-input v-model="projectForm.project_code" :disabled="Boolean(projectForm.id)" /></el-form-item>
        <el-form-item label="项目名称" required><el-input v-model="projectForm.project_name" /></el-form-item>
        <el-form-item label="项目类型"><el-select v-model="projectForm.project_type" style="width: 100%"><el-option v-for="item in options.projectTypes || []" :key="item" :label="item" :value="item" /></el-select></el-form-item>
        <el-form-item label="启动时间"><el-date-picker v-model="projectForm.start_time" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item>
        <el-form-item label="状态"><el-select v-model="projectForm.status" style="width: 100%"><el-option v-for="item in options.projectStatuses || []" :key="item" :label="item" :value="item" /></el-select></el-form-item>
        <el-form-item label="说明"><el-input v-model="projectForm.description" type="textarea" :rows="3" /></el-form-item>
      </el-form>
    </UiFormDrawer>

    <!-- 成员管理 -->
    <el-dialog v-model="memberDialogVisible" title="项目成员" width="min(560px, calc(100vw - 24px))">
      <div class="req-member-row"><el-select v-model="memberUserId" placeholder="选择成员用户" filterable style="flex: 1"><el-option v-for="user in userOptions" :key="user.id" :label="`${user.display_name || user.username}（${user.username}）`" :value="user.id" /></el-select><el-button type="primary" @click="addMember">添加</el-button></div>
      <el-table :data="members" border size="small">
        <el-table-column prop="display_name" label="姓名" min-width="120" />
        <el-table-column prop="username" label="账号" min-width="140" />
        <el-table-column prop="member_role" label="角色" width="90" />
        <el-table-column label="操作" width="90"><template #default="scope"><el-button link type="danger" @click="removeMember(scope.row)">移除</el-button></template></el-table-column>
      </el-table>
    </el-dialog>

    <!-- 差异表单 -->
    <UiFormDrawer v-model="diffFormVisible" :title="diffForm.id ? '编辑差异' : '新增差异'" :loading="diffSaving" width="min(760px, calc(100vw - 24px))" @submit="saveDifference">
      <el-form label-position="top" class="req-form-grid">
        <el-form-item label="序号"><el-input v-model.number="diffForm.seq_no" type="number" /></el-form-item>
        <el-form-item label="事业群"><el-input v-model="diffForm.business_conglomerate" /></el-form-item>
        <el-form-item label="业务板块"><el-input v-model="diffForm.business_section" /></el-form-item>
        <el-form-item label="业务组" required><el-input v-model="diffForm.business_group" /></el-form-item>
        <el-form-item label="需求编号"><el-input v-model="diffForm.requirement_no" placeholder="组件物理子系统编号+三位序号，如 W01812-001" /></el-form-item>
        <el-form-item label="分类"><el-select v-model="diffForm.category" clearable style="width: 100%"><el-option v-for="item in options.categories || []" :key="item" :label="item" :value="item" /></el-select></el-form-item>
        <el-form-item label="名称" required class="req-span-2"><el-input v-model="diffForm.name" /></el-form-item>
        <el-form-item label="涉及系统"><el-select v-model="diffForm.system_id" clearable filterable style="width: 100%"><el-option v-for="system in systems" :key="system.id" :label="`${system.system_code} ${system.system_name}`" :value="system.id" /></el-select></el-form-item>
        <el-form-item label="差异类型"><el-select v-model="diffForm.difference_type" clearable style="width: 100%"><el-option v-for="item in options.differenceTypes || []" :key="item" :label="item" :value="item" /></el-select></el-form-item>
        <el-form-item label="适配方式"><el-select v-model="diffForm.adapt_mode" clearable style="width: 100%"><el-option v-for="item in options.adaptModes || []" :key="item" :label="item" :value="item" /></el-select></el-form-item>
        <el-form-item label="处理状态"><el-select v-model="diffForm.handle_status" clearable style="width: 100%"><el-option v-for="item in options.handleStatuses || []" :key="item" :label="item" :value="item" /></el-select></el-form-item>
        <el-form-item label="是否专题"><el-select v-model="diffForm.is_special" clearable style="width: 100%"><el-option v-for="item in options.yesNo || []" :key="item" :label="item" :value="item" /></el-select></el-form-item>
        <el-form-item label="上升决策层级"><el-select v-model="diffForm.decision_level" clearable style="width: 100%"><el-option v-for="item in options.decisionLevels || []" :key="item" :label="item" :value="item" /></el-select></el-form-item>
        <el-form-item label="开发状态"><el-select v-model="diffForm.dev_status" style="width: 100%"><el-option v-for="item in options.devStatuses || []" :key="item" :label="item" :value="item" /></el-select></el-form-item>
        <el-form-item label="测试状态"><el-select v-model="diffForm.test_status" style="width: 100%"><el-option v-for="item in options.testStatuses || []" :key="item" :label="item" :value="item" /></el-select></el-form-item>
        <el-form-item label="金科做法" class="req-span-2"><el-input v-model="diffForm.jinke_practice" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="蒙商作法" class="req-span-2"><el-input v-model="diffForm.monshang_practice" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="差异描述" class="req-span-2"><el-input v-model="diffForm.difference_desc" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="解决方案" class="req-span-2"><el-input v-model="diffForm.solution" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="蒙商分析部门"><el-input v-model="diffForm.monshang_dept" /></el-form-item>
        <el-form-item label="蒙商分析人"><el-input v-model="diffForm.monshang_analyst" /></el-form-item>
        <el-form-item label="金科分析人"><el-input v-model="diffForm.jinke_analyst" /></el-form-item>
        <el-form-item label="协同组"><el-input v-model="diffForm.coord_group" /></el-form-item>
        <el-form-item label="决策结论" class="req-span-2"><el-input v-model="diffForm.decision_conclusion" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="蒙商确认部门"><el-input v-model="diffForm.monshang_confirm_dept" /></el-form-item>
        <el-form-item label="金科确认人"><el-input v-model="diffForm.jinke_confirmer" /></el-form-item>
      </el-form>
    </UiFormDrawer>

    <!-- 评审处理 -->
    <el-dialog v-model="reviewDialogVisible" title="评审处理（审批流接入前为模拟处理）" width="min(480px, calc(100vw - 24px))">
      <el-form label-position="top">
        <el-form-item label="评审结论" required><el-radio-group v-model="reviewForm.decision"><el-radio value="APPROVE">评审通过</el-radio><el-radio value="RETURN">退回</el-radio></el-radio-group></el-form-item>
        <el-form-item label="评审意见"><el-input v-model="reviewForm.comment" type="textarea" :rows="3" :placeholder="reviewForm.decision === 'RETURN' ? '退回意见必填' : ''" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="reviewDialogVisible = false">取消</el-button><el-button type="primary" :loading="reviewSaving" @click="saveReview">提交</el-button></template>
    </el-dialog>

    <!-- 基线列表 -->
    <el-dialog v-model="baselineDialogVisible" title="基线版本" width="min(720px, calc(100vw - 24px))">
      <el-table :data="baselines" border size="small">
        <el-table-column prop="baseline_no" label="基线版本号" min-width="200" />
        <el-table-column prop="baseline_name" label="基线名称" min-width="200" />
        <el-table-column prop="difference_count" label="差异数" width="90" />
        <el-table-column prop="created_at" label="形成时间" width="170" />
        <el-table-column label="操作" width="100"><template #default="scope"><el-button link type="primary" @click="showBaselineItems(scope.row)">明细</el-button></template></el-table-column>
      </el-table>
      <el-empty v-if="!baselines.length" description="暂无基线" />
    </el-dialog>

    <el-dialog v-model="baselineDetailVisible" title="基线明细（快照）" width="min(720px, calc(100vw - 24px))">
      <el-table :data="baselineItems" border size="small">
        <el-table-column prop="difference_id" label="差异 ID" width="110" />
        <el-table-column label="快照" min-width="320"><template #default="scope"><code class="req-snapshot">{{ scope.row.snapshot_json }}</code></template></el-table-column>
        <el-table-column prop="created_at" label="纳入时间" width="170" />
      </el-table>
    </el-dialog>

    <!-- 导入 -->
    <el-dialog v-model="importDialogVisible" title="Excel 导入（模板下载 → 校验 → 确认导入）" width="min(680px, calc(100vw - 24px))">
      <div class="req-import-box">
        <el-upload drag :auto-upload="false" :show-file-list="false" accept=".xlsx,.xls" :on-change="(file: any) => onImportFile(file.raw)">
          <el-icon :size="30"><UploadFilled /></el-icon>
          <div>拖拽或点击选择差异清单 Excel 文件</div>
          <template #tip><div class="el-upload__tip">仅支持 xlsx/xls，按标准模板逐行校验</div></template>
        </el-upload>
      </div>
      <div v-if="importReport" class="req-import-report">
        <p>总行数 {{ importReport.totalRows }}，成功 {{ importReport.successRows }}，错误 {{ importReport.errorRows }}</p>
        <el-table v-if="importReport.errors.length" :data="importReport.errors" border size="small">
          <el-table-column prop="row" label="行号" width="70" />
          <el-table-column label="错误信息"><template #default="scope"><span v-for="message in scope.row.messages" :key="message" class="req-error-line">{{ message }}</span></template></el-table-column>
        </el-table>
        <el-alert v-else type="success" :closable="false" title="校验全部通过，可确认导入" />
      </div>
      <template #footer>
        <el-button @click="importDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="importing" :disabled="!importReport || importReport.errorRows > 0" @click="confirmImportRows">确认导入</el-button>
      </template>
    </el-dialog>

    <!-- 存量需求表单 -->
    <UiFormDrawer v-model="legacyFormVisible" :title="legacyForm.id ? '编辑存量需求' : '新增存量需求'" :loading="legacySaving" width="min(820px, calc(100vw - 24px))" @submit="saveLegacy">
      <el-form label-position="top" class="req-form-grid">
        <el-form-item label="需求编号" required><el-input v-model="legacyForm.requirement_no" placeholder="维普需求编号，如 JG-W0332C-240507-001" /></el-form-item>
        <el-form-item label="需求名称" required><el-input v-model="legacyForm.requirement_name" /></el-form-item>
        <el-form-item label="业务组" required><el-input v-model="legacyForm.business_group" /></el-form-item>
        <el-form-item label="需求类型"><el-select v-model="legacyForm.requirement_type" clearable style="width: 100%"><el-option v-for="item in options.requirementTypes || []" :key="item" :label="item" :value="item" /></el-select></el-form-item>
        <el-form-item label="监管分类"><el-select v-model="legacyForm.regulation_category" clearable style="width: 100%"><el-option v-for="item in options.regulationCategories || []" :key="item" :label="item" :value="item" /></el-select></el-form-item>
        <el-form-item label="需求状态"><el-select v-model="legacyForm.requirement_status" clearable style="width: 100%"><el-option v-for="item in options.requirementStatuses || []" :key="item" :label="item" :value="item" /></el-select></el-form-item>
        <el-form-item label="业需文档名称"><el-input v-model="legacyForm.legacy_doc_name" /></el-form-item>
        <el-form-item label="需求提出部门"><el-input v-model="legacyForm.propose_dept" /></el-form-item>
        <el-form-item label="需求提出人及电话"><el-input v-model="legacyForm.proposer" placeholder="虚构姓名 13800000000" /></el-form-item>
        <el-form-item label="需求内容简述" class="req-span-2"><el-input v-model="legacyForm.content_summary" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="蒙商 BA"><el-input v-model="legacyForm.monshang_ba" /></el-form-item>
        <el-form-item label="蒙商架构"><el-input v-model="legacyForm.monshang_architect" /></el-form-item>
        <el-form-item label="业需入手日"><el-date-picker v-model="legacyForm.requirement_received_date" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item>
        <el-form-item label="业需评审完成日"><el-date-picker v-model="legacyForm.ba_review_date" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item>
        <el-form-item label="工作量评估完成日"><el-date-picker v-model="legacyForm.workload_date" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item>
        <el-form-item label="财务立项完成日"><el-date-picker v-model="legacyForm.finance_project_date" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item>
        <el-form-item label="软需文档名称"><el-input v-model="legacyForm.soft_doc_name" /></el-form-item>
        <el-form-item label="主责事业群"><el-input v-model="legacyForm.owner_conglomerate" /></el-form-item>
        <el-form-item label="主责物理子系统"><el-input v-model="legacyForm.owner_system" placeholder="编号+名称" /></el-form-item>
        <el-form-item label="软需提交日"><el-date-picker v-model="legacyForm.soft_submit_date" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item>
        <el-form-item label="软需评审完成日"><el-date-picker v-model="legacyForm.soft_review_date" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item>
        <el-form-item label="计划上线时间"><el-date-picker v-model="legacyForm.planned_launch_date" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item>
        <el-form-item label="实际上线时间"><el-date-picker v-model="legacyForm.actual_launch_date" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item>
        <el-form-item label="上线形式"><el-select v-model="legacyForm.launch_mode" clearable style="width: 100%"><el-option v-for="item in options.launchModes || []" :key="item" :label="item" :value="item" /></el-select></el-form-item>
        <el-form-item label="备注" class="req-span-2"><el-input v-model="legacyForm.remark" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="是否涉及需求变更"><el-select v-model="legacyForm.change_involved" style="width: 100%"><el-option v-for="item in options.yesNo || []" :key="item" :label="item" :value="item" /></el-select></el-form-item>
        <el-form-item label="变更评审结论"><el-select v-model="legacyForm.change_review_conclusion" clearable style="width: 100%"><el-option v-for="item in options.changeReviewConclusions || []" :key="item" :label="item" :value="item" /></el-select></el-form-item>
        <el-form-item label="变更结论及状态"><el-select v-model="legacyForm.change_conclusion_status" clearable style="width: 100%"><el-option v-for="item in options.changeConclusionStatuses || []" :key="item" :label="item" :value="item" /></el-select></el-form-item>
        <el-form-item label="需求变更信息" class="req-span-2"><el-input v-model="legacyForm.change_info" type="textarea" :rows="2" placeholder="谁发起变更/变更内容/发起变更阶段" /></el-form-item>
      </el-form>
    </UiFormDrawer>

    <!-- 阶段推进 -->
    <el-dialog v-model="stageDialogVisible" title="阶段推进（状态确认，不走审批流）" width="min(480px, calc(100vw - 24px))">
      <el-form label-position="top">
        <el-form-item label="阶段" required><el-select v-model="stageForm.stage" style="width: 100%"><el-option v-for="stage in options.legacyStages || []" :key="stage" :label="stageLabel(stage)" :value="stage" /></el-select></el-form-item>
        <el-form-item label="动作" required><el-radio-group v-model="stageForm.action"><el-radio value="START">启动（未开始→进行中）</el-radio><el-radio value="COMPLETE">完成（进行中→已完成）</el-radio><el-radio value="BACK">回退（进行中→未开始）</el-radio></el-radio-group></el-form-item>
        <el-form-item label="说明"><el-input v-model="stageForm.comment" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="stageDialogVisible = false">取消</el-button><el-button type="primary" :loading="stageSaving" @click="saveStage">确认</el-button></template>
    </el-dialog>

    <!-- 阶段记录 -->
    <el-dialog v-model="stageLogDialogVisible" title="阶段流转记录" width="min(680px, calc(100vw - 24px))">
      <el-table :data="stageLogs" border size="small">
        <el-table-column label="阶段" min-width="160"><template #default="scope">{{ stageLabel(scope.row.to_stage) }}</template></el-table-column>
        <el-table-column prop="from_status" label="原状态" width="100" />
        <el-table-column prop="to_status" label="新状态" width="100" />
        <el-table-column prop="operator_name" label="推进人" width="130" />
        <el-table-column prop="comment" label="说明" min-width="160" />
        <el-table-column prop="created_at" label="时间" width="170" />
      </el-table>
    </el-dialog>

    <!-- 修改记录 -->
    <el-dialog v-model="changeLogDialogVisible" :title="changeLogTitle" width="min(860px, calc(100vw - 24px))">
      <el-table :data="changeLogs" border size="small">
        <el-table-column prop="created_at" label="时间" width="170" />
        <el-table-column prop="operator_name" label="操作人" width="120" />
        <el-table-column label="操作类型" width="110"><template #default="scope">{{ changeTypeLabels[scope.row.change_type] || scope.row.change_type }}</template></el-table-column>
        <el-table-column label="字段" width="140"><template #default="scope">{{ label(scope.row.field_name) }}</template></el-table-column>
        <el-table-column label="旧值" min-width="150"><template #default="scope"><span class="req-old-value">{{ scope.row.old_value || '-' }}</span></template></el-table-column>
        <el-table-column label="新值" min-width="150"><template #default="scope"><span class="req-new-value">{{ scope.row.new_value || '-' }}</span></template></el-table-column>
      </el-table>
    </el-dialog>

    <!-- 系统表单 -->
    <UiFormDrawer v-model="systemFormVisible" :title="systemForm.id ? '编辑系统' : '新增系统'" :loading="systemSaving" width="min(640px, calc(100vw - 24px))" @submit="saveSystem">
      <el-form label-position="top" class="req-form-grid">
        <el-form-item label="系统编号" required><el-input v-model="systemForm.system_code" :disabled="Boolean(systemForm.id)" /></el-form-item>
        <el-form-item label="系统名称" required><el-input v-model="systemForm.system_name" /></el-form-item>
        <el-form-item label="英文简称"><el-input v-model="systemForm.english_name" /></el-form-item>
        <el-form-item label="事业群"><el-input v-model="systemForm.conglomerate" /></el-form-item>
        <el-form-item label="状态"><el-select v-model="systemForm.status" style="width: 100%"><el-option v-for="item in options.systemStatuses || []" :key="item" :label="item" :value="item" /></el-select></el-form-item>
        <el-form-item label="引入/保留"><el-input v-model="systemForm.source_type" /></el-form-item>
        <el-form-item label="所属逻辑子系统编号"><el-input v-model="systemForm.logical_subsystem_code" /></el-form-item>
        <el-form-item label="所属逻辑子系统名称"><el-input v-model="systemForm.logical_subsystem_name" /></el-form-item>
        <el-form-item label="归属业务组件编号"><el-input v-model="systemForm.business_component_code" /></el-form-item>
        <el-form-item label="归属业务组件名称"><el-input v-model="systemForm.business_component_name" /></el-form-item>
        <el-form-item label="业务领域"><el-input v-model="systemForm.business_domain" /></el-form-item>
        <el-form-item label="产品视图"><el-input v-model="systemForm.product_view" /></el-form-item>
        <el-form-item label="投产点"><el-input v-model="systemForm.launch_point" /></el-form-item>
        <el-form-item label="类别"><el-input v-model="systemForm.category" /></el-form-item>
        <el-form-item label="灾备等级"><el-input v-model="systemForm.disaster_level" /></el-form-item>
        <el-form-item label="系统介绍" class="req-span-2"><el-input v-model="systemForm.introduction" type="textarea" :rows="2" /></el-form-item>
      </el-form>
    </UiFormDrawer>
  </section>
</template>

<style scoped>
.requirements-page {
  min-width: 0;
}
.requirements-tabs {
  margin-bottom: 12px;
}
.req-form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0 16px;
}
.req-span-2 {
  grid-column: span 2;
}
.req-member-row {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}
.req-import-box {
  margin-bottom: 12px;
}
.req-import-report p {
  margin: 8px 0;
}
.req-error-line {
  display: block;
  color: var(--el-color-danger);
  font-size: 12px;
}
.req-snapshot {
  word-break: break-all;
  white-space: pre-wrap;
  font-size: 12px;
}
.req-old-value {
  color: var(--el-color-danger);
}
.req-new-value {
  color: var(--el-color-success);
}
.muted {
  color: var(--el-text-color-secondary);
}
/* 固定操作列不透明：避免横向滚动时左侧内容从固定列背后透出 */
.requirements-page :deep(.el-table__fixed-right),
.requirements-page :deep(.el-table__fixed),
.requirements-page :deep(.el-table__fixed-right td.el-table__cell),
.requirements-page :deep(.el-table__fixed td.el-table__cell) {
  background: var(--panel-bg);
}
.requirements-page :deep(.el-table__fixed-right th.el-table__cell),
.requirements-page :deep(.el-table__fixed th.el-table__cell) {
  background: var(--panel-muted);
}
.requirements-page :deep(.el-table__fixed-right::before),
.requirements-page :deep(.el-table__fixed::before) {
  background: var(--line);
}
@media (max-width: 760px) {
  .req-form-grid {
    grid-template-columns: 1fr;
  }
  .req-span-2 {
    grid-column: span 1;
  }
  .ui-toolbar {
    flex-wrap: wrap;
  }
}
</style>
