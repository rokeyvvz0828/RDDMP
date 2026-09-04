<!--
文件：web/src/modules/test-management/execution/TestExecutionPage.vue
说明：测试执行页面或交互组件。
用途：承载用户可见的加载、空、失败、提交和交互状态。
作者：hengguan
-->
<script setup lang="ts">
// 关键逻辑：页面只消费现有全局项目上下文；当前测试大类、项目和实体选择共同决定请求范围，前端显隐不替代服务端校验。
import {
  computed,
  nextTick,
  onBeforeUnmount,
  onMounted,
  ref,
  shallowRef,
  watch,
} from "vue";
import { useRoute } from "vue-router";
import {
  Delete,
  Download,
  Edit,
  Folder,
  FolderOpened,
  Plus,
  Refresh,
  Search,
  Upload,
} from "@element-plus/icons-vue";
import { ElMessage, ElMessageBox } from "element-plus";
import UiDataTable from "../../../components/ui/UiDataTable.vue";
import UiEmptyState from "../../../components/ui/UiEmptyState.vue";
import UiPageHeader from "../../../components/ui/UiPageHeader.vue";
import TestManagementFormDialog from "../components/TestManagementFormDialog.vue";
import { Editor as WangEditor, Toolbar } from "@wangeditor/editor-for-vue";
import type { IDomEditor } from "@wangeditor/editor";
import "@wangeditor/editor/dist/css/style.css";
import { useProjectContextStore } from "../../../stores/project-context";
import {
  getAttachmentDownload,
  getAttachmentPreview,
  uploadAttachment,
} from "../../../api/attachments";
import {
  associateTestExecutionDefects,
  batchTestExecutionStatus,
  deleteTestExecutionDirectory,
  detachTestExecutionDefect,
  downloadTestExecutionFile,
  getTestExecutionDetail,
  getTestExecutionTree,
  importTestExecutions,
  listTestCaseScopes,
  listTestCases,
  listTestDefects,
  listTestExecutions,
  listTestProjects,
  moveTestExecutions,
  previewTestExecutionImport,
  removeTestExecutions,
  saveTestDefect,
  saveTestExecutionDirectory,
  saveTestExecutionResult,
  type TestDomain,
  type TestExecution,
  type TestExecutionTree,
} from "../api";

type TreeNode = {
  key: string;
  type: "project" | "system" | "round" | "cycle" | "directory";
  label: string;
  systemId?: number;
  roundId?: number;
  cycleId?: number;
  directory?: TestExecutionTree["directories"][number];
  children?: TreeNode[];
};
const route = useRoute();
const context = useProjectContextStore();
const domain = computed(() => String(route.params.domain) as TestDomain);
const domainName = computed(
  () =>
    ({
      "application-assembly": "应用组装测试",
      "user-testing": "用户测试",
      "non-functional": "非功能测试",
      security: "安全测试",
    })[domain.value],
);
const projects = ref<
  Array<{ id: number; project_code: string; project_name: string }>
>([]);
const projectId = computed(
  () =>
    projects.value.find((item) => item.project_code === context.currentRef)?.id,
);
const projectName = computed(
  () =>
    projects.value.find((item) => item.id === projectId.value)?.project_name ||
    "当前项目",
);
const tree = ref<TestExecutionTree>();
const rows = ref<TestExecution[]>([]);
const total = ref(0);
const loading = ref(false);
const error = ref("");
const keyword = ref("");
const selectedDirectory = ref<number>();
const selectedSystem = ref<number>();
const selectedRound = ref<number>();
const selectedCycle = ref<number>();
const activeTreeKey = ref("project");
const executionTreeRef = ref<any>();
const status = ref<string[]>([]);
const executorId = ref<number>();
const defectLink = ref("");
const validity = ref("");
const page = ref(1);
const sort = ref({
  prop: "updated_at",
  order: "descending" as "ascending" | "descending",
});
const importVisible = ref(false);
const importMode = ref<"CODE" | "CASE" | "SCOPE">("CODE");
const pastedCodes = ref("");
const importCaseIds = ref<number[]>([]);
const importScopeIds = ref<number[]>([]);
const importCaseCandidates = ref<any[]>([]);
const importScopeCandidates = ref<any[]>([]);
const importKeyword = ref("");
const importPreview = ref<any>();
const importing = ref(false);
const directoryVisible = ref(false);
const directorySaving = ref(false);
const directoryDraft = ref<any>({ directory_name: "", sort_no: 0 });
const editingDirectory = ref<any>();
const selectedRows = ref<TestExecution[]>([]);
const detailVisible = ref(false);
const detail = ref<any>();
const resultSaving = ref(false);
const failureVisible = ref(false);
const failureDefects = ref<any[]>([]);
const failureIds = ref<number[]>([]);
const defectKeyword = ref("");
const moveVisible = ref(false);
const moveTargetId = ref<number>();
const proposingVisible = ref(false);
const proposal = ref<any>({});
const detailDefectCandidates = ref<any[]>([]);
const detailDefectIds = ref<number[]>([]);
const attachmentUploading = ref(false);
const actualEditor = shallowRef<IDomEditor>();
const remarkEditor = shallowRef<IDomEditor>();
const proposalEditor = shallowRef<IDomEditor>();
const editorConfig = {
  placeholder: "支持从 Word 粘贴，保留常用格式、颜色和表格。",
};
const toolbarConfig = {
  toolbarKeys: [
    "headerSelect",
    "bold",
    "underline",
    "italic",
    "color",
    "bgColor",
    "fontSize",
    "bulletedList",
    "numberedList",
    "justifyLeft",
    "justifyCenter",
    "justifyRight",
    "insertLink",
    "insertTable",
    "undo",
    "redo",
  ],
};
const statusName = (value: string) =>
  ({
    UNEXECUTED: "未执行",
    RUNNING: "执行中",
    SUCCESS: "成功",
    FAILED: "失败",
    INVALID: "无效",
    BLOCKED: "阻塞",
  })[value] || value;
const statusType = (value: string) =>
  (
    ({
      SUCCESS: "success",
      FAILED: "danger",
      RUNNING: "warning",
      INVALID: "info",
      BLOCKED: "danger",
      UNEXECUTED: "info",
    }) as Record<string, any>
  )[value] || "info";
const stamp = (value?: string) =>
  value ? value.slice(5, 16).replace("T", " ") : "—";
const selectedTreeKey = computed(() => activeTreeKey.value);
function directoryChildren(
  systemId: number,
  roundId: number,
  cycleId: number,
  parentId?: number,
): TreeNode[] {
  return (tree.value?.directories || [])
    .filter(
      (item) =>
        item.physical_subsystem_id === systemId &&
        item.round_id === roundId &&
        item.cycle_id === cycleId &&
        (item.parent_id || undefined) === parentId,
    )
    .map((item) => ({
      key: `directory:${item.id}`,
      type: "directory",
      label: item.directory_name,
      systemId,
      roundId,
      cycleId,
      directory: item,
      children: directoryChildren(systemId, roundId, cycleId, item.id),
    }));
}
const treeData = computed<TreeNode[]>(() => [
  {
    key: "project",
    type: "project",
    label: projectName.value,
    children: (tree.value?.systems || []).map((system) => {
      const systemId = system.physical_subsystem_id;
      return {
        key: `system:${systemId}`,
        type: "system",
        label: system.short_name || system.name,
        systemId,
        children: (tree.value?.rounds || []).map((round) => ({
          key: `round:${round.id}:${systemId}`,
          type: "round",
          label: round.round_name,
          systemId,
          roundId: round.id,
          children: (tree.value?.cycles || [])
            .filter((cycle) => cycle.round_id === round.id)
            .map((cycle) => ({
              key: `cycle:${cycle.id}:${systemId}`,
              type: "cycle",
              label: cycle.cycle_name,
              systemId,
              roundId: round.id,
              cycleId: cycle.id,
              children: directoryChildren(systemId, round.id, cycle.id),
            })),
        })),
      };
    }),
  },
]);
async function load() {
  if (!projectId.value) return;
  loading.value = true;
  error.value = "";
  try {
    const [treeResult, listResult] = await Promise.all([
      getTestExecutionTree(domain.value, projectId.value),
      listTestExecutions(domain.value, {
        projectId: projectId.value,
        page: page.value,
        size: 20,
        keyword: keyword.value || undefined,
        physicalSubsystemId: selectedSystem.value,
        roundId: selectedRound.value,
        cycleId: selectedCycle.value,
        directoryId: selectedDirectory.value,
        status: status.value,
        executorId: executorId.value,
        defectLink: defectLink.value || undefined,
        validity: validity.value || undefined,
        sortBy: sort.value.prop,
        sortOrder: sort.value.order,
      }),
    ]);
    tree.value = treeResult.data.data;
    rows.value = listResult.data.data.records || [];
    total.value = listResult.data.data.total || 0;
    await nextTick();
    executionTreeRef.value?.setCurrentKey(activeTreeKey.value);
  } catch (cause: any) {
    error.value =
      cause?.response?.data?.message || "测试执行加载失败，请稍后重试。";
  } finally {
    loading.value = false;
  }
}
async function initialize() {
  try {
    projects.value = (await listTestProjects(domain.value)).data.data || [];
    await load();
  } catch (cause: any) {
    error.value = cause?.response?.data?.message || "项目上下文加载失败。";
  }
}
function selectTree(node: TreeNode) {
  activeTreeKey.value = node.key;
  selectedSystem.value = node.systemId;
  selectedRound.value = node.roundId;
  selectedCycle.value = node.cycleId;
  selectedDirectory.value = node.directory?.id;
  executionTreeRef.value?.setCurrentKey(node.key);
  page.value = 1;
  void load();
}
function openDirectory(
  item?: TestExecutionTree["directories"][number],
  child = false,
  parent?: TreeNode,
) {
  const systemId =
    item?.physical_subsystem_id || parent?.systemId || selectedSystem.value;
  const roundId = item?.round_id || parent?.roundId || selectedRound.value;
  const cycleId = item?.cycle_id || parent?.cycleId || selectedCycle.value;
  if (!systemId || !roundId || !cycleId)
    return ElMessage.warning("请先在左侧选择系统下的轮次和周期");
  editingDirectory.value = item;
  directoryDraft.value = {
    directory_name: item?.directory_name || "",
    sort_no: item?.sort_no || 0,
    physical_subsystem_id: systemId,
    round_id: roundId,
    cycle_id: cycleId,
    parent_id: item ? (child ? item.id : item.parent_id) : undefined,
  };
  directoryVisible.value = true;
}
async function saveDirectory() {
  if (
    !projectId.value ||
    !directoryDraft.value.physical_subsystem_id ||
    !directoryDraft.value.round_id ||
    !directoryDraft.value.cycle_id
  )
    return;
  if (!directoryDraft.value.directory_name?.trim())
    return ElMessage.warning("请输入执行目录名称");
  directorySaving.value = true;
  try {
    await saveTestExecutionDirectory(
      domain.value,
      projectId.value,
      directoryDraft.value,
      editingDirectory.value?.id,
    );
    ElMessage.success(
      editingDirectory.value ? "执行目录已更新" : "执行目录已创建",
    );
    directoryVisible.value = false;
    await load();
  } catch (cause: any) {
    ElMessage.error(cause?.response?.data?.message || "保存目录失败");
  } finally {
    directorySaving.value = false;
  }
}
async function removeDirectory(item: TestExecutionTree["directories"][number]) {
  if (!projectId.value) return;
  try {
    await ElMessageBox.confirm(
      `确认删除执行目录“${item.directory_name}”？有执行记录或子目录时不可删除。`,
      "删除执行目录",
      { type: "warning" },
    );
    await deleteTestExecutionDirectory(domain.value, projectId.value, item.id);
    if (selectedDirectory.value === item.id)
      selectedDirectory.value = undefined;
    ElMessage.success("已删除");
    await load();
  } catch (cause: any) {
    if (cause !== "cancel" && cause !== "close")
      ElMessage.error(cause?.response?.data?.message || "删除目录失败");
  }
}
async function openDetail(row: TestExecution) {
  if (!projectId.value) return;
  try {
    detail.value = (
      await getTestExecutionDetail(domain.value, projectId.value, row.id)
    ).data.data;
    detail.value.attachment_ids = (detail.value.attachments || []).map(
      (item: any) => item.id,
    );
    detailDefectIds.value = [];
    detailDefectCandidates.value =
      (
        await listTestDefects(domain.value, {
          projectId: projectId.value,
          page: 1,
          size: 100,
          physicalSubsystemId: detail.value.physical_subsystem_id,
          status: [],
        })
      ).data.data.records || [];
    detailVisible.value = true;
  } catch (cause: any) {
    ElMessage.error(cause?.response?.data?.message || "加载执行详情失败");
  }
}
async function saveResult() {
  if (!projectId.value || !detail.value) return;
  const state = detail.value.execution_status;
  if (
    (state === "INVALID" || state === "BLOCKED") &&
    !String(detail.value.remark_html || "")
      .replace(/<[^>]+>/g, "")
      .trim()
  )
    return ElMessage.warning("无效或阻塞时备注必填");
  if (state === "FAILED" && !(detail.value.defects || []).length)
    return ElMessage.warning("失败必须关联缺陷，请先关联缺陷");
  resultSaving.value = true;
  try {
    detail.value = (
      await saveTestExecutionResult(
        domain.value,
        projectId.value,
        detail.value.id,
        {
          execution_status: state,
          actual_result_html: detail.value.actual_result_html,
          remark_html: detail.value.remark_html,
          attachment_ids: detail.value.attachment_ids || [],
        },
      )
    ).data.data;
    detail.value.attachment_ids = (detail.value.attachments || []).map(
      (item: any) => item.id,
    );
    ElMessage.success("执行结果已保存");
    await load();
  } catch (cause: any) {
    ElMessage.error(cause?.response?.data?.message || "保存执行结果失败");
  } finally {
    resultSaving.value = false;
  }
}
async function addDetailDefects() {
  if (!projectId.value || !detail.value || !detailDefectIds.value.length)
    return ElMessage.warning("请选择要关联的缺陷");
  try {
    await associateTestExecutionDefects(
      domain.value,
      projectId.value,
      detail.value.id,
      detailDefectIds.value,
    );
    detail.value = (
      await getTestExecutionDetail(
        domain.value,
        projectId.value,
        detail.value.id,
      )
    ).data.data;
    detail.value.attachment_ids = (detail.value.attachments || []).map(
      (item: any) => item.id,
    );
    detailDefectIds.value = [];
    ElMessage.success("缺陷已关联");
  } catch (cause: any) {
    ElMessage.error(cause?.response?.data?.message || "关联缺陷失败");
  }
}
async function detachDetailDefect(defectId: number) {
  if (!projectId.value || !detail.value) return;
  try {
    await detachTestExecutionDefect(
      domain.value,
      projectId.value,
      detail.value.id,
      defectId,
    );
    detail.value.defects = (detail.value.defects || []).filter(
      (item: any) => item.id !== defectId,
    );
    ElMessage.success("缺陷关联已解除");
  } catch (cause: any) {
    ElMessage.error(cause?.response?.data?.message || "解除关联失败");
  }
}
async function uploadExecutionAttachment(options: any) {
  if (!detail.value) return;
  attachmentUploading.value = true;
  try {
    const uploaded = (await uploadAttachment(options.file)).data.data;
    detail.value.attachments = [
      ...(detail.value.attachments || []),
      {
        id: uploaded.id,
        file_name: uploaded.fileName,
        file_size: uploaded.fileSize,
      },
    ];
    detail.value.attachment_ids = [
      ...(detail.value.attachment_ids || []),
      uploaded.id,
    ];
    ElMessage.success("附件已添加，保存结果后生效");
    options.onSuccess?.(uploaded);
  } catch (cause: any) {
    options.onError?.(cause);
    ElMessage.error(cause?.response?.data?.message || "附件上传失败");
  } finally {
    attachmentUploading.value = false;
  }
}
async function openAttachment(id: number, download = false) {
  try {
    if (download) {
      const response = await getAttachmentDownload(id);
      window.open(response.data.data.downloadUrl, "_blank", "noopener");
    } else {
      const response = await getAttachmentPreview(id);
      window.open(response.data.data.previewUrl, "_blank", "noopener");
    }
  } catch (cause: any) {
    ElMessage.error(
      cause?.response?.data?.message ||
        (download ? "下载附件失败" : "预览附件失败"),
    );
  }
}
async function batchStatus(value: "SUCCESS" | "RUNNING") {
  if (!projectId.value || !selectedRows.value.length)
    return ElMessage.warning("请先勾选执行记录");
  try {
    await batchTestExecutionStatus(domain.value, projectId.value, {
      ids: selectedRows.value.map((item) => item.id),
      execution_status: value,
    });
    ElMessage.success(`已批量标记为${statusName(value)}`);
    await load();
  } catch (cause: any) {
    ElMessage.error(cause?.response?.data?.message || "批量更新失败");
  }
}
async function openFailure() {
  if (!projectId.value || !selectedRows.value.length)
    return ElMessage.warning("请先勾选执行记录");
  failureIds.value = [];
  failureDefects.value =
    (
      await listTestDefects(domain.value, {
        projectId: projectId.value,
        page: 1,
        size: 100,
        keyword: defectKeyword.value || undefined,
        physicalSubsystemId: selectedSystem.value,
        status: [],
      })
    ).data.data.records || [];
  failureVisible.value = true;
}
async function confirmFailure() {
  if (!projectId.value || !failureIds.value.length)
    return ElMessage.warning("请至少关联一个缺陷");
  try {
    await batchTestExecutionStatus(domain.value, projectId.value, {
      ids: selectedRows.value.map((item) => item.id),
      execution_status: "FAILED",
      defect_ids: failureIds.value,
    });
    failureVisible.value = false;
    ElMessage.success("已批量标记失败并关联缺陷");
    await load();
  } catch (cause: any) {
    ElMessage.error(cause?.response?.data?.message || "批量失败处理失败");
  }
}
function openProposal() {
  if (!selectedRows.value.length || !selectedSystem.value)
    return ElMessage.warning("请先在同一系统下勾选执行记录");
  proposal.value = {
    physical_subsystem_id: selectedSystem.value,
    defect_category: "FUNCTION",
    severity: "GENERAL",
    priority: "MEDIUM",
    urgency: "MEDIUM",
    execution_ids: selectedRows.value.map((item) => item.id),
    summary: "",
    description_html: "",
  };
  proposingVisible.value = true;
}
async function saveProposal() {
  if (
    !projectId.value ||
    !proposal.value.summary?.trim() ||
    !String(proposal.value.description_html || "")
      .replace(/<[^>]+>/g, "")
      .trim()
  )
    return ElMessage.warning("请填写概述和缺陷描述");
  try {
    await saveTestDefect(domain.value, projectId.value, proposal.value);
    proposingVisible.value = false;
    ElMessage.success("缺陷已提出并关联所选执行记录");
    await load();
  } catch (cause: any) {
    ElMessage.error(cause?.response?.data?.message || "提出缺陷失败");
  }
}
async function commitMove() {
  if (!projectId.value || !moveTargetId.value || !selectedRows.value.length)
    return ElMessage.warning("请选择目标目录");
  try {
    await moveTestExecutions(domain.value, projectId.value, {
      ids: selectedRows.value.map((item) => item.id),
      target_directory_id: moveTargetId.value,
    });
    moveVisible.value = false;
    ElMessage.success("执行记录已移动");
    await load();
  } catch (cause: any) {
    ElMessage.error(cause?.response?.data?.message || "移动失败");
  }
}
async function removeSelected() {
  if (!projectId.value || !selectedRows.value.length)
    return ElMessage.warning("请先勾选执行记录");
  try {
    await ElMessageBox.confirm(
      `确认移除选中的 ${selectedRows.value.length} 条执行记录？关联未解决缺陷的记录将被保留。`,
      "移除执行记录",
      { type: "warning" },
    );
    const result: any = await removeTestExecutions(
      domain.value,
      projectId.value,
      { ids: selectedRows.value.map((item) => item.id) },
    );
    ElMessage.success(
      `已移除 ${result.data.data.removed || 0} 条${result.data.data.blocked_execution_ids?.length ? "，部分记录因未解决缺陷被保留" : ""}`,
    );
    await load();
  } catch (cause: any) {
    if (cause !== "cancel" && cause !== "close")
      ElMessage.error(cause?.response?.data?.message || "移除失败");
  }
}
async function previewImport() {
  if (!projectId.value || !selectedDirectory.value)
    return ElMessage.warning("请先在左侧选择执行目录");
  importing.value = true;
  try {
    importPreview.value = (
      await previewTestExecutionImport(
        domain.value,
        projectId.value,
        importPayload(),
      )
    ).data.data;
  } catch (cause: any) {
    ElMessage.error(cause?.response?.data?.message || "导入预览失败");
  } finally {
    importing.value = false;
  }
}
async function confirmImport() {
  if (!projectId.value || !selectedDirectory.value) return;
  importing.value = true;
  try {
    const result: any = await importTestExecutions(
      domain.value,
      projectId.value,
      importPayload(),
    );
    ElMessage.success(`已导入 ${result.data.data.created || 0} 条案例`);
    importVisible.value = false;
    await load();
  } catch (cause: any) {
    ElMessage.error(cause?.response?.data?.message || "导入失败");
  } finally {
    importing.value = false;
  }
}
function importPayload() {
  return {
    directory_id: selectedDirectory.value,
    case_codes: importMode.value === "CODE" ? pastedCodes.value : undefined,
    case_ids: importMode.value === "CASE" ? importCaseIds.value : undefined,
    scope_ids: importMode.value === "SCOPE" ? importScopeIds.value : undefined,
  };
}
function openImport() {
  if (!selectedDirectory.value)
    return ElMessage.warning("请先在左侧选择执行目录");
  importPreview.value = undefined;
  importVisible.value = true;
  void loadImportCandidates();
}
async function loadImportCandidates() {
  if (!projectId.value || !selectedSystem.value) return;
  try {
    const [cases, scopes] = await Promise.all([
      listTestCases(domain.value, {
        projectId: projectId.value,
        page: 1,
        size: 100,
        physicalSubsystemId: selectedSystem.value,
      }),
      listTestCaseScopes(domain.value, projectId.value, selectedSystem.value),
    ]);
    importCaseCandidates.value = cases.data.data.records || [];
    importScopeCandidates.value = scopes.data.data || [];
  } catch (cause: any) {
    ElMessage.error(cause?.response?.data?.message || "加载可导入案例失败");
  }
}
function sortChange(value: {
  prop: string;
  order: "ascending" | "descending" | null;
}) {
  sort.value = {
    prop: value.prop || "updated_at",
    order: value.order || "descending",
  };
  void load();
}
async function exportRows() {
  if (!projectId.value) return;
  try {
    await downloadTestExecutionFile(domain.value, {
      projectId: projectId.value,
      physicalSubsystemId: selectedSystem.value,
      roundId: selectedRound.value,
      cycleId: selectedCycle.value,
      directoryId: selectedDirectory.value,
      keyword: keyword.value || undefined,
      status: status.value,
    });
  } catch (cause: any) {
    ElMessage.error(cause?.response?.data?.message || "导出失败");
  }
}
watch([() => context.currentRef, domain], () => {
  page.value = 1;
  activeTreeKey.value = "project";
  selectedSystem.value = undefined;
  selectedRound.value = undefined;
  selectedCycle.value = undefined;
  selectedDirectory.value = undefined;
  void initialize();
});
onMounted(initialize);
onBeforeUnmount(() => {
  actualEditor.value?.destroy();
  remarkEditor.value?.destroy();
  proposalEditor.value?.destroy();
});
</script>

<template>
  <section class="execution-page">
    <UiPageHeader eyebrow="测试管理" :title="domainName + ' · 测试执行'"
      ><template #actions
        ><el-button
          type="primary"
          size="small"
          :icon="Plus"
          @click="openDirectory()"
          >新建执行目录</el-button
        ><el-tooltip content="刷新"
          ><el-button
            size="small"
            text
            circle
            :icon="Refresh"
            aria-label="刷新执行记录"
            @click="load" /></el-tooltip></template
    ></UiPageHeader>
    <UiEmptyState
      v-if="!projectId"
      title="请先选择项目"
      description="请使用顶部全局项目选择器后查看测试执行。"
    />
    <UiEmptyState
      v-else-if="error"
      title="测试执行加载失败"
      :description="error"
      ><template #action
        ><el-button type="primary" @click="load">重新加载</el-button></template
      ></UiEmptyState
    >
    <section v-else class="execution-workspace" v-loading="loading">
      <aside class="execution-tree">
        <header>
          <span>执行目录</span
          ><el-tooltip content="新建执行目录"
            ><el-button
              link
              :icon="Plus"
              aria-label="新建执行目录"
              @click="openDirectory()"
          /></el-tooltip>
        </header>
        <el-tree
          ref="executionTreeRef"
          :data="treeData"
          node-key="key"
          :current-node-key="selectedTreeKey"
          default-expand-all
          highlight-current
          :expand-on-click-node="false"
          @node-click="selectTree"
          ><template #default="{ data }"
            ><span class="execution-tree-node"
              ><el-icon
                ><FolderOpened v-if="data.type === 'project'" /><Folder
                  v-else /></el-icon
              ><span class="execution-tree-label">{{ data.label }}</span
              ><small v-if="data.type === 'directory'">{{
                data.directory.execution_count
              }}</small
              ><span
                v-if="data.type === 'cycle' || data.type === 'directory'"
                class="tree-actions"
                ><el-button
                  link
                  :icon="Plus"
                  aria-label="新增子目录"
                  @click.stop="
                    openDirectory(
                      data.type === 'directory' ? data.directory : undefined,
                      data.type === 'directory',
                      data,
                    )
                  " /><el-button
                  v-if="data.type === 'directory'"
                  link
                  :icon="Edit"
                  aria-label="编辑目录"
                  @click.stop="openDirectory(data.directory)" /><el-button
                  v-if="data.type === 'directory'"
                  link
                  type="danger"
                  :icon="Delete"
                  aria-label="删除目录"
                  @click.stop="
                    removeDirectory(data.directory)
                  " /></span></span></template
        ></el-tree>
      </aside>
      <main class="execution-content">
        <div class="execution-toolbar">
          <el-input
            v-model="keyword"
            clearable
            size="small"
            placeholder="案例编号、名称"
            @keyup.enter="
              page = 1;
              load();
            "
            ><template #prefix><Search /></template></el-input
          ><el-select
            v-model="status"
            multiple
            collapse-tags
            size="small"
            clearable
            placeholder="执行状态"
            ><el-option
              v-for="item in [
                'UNEXECUTED',
                'RUNNING',
                'SUCCESS',
                'FAILED',
                'INVALID',
                'BLOCKED',
              ]"
              :key="item"
              :label="statusName(item)"
              :value="item" /></el-select
          ><el-select
            v-model="defectLink"
            size="small"
            clearable
            placeholder="缺陷关联"
            ><el-option label="已关联缺陷" value="LINKED" /><el-option
              label="未关联缺陷"
              value="UNLINKED" /></el-select
          ><el-select
            v-model="validity"
            size="small"
            clearable
            placeholder="案例有效性"
            ><el-option label="正常" value="NORMAL" /><el-option
              label="无效"
              value="INVALID" /></el-select
          ><el-button
            size="small"
            :icon="Search"
            @click="
              page = 1;
              load();
            "
            >查询</el-button
          ><el-button
            size="small"
            text
            @click="
              keyword = '';
              status = [];
              defectLink = '';
              validity = '';
              page = 1;
              load();
            "
            >重置</el-button
          ><span class="toolbar-spacer" /><el-button
            size="small"
            :disabled="!selectedRows.length"
            @click="batchStatus('RUNNING')"
            >批量执行中</el-button
          ><el-button
            size="small"
            type="success"
            :disabled="!selectedRows.length"
            @click="batchStatus('SUCCESS')"
            >批量成功</el-button
          ><el-button
            size="small"
            type="danger"
            :disabled="!selectedRows.length"
            @click="openFailure"
            >批量失败</el-button
          ><el-button
            size="small"
            :disabled="!selectedRows.length"
            @click="openProposal"
            >提出缺陷</el-button
          ><el-button
            size="small"
            :disabled="!selectedRows.length"
            @click="moveVisible = true"
            >批量移动</el-button
          ><el-button
            size="small"
            type="danger"
            :disabled="!selectedRows.length"
            @click="removeSelected"
            >移除</el-button
          ><el-button
            size="small"
            type="primary"
            :icon="Upload"
            :disabled="!selectedDirectory"
            @click="openImport"
            >导入案例</el-button
          ><el-tooltip content="导出当前结果"
            ><el-button
              size="small"
              text
              circle
              :icon="Download"
              aria-label="导出执行记录"
              @click="exportRows"
          /></el-tooltip>
        </div>
        <div class="execution-result-bar">
          <span v-if="selectedDirectory"
            >当前目录已选择，可导入案例并登记执行结果。</span
          ><span v-else
            >请选择系统、轮次、周期或执行目录查看对应执行记录。</span
          >
        </div>
        <UiDataTable
          :data="rows"
          :loading="loading"
          row-key="id"
          border
          class="execution-table"
          @selection-change="selectedRows = $event"
          @sort-change="sortChange"
          ><el-table-column
            type="selection"
            width="42"
            :resizable="false" /><el-table-column
            prop="case_code"
            label="案例编号"
            min-width="160"
            resizable
            sortable="custom"
            show-overflow-tooltip /><el-table-column
            prop="case_name"
            label="案例名称"
            min-width="180"
            resizable
            sortable="custom"
            show-overflow-tooltip /><el-table-column
            prop="case_type"
            label="案例类型"
            min-width="94"
            resizable
            show-overflow-tooltip /><el-table-column
            label="所属范围"
            min-width="164"
            resizable
            show-overflow-tooltip
            ><template #default="scope"
              >{{ scope.row.scope_code }} · {{ scope.row.scope_name }}</template
            ></el-table-column
          ><el-table-column
            prop="execution_status"
            label="执行状态"
            width="92"
            resizable
            ><template #default="scope"
              ><el-tag
                :type="statusType(scope.row.execution_status)"
                size="small"
                >{{ statusName(scope.row.execution_status) }}</el-tag
              ></template
            ></el-table-column
          ><el-table-column
            prop="executor_name"
            label="执行人"
            min-width="92"
            resizable
            show-overflow-tooltip
            ><template #default="scope">{{
              scope.row.executor_name || "—"
            }}</template></el-table-column
          ><el-table-column
            prop="executed_at"
            label="执行时间"
            min-width="126"
            resizable
            sortable="custom"
            ><template #default="scope">{{
              stamp(scope.row.executed_at)
            }}</template></el-table-column
          ><el-table-column
            prop="defect_count"
            label="缺陷数"
            width="74"
            align="center"
            resizable /><el-table-column
            label="操作"
            width="58"
            fixed="right"
            align="center"
            header-align="center"
            :resizable="false"
            ><template #default="scope"
              ><el-tooltip content="执行详情"
                ><el-button
                  link
                  :icon="Edit"
                  aria-label="执行详情"
                  @click="
                    openDetail(scope.row)
                  " /></el-tooltip></template></el-table-column
          ><template #footer
            ><el-pagination
              v-model:current-page="page"
              :total="total"
              :page-size="20"
              layout="total, prev, pager, next"
              @current-change="load" /></template
        ></UiDataTable>
      </main>
    </section>
    <TestManagementFormDialog
      v-model="directoryVisible"
      :title="editingDirectory ? '编辑执行目录' : '新建执行目录'"
      width="min(480px, calc(100vw - 24px))"
      :loading="directorySaving"
      @submit="saveDirectory"
      ><el-form label-width="86px"
        ><el-form-item label="所属系统"
          ><el-input
            :model-value="
              tree?.systems?.find(
                (item) =>
                  item.physical_subsystem_id ===
                  directoryDraft.physical_subsystem_id,
              )?.name || '-'
            "
            disabled /></el-form-item
        ><el-form-item label="所属轮次"
          ><el-input
            :model-value="
              tree?.rounds?.find((item) => item.id === directoryDraft.round_id)
                ?.round_name || '-'
            "
            disabled /></el-form-item
        ><el-form-item label="所属周期"
          ><el-input
            :model-value="
              tree?.cycles?.find((item) => item.id === directoryDraft.cycle_id)
                ?.cycle_name || '-'
            "
            disabled /></el-form-item
        ><el-form-item label="目录名称" required
          ><el-input
            v-model="directoryDraft.directory_name"
            maxlength="100"
            show-word-limit /></el-form-item
        ><el-form-item label="排序号"
          ><el-input-number
            v-model="directoryDraft.sort_no"
            :min="0" /></el-form-item></el-form
    ></TestManagementFormDialog>
    <el-dialog
      v-model="importVisible"
      title="导入案例到执行目录"
      width="min(800px, 94vw)"
      align-center
      destroy-on-close
      ><el-alert
        title="导入前会统一预览，重复或无效案例不会写入。"
        type="info"
        :closable="false"
      /><el-tabs
        v-model="importMode"
        class="execution-import-tabs"
        @tab-change="loadImportCandidates"
        ><el-tab-pane label="粘贴案例编号" name="CODE"
          ><el-input
            v-model="pastedCodes"
            type="textarea"
            :rows="8"
            placeholder="粘贴案例编号，例如：W10011-0001-0001" /></el-tab-pane
        ><el-tab-pane label="按案例选择" name="CASE"
          ><div class="import-picker-toolbar">
            <el-input
              v-model="importKeyword"
              size="small"
              clearable
              placeholder="案例编号、名称"
              @keyup.enter="loadImportCandidates"
            /><el-button size="small" @click="loadImportCandidates"
              >查询</el-button
            >
          </div>
          <el-checkbox-group v-model="importCaseIds" class="import-checkboxes"
            ><el-checkbox
              v-for="item in importCaseCandidates.filter(
                (item) =>
                  !importKeyword ||
                  `${item.case_code}${item.case_name}`.includes(importKeyword),
              )"
              :key="item.id"
              :value="item.id"
              ><b>{{ item.case_code }}</b> · {{ item.case_name }}</el-checkbox
            ></el-checkbox-group
          ><el-empty
            v-if="!importCaseCandidates.length"
            description="未找到可导入案例"
            :image-size="50" /></el-tab-pane
        ><el-tab-pane label="按范围选择" name="SCOPE"
          ><el-checkbox-group v-model="importScopeIds" class="import-checkboxes"
            ><el-checkbox
              v-for="item in importScopeCandidates"
              :key="item.id"
              :value="item.id"
              ><b>{{ item.scope_code }}</b> · {{ item.scope_name }}</el-checkbox
            ></el-checkbox-group
          ><el-empty
            v-if="!importScopeCandidates.length"
            description="未找到可导入范围"
            :image-size="50" /></el-tab-pane
      ></el-tabs>
      <div class="execution-import-actions">
        <el-button :loading="importing" @click="previewImport">预览</el-button
        ><el-button
          type="primary"
          :disabled="!importPreview?.valid_count"
          :loading="importing"
          @click="confirmImport"
          >确认导入</el-button
        >
      </div>
      <el-descriptions v-if="importPreview" :column="3" border
        ><el-descriptions-item label="有效案例">{{
          importPreview.valid_count
        }}</el-descriptions-item
        ><el-descriptions-item label="重复">{{
          importPreview.duplicates?.length || 0
        }}</el-descriptions-item
        ><el-descriptions-item label="无效">{{
          importPreview.invalid?.length || 0
        }}</el-descriptions-item></el-descriptions
      ></el-dialog
    >
    <el-dialog
      v-model="failureVisible"
      title="批量失败并关联缺陷"
      width="min(760px, 94vw)"
      align-center
      destroy-on-close
      ><el-alert
        title="失败状态必须关联至少一个缺陷；所选缺陷会关联到每条已勾选执行记录。"
        type="warning"
        :closable="false"
      />
      <div class="dialog-filter">
        <el-input
          v-model="defectKeyword"
          size="small"
          clearable
          placeholder="缺陷编号、概述"
          @keyup.enter="openFailure"
        /><el-button size="small" @click="openFailure">查询</el-button>
      </div>
      <el-checkbox-group v-model="failureIds" class="defect-checkboxes"
        ><el-checkbox
          v-for="item in failureDefects"
          :key="item.id"
          :value="item.id"
          ><b>{{ item.defect_code }}</b> · {{ item.summary }}
          <small>({{ item.status }})</small></el-checkbox
        ></el-checkbox-group
      ><el-empty
        v-if="!failureDefects.length"
        description="未找到可关联的缺陷"
        :image-size="56"
      /><template #footer
        ><el-button @click="failureVisible = false">取消</el-button
        ><el-button type="danger" @click="confirmFailure"
          >确认失败</el-button
        ></template
      ></el-dialog
    >
    <TestManagementFormDialog
      v-model="moveVisible"
      title="批量移动执行记录"
      width="min(520px, calc(100vw - 24px))"
      @submit="commitMove"
      ><el-alert
        title="只能移动到同一系统、轮次和周期下的执行目录。"
        type="info"
        :closable="false" /><el-form label-width="88px" class="dialog-form"
        ><el-form-item label="目标目录" required
          ><el-select
            v-model="moveTargetId"
            filterable
            placeholder="选择目标执行目录"
            ><el-option
              v-for="item in (tree?.directories || []).filter(
                (item) =>
                  item.physical_subsystem_id === selectedSystem &&
                  item.round_id === selectedRound &&
                  item.cycle_id === selectedCycle,
              )"
              :key="item.id"
              :label="item.directory_name"
              :value="item.id" /></el-select></el-form-item></el-form
    ></TestManagementFormDialog>
    <TestManagementFormDialog
      v-model="proposingVisible"
      title="提出缺陷"
      width="min(860px, 94vw)"
      @submit="saveProposal"
      ><el-form label-width="86px"
        ><el-row :gutter="12"
          ><el-col :span="24"
            ><el-form-item label="缺陷概述" required
              ><el-input
                v-model="proposal.summary"
                maxlength="200"
                show-word-limit /></el-form-item></el-col
          ><el-col :span="8"
            ><el-form-item label="缺陷类别" required
              ><el-select v-model="proposal.defect_category"
                ><el-option label="功能缺陷" value="FUNCTION" /><el-option
                  label="性能缺陷"
                  value="PERFORMANCE" /><el-option
                  label="界面缺陷"
                  value="UI" /><el-option
                  label="数据缺陷"
                  value="DATA" /></el-select></el-form-item></el-col
          ><el-col :span="8"
            ><el-form-item label="严重程度" required
              ><el-select v-model="proposal.severity"
                ><el-option label="致命" value="FATAL" /><el-option
                  label="严重"
                  value="SERIOUS" /><el-option
                  label="一般"
                  value="GENERAL" /><el-option
                  label="提示"
                  value="SUGGESTION" /></el-select></el-form-item></el-col
          ><el-col :span="8"
            ><el-form-item label="优先级" required
              ><el-select v-model="proposal.priority"
                ><el-option label="高" value="HIGH" /><el-option
                  label="中"
                  value="MEDIUM" /><el-option
                  label="低"
                  value="LOW" /></el-select></el-form-item></el-col></el-row
        ><el-form-item label="缺陷描述" required
          ><div class="rich-editor">
            <Toolbar
              :editor="proposalEditor"
              :default-config="toolbarConfig"
              mode="default"
            /><WangEditor
              v-model="proposal.description_html"
              :default-config="editorConfig"
              mode="default"
              @on-created="(editor: IDomEditor) => (proposalEditor = editor)"
            /></div></el-form-item></el-form
    ></TestManagementFormDialog>
    <el-dialog
      v-model="detailVisible"
      title="执行详情"
      width="min(960px, 94vw)"
      align-center
      destroy-on-close
      ><template v-if="detail"
        ><el-alert
          v-if="detail.invalidated"
          title="案例已无效，执行记录保留可查。"
          type="warning"
          :closable="false" /><el-descriptions :column="3" border
          ><el-descriptions-item label="案例编号">{{
            detail.case_code
          }}</el-descriptions-item
          ><el-descriptions-item label="案例名称">{{
            detail.case_name
          }}</el-descriptions-item
          ><el-descriptions-item label="案例更新时间">{{
            detail.case_updated_at
          }}</el-descriptions-item
          ><el-descriptions-item label="所属范围" :span="3"
            >{{ detail.scope_code }} ·
            {{ detail.scope_name }}</el-descriptions-item
          ></el-descriptions
        ><el-divider>执行结果</el-divider
        ><el-form label-width="88px"
          ><el-form-item label="执行状态"
            ><el-select v-model="detail.execution_status"
              ><el-option
                v-for="item in [
                  'UNEXECUTED',
                  'RUNNING',
                  'SUCCESS',
                  'FAILED',
                  'INVALID',
                  'BLOCKED',
                ]"
                :key="item"
                :label="statusName(item)"
                :value="item" /></el-select></el-form-item
          ><el-form-item label="实际结果"
            ><div class="rich-editor">
              <Toolbar
                :editor="actualEditor"
                :default-config="toolbarConfig"
                mode="default"
              /><WangEditor
                v-model="detail.actual_result_html"
                :default-config="editorConfig"
                mode="default"
                @on-created="(editor: IDomEditor) => (actualEditor = editor)"
              /></div></el-form-item
          ><el-form-item label="备注"
            ><div class="rich-editor">
              <Toolbar
                :editor="remarkEditor"
                :default-config="toolbarConfig"
                mode="default"
              /><WangEditor
                v-model="detail.remark_html"
                :default-config="editorConfig"
                mode="default"
                @on-created="(editor: IDomEditor) => (remarkEditor = editor)"
              /></div></el-form-item
          ><el-form-item label="执行附件"
            ><el-upload
              :show-file-list="false"
              :http-request="uploadExecutionAttachment"
              :disabled="attachmentUploading"
              ><el-button
                size="small"
                :loading="attachmentUploading"
                :icon="Upload"
                >添加附件</el-button
              ></el-upload
            >
            <div class="attachment-list">
              <span v-for="item in detail.attachments || []" :key="item.id"
                ><el-button
                  link
                  size="small"
                  @click="openAttachment(item.id)"
                  >{{ item.file_name || item.fileName }}</el-button
                ><el-button
                  link
                  size="small"
                  :icon="Download"
                  aria-label="下载附件"
                  @click="openAttachment(item.id, true)" /><el-button
                  link
                  size="small"
                  type="danger"
                  :icon="Delete"
                  aria-label="移除附件"
                  @click="
                    detail.attachments = detail.attachments.filter(
                      (attachment: any) => attachment.id !== item.id,
                    );
                    detail.attachment_ids = detail.attachment_ids.filter(
                      (id: number) => id !== item.id,
                    );
                  "
              /></span></div></el-form-item></el-form
        ><el-divider>关联缺陷</el-divider>
        <div class="defect-linker">
          <el-select
            v-model="detailDefectIds"
            multiple
            filterable
            collapse-tags
            placeholder="选择已有缺陷"
            ><el-option
              v-for="item in detailDefectCandidates.filter(
                (item) =>
                  !(detail.defects || []).some(
                    (linked: any) => linked.id === item.id,
                  ),
              )"
              :key="item.id"
              :label="`${item.defect_code} · ${item.summary}`"
              :value="item.id" /></el-select
          ><el-button size="small" @click="addDetailDefects">关联</el-button>
        </div>
        <el-empty
          v-if="!detail.defects?.length"
          description="暂无关联缺陷；失败状态必须先关联缺陷。"
          :image-size="60" /><el-table
          v-else
          :data="detail.defects"
          size="small"
          ><el-table-column
            prop="defect_code"
            label="缺陷编号"
            width="150" /><el-table-column
            prop="summary"
            label="概述" /><el-table-column
            prop="status"
            label="状态"
            width="105" /><el-table-column
            label="操作"
            width="60"
            align="center"
            ><template #default="scope"
              ><el-tooltip content="解除关联"
                ><el-button
                  link
                  type="danger"
                  :icon="Delete"
                  aria-label="解除关联"
                  @click="
                    detachDetailDefect(scope.row.id)
                  " /></el-tooltip></template></el-table-column></el-table
        ><el-divider>执行轨迹</el-divider
        ><el-table :data="detail.traces || []" size="small" max-height="180"
          ><el-table-column
            prop="created_at"
            label="时间"
            width="170" /><el-table-column
            prop="operator_name"
            label="操作人"
            width="120" /><el-table-column
            prop="action_code"
            label="动作" /></el-table></template
      ><template #footer
        ><el-button @click="detailVisible = false">关闭</el-button
        ><el-button type="primary" :loading="resultSaving" @click="saveResult"
          >保存结果</el-button
        ></template
      ></el-dialog
    >
  </section>
</template>

<style scoped>
.execution-page {
  min-width: 0;
  max-width: 1440px;
  margin: 0 auto;
}
.execution-workspace {
  display: grid;
  grid-template-columns: 248px minmax(0, 1fr);
  min-height: 590px;
  overflow: hidden;
  border: 1px solid var(--line);
  border-radius: 6px;
  background: var(--panel-bg);
}
.execution-tree {
  min-width: 0;
  padding: 9px;
  overflow: auto;
  border-right: 1px solid var(--line);
  background: color-mix(in srgb, var(--panel-muted) 58%, var(--panel-bg));
}
.execution-tree > header {
  display: flex;
  min-height: 26px;
  align-items: center;
  justify-content: space-between;
  padding: 0 5px 7px;
  color: var(--text);
  font-size: 13px;
  font-weight: 650;
}
.execution-tree :deep(.el-tree) {
  --el-tree-node-hover-bg-color: var(--panel-bg);
  background: transparent;
  color: var(--text);
  font-size: 12px;
}
.execution-tree :deep(.el-tree-node__content) {
  height: 29px;
  border-radius: 4px;
}
.execution-tree :deep(.el-tree-node.is-current > .el-tree-node__content) {
  color: var(--brand-strong);
  background: color-mix(in srgb, var(--brand) 14%, var(--panel-bg));
  font-weight: 650;
}
.execution-tree-node {
  display: flex;
  min-width: 0;
  width: 100%;
  gap: 5px;
  align-items: center;
}
.execution-tree-label {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.execution-tree-node small {
  margin-left: auto;
  color: var(--text-muted);
  font-size: 11px;
}
.tree-actions {
  display: none;
  margin-left: auto;
  white-space: nowrap;
}
.execution-tree-node:hover .tree-actions {
  display: inline-flex;
}
.execution-tree-node:hover small {
  display: none;
}
.tree-actions .el-button {
  min-width: 18px;
  height: 22px;
  margin: 0;
  padding: 0 2px;
}
.execution-content {
  min-width: 0;
  padding: 10px;
  background: var(--panel-bg);
}
.execution-toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: center;
  margin-bottom: 7px;
}
.execution-toolbar .el-input {
  width: 188px;
}
.execution-toolbar .el-select {
  width: 128px;
}
.toolbar-spacer {
  flex: 1;
}
.execution-result-bar {
  display: flex;
  align-items: center;
  min-height: 25px;
  margin-bottom: 7px;
  color: var(--text-muted);
  font-size: 12px;
}
.execution-table :deep(.el-table__header th .cell) {
  white-space: nowrap;
  font-size: 12px;
}
.execution-table
  :deep(.el-table__header th:not(.ascending):not(.descending) .caret-wrapper) {
  display: none;
}
.execution-table :deep(.el-table__cell) {
  font-size: 12px;
}
.execution-table :deep(.el-button) {
  min-width: 22px;
  height: 23px;
  margin: 0;
  padding: 0 3px;
  font-size: 12px;
}
.rich-editor {
  width: 100%;
  overflow: hidden;
  border: 1px solid var(--line);
  border-radius: 4px;
}
.rich-editor :deep(.w-e-toolbar) {
  border-bottom: 1px solid var(--line);
}
.rich-editor :deep(.w-e-text-container) {
  min-height: 160px;
}
.execution-import-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin: 12px 0;
}
@media (max-width: 760px) {
  .execution-workspace {
    display: block;
    overflow: visible;
    border: 0;
    background: transparent;
  }
  .execution-tree {
    max-height: 250px;
    margin-bottom: 10px;
    border: 1px solid var(--line);
    border-radius: 6px;
  }
  .execution-content {
    padding: 9px;
    border: 1px solid var(--line);
    border-radius: 6px;
  }
  .execution-toolbar .el-input,
  .execution-toolbar .el-select {
    flex: 1 1 calc(50% - 4px);
    width: auto;
  }
  .toolbar-spacer {
    display: none;
  }
}
.dialog-filter,
.defect-linker {
  display: flex;
  gap: 8px;
  align-items: center;
  margin: 12px 0;
}
.dialog-filter .el-input,
.defect-linker .el-select {
  flex: 1;
}
.defect-checkboxes {
  display: grid;
  gap: 8px;
  max-height: 270px;
  overflow: auto;
}
.defect-checkboxes small {
  color: var(--text-muted);
}
.dialog-form {
  margin-top: 14px;
}
.attachment-list {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 10px;
  margin-top: 6px;
}
.attachment-list > span {
  display: inline-flex;
  align-items: center;
  max-width: 100%;
}
.attachment-list .el-button:first-child {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
