<!--
文件：web/src/modules/test-management/configuration/TestConfigurationPage.vue
说明：管理配置页面或交互组件。
用途：承载用户可见的加载、空、失败、提交和交互状态。
作者：hengguan
-->
<script setup lang="ts">
// 关键逻辑：页面只消费现有全局项目上下文；当前测试大类、项目和实体选择共同决定请求范围，前端显隐不替代服务端校验。
import { computed, onMounted, reactive, ref, watch } from "vue";
import {
  Calendar,
  Collection,
  Delete,
  Edit,
  Monitor,
  Plus,
  Refresh,
  Upload,
  UserFilled,
} from "@element-plus/icons-vue";
import {
  ElMessage,
  ElMessageBox,
  type FormInstance,
  type FormRules,
} from "element-plus";
import { useRoute } from "vue-router";
import { useProjectContextStore } from "../../../stores/project-context";
import UiDataTable from "../../../components/ui/UiDataTable.vue";
import UiEmptyState from "../../../components/ui/UiEmptyState.vue";
import TestManagementFormDialog from "../components/TestManagementFormDialog.vue";
import UiPageHeader from "../../../components/ui/UiPageHeader.vue";
import {
  createSystemRole,
  deleteSystemRole,
  deleteTestCycle,
  deleteTestDictionary,
  deleteTestDictionaryOption,
  deleteTestRound,
  downloadConfigurationTemplate,
  importParticipatingSystems,
  importSystemRoles,
  listConfigurationUsers,
  listParticipatingSystems,
  listSystemRoles,
  listTestDictionaries,
  listTestDictionaryOptions,
  listTestProjects,
  listTestRounds,
  listTestCycles,
  saveTestDictionary,
  saveTestDictionaryOption,
  saveTestCycle,
  saveTestRound,
  setParticipatingSystem,
  type ParticipatingSystem,
  type SystemRole,
  type TestCycle,
  type TestDictionary,
  type TestDictionaryOption,
  type TestDomain,
  type TestProjectOption,
  type TestRound,
  type UserDirectoryItem,
} from "../api";
import "./test-configuration.css";

const route = useRoute();
const projectContext = useProjectContextStore();
const domain = computed(() => String(route.params.domain) as TestDomain);
const domainName = computed(
  () =>
    ({
      "application-assembly": "应用组装测试",
      "user-testing": "用户测试",
      "non-functional": "非功能测试",
      security: "安全测试",
    })[domain.value] || "测试管理",
);
const projects = ref<TestProjectOption[]>([]);
const projectId = computed(
  () =>
    projects.value.find(
      (project) => project.project_code === projectContext.currentRef,
    )?.id,
);
const activeProjectName = computed(
  () =>
    projects.value.find((project) => project.id === projectId.value)
      ?.project_name || "请在顶部选择项目",
);
const activeTab = ref("systems");
const loading = ref(false);
const error = ref("");
const systems = ref<ParticipatingSystem[]>([]);
const roles = ref<SystemRole[]>([]);
const rounds = ref<TestRound[]>([]);
const dictionaries = ref<TestDictionary[]>([]);
const cycles = ref<TestCycle[]>([]);
const options = ref<TestDictionaryOption[]>([]);
const selectedSystem = ref<ParticipatingSystem>();
const selectedRound = ref<TestRound>();
const selectedDictionary = ref<TestDictionary>();
const users = ref<UserDirectoryItem[]>([]);
const drawerOpen = ref(false);
const drawerType = ref<"role" | "round" | "cycle" | "dictionary" | "option">(
  "role",
);
const editingId = ref<number>();
const saving = ref(false);
const importInput = ref<HTMLInputElement>();
const importKind = ref<"systems" | "roles">("systems");
const formRef = ref<FormInstance>();
const form = reactive<Record<string, any>>({});
const rules: FormRules = {
  code: [{ required: true, message: "请输入编码", trigger: "blur" }],
  name: [{ required: true, message: "请输入名称", trigger: "blur" }],
  planned_start_date: [
    { required: true, message: "请选择开始日期", trigger: "change" },
  ],
  planned_end_date: [
    { required: true, message: "请选择结束日期", trigger: "change" },
  ],
  user_id: [{ required: true, message: "请选择用户", trigger: "change" }],
  role_codes: [
    {
      type: "array",
      required: true,
      min: 1,
      message: "请至少选择一个测试角色",
      trigger: "change",
    },
  ],
};

const hasProject = computed(() => Boolean(projectId.value));
const drawerTitle = computed(
  () =>
    `${editingId.value ? "编辑" : "新增"}${{ role: "系统角色", round: "测试轮次", cycle: "测试周期", dictionary: "数据字典", option: "字典选项" }[drawerType.value]}`,
);
const systemName = computed(() =>
  selectedSystem.value
    ? `${selectedSystem.value.short_name || selectedSystem.value.name}（${selectedSystem.value.code}）`
    : "",
);
const groupedRoles = computed(() => {
  const directory = new Map(users.value.map((user) => [user.id, user]));
  const rows = new Map<
    number,
    {
      user_id: number;
      display_name: string;
      username: string;
      role_names: string[];
      record_ids: number[];
      created_at?: string;
    }
  >();
  for (const role of roles.value) {
    const user = directory.get(role.user_id);
    const row = rows.get(role.user_id) || {
      user_id: role.user_id,
      display_name: user?.displayName || `用户 ${role.user_id}`,
      username: user?.username || "—",
      role_names: [],
      record_ids: [],
      created_at: role.created_at,
    };
    row.role_names.push(role.role_name);
    row.record_ids.push(role.id);
    rows.set(role.user_id, row);
  }
  return [...rows.values()];
});
const roleTreeData = computed(() =>
  !projectId.value
    ? []
    : [
        {
          id: `project-${projectId.value}`,
          label: activeProjectName.value,
          node_type: "PROJECT",
          children: systems.value
            .filter((item) => item.enabled)
            .map((item) => ({
              id: `system-${item.physical_subsystem_id}`,
              label: `${item.short_name || item.name}（${item.code}）`,
              node_type: "SYSTEM",
              system: item,
            })),
        },
      ],
);
const roundTreeData = computed(() =>
  rounds.value.map((round) => ({
    id: round.id,
    label: `${round.round_code} · ${round.round_name}`,
    round,
  })),
);
const dictionaryTreeData = computed(() =>
  !projectId.value
    ? []
    : [
        {
          id: `dictionary-project-${projectId.value}`,
          label: activeProjectName.value,
          disabled: true,
          children: ["LOCAL", "SYSTEM", "EXTERNAL"]
            .map((source_type) => ({
              id: `dictionary-source-${source_type}`,
              label:
                source_type === "LOCAL"
                  ? "本地维护"
                  : source_type === "SYSTEM"
                    ? "系统生成"
                    : "外部数据源",
              disabled: true,
              children: dictionaries.value
                .filter((item) => item.source_type === source_type)
                .map((dictionary) => ({
                  id: dictionary.id,
                  label: dictionary.dictionary_name,
                  dictionary,
                })),
            }))
            .filter((group) => group.children.length),
        },
      ],
);
const roleTreeCurrentKey = computed(() =>
  selectedSystem.value
    ? `system-${selectedSystem.value.physical_subsystem_id}`
    : undefined,
);
const dictionaryTreeCurrentKey = computed(() => selectedDictionary.value?.id);

async function loadProjects() {
  loading.value = true;
  error.value = "";
  try {
    await projectContext.initialize();
    const response = await listTestProjects(domain.value);
    projects.value = response.data.data;
    await loadCurrent();
  } catch (cause: any) {
    error.value =
      cause?.response?.data?.message || "加载项目失败，请稍后重试。";
  } finally {
    loading.value = false;
  }
}
async function loadCurrent() {
  if (!projectId.value) return;
  loading.value = true;
  error.value = "";
  try {
    if (activeTab.value === "systems")
      systems.value = (
        await listParticipatingSystems(domain.value, {
          projectId: projectId.value,
          page: 1,
          size: 100,
        })
      ).data.data.records;
    if (activeTab.value === "roles") {
      systems.value = (
        await listParticipatingSystems(domain.value, {
          projectId: projectId.value,
          page: 1,
          size: 100,
        })
      ).data.data.records;
      if (!users.value.length)
        users.value = (await listConfigurationUsers(domain.value)).data.data;
      selectedSystem.value = systems.value.find(
        (item) =>
          item.physical_subsystem_id ===
          selectedSystem.value?.physical_subsystem_id,
      );
      if (!selectedSystem.value?.enabled) {
        roles.value = [];
        return;
      }
      roles.value = (
        await listSystemRoles(domain.value, {
          projectId: projectId.value,
          physicalId: selectedSystem.value.physical_subsystem_id,
          page: 1,
          size: 100,
        })
      ).data.data.records;
    }
    if (activeTab.value === "rounds") {
      rounds.value = (
        await listTestRounds(domain.value, {
          projectId: projectId.value,
          page: 1,
          size: 100,
        })
      ).data.data.records;
      selectedRound.value =
        rounds.value.find((item) => item.id === selectedRound.value?.id) ||
        rounds.value[0];
      cycles.value = selectedRound.value
        ? (
            await listTestCycles(domain.value, {
              projectId: projectId.value,
              roundId: selectedRound.value.id,
              page: 1,
              size: 100,
            })
          ).data.data.records
        : [];
    }
    if (activeTab.value === "dictionaries") {
      dictionaries.value = (
        await listTestDictionaries(domain.value, {
          projectId: projectId.value,
          page: 1,
          size: 100,
        })
      ).data.data.records;
      if (selectedDictionary.value)
        options.value = (
          await listTestDictionaryOptions(domain.value, {
            projectId: projectId.value,
            dictionaryId: selectedDictionary.value.id,
            page: 1,
            size: 100,
          })
        ).data.data.records;
    }
  } catch (cause: any) {
    error.value =
      cause?.response?.data?.message || "加载配置失败，请稍后重试。";
  } finally {
    loading.value = false;
  }
}
watch(projectId, () => {
  selectedSystem.value = undefined;
  selectedRound.value = undefined;
  selectedDictionary.value = undefined;
  void loadCurrent();
});
watch(activeTab, () => {
  void loadCurrent();
});
onMounted(loadProjects);

async function toggleSystem(system: ParticipatingSystem, enabled: boolean) {
  if (!projectId.value) return;
  if (!enabled) {
    const allowed = await ElMessageBox.confirm(
      `停用“${system.name}”后不能再分配新角色；后续业务模块的数据将保留。`,
      "确认停用参测系统",
      {
        type: "warning",
        confirmButtonText: "确认停用",
        cancelButtonText: "取消",
      },
    )
      .then(() => true)
      .catch(() => false);
    if (!allowed) return;
  }
  try {
    const response = await setParticipatingSystem(
      domain.value,
      projectId.value,
      system.physical_subsystem_id,
      { enabled, confirmed: !enabled, remark: system.remark },
    );
    if ("confirmation_required" in response.data.data) return;
    ElMessage.success(enabled ? "已设为参测系统" : "已停用参测系统");
    await loadCurrent();
  } catch (cause: any) {
    ElMessage.error(cause?.response?.data?.message || "保存失败");
  }
}
async function selectSystem(system: ParticipatingSystem) {
  selectedSystem.value = system;
  activeTab.value = "roles";
  await loadCurrent();
}
async function selectRoleSystem(physicalId: number) {
  selectedSystem.value = systems.value.find(
    (item) => item.physical_subsystem_id === physicalId,
  );
  await loadCurrent();
}
async function selectRoleTree(node: any) {
  if (node.node_type === "SYSTEM")
    await selectRoleSystem(node.system.physical_subsystem_id);
}
async function selectRound(node: TestRound | { round: TestRound }) {
  const round = "round" in node ? node.round : node;
  selectedRound.value = round;
  cycles.value = projectId.value
    ? (
        await listTestCycles(domain.value, {
          projectId: projectId.value,
          roundId: round.id,
          page: 1,
          size: 100,
        })
      ).data.data.records
    : [];
}
async function selectDictionary(dictionary: TestDictionary) {
  selectedDictionary.value = dictionary;
  options.value = projectId.value
    ? (
        await listTestDictionaryOptions(domain.value, {
          projectId: projectId.value,
          dictionaryId: dictionary.id,
          page: 1,
          size: 100,
        })
      ).data.data.records
    : [];
}
async function selectDictionaryTree(node: any) {
  if (node.dictionary) await selectDictionary(node.dictionary);
}

async function openCreate(type: typeof drawerType.value) {
  if (type === "cycle" && !selectedRound.value) {
    ElMessage.warning("请先在左侧选择测试轮次");
    return;
  }
  drawerType.value = type;
  editingId.value = undefined;
  Object.keys(form).forEach((key) => delete form[key]);
  if (type === "role") {
    users.value = (await listConfigurationUsers(domain.value)).data.data;
    form.role_codes = [];
  }
  if (type === "round")
    form.sort_no =
      Math.max(0, ...rounds.value.map((item) => item.sort_no || 0)) + 1;
  if (type === "cycle")
    form.sort_no =
      Math.max(0, ...cycles.value.map((item) => item.sort_no || 0)) + 1;
  form.enabled = true;
  form.status = "DRAFT";
  drawerOpen.value = true;
}
function openEdit(type: typeof drawerType.value, record: any) {
  drawerType.value = type;
  editingId.value = record.id;
  Object.keys(form).forEach((key) => delete form[key]);
  Object.assign(form, {
    ...record,
    code:
      record.round_code ||
      record.cycle_code ||
      record.dictionary_code ||
      record.option_code,
    name:
      record.round_name ||
      record.cycle_name ||
      record.dictionary_name ||
      record.option_name,
  });
  drawerOpen.value = true;
}
async function save() {
  if (!projectId.value) return;
  if (!(await formRef.value?.validate().catch(() => false))) return;
  saving.value = true;
  try {
    if (drawerType.value === "role" && selectedSystem.value)
      await Promise.all(
        (form.role_codes as string[]).map((role_code) =>
          createSystemRole(
            domain.value,
            projectId.value!,
            selectedSystem.value!.physical_subsystem_id,
            { user_id: Number(form.user_id), role_code },
          ),
        ),
      );
    if (drawerType.value === "round") {
      const response = await saveTestRound(domain.value, projectId.value, {
        id: editingId.value,
        round_code: editingId.value ? String(form.code) : undefined,
        round_name: String(form.name),
        planned_start_date: String(form.planned_start_date || ""),
        planned_end_date: String(form.planned_end_date || ""),
        status: String(form.status),
        sort_no: Number(form.sort_no || 0),
        remark: String(form.remark || ""),
      });
      selectedRound.value = response.data.data;
    }
    if (drawerType.value === "cycle") {
      if (!selectedRound.value) {
        ElMessage.error("请先在左侧选择测试轮次");
        return;
      }
      await saveTestCycle(
        domain.value,
        projectId.value,
        selectedRound.value.id,
        {
          id: editingId.value,
          cycle_code: editingId.value ? String(form.code) : undefined,
          cycle_name: String(form.name),
          planned_start_date: String(form.planned_start_date || ""),
          planned_end_date: String(form.planned_end_date || ""),
          status: String(form.status),
          sort_no: Number(form.sort_no || 0),
          remark: String(form.remark || ""),
        },
      );
    }
    if (drawerType.value === "dictionary")
      await saveTestDictionary(domain.value, projectId.value, {
        id: editingId.value,
        dictionary_code: String(form.code),
        dictionary_name: String(form.name),
        enabled: Boolean(form.enabled),
        remark: String(form.remark || ""),
      });
    if (drawerType.value === "option" && selectedDictionary.value)
      await saveTestDictionaryOption(
        domain.value,
        projectId.value,
        selectedDictionary.value.id,
        {
          id: editingId.value,
          option_code: String(form.code),
          option_name: String(form.name),
          enabled: Boolean(form.enabled),
          sort_no: Number(form.sort_no || 0),
          remark: String(form.remark || ""),
        },
      );
    ElMessage.success("已保存");
    drawerOpen.value = false;
    await loadCurrent();
  } catch (cause: any) {
    ElMessage.error(cause?.response?.data?.message || "保存失败");
  } finally {
    saving.value = false;
  }
}
function statusText(status: string) {
  return (
    (
      { DRAFT: "未开始", ACTIVE: "进行中", CLOSED: "已结束" } as Record<
        string,
        string
      >
    )[status] || status
  );
}
function statusType(status: string) {
  return status === "CLOSED"
    ? "success"
    : status === "ACTIVE"
      ? "primary"
      : "info";
}
async function removeRole(row: { display_name: string; record_ids: number[] }) {
  const allowed = await ElMessageBox.confirm(
    `确认移除 ${row.display_name} 在当前系统下的全部测试角色？后续业务记录不会被删除。`,
    "确认移除角色",
    { type: "warning" },
  )
    .then(() => true)
    .catch(() => false);
  if (!allowed) return;
  try {
    await Promise.all(
      row.record_ids.map((id) => deleteSystemRole(domain.value, id)),
    );
    ElMessage.success("已移除");
    await loadCurrent();
  } catch (cause: any) {
    ElMessage.error(cause?.response?.data?.message || "移除失败");
  }
}
async function removeRecord(
  type: "round" | "cycle" | "dictionary" | "option",
  id: number,
) {
  if (!projectId.value) return;
  const allowed = await ElMessageBox.confirm(
    "确认删除该配置？存在下级或后续业务引用时，服务端将拒绝删除。",
    "确认删除",
    { type: "warning" },
  )
    .then(() => true)
    .catch(() => false);
  if (!allowed) return;
  try {
    if (type === "round")
      await deleteTestRound(domain.value, projectId.value, id);
    if (type === "cycle" && selectedRound.value)
      await deleteTestCycle(
        domain.value,
        projectId.value,
        selectedRound.value.id,
        id,
      );
    if (type === "dictionary")
      await deleteTestDictionary(domain.value, projectId.value, id);
    if (type === "option" && selectedDictionary.value)
      await deleteTestDictionaryOption(
        domain.value,
        projectId.value,
        selectedDictionary.value.id,
        id,
      );
    ElMessage.success("已删除");
    await loadCurrent();
  } catch (cause: any) {
    ElMessage.error(cause?.response?.data?.message || "删除失败");
  }
}
async function finishRound(round: TestRound) {
  if (!projectId.value || round.status === "CLOSED") return;
  const allowed = await ElMessageBox.confirm(
    `确认结束“${round.round_name}”？结束后仍可查看，后续模块可据此归档统计和生成报告。`,
    "结束轮次",
    {
      type: "warning",
      confirmButtonText: "确认结束",
      cancelButtonText: "取消",
    },
  )
    .then(() => true)
    .catch(() => false);
  if (!allowed) return;
  try {
    await saveTestRound(domain.value, projectId.value, {
      ...round,
      status: "CLOSED",
    });
    ElMessage.success("轮次已结束");
    await loadCurrent();
  } catch (cause: any) {
    ElMessage.error(cause?.response?.data?.message || "结束轮次失败");
  }
}
async function moveCycle(cycle: TestCycle, offset: -1 | 1) {
  if (!projectId.value || !selectedRound.value) return;
  const index = cycles.value.findIndex((item) => item.id === cycle.id);
  const adjacent = cycles.value[index + offset];
  if (!adjacent) return;
  try {
    await Promise.all([
      saveTestCycle(domain.value, projectId.value, selectedRound.value.id, {
        ...cycle,
        sort_no: adjacent.sort_no,
      }),
      saveTestCycle(domain.value, projectId.value, selectedRound.value.id, {
        ...adjacent,
        sort_no: cycle.sort_no,
      }),
    ]);
    await selectRound(selectedRound.value);
    ElMessage.success("排序已保存");
  } catch (cause: any) {
    ElMessage.error(cause?.response?.data?.message || "调整排序失败");
  }
}
async function toggleOption(option: TestDictionaryOption, enabled: boolean) {
  if (!projectId.value || !selectedDictionary.value) return;
  try {
    await saveTestDictionaryOption(
      domain.value,
      projectId.value,
      selectedDictionary.value.id,
      { ...option, enabled },
    );
    ElMessage.success(enabled ? "选项已启用" : "选项已停用");
    await loadCurrent();
  } catch (cause: any) {
    ElMessage.error(cause?.response?.data?.message || "更新选项失败");
  }
}
function openImport(kind: "systems" | "roles") {
  importKind.value = kind;
  importInput.value?.click();
}
async function downloadTemplate(kind: "systems" | "roles") {
  try {
    await downloadConfigurationTemplate(
      domain.value,
      kind === "systems" ? "/systems/template" : "/roles/template",
      kind === "systems" ? "参测系统导入模板.xlsx" : "系统角色导入模板.xlsx",
    );
  } catch (cause: any) {
    ElMessage.error(cause?.response?.data?.message || "模板下载失败");
  }
}
async function importFile(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0];
  if (!file || !projectId.value) return;
  try {
    const response =
      importKind.value === "systems"
        ? await importParticipatingSystems(domain.value, projectId.value, file)
        : await importSystemRoles(domain.value, projectId.value, file);
    const result = response.data.data;
    if (result.success) {
      ElMessage.success(`已导入 ${result.written} 条配置`);
      await loadCurrent();
    } else
      ElMessage.error(
        `导入未写入：第 ${result.errors[0]?.row_number} 行 ${result.errors[0]?.message}`,
      );
  } catch (cause: any) {
    ElMessage.error(cause?.response?.data?.message || "导入失败");
  } finally {
    (event.target as HTMLInputElement).value = "";
  }
}
</script>

<template>
  <section class="test-configuration-page">
    <UiPageHeader eyebrow="测试管理" :title="`${domainName} · 管理配置`"
      ><template #actions
        ><el-tooltip content="刷新"
          ><el-button
            text
            circle
            size="small"
            :icon="Refresh"
            :loading="loading"
            aria-label="刷新配置"
            @click="loadCurrent" /></el-tooltip></template
    ></UiPageHeader>
    <el-alert
      v-if="error"
      type="error"
      :closable="false"
      show-icon
      :title="error"
      ><template #default
        ><el-button size="small" @click="loadCurrent">重试</el-button></template
      ></el-alert
    >
    <UiEmptyState
      v-if="!loading && !hasProject"
      title="请先选择项目"
      description="请通过页面顶部的全局项目选择器切换项目后，再维护测试管理配置。"
    />
    <el-tabs v-else v-model="activeTab" class="test-configuration-tabs">
      <el-tab-pane name="systems"
        ><template #label
          ><span class="test-configuration-tab-label"
            ><el-icon><Monitor /></el-icon><span>参测系统</span></span
          ></template
        >
        <div class="test-configuration-actions">
          <span>全部未删除物理子系统均可选；开关决定参测范围。</span>
          <div>
            <el-button @click="downloadTemplate('systems')">下载模板</el-button
            ><el-button @click="openImport('systems')"
              ><el-icon><Upload /></el-icon>导入</el-button
            >
          </div>
        </div>
        <UiDataTable
          :data="systems"
          :loading="loading"
          row-key="physical_subsystem_id"
          border
          class="configuration-table"
          ><el-table-column
            prop="name"
            label="物理子系统"
            min-width="260"
            sortable
            resizable
            ><template #default="scope"
              ><strong>{{ scope.row.short_name || scope.row.name }}</strong
              ><small>{{ scope.row.code }}</small></template
            ></el-table-column
          ><el-table-column
            prop="enabled"
            label="参测"
            width="100"
            align="center"
            header-align="center"
            sortable
            resizable
            ><template #default="scope"
              ><el-switch
                :model-value="Boolean(scope.row.enabled)"
                @update:model-value="
                  toggleSystem(scope.row, $event)
                " /></template></el-table-column
          ><el-table-column
            label="操作"
            width="110"
            fixed="right"
            align="center"
            header-align="center"
            :resizable="false"
            ><template #default="scope"
              ><el-button
                link
                type="primary"
                :disabled="!scope.row.enabled"
                @click="selectSystem(scope.row)"
                >配置角色</el-button
              ></template
            ></el-table-column
          ></UiDataTable
        >
        <div class="test-configuration-mobile-list">
          <article
            v-for="system in systems"
            :key="system.physical_subsystem_id"
          >
            <header>
              <div>
                <strong>{{ system.short_name || system.name }}</strong
                ><small>{{ system.code }}</small>
              </div>
              <el-switch
                :model-value="Boolean(system.enabled)"
                @update:model-value="toggleSystem(system, $event)"
              />
            </header>
            <footer>
              <el-button
                link
                type="primary"
                :disabled="!system.enabled"
                @click="selectSystem(system)"
                >配置角色</el-button
              >
            </footer>
          </article>
        </div>
        <UiEmptyState
          v-if="!loading && !systems.length"
          title="暂无物理子系统"
          description="当前租户没有可作为测试候选的物理子系统。"
      /></el-tab-pane>
      <el-tab-pane name="roles"
        ><template #label
          ><span class="test-configuration-tab-label"
            ><el-icon><UserFilled /></el-icon><span>系统角色</span></span
          ></template
        >
        <div class="test-configuration-workspace">
          <aside class="test-configuration-tree-panel">
            <header>项目 / 参测系统</header>
            <el-tree
              :data="roleTreeData"
              node-key="id"
              :current-node-key="roleTreeCurrentKey"
              highlight-current
              :expand-on-click-node="false"
              default-expand-all
              @node-click="selectRoleTree"
            />
          </aside>
          <div class="test-configuration-content-panel">
            <div class="test-configuration-actions">
              <span>{{ systemName || "请在左侧树选择已参测系统。" }}</span>
              <div>
                <el-button
                  :disabled="!selectedSystem"
                  @click="downloadTemplate('roles')"
                  >下载模板</el-button
                ><el-button
                  :disabled="!selectedSystem"
                  @click="openImport('roles')"
                  ><el-icon><Upload /></el-icon>导入</el-button
                ><el-button
                  type="primary"
                  :disabled="!selectedSystem"
                  @click="openCreate('role')"
                  ><el-icon><Plus /></el-icon>新增人员</el-button
                >
              </div>
            </div>
            <UiEmptyState
              v-if="!loading && !systems.some((item) => item.enabled)"
              title="暂无已启用参测系统"
              description="请先在“参测系统”中开启至少一个物理子系统。"
            /><UiDataTable
              v-else
              :data="groupedRoles"
              :loading="loading"
              row-key="user_id"
              border
              class="configuration-table"
              ><el-table-column
                prop="display_name"
                label="姓名"
                min-width="130"
                sortable
                resizable
              /><el-table-column
                prop="username"
                label="账号"
                min-width="140"
                sortable
                resizable
              /><el-table-column label="角色" min-width="220" resizable
                ><template #default="scope"
                  ><el-tag
                    v-for="roleName in scope.row.role_names"
                    :key="roleName"
                    class="test-configuration-role-tag"
                    >{{ roleName }}</el-tag
                  ></template
                ></el-table-column
              ><el-table-column
                prop="created_at"
                label="分配时间"
                min-width="170"
                sortable
                resizable
              /><el-table-column
                label="操作"
                width="72"
                align="center"
                header-align="center"
                :resizable="false"
                ><template #default="scope"
                  ><el-button link type="danger" @click="removeRole(scope.row)"
                    >移除</el-button
                  ></template
                ></el-table-column
              ></UiDataTable
            >
          </div>
        </div></el-tab-pane
      >
      <el-tab-pane name="rounds"
        ><template #label
          ><span class="test-configuration-tab-label"
            ><el-icon><Calendar /></el-icon><span>轮次与周期</span></span
          ></template
        >
        <div
          class="test-configuration-workspace test-configuration-round-workspace"
        >
          <aside
            class="test-configuration-tree-panel test-configuration-round-tree"
          >
            <header>
              <span>测试轮次</span
              ><el-button
                type="primary"
                size="small"
                @click="openCreate('round')"
                ><el-icon><Plus /></el-icon>新建轮次</el-button
              >
            </header>
            <el-tree
              :data="roundTreeData"
              node-key="id"
              :current-node-key="selectedRound?.id"
              highlight-current
              :expand-on-click-node="false"
              empty-text="暂无测试轮次"
              @node-click="selectRound"
            /><UiEmptyState
              v-if="!loading && !rounds.length"
              title="暂无测试轮次"
              description="请在这里新建第一个测试轮次。"
              ><template #action
                ><el-button type="primary" @click="openCreate('round')"
                  >新建轮次</el-button
                ></template
              ></UiEmptyState
            >
          </aside>
          <div class="test-configuration-content-panel">
            <UiEmptyState
              v-if="!loading && !selectedRound"
              title="请选择或新建测试轮次"
              description="周期从属于轮次。先在左侧选择轮次，再在此处维护周期。"
            /><template v-if="selectedRound"
              ><section class="test-configuration-round-summary">
                <div>
                  <strong
                    >{{ selectedRound.round_code }} ·
                    {{ selectedRound.round_name }}</strong
                  ><span
                    >{{ selectedRound.planned_start_date || "未设置开始日期" }}
                    至
                    {{
                      selectedRound.planned_end_date || "未设置结束日期"
                    }}</span
                  >
                </div>
                <div>
                  <el-tag
                    :type="statusType(selectedRound.status)"
                    effect="plain"
                    >{{ statusText(selectedRound.status) }}</el-tag
                  ><el-button @click="openEdit('round', selectedRound)"
                    ><el-icon><Edit /></el-icon>编辑轮次</el-button
                  ><el-button
                    v-if="selectedRound.status !== 'CLOSED'"
                    type="warning"
                    plain
                    @click="finishRound(selectedRound)"
                    >结束轮次</el-button
                  ><el-button
                    type="danger"
                    plain
                    @click="removeRecord('round', selectedRound.id)"
                    ><el-icon><Delete /></el-icon>删除轮次</el-button
                  >
                </div>
              </section>
              <section
                class="test-configuration-section test-configuration-cycle-section"
              >
                <header>
                  <div>
                    <strong>测试周期</strong
                    ><small>周期仅属于当前轮次；新建后自动排在末尾。</small>
                  </div>
                  <el-button type="primary" @click="openCreate('cycle')"
                    ><el-icon><Plus /></el-icon>新建周期</el-button
                  >
                </header>
                <UiDataTable
                  :data="cycles"
                  :loading="loading"
                  row-key="id"
                  border
                  class="configuration-table"
                  ><el-table-column
                    prop="cycle_name"
                    label="周期名称"
                    min-width="180"
                    sortable
                    resizable
                  /><el-table-column
                    prop="planned_start_date"
                    label="开始日期"
                    min-width="116"
                    sortable
                    resizable
                    ><template #default="scope">{{
                      scope.row.planned_start_date || "—"
                    }}</template></el-table-column
                  ><el-table-column
                    prop="planned_end_date"
                    label="结束日期"
                    min-width="116"
                    sortable
                    resizable
                    ><template #default="scope">{{
                      scope.row.planned_end_date || "—"
                    }}</template></el-table-column
                  ><el-table-column
                    prop="status"
                    label="状态"
                    width="96"
                    align="center"
                    header-align="center"
                    sortable
                    resizable
                    ><template #default="scope"
                      ><el-tag
                        :type="statusType(scope.row.status)"
                        effect="plain"
                        size="small"
                        >{{ statusText(scope.row.status) }}</el-tag
                      ></template
                    ></el-table-column
                  ><el-table-column
                    label="排序"
                    width="94"
                    align="center"
                    header-align="center"
                    :resizable="false"
                    ><template #default="scope"
                      ><el-button
                        link
                        :disabled="scope.$index === 0"
                        aria-label="周期上移"
                        @click="moveCycle(scope.row, -1)"
                        >上移</el-button
                      ><el-button
                        link
                        :disabled="scope.$index === cycles.length - 1"
                        aria-label="周期下移"
                        @click="moveCycle(scope.row, 1)"
                        >下移</el-button
                      ></template
                    ></el-table-column
                  ><el-table-column
                    label="操作"
                    width="112"
                    align="center"
                    header-align="center"
                    :resizable="false"
                    ><template #default="scope"
                      ><el-button link @click="openEdit('cycle', scope.row)"
                        >编辑</el-button
                      ><el-button
                        link
                        type="danger"
                        @click="removeRecord('cycle', scope.row.id)"
                        >删除</el-button
                      ></template
                    ></el-table-column
                  ></UiDataTable
                >
                <div class="test-configuration-cycle-mobile-list">
                  <article v-for="(cycle, index) in cycles" :key="cycle.id">
                    <header>
                      <strong>{{ cycle.cycle_name }}</strong
                      ><el-tag
                        :type="statusType(cycle.status)"
                        effect="plain"
                        size="small"
                        >{{ statusText(cycle.status) }}</el-tag
                      >
                    </header>
                    <p>
                      {{ cycle.planned_start_date || "—" }} 至
                      {{ cycle.planned_end_date || "—" }}
                    </p>
                    <footer>
                      <el-button
                        link
                        :disabled="index === 0"
                        @click="moveCycle(cycle, -1)"
                        >上移</el-button
                      ><el-button
                        link
                        :disabled="index === cycles.length - 1"
                        @click="moveCycle(cycle, 1)"
                        >下移</el-button
                      ><el-button link @click="openEdit('cycle', cycle)"
                        >编辑</el-button
                      ><el-button
                        link
                        type="danger"
                        @click="removeRecord('cycle', cycle.id)"
                        >删除</el-button
                      >
                    </footer>
                  </article>
                </div>
                <UiEmptyState
                  v-if="!loading && !cycles.length"
                  title="当前轮次暂无周期"
                  description="请新建一个周期，例如“主体测试”或“回归测试”。"
                  ><template #action
                    ><el-button type="primary" @click="openCreate('cycle')"
                      >新建周期</el-button
                    ></template
                  ></UiEmptyState
                ><el-alert
                  class="test-configuration-cycle-warning"
                  type="warning"
                  :closable="false"
                  show-icon
                  title="周期日期允许超出轮次范围；这是回归跨轮次场景的提醒，不会阻断保存。"
                /></section
            ></template>
          </div></div
      ></el-tab-pane>
      <el-tab-pane name="dictionaries"
        ><template #label
          ><span class="test-configuration-tab-label"
            ><el-icon><Collection /></el-icon><span>数据字典</span></span
          ></template
        >
        <div class="test-configuration-workspace">
          <aside class="test-configuration-tree-panel">
            <header>数据字典</header>
            <el-tree
              :data="dictionaryTreeData"
              node-key="id"
              :current-node-key="dictionaryTreeCurrentKey"
              highlight-current
              :expand-on-click-node="false"
              default-expand-all
              @node-click="selectDictionaryTree"
            />
          </aside>
          <div class="test-configuration-content-panel">
            <div class="test-configuration-actions">
              <span>{{
                selectedDictionary
                  ? `${selectedDictionary.dictionary_name}（${selectedDictionary.dictionary_code}）`
                  : "请在左侧选择数据字典。"
              }}</span
              ><el-button
                v-if="selectedDictionary?.source_type === 'LOCAL'"
                type="primary"
                @click="openCreate('option')"
                ><el-icon><Plus /></el-icon>新增选项</el-button
              >
            </div>
            <el-alert
              v-if="
                selectedDictionary && selectedDictionary.source_type !== 'LOCAL'
              "
              type="info"
              :closable="false"
              show-icon
              :title="
                selectedDictionary.source_type === 'SYSTEM'
                  ? '该字典由系统按轮次和周期自动生成，只读。'
                  : '该字典由外部数据源接入，只读。'
              "
            /><UiDataTable
              :data="options"
              :loading="loading"
              row-key="id"
              border
              class="configuration-table"
              ><el-table-column
                prop="option_name"
                label="选项名称"
                min-width="180"
                sortable
                resizable
              /><el-table-column
                prop="option_code"
                label="选项编码"
                min-width="130"
                sortable
                resizable
              /><el-table-column
                prop="sort_no"
                label="排序号"
                width="80"
                align="center"
                header-align="center"
                sortable
                resizable
              /><el-table-column
                prop="enabled"
                label="启用"
                width="80"
                align="center"
                header-align="center"
                sortable
                resizable
                ><template #default="scope"
                  ><el-switch
                    :model-value="Boolean(scope.row.enabled)"
                    :disabled="selectedDictionary?.source_type !== 'LOCAL'"
                    @update:model-value="
                      toggleOption(scope.row, $event)
                    " /></template></el-table-column
              ><el-table-column
                label="操作"
                width="120"
                align="center"
                header-align="center"
                :resizable="false"
                ><template #default="scope"
                  ><el-button
                    :disabled="selectedDictionary?.source_type !== 'LOCAL'"
                    link
                    @click="openEdit('option', scope.row)"
                    >编辑</el-button
                  ><el-button
                    :disabled="selectedDictionary?.source_type !== 'LOCAL'"
                    link
                    type="danger"
                    @click="removeRecord('option', scope.row.id)"
                    >删除</el-button
                  ></template
                ></el-table-column
              ></UiDataTable
            >
          </div>
        </div></el-tab-pane
      >
    </el-tabs>
    <input
      ref="importInput"
      class="test-configuration-file"
      type="file"
      accept=".xlsx"
      @change="importFile"
    />
    <TestManagementFormDialog
      v-model="drawerOpen"
      :title="drawerTitle"
      width="min(520px, calc(100vw - 24px))"
      :loading="saving"
      @submit="save"
      ><el-form ref="formRef" :model="form" :rules="rules" label-position="top"
        ><template v-if="drawerType === 'role'"
          ><el-form-item label="用户" prop="user_id"
            ><el-select
              v-model="form.user_id"
              filterable
              placeholder="选择有效用户"
              ><el-option
                v-for="user in users"
                :key="user.id"
                :label="`${user.displayName}（${user.username}）`"
                :value="user.id" /></el-select></el-form-item
          ><el-form-item label="测试角色" prop="role_codes"
            ><el-checkbox-group v-model="form.role_codes"
              ><el-checkbox label="TEST_MANAGER">测试经理</el-checkbox
              ><el-checkbox label="TESTER">测试人员</el-checkbox
              ><el-checkbox label="DEVELOPER"
                >开发人员</el-checkbox
              ></el-checkbox-group
            ></el-form-item
          ></template
        ><template v-else
          ><template v-if="drawerType === 'round' || drawerType === 'cycle'"
            ><el-form-item label="编号"
              ><el-input
                :model-value="editingId ? form.code : '保存后系统自动生成'"
                disabled /></el-form-item
            ><el-form-item
              :label="drawerType === 'round' ? '轮次名称' : '周期名称'"
              prop="name"
              ><el-input
                v-model="form.name"
                maxlength="50"
                show-word-limit
                :placeholder="
                  drawerType === 'round' ? '如：第一轮测试' : '如：主体测试'
                "
            /></el-form-item>
            <div class="test-configuration-date-grid">
              <el-form-item label="开始日期" prop="planned_start_date"
                ><el-date-picker
                  v-model="form.planned_start_date"
                  value-format="YYYY-MM-DD"
                  placeholder="选择开始日期" /></el-form-item
              ><el-form-item label="结束日期" prop="planned_end_date"
                ><el-date-picker
                  v-model="form.planned_end_date"
                  value-format="YYYY-MM-DD"
                  placeholder="选择结束日期"
              /></el-form-item>
            </div>
            <el-alert
              v-if="drawerType === 'cycle'"
              type="warning"
              :closable="false"
              title="周期日期允许超出轮次范围；保存时仅提示，不阻断。" /></template
          ><template v-else
            ><el-form-item label="编码" prop="code"
              ><el-input
                v-model="form.code"
                placeholder="如：DEFECT_CATEGORY" /></el-form-item
            ><el-form-item label="名称" prop="name"
              ><el-input
                v-model="form.name"
                maxlength="30"
                show-word-limit
                placeholder="请输入名称" /></el-form-item
            ><el-form-item
              v-if="drawerType === 'dictionary' || drawerType === 'option'"
              label="是否启用"
              ><el-switch v-model="form.enabled" /></el-form-item
            ><el-form-item label="排序"
              ><el-input-number v-model="form.sort_no" :min="0" /></el-form-item
            ><el-form-item label="备注"
              ><el-input
                v-model="form.remark"
                type="textarea"
                :rows="3"
                maxlength="500"
                show-word-limit
                placeholder="选填" /></el-form-item></template></template></el-form
    ></TestManagementFormDialog>
  </section>
</template>
