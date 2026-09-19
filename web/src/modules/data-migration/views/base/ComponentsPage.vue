<!--
  用途：数迁基础资料 - 系统/组件清单页
  说明：维护当前全局项目下涉及数据迁移的系统与组件。支持按事业群/系统编号/负责团队/简称名称/总分核对/关键字筛选，
        12 列分页列表、筛选后 Excel 导出；新增时通过系统编号联动物理子系统带出只读元数据（不落库），
        修改仅允许变更"是否涉及总分核对"；覆盖加载/空/失败/无权限/提交中状态与移动端卡片化。
        所属项目唯一取自全局项目上下文：页内不再有项目筛选、项目下拉与「所属项目」字段，列表/导出/新增均自动使用当前项目，
        项目切换后重置分页与其他筛选条件重查。
        基础资料子页面不展示标题横幅，定位依赖顶部 Tabs（见 T5-r8）。
-->
<script setup lang="ts">
import '../../data-migration.css'
import { computed, defineComponent, h, onMounted, reactive, ref, watch, type PropType } from 'vue'
import { ElMessage, ElMessageBox, ElPopover, ElTag } from 'element-plus'
import { Delete, Document, Download, Edit, Plus, Refresh, Search, UploadFilled } from '@element-plus/icons-vue'
import UiDataTable from '../../../../components/ui/UiDataTable.vue'
import UiEmptyState from '../../../../components/ui/UiEmptyState.vue'
import UiFormDrawer from '../../../../components/ui/UiFormDrawer.vue'
import UiPageHeader from '../../../../components/ui/UiPageHeader.vue'
import UiToolbar from '../../../../components/ui/UiToolbar.vue'
import UiUserIdentity from '../../../../components/ui/UiUserIdentity.vue'
import { apiErrorMessage } from '../../../../api/error'
import { useAuthStore } from '../../../../stores/auth'
import {
  DM_CODE_CATEGORIES,
  createDataMigrationComponent,
  deleteDataMigrationComponent,
  exportDataMigrationComponents,
  getComponentMemberOptions,
  getComponentPersons,
  getDataMigrationParamOptions,
  getSystemOptions,
  listDataMigrationComponents,
  listAllPhysicalSubsystems,
  saveComponentPersons,
  setDataMigrationComponentEnabled,
  updateDataMigrationComponent,
  type ComponentMemberOption,
  type ComponentPerson,
  type DataMigrationComponent,
  type SelectOption
} from '../../../../api/data-migration'
import ProjectScopeState from '../../components/ProjectScopeState.vue'
import { useProjectScope } from '../../composables/useProjectScope'

const auth = useAuthStore()
const canManage = computed(() => auth.hasPermission('data-migration:manage') || auth.hasPermission('system:admin'))

/**
 * 关联人员展示块：前 2 名成员（UiUserIdentity + 职责标签），超过 2 人折叠为 +N，
 * 点击浮层查看看全部成员；无关系显示“-”。成员姓名悬浮/点击的人员详情浮层由
 * UiUserIdentity 自带（hover 桌面 / 触屏 click 自动切换）。
 */
const PersonChips = defineComponent({
  name: 'ComponentPersonChips',
  props: {
    persons: { type: Array as PropType<ComponentPerson[]>, default: () => [] }
  },
  setup(props) {
    const chip = (person: ComponentPerson) =>
      h('span', { class: 'dm-person-chip' }, [
        // element-plus 2.14 起 ElAvatar.size 仅接受 ''/default/small/large 字符串枚举，数字会触发 prop 校验警告；24px 恰等于内置 small 档，改用枚举保持视觉不变。
        h(UiUserIdentity, { userId: person.user_id, fallbackName: person.display_name, size: 'small', variant: 'compact' }),
        h(ElTag, { size: 'small', effect: 'plain', type: 'info' }, () => person.person_role_label || person.person_role)
      ])
    return () => {
      const list = props.persons
      if (!list.length) return h('span', { class: 'dm-muted' }, '-')
      const shown = list.slice(0, 2)
      const extra = list.slice(2)
      const more = extra.length
        ? h(ElPopover, { width: 340, trigger: 'click', placement: 'bottom-start', popperClass: 'dm-person-popover' }, {
            reference: () => h('span', { class: 'dm-person-more' }, `+${extra.length}`),
            default: () => h('div', { class: 'dm-person-popover-list' }, list.map(chip))
          })
        : null
      return h('span', { class: 'dm-person-chips' }, [...shown.map(chip), more])
    }
  }
})

const scope = useProjectScope()
const scopeState = scope.state
const scopeProjectId = scope.projectId
const scopeProjectRef = scope.projectRef

const loading = ref(false)
const error = ref('')
const forbidden = ref(false)
const rows = ref<DataMigrationComponent[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)
const actionBusy = ref(false)

const filters = reactive<Record<string, unknown>>({
  businessGroupName: '',
  systemCode: '',
  responsibleTeam: '',
  systemKeyword: '',
  totalCheck: undefined,
  keyword: ''
})
const advanced = ref(false)

function httpStatus(error: unknown) {
  return (error as { response?: { status?: number } }).response?.status
}

function cancelled(error: unknown) {
  const action = (error as { action?: string }).action
  return action === 'cancel' || action === 'close'
}

function parseList(response: { data: { data: { records: DataMigrationComponent[]; total: number } } }) {
  return { records: response.data.data.records ?? [], total: response.data.data.total ?? 0 }
}

async function load() {
  if (scopeProjectId.value == null) {
    rows.value = []
    total.value = 0
    return
  }
  loading.value = true
  error.value = ''
  forbidden.value = false
  try {
    const response = await listDataMigrationComponents({
      ...filters,
      projectId: scopeProjectId.value,
      page: page.value,
      size: pageSize.value
    })
    const { records, total: totalCount } = parseList(response)
    rows.value = records
    total.value = totalCount
  } catch (e) {
    if (httpStatus(e) === 403) forbidden.value = true
    else error.value = apiErrorMessage(e, '组件列表加载失败')
  } finally {
    loading.value = false
  }
}

function search() {
  page.value = 1
  load()
}

function resetFilters() {
  Object.assign(filters, {
    businessGroupName: '',
    systemCode: '',
    responsibleTeam: '',
    systemKeyword: '',
    totalCheck: undefined,
    keyword: ''
  })
  page.value = 1
  load()
}

function onPageChange(nextPage: number) {
  page.value = nextPage
  load()
}

function onSizeChange(nextSize: number) {
  pageSize.value = nextSize
  page.value = 1
  load()
}

/* ---------- 筛选栏：系统一次性加载、下拉本地随输随筛（编号/名称均可、不区分大小写，候选「编号 - 名称」） ---------- */
const filterSystemOpts = ref<SelectOption[]>([])
const filterSystemLoading = ref(false)
async function loadFilterSystemOptions() {
  if (scopeProjectId.value == null) { filterSystemOpts.value = []; return }
  filterSystemLoading.value = true
  try {
    const { data } = await getSystemOptions(scopeProjectId.value)
    filterSystemOpts.value = data.data ?? []
  } catch {
    filterSystemOpts.value = []
  } finally {
    filterSystemLoading.value = false
  }
}

async function exportExcel() {
  if (scopeProjectId.value == null) {
    ElMessage.warning('当前项目不可用，请在顶部项目切换器中重新选择项目')
    return
  }
  actionBusy.value = true
  try {
    const response = await exportDataMigrationComponents({ ...filters, projectId: scopeProjectId.value })
    const blob = new Blob([response.data], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' })
    const url = URL.createObjectURL(blob)
    const anchor = document.createElement('a')
    anchor.href = url
    anchor.download = `系统组件清单_${new Date().toISOString().slice(0, 10)}.xlsx`
    document.body.appendChild(anchor)
    anchor.click()
    document.body.removeChild(anchor)
    URL.revokeObjectURL(url)
    ElMessage.success('导出成功')
  } catch (e) {
    ElMessage.error(apiErrorMessage(e, '导出失败'))
  } finally {
    actionBusy.value = false
  }
}

/* ---------- 批量新增抽屉 ---------- */
interface SubsystemCandidate { code: string; shortName: string; name: string; businessGroupName?: string; description?: string; responsibleTeamDisplayName?: string }

const createOpen = ref(false)
const createSaving = ref(false)
const subsystemSearching = ref(false)
const subsystemForbidden = ref(false)
const subsystemCandidates = ref<SubsystemCandidate[]>([])
const subsystemLoaded = ref(false)

const filterBusinessGroup = ref('')
const filterKeyword = ref('')

// 已存在的系统编号集合，用于过滤可选列表
const existingSystemCodes = computed(() => new Set(rows.value.map(r => r.system_code)))

// 全部可选系统（不含已存在的）
const availableSystems = computed(() =>
  subsystemCandidates.value.filter(s => !existingSystemCodes.value.has(s.code))
)

// 事业群选项（从可选系统中提取）
const businessGroupOptions = computed(() => {
  const set = new Set<string>()
  for (const s of availableSystems.value) {
    if (s.businessGroupName) set.add(s.businessGroupName)
  }
  return Array.from(set).sort()
})

// 筛选后的列表
const filteredSystems = computed(() => {
  const bg = filterBusinessGroup.value.trim()
  const kw = filterKeyword.value.trim().toLowerCase()
  return availableSystems.value.filter(s => {
    if (bg && s.businessGroupName !== bg) return false
    if (kw) {
      const matchCode = s.code.toLowerCase().includes(kw)
      const matchName = s.name.toLowerCase().includes(kw)
      const matchShort = (s.shortName || '').toLowerCase().includes(kw)
      if (!matchCode && !matchName && !matchShort) return false
    }
    return true
  })
})

// 选中的系统（用 Map 保存 code -> totalCheck）
const selectedSystems = reactive(new Map<string, number>())

const selectedCount = computed(() => selectedSystems.size)

function isSelected(code: string) { return selectedSystems.has(code) }
function getTotalCheck(code: string) { return selectedSystems.get(code) ?? 0 }

function toggleSelect(system: SubsystemCandidate) {
  if (selectedSystems.has(system.code)) {
    selectedSystems.delete(system.code)
  } else {
    selectedSystems.set(system.code, 0)
  }
}

function setSelectedTotalCheck(code: string, value: number) {
  if (selectedSystems.has(code)) {
    selectedSystems.set(code, value)
  }
}

function setAllSelectedTotalCheck(value: number) {
  for (const code of selectedSystems.keys()) {
    selectedSystems.set(code, value)
  }
}

// 全选当前筛选结果
function selectAllFiltered() {
  for (const s of filteredSystems.value) {
    if (!selectedSystems.has(s.code)) {
      selectedSystems.set(s.code, 0)
    }
  }
}

// 取消全选当前筛选结果
function deselectAllFiltered() {
  for (const s of filteredSystems.value) {
    selectedSystems.delete(s.code)
  }
}

function subsystemLabel(c: SubsystemCandidate) { return `${c.code} - ${c.shortName || c.name || ''}` }

// 架构主数据系统约 500 条以内：首次打开批量新增抽屉时一次性全量加载，之后复用缓存。
async function loadSubsystemOptions(force = false) {
  if (subsystemLoaded.value && !force) return
  if (!scopeProjectRef.value) {
    subsystemCandidates.value = []
    return
  }
  subsystemSearching.value = true
  subsystemForbidden.value = false
  try {
    const list = (await listAllPhysicalSubsystems(scopeProjectRef.value)).map(r => ({
      code: r.code,
      shortName: r.shortName,
      name: r.name,
      businessGroupName: r.businessGroupName ?? undefined,
      description: r.description ?? undefined,
      responsibleTeamDisplayName: r.responsibleTeamDisplayName
    }))
    subsystemCandidates.value = list
    subsystemLoaded.value = true
  } catch (e) {
    subsystemCandidates.value = []
    if (httpStatus(e) === 403) {
      subsystemForbidden.value = true
      ElMessage.warning('缺少物理子系统查询权限，无法联动带出系统信息')
    } else {
      ElMessage.error(apiErrorMessage(e, '系统列表加载失败'))
    }
  } finally {
    subsystemSearching.value = false
  }
}

function openCreate() {
  selectedSystems.clear()
  pendingPersons.clear()
  setupSystem.value = null
  personOptionsError.value = ''
  filterBusinessGroup.value = ''
  filterKeyword.value = ''
  subsystemForbidden.value = false
  void loadSubsystemOptions()
  void ensurePersonOptions()
  createOpen.value = true
}

// 批量新增时按系统提前配置的关联人员（code -> persons），确认新增后自动保存到各系统
const pendingPersons = reactive(new Map<string, ComponentPerson[]>())
const setupSystem = ref<SubsystemCandidate | null>(null)
const personOptionsLoading = ref(false)
const personOptionsError = ref('')
const setupNewMemberUserId = ref<number | null>(null)
const setupNewMemberRole = ref<string>('')

async function ensurePersonOptions() {
  if (scopeProjectId.value == null) return
  if (memberOptions.value.length || roleOptions.value.length) return
  personOptionsLoading.value = true
  personOptionsError.value = ''
  try {
    const [memberRes, roleRes] = await Promise.all([
      getComponentMemberOptions(scopeProjectId.value),
      getDataMigrationParamOptions(DM_CODE_CATEGORIES.componentPersonRole)
    ])
    memberOptions.value = memberRes.data.data ?? []
    roleOptions.value = roleRes.data.data ?? []
  } catch (e) {
    personOptionsError.value = apiErrorMessage(e, '成员/角色选项加载失败')
  } finally {
    personOptionsLoading.value = false
  }
}

function openPersonsSetup(system: SubsystemCandidate | null) {
  setupSystem.value = system
  setupNewMemberUserId.value = null
  setupNewMemberRole.value = ''
}

const setupPersons = computed(() => {
  const code = setupSystem.value?.code
  return code ? (pendingPersons.get(code) ?? []) : []
})

const setupAvailableMembers = computed(() => {
  const used = new Set(setupPersons.value.map(person => person.user_id))
  return memberOptions.value.filter(member => !used.has(member.user_id))
})

function addSetupPerson() {
  if (!setupSystem.value) return
  if (setupNewMemberUserId.value == null || !setupNewMemberRole.value) return
  const member = memberOptions.value.find(option => option.user_id === setupNewMemberUserId.value)
  if (!member) return
  const list = pendingPersons.get(setupSystem.value.code) ?? []
  if (list.some(person => person.user_id === member.user_id)) {
    ElMessage.warning('该成员已在此系统的关联人员中')
    return
  }
  list.push({
    user_id: member.user_id,
    display_name: member.display_name,
    person_role: setupNewMemberRole.value,
    person_role_label: roleOptions.value.find(option => option.value === setupNewMemberRole.value)?.label
  })
  pendingPersons.set(setupSystem.value.code, list)
  setupNewMemberUserId.value = null
  setupNewMemberRole.value = ''
}

function removeSetupPerson(userId: number) {
  if (!setupSystem.value) return
  const list = pendingPersons.get(setupSystem.value.code) ?? []
  pendingPersons.set(setupSystem.value.code, list.filter(person => person.user_id !== userId))
}

function updateSetupPersonRole(userId: number, role: string) {
  if (!setupSystem.value) return
  const list = pendingPersons.get(setupSystem.value.code) ?? []
  const person = list.find(candidate => candidate.user_id === userId)
  if (!person) return
  person.person_role = role
  person.person_role_label = roleOptions.value.find(option => option.value === role)?.label
  pendingPersons.set(setupSystem.value.code, list)
}

async function submitCreate() {
  if (scopeProjectId.value == null) return ElMessage.warning('当前项目不可用，请在顶部项目切换器中重新选择项目')
  if (selectedSystems.size === 0) return ElMessage.warning('请先勾选要新增的系统')
  createSaving.value = true
  let successCount = 0
  let failCount = 0
  const errors: string[] = []
  const createdCodes = new Set<string>()
  try {
    for (const [code, totalCheck] of selectedSystems) {
      try {
        await createDataMigrationComponent({
          projectId: scopeProjectId.value,
          systemCode: code,
          totalCheck
        })
        createdCodes.add(code)
        successCount++
      } catch (e) {
        failCount++
        errors.push(`${code}：${apiErrorMessage(e, '新增失败')}`)
        if (failCount >= 10) break
      }
    }
    const personErrors: string[] = []
    for (const [code, persons] of pendingPersons) {
      if (!persons.length || !createdCodes.has(code)) continue
      try {
        await saveComponentPersons(scopeProjectId.value, code,
          persons.map(person => ({ userId: person.user_id, personRole: person.person_role })))
      } catch (e) {
        personErrors.push(`${code}：${apiErrorMessage(e, '关联人员保存失败')}`)
      }
    }
    if (failCount === 0) {
      if (personErrors.length === 0) {
        ElMessage.success(`批量新增成功（${successCount} 个系统）`)
      } else {
        ElMessage.warning(`新增成功（${successCount} 个系统），但 ${personErrors.length} 个系统的关联人员保存失败，请到修改中重试`)
      }
      createOpen.value = false
    } else {
      const extra = personErrors.length ? `；另有 ${personErrors.length} 个系统的关联人员保存失败` : ''
      ElMessage.warning(`批量新增完成：成功 ${successCount} 个，失败 ${failCount} 个${extra}`)
    }
    await load()
  } catch (e) {
    ElMessage.error(apiErrorMessage(e, '批量新增失败'))
  } finally {
    createSaving.value = false
  }
}

/* ---------- 编辑抽屉（仅总分核对） ---------- */
const editOpen = ref(false)
const editSaving = ref(false)
const editing = ref<DataMigrationComponent | null>(null)
const editTotalCheck = ref(0)

function openEdit(row: DataMigrationComponent) {
  editing.value = row
  editTotalCheck.value = row.total_check
  editOpen.value = true
  resetPersonsState()
  if (scopeProjectId.value != null) {
    void openViewPersons(scopeProjectId.value, row.system_code)
  }
}

async function submitEdit() {
  if (!editing.value) return
  if (scopeProjectId.value == null) return ElMessage.warning('当前项目不可用，请在顶部项目切换器中重新选择项目')
  editSaving.value = true
  try {
    await updateDataMigrationComponent({ projectId: scopeProjectId.value, systemCode: editing.value.system_code, totalCheck: editTotalCheck.value })
    ElMessage.success('修改成功')
    editOpen.value = false
    await load()
  } catch (e) {
    ElMessage.error(apiErrorMessage(e, '修改失败'))
  } finally {
    editSaving.value = false
  }
}

async function removeComponent(row: DataMigrationComponent) {
  try {
    if (scopeProjectId.value == null) return ElMessage.warning('当前项目不可用，请在顶部项目切换器中重新选择项目')
    await ElMessageBox.confirm(`确认物理删除组件「${row.system_name || row.system_code}」（系统编号 ${row.system_code}）吗？该操作不可恢复。`, '删除组件', { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' })
    actionBusy.value = true
    await deleteDataMigrationComponent(scopeProjectId.value, row.system_code)
    ElMessage.success('已删除')
    await load()
  } catch (e) {
    if (!cancelled(e)) ElMessage.error(apiErrorMessage(e, '删除失败'))
  } finally {
    actionBusy.value = false
  }
}

async function toggleEnabled(row: DataMigrationComponent) {
  if (scopeProjectId.value == null) return ElMessage.warning('当前项目不可用，请在顶部项目切换器中重新选择项目')
  const enabled = row.enabled === 1
  try {
    await ElMessageBox.confirm(enabled ? `确认停用系统「${row.system_code}」吗？停用后新增/编辑/下拉将不再可选。` : `确认重新启用系统「${row.system_code}」吗？`, enabled ? '停用系统' : '启用系统', { type: 'warning' })
    actionBusy.value = true
    await setDataMigrationComponentEnabled(scopeProjectId.value, row.system_code, !enabled)
    ElMessage.success(enabled ? '已停用' : '已启用')
    await load()
  } catch (e) {
    if (!cancelled(e)) ElMessage.error(apiErrorMessage(e, enabled ? '停用失败' : '启用失败'))
  } finally {
    actionBusy.value = false
  }
}

/* ---------- 查看详情：关联人员展示与管理 ---------- */
const viewOpen = ref(false)
const viewing = ref<DataMigrationComponent | null>(null)
const viewPersons = ref<ComponentPerson[]>([])
const viewPersonsLoading = ref(false)
const viewPersonsError = ref('')
const viewPersonsForbidden = ref(false)
const memberOptions = ref<ComponentMemberOption[]>([])
const roleOptions = ref<SelectOption[]>([])
const newMemberUserId = ref<number | null>(null)
const newMemberRole = ref<string>('')
const personsSaving = ref(false)

function resetPersonsState() {
  viewPersons.value = []
  viewPersonsLoading.value = false
  viewPersonsError.value = ''
  viewPersonsForbidden.value = false
  memberOptions.value = []
  roleOptions.value = []
  newMemberUserId.value = null
  newMemberRole.value = ''
  personsSaving.value = false
}

function openView(row: DataMigrationComponent) {
  viewing.value = row
  viewOpen.value = true
  resetPersonsState()
}

async function openViewPersons(projectId: number, systemCode: string) {
  viewPersonsLoading.value = true
  viewPersonsError.value = ''
  viewPersonsForbidden.value = false
  try {
    const [personsRes, memberRes, roleRes] = await Promise.all([
      getComponentPersons(projectId, systemCode),
      getComponentMemberOptions(projectId),
      getDataMigrationParamOptions(DM_CODE_CATEGORIES.componentPersonRole)
    ])
    viewPersons.value = personsRes.data.data ?? []
    memberOptions.value = memberRes.data.data ?? []
    roleOptions.value = roleRes.data.data ?? []
  } catch (e) {
    if (httpStatus(e) === 403) viewPersonsForbidden.value = true
    else viewPersonsError.value = apiErrorMessage(e, '关联人员加载失败')
  } finally {
    viewPersonsLoading.value = false
  }
}

const availableMemberOptions = computed(() => {
  const used = new Set(viewPersons.value.map(person => person.user_id))
  return memberOptions.value.filter(member => !used.has(member.user_id))
})

function addPersonRow() {
  if (newMemberUserId.value == null || !newMemberRole.value) return
  const member = memberOptions.value.find(option => option.user_id === newMemberUserId.value)
  if (!member) return
  viewPersons.value.push({
    user_id: member.user_id,
    display_name: member.display_name,
    person_role: newMemberRole.value,
    person_role_label: roleOptions.value.find(option => option.value === newMemberRole.value)?.label
  })
  newMemberUserId.value = null
  newMemberRole.value = ''
}

function removePersonRow(userId: number) {
  viewPersons.value = viewPersons.value.filter(person => person.user_id !== userId)
}

function updatePersonRole(userId: number, role: string) {
  const person = viewPersons.value.find(candidate => candidate.user_id === userId)
  if (!person) return
  person.person_role = role
  person.person_role_label = roleOptions.value.find(option => option.value === role)?.label
}

async function savePersons() {
  if (scopeProjectId.value == null) return ElMessage.warning('当前项目不可用，请在顶部项目切换器中重新选择项目')
  const systemCode = viewing.value?.system_code ?? editing.value?.system_code
  if (!systemCode) return
  personsSaving.value = true
  try {
    await saveComponentPersons(scopeProjectId.value, systemCode,
      viewPersons.value.map(person => ({ userId: person.user_id, personRole: person.person_role })))
    ElMessage.success('关联人员已保存')
    await openViewPersons(scopeProjectId.value, systemCode)
    await load()
  } catch (e) {
    if (httpStatus(e) === 403) ElMessage.error('没有关联人员保存权限')
    else ElMessage.error(apiErrorMessage(e, '关联人员保存失败'))
  } finally {
    personsSaving.value = false
  }
}

onMounted(() => { void scope.ensureLoaded() })

// 全局项目变化：丢弃上一个项目的列表、筛选与分页状态，按新项目重新查询。
watch(scopeProjectId, () => {
  rows.value = []
  total.value = 0
  page.value = 1
  Object.assign(filters, {
    businessGroupName: '',
    systemCode: '',
    responsibleTeam: '',
    systemKeyword: '',
    totalCheck: undefined,
    keyword: ''
  })
  createOpen.value = false
  editOpen.value = false
  viewOpen.value = false
  resetPersonsState()
  pendingPersons.clear()
  setupSystem.value = null
  personOptionsError.value = ''
  error.value = ''
  forbidden.value = false
  filterSystemOpts.value = []
  void loadFilterSystemOptions()
  void load()
}, { immediate: true })

watch(createOpen, (open) => {
  if (!open) {
    pendingPersons.clear()
    setupSystem.value = null
    setupNewMemberUserId.value = null
    setupNewMemberRole.value = ''
  }
})
</script>

<template>
  <main class="dm-page-root components-page">
    <UiPageHeader title="系统/组件清单" description="列表、新增均固定属于顶部项目切换器选择的当前项目。">
      <template #actions>
        <el-tooltip content="批量导入功能开发中，敬请期待" placement="bottom">
          <el-button disabled><el-icon><UploadFilled /></el-icon>批量导入</el-button>
        </el-tooltip>
        <el-button v-if="canManage && scopeState === 'ready' && !forbidden && !error" type="primary" :disabled="loading || actionBusy" @click="openCreate">
          <el-icon><Plus /></el-icon>批量新增组件
        </el-button>
      </template>
    </UiPageHeader>

    <ProjectScopeState v-if="scopeState !== 'ready'" :state="scopeState" @retry="scope.retry()" />
    <section v-else-if="forbidden" class="dm-state-panel"><el-result icon="warning" title="暂无组件清单查看权限" sub-title="请向数据迁移管理员申请组件清单管理权限。" /></section>
    <section v-else-if="error" class="dm-state-panel"><el-result icon="error" title="组件清单加载失败" :sub-title="error"><template #extra><el-button type="primary" @click="load">重新加载</el-button></template></el-result></section>
    <template v-else>
      <UiToolbar>
        <el-select v-model="filters.systemCode" clearable filterable :loading="filterSystemLoading"
          placeholder="涉及物理子系统(编号/名称)" style="width: 250px" @change="search" @clear="search">
          <el-option v-for="o in filterSystemOpts" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
        <el-select v-model="filters.totalCheck" clearable placeholder="总分核对" style="width: 120px">
          <el-option label="是" :value="1" />
          <el-option label="否" :value="0" />
        </el-select>
        <el-button v-if="!advanced" link type="primary" @click="advanced = true">更多筛选</el-button>
        <template #actions>
          <el-button :disabled="loading || actionBusy" @click="load"><el-icon><Refresh /></el-icon>刷新</el-button>
          <el-button :disabled="loading || actionBusy" @click="search"><el-icon><Search /></el-icon>查询</el-button>
          <el-button :disabled="loading" @click="resetFilters">重置</el-button>
          <el-button :disabled="loading || actionBusy" @click="exportExcel"><el-icon><Download /></el-icon>导出 Excel</el-button>
        </template>
      </UiToolbar>

      <section v-if="advanced" class="components-advanced-filter">
        <el-form label-width="84px">
          <el-form-item label="事业群"><el-input v-model="filters.businessGroupName" clearable placeholder="事业群模糊匹配" @keyup.enter="search" /></el-form-item>
          <el-form-item label="负责团队"><el-input v-model="filters.responsibleTeam" clearable placeholder="负责团队模糊匹配" @keyup.enter="search" /></el-form-item>
          <el-form-item label="关键字"><el-input v-model="filters.keyword" clearable placeholder="系统编号/名称/简称模糊" @keyup.enter="search" /></el-form-item>
          <el-form-item>
            <el-button type="primary" @click="advanced = false">收起</el-button>
          </el-form-item>
        </el-form>
      </section>

      <div v-if="rows.length || loading" class="components-desktop-table">
        <UiDataTable :data="rows" :loading="loading" row-key="system_code" border empty-text="暂无组件数据">
          <el-table-column prop="business_group_name" label="所属事业群" min-width="120" show-overflow-tooltip />
          <el-table-column label="系统编号" min-width="140">
            <template #default="{ row }">
              <button type="button" class="dm-code-link" :disabled="actionBusy" @click="openView(row)">{{ row.system_code }}</button>
            </template>
          </el-table-column>
          <el-table-column prop="system_short_name" label="系统简称" min-width="120" show-overflow-tooltip />
          <el-table-column prop="system_name" label="系统名称" min-width="170" show-overflow-tooltip />
          <el-table-column prop="system_description" label="系统描述" min-width="180" show-overflow-tooltip />
          <el-table-column prop="responsible_team_name" label="负责团队" min-width="130" show-overflow-tooltip />
          <el-table-column label="关联人员" min-width="220">
            <template #default="{ row }"><PersonChips :persons="row.persons || []" /></template>
          </el-table-column>
          <el-table-column label="总分核对" width="100" align="center">
            <template #default="{ row }"><el-tag :type="row.total_check === 1 ? 'success' : 'info'" effect="plain" size="small">{{ row.total_check === 1 ? '是' : '否' }}</el-tag></template>
          </el-table-column>
          <el-table-column label="启用状态" width="100" align="center">
            <template #default="{ row }"><el-tag :type="row.enabled === 1 ? 'success' : 'danger'" effect="plain" size="small">{{ row.enabled === 1 ? '启用' : '停用' }}</el-tag></template>
          </el-table-column>
          <el-table-column prop="created_at" label="创建时间" min-width="160" show-overflow-tooltip />
          <el-table-column prop="created_by_name" label="创建人" min-width="100" show-overflow-tooltip />
          <el-table-column prop="updated_at" label="更新时间" min-width="160" show-overflow-tooltip />
          <el-table-column prop="updated_by_name" label="更新人" min-width="100" show-overflow-tooltip />
          <el-table-column label="操作" width="260" fixed="right" align="center">
            <template #default="{ row }">
              <div class="dm-table-actions">
                <el-button v-if="canManage" link type="primary" :disabled="actionBusy" @click="openEdit(row)"><el-icon><Edit /></el-icon>修改</el-button>
                <el-button v-if="canManage" link :type="row.enabled === 1 ? 'warning' : 'success'" :disabled="actionBusy" @click="toggleEnabled(row)">{{ row.enabled === 1 ? '停用' : '启用' }}</el-button>
                <el-button v-if="canManage" link type="danger" :disabled="actionBusy" @click="removeComponent(row)"><el-icon><Delete /></el-icon>删除</el-button>
              </div>
            </template>
          </el-table-column>
          <template #footer>
            <div class="dm-table-footer">
              <span>共 {{ total }} 条</span>
              <el-pagination background layout="total, sizes, prev, pager, next" :total="total" :current-page="page" :page-size="pageSize" :page-sizes="[20, 50, 100]" @current-change="onPageChange" @size-change="onSizeChange" />
            </div>
          </template>
        </UiDataTable>
      </div>

      <div v-if="rows.length || loading" class="dm-mobile-list">
        <article v-for="row in rows" :key="row.system_code">
          <header>
            <div>
              <button type="button" class="dm-code-link" :disabled="actionBusy" @click="openView(row)">{{ row.system_code }}</button>
              <small>{{ row.system_short_name || row.system_name }}</small>
            </div>
            <el-tag :type="row.total_check === 1 ? 'success' : 'info'" effect="plain" size="small">总分核对：{{ row.total_check === 1 ? '是' : '否' }}</el-tag>
          </header>
          <dl>
            <div><dt>系统编号</dt><dd>{{ row.system_code }}</dd></div>
            <div><dt>事业群</dt><dd>{{ row.business_group_name }}</dd></div>
            <div><dt>系统简称</dt><dd>{{ row.system_short_name }}</dd></div>
            <div><dt>负责团队</dt><dd>{{ row.responsible_team_name }}</dd></div>
            <div><dt>关联人员</dt><dd><PersonChips :persons="row.persons || []" /></dd></div>
            <div><dt>创建时间</dt><dd>{{ row.created_at }}</dd></div>
            <div><dt>创建人</dt><dd>{{ row.created_by_name }}</dd></div>
            <div><dt>更新时间</dt><dd>{{ row.updated_at }}</dd></div>
            <div><dt>更新人</dt><dd>{{ row.updated_by_name }}</dd></div>
          </dl>
          <footer>
            <el-button v-if="canManage" link type="primary" :disabled="actionBusy" @click="openEdit(row)"><el-icon><Edit /></el-icon>修改</el-button>
            <el-button v-if="canManage" link :type="row.enabled === 1 ? 'warning' : 'success'" :disabled="actionBusy" @click="toggleEnabled(row)">{{ row.enabled === 1 ? '停用' : '启用' }}</el-button>
            <el-button v-if="canManage" link type="danger" :disabled="actionBusy" @click="removeComponent(row)"><el-icon><Delete /></el-icon>删除</el-button>
          </footer>
        </article>
        <div class="dm-table-footer">
          <span>共 {{ total }} 条</span>
          <el-pagination background layout="prev, pager, next" :total="total" :current-page="page" :page-size="pageSize" @current-change="onPageChange" />
        </div>
      </div>

      <UiEmptyState v-if="!loading && !rows.length" title="暂无组件数据" description="当前项目下没有组件记录，可通过「批量新增组件」录入，或调整筛选条件。" />
    </template>

    <UiFormDrawer v-model="createOpen" title="批量新增组件" width="min(900px, 92vw)" :loading="createSaving || subsystemSearching" confirm-text="确认新增" @submit="submitCreate">
      <el-alert type="info" :closable="false" show-icon
        :title="`当前项目可新增 ${availableSystems.length} 个系统，已选 ${selectedCount} 个`"
        sub-title="勾选要新增的系统，每个系统可单独设置是否涉及总分核对与关联人员；系统信息从架构主数据联动带出，仅展示、不保存。" />

      <div class="components-batch-filter">
        <el-select v-model="filterBusinessGroup" clearable placeholder="所属事业群" style="width: 200px">
          <el-option v-for="bg in businessGroupOptions" :key="bg" :label="bg" :value="bg" />
        </el-select>
        <el-input v-model="filterKeyword" clearable placeholder="子系统编号 / 名称（不区分大小写）" style="width: 320px">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <div class="components-batch-filter-actions">
          <el-button link type="primary" :disabled="!filteredSystems.length" @click="selectAllFiltered">全选当前筛选</el-button>
          <el-button link type="danger" :disabled="!filteredSystems.length" @click="deselectAllFiltered">取消全选</el-button>
        </div>
      </div>

      <div class="components-batch-table-wrap">
        <el-table
          :data="filteredSystems"
          :row-key="(row: SubsystemCandidate) => row.code"
          border
          size="small"
          max-height="420px"
          empty-text="没有匹配的系统"
        >
          <el-table-column type="selection" width="44" :selectable="() => true">
            <template #header>
              <el-checkbox
                :model-value="filteredSystems.length > 0 && filteredSystems.every(s => isSelected(s.code))"
                :indeterminate="filteredSystems.some(s => isSelected(s.code)) && !filteredSystems.every(s => isSelected(s.code))"
                @change="(val: boolean) => val ? selectAllFiltered() : deselectAllFiltered()"
              />
            </template>
            <template #default="{ row }">
              <el-checkbox
                :model-value="isSelected(row.code)"
                @change="toggleSelect(row)"
              />
            </template>
          </el-table-column>
          <el-table-column prop="businessGroupName" label="所属事业群" min-width="120" show-overflow-tooltip>
            <template #default="{ row }">{{ row.businessGroupName || '-' }}</template>
          </el-table-column>
          <el-table-column prop="code" label="物理子系统编号" min-width="140" show-overflow-tooltip>
            <template #default="{ row }"><span class="dm-system-code">{{ row.code }}</span></template>
          </el-table-column>
          <el-table-column label="物理子系统名称" min-width="180" show-overflow-tooltip>
            <template #default="{ row }">{{ row.shortName || row.name }}</template>
          </el-table-column>
          <el-table-column label="总分核对" width="140" align="center">
            <template #default="{ row }">
              <el-radio-group
                :model-value="getTotalCheck(row.code)"
                size="small"
                :disabled="!isSelected(row.code)"
                @change="(val: number) => setSelectedTotalCheck(row.code, val)"
              >
                <el-radio-button :value="0">否</el-radio-button>
                <el-radio-button :value="1">是</el-radio-button>
              </el-radio-group>
            </template>
          </el-table-column>
          <el-table-column label="关联人员" min-width="180">
            <template #default="{ row }">
              <span v-if="!isSelected(row.code)" class="dm-muted">-</span>
              <div v-else class="dm-person-cell">
                <PersonChips v-if="(pendingPersons.get(row.code) || []).length" :persons="pendingPersons.get(row.code) || []" />
                <el-button link type="primary" size="small" @click="openPersonsSetup(row)">
                  {{ (pendingPersons.get(row.code) || []).length ? '编辑' : '设置关联人员' }}
                </el-button>
              </div>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <div v-if="setupSystem" class="components-person-setup">
        <div class="components-person-setup__head">
          <strong>{{ setupSystem.code }} - {{ setupSystem.shortName || setupSystem.name }}：关联人员</strong>
          <el-button link type="primary" @click="openPersonsSetup(null)">收起</el-button>
        </div>
        <el-alert v-if="personOptionsError" type="error" :closable="false" show-icon :title="personOptionsError">
          <template #default>
            <el-button link type="primary" @click="ensurePersonOptions">重试</el-button>
          </template>
        </el-alert>
        <template v-else>
          <div class="dm-person-manage__add">
            <el-select v-model="setupNewMemberUserId" filterable clearable placeholder="选择项目成员" class="dm-person-manage__member" :loading="personOptionsLoading">
              <el-option v-for="member in setupAvailableMembers" :key="member.user_id" :label="member.display_name" :value="member.user_id" />
            </el-select>
            <el-select v-model="setupNewMemberRole" clearable placeholder="职责/角色" class="dm-person-manage__role">
              <el-option v-for="option in roleOptions" :key="option.value" :label="option.label" :value="option.value" />
            </el-select>
            <el-button type="primary" :disabled="setupNewMemberUserId == null || !setupNewMemberRole || personOptionsLoading" @click="addSetupPerson">添加</el-button>
          </div>
          <el-alert v-if="!roleOptions.length" type="warning" :closable="false" show-icon
            title="角色选项缺失，请先在“系统管理/参数管理”配置类别 DM_COMPONENT_PERSON_ROLE" class="dm-person-manage__notice" />
          <div v-if="!setupPersons.length" class="dm-person-empty">暂无关联人员</div>
          <div v-else class="dm-person-rows">
            <div v-for="person in setupPersons" :key="person.user_id" class="dm-person-row">
              <UiUserIdentity :user-id="person.user_id" :fallback-name="person.display_name" size="default" />
              <el-select :model-value="person.person_role" class="dm-person-row__role" size="small" @update:model-value="(role: string) => updateSetupPersonRole(person.user_id, role)">
                <el-option v-for="option in roleOptions" :key="option.value" :label="option.label" :value="option.value" />
              </el-select>
              <el-button link type="danger" @click="removeSetupPerson(person.user_id)">移除</el-button>
            </div>
          </div>
          <p class="dm-person-manage__hint">此处配置的关联人员将在确认新增后自动保存到对应系统；一人一个角色一条。</p>
        </template>
      </div>

      <div class="components-batch-footer">
        <span>已选 <b>{{ selectedCount }}</b> 个系统</span>
        <el-radio-group size="small" :disabled="selectedCount === 0" @change="(val: number) => setAllSelectedTotalCheck(val)">
          <el-radio-button :value="0">全部设为「否」</el-radio-button>
          <el-radio-button :value="1">全部设为「是」</el-radio-button>
        </el-radio-group>
      </div>
    </UiFormDrawer>

    <UiFormDrawer v-model="editOpen" title="修改组件" width="min(640px, 92vw)" :loading="editSaving" confirm-text="保存" @submit="submitEdit">
      <el-form label-width="96px" label-position="left">
        <el-form-item label="系统编号"><el-input :model-value="editing?.system_code" disabled /></el-form-item>
        <el-form-item label="所属事业群"><el-input :model-value="editing?.business_group_name || '-'" disabled /></el-form-item>
        <el-form-item label="系统简称"><el-input :model-value="editing?.system_short_name" disabled /></el-form-item>
        <el-form-item label="系统名称"><el-input :model-value="editing?.system_name" disabled /></el-form-item>
        <el-form-item label="系统描述"><el-input :model-value="editing?.system_description || '-'" type="textarea" :rows="2" disabled /></el-form-item>
        <el-form-item label="负责团队"><el-input :model-value="editing?.responsible_team_name || '-'" disabled /></el-form-item>
        <el-form-item label="总分核对" required>
          <el-radio-group v-model="editTotalCheck">
            <el-radio :value="0">否</el-radio>
            <el-radio :value="1">是</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="关联人员">
          <div class="dm-person-manage">
            <el-alert v-if="viewPersonsForbidden" type="error" :closable="false" show-icon title="没有关联人员访问权限" />
            <el-alert v-else-if="viewPersonsError" type="error" :closable="false" show-icon :title="viewPersonsError">
              <template #default>
                <el-button link type="primary" @click="editing && scopeProjectId != null && openViewPersons(scopeProjectId, editing.system_code)">重试</el-button>
              </template>
            </el-alert>
            <el-skeleton v-else-if="viewPersonsLoading" :rows="2" animated />
            <div v-else class="dm-person-manage__body">
              <div class="dm-person-manage__add">
                <el-select v-model="newMemberUserId" filterable clearable placeholder="选择项目成员" class="dm-person-manage__member">
                  <el-option v-for="member in availableMemberOptions" :key="member.user_id" :label="member.display_name" :value="member.user_id" />
                </el-select>
                <el-select v-model="newMemberRole" clearable placeholder="职责/角色" class="dm-person-manage__role">
                  <el-option v-for="option in roleOptions" :key="option.value" :label="option.label" :value="option.value" />
                </el-select>
                <el-button type="primary" :disabled="newMemberUserId == null || !newMemberRole || personsSaving" @click="addPersonRow">添加</el-button>
              </div>
              <el-alert v-if="!roleOptions.length" type="warning" :closable="false" show-icon
                title="角色选项缺失，请先在“系统管理/参数管理”配置类别 DM_COMPONENT_PERSON_ROLE" class="dm-person-manage__notice" />
              <div v-if="!viewPersons.length" class="dm-person-empty">暂无关联人员</div>
              <div v-else class="dm-person-rows">
                <div v-for="person in viewPersons" :key="person.user_id" class="dm-person-row">
                  <UiUserIdentity :user-id="person.user_id" :fallback-name="person.display_name" size="default" />
                  <el-select :model-value="person.person_role" class="dm-person-row__role" size="small" @update:model-value="(role: string) => updatePersonRole(person.user_id, role)">
                    <el-option v-for="option in roleOptions" :key="option.value" :label="option.label" :value="option.value" />
                  </el-select>
                  <el-button link type="danger" :disabled="personsSaving" @click="removePersonRow(person.user_id)">移除</el-button>
                </div>
              </div>
              <div class="dm-person-manage__actions">
                <el-button type="primary" :loading="personsSaving" :disabled="personsSaving || viewPersonsLoading" @click="savePersons">保存关联人员</el-button>
                <span class="dm-person-manage__hint">一人一个角色一条，保存为全量替换</span>
              </div>
            </div>
          </div>
        </el-form-item>
        <el-alert type="info" :closable="false" show-icon title="组件其他信息由系统编号联动物理子系统维护，仅允许修改总分核对与关联人员。" class="components-subsystem-alert" />
      </el-form>
    </UiFormDrawer>

    <!-- 查看详情弹窗 -->
    <el-dialog v-model="viewOpen" title="组件详情" :width="'min(600px, calc(100vw - 24px))'" :close-on-click-modal="true" align-center destroy-on-close>
      <el-form label-width="96px" label-position="left">
        <el-form-item label="系统编号"><el-input :model-value="viewing?.system_code" disabled /></el-form-item>
        <el-form-item label="所属事业群"><el-input :model-value="viewing?.business_group_name || '-'" disabled /></el-form-item>
        <el-form-item label="系统简称"><el-input :model-value="viewing?.system_short_name" disabled /></el-form-item>
        <el-form-item label="系统名称"><el-input :model-value="viewing?.system_name" disabled /></el-form-item>
        <el-form-item label="系统描述"><el-input :model-value="viewing?.system_description || '-'" type="textarea" :rows="2" disabled /></el-form-item>
        <el-form-item label="负责团队"><el-input :model-value="viewing?.responsible_team_name || '-'" disabled /></el-form-item>
        <el-form-item label="关联人员">
          <div class="dm-person-rows">
            <template v-if="viewing && viewing.persons && viewing.persons.length">
              <div v-for="person in viewing.persons" :key="person.user_id" class="dm-person-row">
                <UiUserIdentity :user-id="person.user_id" :fallback-name="person.display_name" size="default" />
                <el-tag size="small" effect="plain" type="info">{{ person.person_role_label || person.person_role }}</el-tag>
              </div>
            </template>
            <span v-else class="dm-muted">暂无关联人员</span>
          </div>
        </el-form-item>
        <el-form-item label="总分核对">
          <el-tag :type="viewing?.total_check === 1 ? 'success' : 'info'" effect="plain" size="small">{{ viewing?.total_check === 1 ? '是' : '否' }}</el-tag>
        </el-form-item>
        <el-form-item label="创建时间"><el-input :model-value="viewing?.created_at" disabled /></el-form-item>
        <el-form-item label="创建人"><el-input :model-value="viewing?.created_by_name" disabled /></el-form-item>
        <el-form-item label="更新时间"><el-input :model-value="viewing?.updated_at" disabled /></el-form-item>
        <el-form-item label="更新人"><el-input :model-value="viewing?.updated_by_name" disabled /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="viewOpen = false">关闭</el-button>
      </template>
    </el-dialog>
  </main>
</template>

<style scoped>
.components-page .ui-toolbar { align-items: flex-start; }
.components-page .ui-toolbar__filters, .components-page .ui-toolbar__actions { flex-wrap: wrap; }
.components-page .dm-table-actions { flex-wrap: nowrap; }
/* 系统编号点击查看详情（替代原“查看”按钮）；移动端保持与旧 strong 一致的块级换行布局（data-migration.css 的 .dm-mobile-list strong 规则已不再作用于该文本）。 */
.dm-code-link { padding: 0; border: 0; background: none; font: inherit; color: var(--primary); cursor: pointer; font-weight: 600; line-height: inherit; }
.dm-code-link:hover { text-decoration: underline; }
.dm-code-link:disabled { color: var(--muted); cursor: not-allowed; text-decoration: none; }
.dm-mobile-list .dm-code-link { display: block; max-width: 100%; overflow-wrap: anywhere; text-align: left; }
.components-page .dm-state-panel { padding: 0; }
.components-advanced-filter { margin: -6px 0 16px; padding: 14px 16px 0; background: var(--panel-bg); border: 1px solid var(--line); border-radius: 6px; }
.components-advanced-filter .el-form { display: flex; flex-wrap: wrap; gap: 0 14px; }
.components-subsystem-alert { width: 100%; margin-bottom: 16px; }
.components-batch-filter {
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 12px 0;
  flex-wrap: wrap;
}
.components-batch-filter-actions {
  margin-left: auto;
  display: flex;
  gap: 4px;
}
.components-batch-table-wrap {
  border: 1px solid var(--line);
  border-radius: 6px;
  overflow: hidden;
}
.components-batch-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 12px;
  font-size: 13px;
}
.components-batch-footer b { color: var(--primary); font-weight: 600; }
.dm-system-code { font-family: var(--mono-font, monospace); font-size: 12px; color: var(--primary); background: var(--primary-light); padding: 2px 6px; border-radius: 4px; }
.dm-muted { color: var(--muted); }
.dm-person-chips { display: inline-flex; flex-wrap: wrap; align-items: center; gap: 6px 12px; }
.dm-person-chip { display: inline-flex; align-items: center; gap: 6px; min-width: 0; }
.dm-person-more { cursor: pointer; color: var(--primary); font-weight: 600; padding: 2px 8px; border: 1px solid var(--line); border-radius: 12px; background: var(--panel-muted); }
.dm-person-cell { display: flex; flex-wrap: wrap; align-items: center; gap: 6px 10px; }
.components-person-setup { margin-top: 12px; padding: 12px 14px; border: 1px solid var(--line); border-radius: 6px; background: var(--panel-bg); }
.components-person-setup__head { display: flex; align-items: center; justify-content: space-between; gap: 8px; margin-bottom: 10px; font-size: 13px; }
.dm-person-manage { width: 100%; min-width: 0; }
.dm-person-manage__add { display: flex; flex-wrap: wrap; gap: 8px; align-items: center; margin-bottom: 10px; }
.dm-person-manage__member { flex: 1 1 160px; min-width: 130px; }
.dm-person-manage__role { flex: 1 1 120px; min-width: 110px; }
.dm-person-manage__notice { margin-bottom: 10px; }
.dm-person-manage__actions { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; margin-top: 12px; }
.dm-person-manage__hint { color: var(--muted); font-size: 12px; }
.dm-person-empty { color: var(--muted); padding: 6px 0; }
.dm-person-rows { display: flex; flex-direction: column; gap: 8px; }
.dm-person-row { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; padding: 6px 8px; border: 1px solid var(--line); border-radius: 6px; background: var(--panel-muted); }
/* UiUserIdentity 的 size 仅接受 ''/default/small/large 枚举：默认档 40px 通过 --el-avatar-size 的 CSS 变量还原为 28px 原尺寸（与 element-plus 数字 size 的内联变量渲染等价）。 */
.dm-person-row :deep(.el-avatar) { --el-avatar-size: 28px; }
.dm-person-row__role { width: 140px; }
.dm-person-readonly { width: 100%; min-width: 0; }

@media (max-width: 760px) {
  .components-page .ui-toolbar__filters, .components-page .ui-toolbar__actions { width: 100%; }
  .components-page .ui-toolbar .el-input, .components-page .ui-toolbar .el-select { width: 100% !important; }
  .components-advanced-filter .el-form-item,
  .components-advanced-filter .el-input,
  .components-advanced-filter .el-select { width: 100% !important; }
  .components-page .ui-toolbar__filters > .el-button, .components-page .ui-toolbar__actions > .el-button { flex: 1; }
  .components-desktop-table { display: none; }
}
</style>

<style>
/* 关联人员 +N 浮层由 el-popover 挂载到 body，非 scoped 样式跟随 popperClass。 */
.dm-person-popover .dm-person-popover-list { display: flex; flex-direction: column; gap: 8px; max-height: 60vh; overflow-y: auto; }
.dm-person-popover .dm-person-chip { justify-content: flex-start; }
</style>
