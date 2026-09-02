<!--
文件：web/src/modules/test-management/plan/TestPlanPage.vue
说明：测试方案页面或交互组件。
用途：承载用户可见的加载、空、失败、提交和交互状态。
作者：hengguan
-->
<script setup lang="ts">
// 关键逻辑：页面只消费现有全局项目上下文；当前测试大类、项目和实体选择共同决定请求范围，前端显隐不替代服务端校验。
import { computed, onMounted, reactive, ref, watch } from "vue";
import { useRoute } from "vue-router";
import {
  Clock,
  Delete,
  Download,
  Edit,
  Folder,
  FolderOpened,
  Plus,
  Upload,
  View,
} from "@element-plus/icons-vue";
import { ElMessage, ElMessageBox } from "element-plus";
import * as XLSX from "xlsx";
import { useAuthStore } from "../../../stores/auth";
import { useProjectContextStore } from "../../../stores/project-context";
import {
  getAttachmentDownload,
  getAttachmentPreview,
  uploadAttachment,
} from "../../../api/attachments";
import UiDataTable from "../../../components/ui/UiDataTable.vue";
import UiEmptyState from "../../../components/ui/UiEmptyState.vue";
import UiFilePreview from "../../../components/ui/UiFilePreview.vue";
import UiPageHeader from "../../../components/ui/UiPageHeader.vue";
import {
  createTestPlanSpecial,
  deleteTestPlan,
  deleteTestPlanSpecial,
  getCurrentTestPlan,
  getTestPlanTree,
  listTestPlans,
  listTestPlanVersions,
  listTestProjects,
  updateTestPlanSpecial,
  uploadTestPlan,
  uploadTestPlanVersion,
  type TestDomain,
  type TestPlan,
  type TestPlanNode,
  type TestPlanSpecialNode,
  type TestPlanTree,
  type TestPlanVersion,
} from "../api";
import "./test-plan.css";

type UploadForm = {
  planId?: number;
  planName: string;
  versionNote: string;
  file?: File;
};
type SpreadsheetPreview = {
  name: string;
  rows: unknown[][];
  sheetCount: number;
};

const route = useRoute();
const auth = useAuthStore();
const context = useProjectContextStore();
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
const projects = ref<
  Array<{ id: number; project_code: string; project_name: string }>
>([]);
const projectId = computed(
  () =>
    projects.value.find((item) => item.project_code === context.currentRef)?.id,
);
const hasProject = computed(() => Boolean(projectId.value));
const tree = ref<TestPlanTree>();
const selectedNode = ref<TestPlanNode>();
const tab = ref<"current" | "manage">("manage");
const currentPlan = ref<TestPlan>();
const plans = ref<TestPlan[]>([]);
const total = ref(0);
const page = ref(1);
const loading = ref(false);
const error = ref("");
const systemsExpanded = ref(false);
const previewVisible = ref(false);
const previewUrl = ref<string | null>(null);
const previewName = ref("方案预览");
const sheetVisible = ref(false);
const sheetPreview = ref<SpreadsheetPreview>();
const currentDocxUrl = ref<string>();
const currentSheet = ref<SpreadsheetPreview>();
const uploadVisible = ref(false);
const uploadSaving = ref(false);
const uploadForm = reactive<UploadForm>({ planName: "", versionNote: "" });
const versionVisible = ref(false);
const versions = ref<TestPlanVersion[]>([]);
const versionTitle = ref("历史版本");
const specialDialogVisible = ref(false);
const specialSaving = ref(false);
const editingSpecial = ref<TestPlanSpecialNode>();
const specialName = ref("");

const permission = (action?: "create" | "update" | "delete") =>
  `test-management:${domain.value}:plans${action ? `:${action}` : ""}`;
const canCreate = computed(() => auth.hasPermission(permission("create")));
const canUpdate = computed(() => auth.hasPermission(permission("update")));
const canDelete = computed(() => auth.hasPermission(permission("delete")));
const selectedKey = computed(() => nodeKey(selectedNode.value));
const selectedTitle = computed(() => {
  if (!selectedNode.value || !tree.value) return "";
  if (selectedNode.value.node_type === "PROJECT")
    return tree.value.project.project_name;
  if (selectedNode.value.node_type === "SYSTEM") {
    const system = tree.value.systems.find(
      (item) => item.id === selectedNode.value?.physical_subsystem_id,
    );
    return system
      ? `${system.short_name || system.name}（${system.code}）`
      : "";
  }
  return (
    tree.value.specials.find(
      (item) => item.id === selectedNode.value?.special_node_id,
    )?.node_name || ""
  );
});
const nodeLevel = computed(
  () =>
    ({ PROJECT: "项目级方案", SYSTEM: "系统级方案", SPECIAL: "专项方案" })[
      selectedNode.value?.node_type || "PROJECT"
    ],
);
const hasCurrent = computed(() => Boolean(currentPlan.value?.id));

function requestError(cause: any, fallback = "请求失败，请稍后重试") {
  const message = cause?.response?.data?.message || fallback;
  ElMessage.error(message);
}
function nodeKey(node?: TestPlanNode) {
  if (!node) return "";
  if (node.node_type === "SYSTEM")
    return `system:${node.physical_subsystem_id}`;
  if (node.node_type === "SPECIAL") return `special:${node.special_node_id}`;
  return "project";
}
function persistNode() {
  if (selectedKey.value)
    localStorage.setItem(`tm-plan-node:${domain.value}`, selectedKey.value);
}
function restoreNode() {
  if (!tree.value) return;
  const saved = localStorage.getItem(`tm-plan-node:${domain.value}`);
  if (saved?.startsWith("system:")) {
    const id = Number(saved.slice(7));
    if (tree.value.systems.some((item) => item.id === id)) {
      selectedNode.value = { node_type: "SYSTEM", physical_subsystem_id: id };
      return;
    }
  }
  if (saved?.startsWith("special:")) {
    const id = Number(saved.slice(8));
    if (tree.value.specials.some((item) => item.id === id)) {
      selectedNode.value = { node_type: "SPECIAL", special_node_id: id };
      return;
    }
  }
  selectedNode.value = { node_type: "PROJECT" };
}
function selectNode(node: TestPlanNode) {
  selectedNode.value = node;
  persistNode();
  page.value = 1;
  void loadNode();
}
function formatTime(value?: string) {
  if (!value) return "—";
  const date = new Date(value.replace(" ", "T"));
  if (Number.isNaN(date.getTime())) return value;
  const pad = (number: number) => String(number).padStart(2, "0");
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`;
}
function formatFileSize(size?: number) {
  if (!size) return "—";
  return size < 1024 * 1024
    ? `${Math.max(1, Math.round(size / 1024))} KB`
    : `${(size / 1024 / 1024).toFixed(1)} MB`;
}
function extensionType(extension?: string) {
  return extension === "xlsx" ? "success" : "primary";
}
function basename(file: File) {
  return file.name.replace(/\.[^.]+$/, "");
}

async function loadTree() {
  if (!projectId.value) return;
  loading.value = true;
  error.value = "";
  try {
    tree.value = (
      await getTestPlanTree(domain.value, projectId.value)
    ).data.data;
    restoreNode();
    await loadNode();
  } catch (cause: any) {
    error.value =
      cause?.response?.data?.message || "测试方案加载失败，请稍后重试。";
  } finally {
    loading.value = false;
  }
}
async function loadNode() {
  if (!projectId.value || !selectedNode.value) return;
  loading.value = true;
  error.value = "";
  currentDocxUrl.value = undefined;
  currentSheet.value = undefined;
  try {
    const [current, list] = await Promise.all([
      getCurrentTestPlan(domain.value, projectId.value, selectedNode.value),
      listTestPlans(
        domain.value,
        projectId.value,
        selectedNode.value,
        page.value,
        20,
      ),
    ]);
    currentPlan.value = current.data.data?.id ? current.data.data : undefined;
    plans.value = list.data.data.records || [];
    total.value = list.data.data.total || 0;
    if (!currentPlan.value) tab.value = "manage";
    else if (tab.value === "current")
      await prepareCurrentPreview(currentPlan.value);
  } catch (cause: any) {
    error.value =
      cause?.response?.data?.message || "方案列表加载失败，请稍后重试。";
  } finally {
    loading.value = false;
  }
}
async function prepareCurrentPreview(plan: TestPlan) {
  if (plan.file_extension === "docx") {
    try {
      currentDocxUrl.value = (
        await getAttachmentPreview(plan.attachment_id)
      ).data.data.previewUrl;
    } catch (cause) {
      requestError(cause, "在线预览服务暂不可用，请下载原始文件查看");
    }
    return;
  }
  currentSheet.value = await readSheet(plan).catch((cause) => {
    requestError(cause, "Excel 预览失败，请下载原始文件查看");
    return undefined;
  });
}
async function readSheet(
  item: Pick<TestPlanVersion, "attachment_id" | "file_name">,
): Promise<SpreadsheetPreview> {
  const link = (await getAttachmentDownload(item.attachment_id)).data.data
    .downloadUrl;
  const response = await fetch(link);
  if (!response.ok) throw new Error(`文件读取失败（${response.status}）`);
  const workbook = XLSX.read(await response.arrayBuffer(), { type: "array" });
  const first = workbook.SheetNames[0];
  if (!first) throw new Error("Excel 文件不包含工作表");
  return {
    name: item.file_name,
    sheetCount: workbook.SheetNames.length,
    rows: XLSX.utils
      .sheet_to_json(workbook.Sheets[first], { header: 1, defval: "" })
      .slice(0, 500) as unknown[][],
  };
}
async function download(
  item: Pick<TestPlanVersion, "attachment_id" | "file_name">,
) {
  try {
    const url = (await getAttachmentDownload(item.attachment_id)).data.data
      .downloadUrl;
    const link = document.createElement("a");
    link.href = url;
    link.download = item.file_name;
    link.target = "_blank";
    link.rel = "noopener";
    document.body.appendChild(link);
    link.click();
    link.remove();
  } catch (cause) {
    requestError(cause, "下载地址获取失败");
  }
}
async function preview(item: TestPlanVersion | TestPlan) {
  if (item.file_extension === "xlsx") {
    try {
      sheetPreview.value = await readSheet(item);
      sheetVisible.value = true;
    } catch (cause) {
      requestError(cause, "Excel 预览失败，请下载原始文件查看");
    }
    return;
  }
  try {
    previewName.value = item.file_name;
    previewUrl.value = (
      await getAttachmentPreview(item.attachment_id)
    ).data.data.previewUrl;
    previewVisible.value = true;
  } catch (cause) {
    requestError(cause, "在线预览服务暂不可用");
  }
}
function openUpload(plan?: TestPlan) {
  if (!selectedNode.value) return;
  Object.assign(uploadForm, {
    planId: plan?.id,
    planName: plan?.plan_name || "",
    versionNote: "",
    file: undefined,
  });
  uploadVisible.value = true;
}
function chooseFile(upload: any) {
  const file = upload.raw as File;
  const suffix = file.name.split(".").pop()?.toLowerCase();
  if (!["docx", "xlsx"].includes(String(suffix))) {
    ElMessage.warning("仅支持 .docx 或 .xlsx 格式，请另存为新格式后上传");
    return;
  }
  if (file.size > 50 * 1024 * 1024) {
    ElMessage.warning("单个方案文件不能超过 50 MB");
    return;
  }
  uploadForm.file = file;
  if (!uploadForm.planId) uploadForm.planName = basename(file);
}
async function submitUpload(confirmVersion = false) {
  if (!projectId.value || !selectedNode.value || !uploadForm.file) {
    ElMessage.warning("请选择方案文件");
    return;
  }
  if (!uploadForm.planId && !uploadForm.planName.trim()) {
    ElMessage.warning("请填写方案名称");
    return;
  }
  if (!uploadForm.versionNote.trim()) {
    ElMessage.warning("请填写版本说明");
    return;
  }
  uploadSaving.value = true;
  try {
    const attachment = (await uploadAttachment(uploadForm.file)).data.data;
    const base = {
      attachment_id: attachment.id,
      version_note: uploadForm.versionNote.trim(),
    };
    const result = uploadForm.planId
      ? (
          await uploadTestPlanVersion(
            domain.value,
            projectId.value,
            uploadForm.planId,
            base,
          )
        ).data.data
      : (
          await uploadTestPlan(domain.value, projectId.value, {
            ...selectedNode.value,
            ...base,
            plan_name: uploadForm.planName.trim(),
            confirm_version: confirmVersion,
          })
        ).data.data;
    if (result.version_confirmation_required) {
      uploadSaving.value = false;
      await ElMessageBox.confirm(
        `已存在同名方案，将作为新版本 V${result.next_version} 上传。是否继续？`,
        "确认新增版本",
        {
          type: "warning",
          confirmButtonText: "确认上传",
          cancelButtonText: "取消",
        },
      );
      await submitUpload(true);
      return;
    }
    uploadVisible.value = false;
    ElMessage.success(uploadForm.planId ? "新版本已上传" : "方案已上传");
    await loadNode();
  } catch (cause: any) {
    if (cause !== "cancel" && cause !== "close")
      requestError(cause, "方案上传失败");
  } finally {
    uploadSaving.value = false;
  }
}
async function showVersions(plan: TestPlan) {
  if (!projectId.value) return;
  try {
    versions.value =
      (await listTestPlanVersions(domain.value, projectId.value, plan.id)).data
        .data || [];
    versionTitle.value = `${plan.plan_name} · 历史版本`;
    versionVisible.value = true;
  } catch (cause) {
    requestError(cause, "历史版本加载失败");
  }
}
async function removePlan(plan: TestPlan) {
  if (!projectId.value) return;
  try {
    await ElMessageBox.confirm(
      `确认删除方案“${plan.plan_name}”？整个方案（含全部版本）将进入回收站保留 30 天。`,
      "删除方案",
      {
        type: "warning",
        confirmButtonText: "确认删除",
        confirmButtonClass: "el-button--danger",
      },
    );
    await deleteTestPlan(domain.value, projectId.value, plan.id);
    ElMessage.success("方案已删除");
    await loadNode();
  } catch (cause: any) {
    if (cause !== "cancel" && cause !== "close")
      requestError(cause, "删除方案失败");
  }
}
function openSpecial(special?: TestPlanSpecialNode) {
  editingSpecial.value = special;
  specialName.value = special?.node_name || "";
  specialDialogVisible.value = true;
}
async function saveSpecial() {
  if (!projectId.value || !specialName.value.trim()) {
    ElMessage.warning("请填写专项名称");
    return;
  }
  specialSaving.value = true;
  try {
    if (editingSpecial.value)
      await updateTestPlanSpecial(
        domain.value,
        projectId.value,
        editingSpecial.value.id,
        specialName.value.trim(),
      );
    else
      await createTestPlanSpecial(
        domain.value,
        projectId.value,
        specialName.value.trim(),
      );
    specialDialogVisible.value = false;
    ElMessage.success(editingSpecial.value ? "专项已重命名" : "专项节点已新增");
    await loadTree();
  } catch (cause) {
    requestError(cause, "专项节点保存失败");
  } finally {
    specialSaving.value = false;
  }
}
async function removeSpecial(special: TestPlanSpecialNode) {
  if (!projectId.value) return;
  try {
    await ElMessageBox.confirm(
      `确认删除专项节点“${special.node_name}”？存在方案时服务端会拒绝删除。`,
      "删除专项节点",
      { type: "warning" },
    );
    await deleteTestPlanSpecial(domain.value, projectId.value, special.id);
    ElMessage.success("专项节点已删除");
    await loadTree();
  } catch (cause: any) {
    if (cause !== "cancel" && cause !== "close")
      requestError(cause, "删除专项节点失败");
  }
}

watch(tab, (value) => {
  if (value === "current" && currentPlan.value)
    void prepareCurrentPreview(currentPlan.value);
});
watch([() => context.currentRef, domain], () => {
  page.value = 1;
  void loadTree();
});
onMounted(async () => {
  await context.initialize();
  try {
    projects.value = (await listTestProjects(domain.value)).data.data || [];
    await loadTree();
  } catch (cause: any) {
    error.value =
      cause?.response?.data?.message || "项目上下文加载失败，请稍后重试。";
  }
});
</script>

<template>
  <section class="test-plan-page">
    <UiPageHeader eyebrow="测试管理" :title="`${domainName} · 测试方案`" />
    <el-alert
      v-if="error"
      type="error"
      :closable="false"
      show-icon
      :title="error"
      ><template #default
        ><el-button size="small" @click="loadTree">重试</el-button></template
      ></el-alert
    >
    <UiEmptyState
      v-if="!loading && !hasProject"
      title="请先选择项目"
      description="请使用顶部全局项目选择器后查看测试方案。"
    />
    <section
      v-else-if="hasProject"
      class="test-plan-workspace"
      v-loading="loading"
    >
      <aside class="test-plan-tree" aria-label="测试方案目录">
        <header><span>方案目录</span></header>
        <section class="plan-tree-group">
          <h3>项目级方案</h3>
          <button
            type="button"
            class="plan-tree-node"
            :class="{ active: selectedKey === 'project' }"
            @click="selectNode({ node_type: 'PROJECT' })"
          >
            <el-icon><FolderOpened /></el-icon
            >{{ tree?.project?.project_name || "当前项目" }}
          </button>
        </section>
        <section class="plan-tree-group">
          <button
            type="button"
            class="plan-tree-group-toggle"
            @click="systemsExpanded = !systemsExpanded"
          >
            <h3>系统级方案</h3>
            <span>{{ systemsExpanded ? "收起" : "展开" }}</span>
          </button>
          <div v-show="systemsExpanded" class="plan-tree-children">
            <button
              v-for="system in tree?.systems || []"
              :key="system.id"
              type="button"
              class="plan-tree-node"
              :class="{ active: selectedKey === `system:${system.id}` }"
              @click="
                selectNode({
                  node_type: 'SYSTEM',
                  physical_subsystem_id: system.id,
                })
              "
            >
              <el-icon><Folder /></el-icon
              ><span>{{ system.short_name || system.name }}</span
              ><small>{{ system.code }}</small>
            </button>
            <p v-if="!(tree?.systems || []).length" class="plan-tree-empty">
              暂无已启用参测系统
            </p>
          </div>
        </section>
        <section class="plan-tree-group plan-tree-special">
          <div class="plan-tree-group-toggle">
            <h3>专项方案</h3>
            <el-button
              v-if="canCreate"
              link
              type="primary"
              size="small"
              @click="openSpecial()"
              ><el-icon><Plus /></el-icon>新增专项</el-button
            >
          </div>
          <div class="plan-tree-children">
            <div
              v-for="special in tree?.specials || []"
              :key="special.id"
              class="plan-tree-special-row"
            >
              <button
                type="button"
                class="plan-tree-node"
                :class="{ active: selectedKey === `special:${special.id}` }"
                @click="
                  selectNode({
                    node_type: 'SPECIAL',
                    special_node_id: special.id,
                  })
                "
              >
                <el-icon><Folder /></el-icon>{{ special.node_name }}</button
              ><span
                v-if="canUpdate || canDelete"
                class="plan-tree-special-actions"
                ><el-button
                  v-if="canUpdate"
                  link
                  aria-label="重命名专项"
                  @click="openSpecial(special)"
                  ><el-icon><Edit /></el-icon></el-button
                ><el-tooltip
                  v-if="canDelete && special.plan_count"
                  content="该专项下已有方案"
                  placement="top"
                  ><el-button link type="danger" aria-label="删除专项" disabled
                    ><el-icon><Delete /></el-icon></el-button></el-tooltip
                ><el-button
                  v-else-if="canDelete"
                  link
                  type="danger"
                  aria-label="删除专项"
                  @click="removeSpecial(special)"
                  ><el-icon><Delete /></el-icon></el-button
              ></span>
            </div>
            <p v-if="!(tree?.specials || []).length" class="plan-tree-empty">
              暂无专项节点
            </p>
          </div>
        </section>
      </aside>
      <main class="test-plan-content">
        <template v-if="selectedNode">
          <nav class="test-plan-tabs" aria-label="测试方案视图">
            <button
              v-if="hasCurrent"
              type="button"
              :class="{ active: tab === 'current' }"
              @click="tab = 'current'"
            >
              当前方案</button
            ><button
              type="button"
              :class="{ active: tab === 'manage' }"
              @click="tab = 'manage'"
            >
              方案管理
            </button>
          </nav>
          <section
            v-if="tab === 'current' && currentPlan"
            class="plan-current-panel"
          >
            <header class="plan-current-header">
              <div>
                <h2>
                  {{ currentPlan.plan_name }}
                  <el-tag size="small" type="primary"
                    >V{{ currentPlan.version_no }}</el-tag
                  >
                </h2>
                <p>
                  {{ currentPlan.version_note }} ·
                  {{ currentPlan.uploader_name || "—" }} ·
                  {{ formatTime(currentPlan.uploaded_at) }}
                </p>
              </div>
              <el-button type="primary" @click="download(currentPlan)"
                ><el-icon><Download /></el-icon>下载</el-button
              >
            </header>
            <div
              v-if="currentPlan.file_extension === 'docx'"
              class="plan-docx-preview"
            >
              <iframe
                v-if="currentDocxUrl"
                :src="currentDocxUrl"
                :title="`${currentPlan.plan_name}在线预览`"
                sandbox="allow-same-origin allow-scripts allow-forms allow-popups allow-downloads"
                referrerpolicy="no-referrer"
              /><UiEmptyState
                v-else
                title="预览暂不可用"
                description="请使用右上角下载查看原始文件。"
              />
            </div>
            <section v-else class="plan-sheet-preview">
              <el-alert
                v-if="currentSheet && currentSheet.sheetCount > 1"
                type="warning"
                :closable="false"
                show-icon
                :title="`共 ${currentSheet.sheetCount} 个 Sheet，请下载查看完整内容`"
              />
              <div v-if="currentSheet" class="plan-sheet-scroll">
                <table>
                  <tbody>
                    <tr
                      v-for="(row, rowIndex) in currentSheet.rows"
                      :key="rowIndex"
                    >
                      <td v-for="(cell, cellIndex) in row" :key="cellIndex">
                        {{ cell }}
                      </td>
                    </tr>
                  </tbody>
                </table>
              </div>
              <UiEmptyState
                v-else
                title="Excel 预览暂不可用"
                description="请使用右上角下载查看原始文件。"
              />
            </section>
          </section>
          <section v-else class="plan-management-panel">
            <header class="plan-management-header">
              <div>
                <h2>{{ selectedTitle }}</h2>
                <p>{{ nodeLevel }} · 方案按更新时间倒序</p>
              </div>
              <el-button v-if="canCreate" type="primary" @click="openUpload()"
                ><el-icon><Upload /></el-icon>上传方案</el-button
              >
            </header>
            <UiDataTable
              :data="plans"
              :loading="loading"
              row-key="id"
              border
              class="plan-table"
              ><el-table-column
                prop="plan_name"
                label="方案名称"
                min-width="180"
                sortable
                resizable /><el-table-column
                prop="version_no"
                label="当前版本"
                width="96"
                align="center"
                header-align="center"
                sortable
                resizable
                ><template #default="{ row }"
                  ><el-tag type="primary" size="small"
                    >V{{ row.version_no }}</el-tag
                  ></template
                ></el-table-column
              ><el-table-column
                prop="version_note"
                label="版本说明"
                min-width="180"
                show-overflow-tooltip
                resizable /><el-table-column
                prop="file_extension"
                label="文件格式"
                width="94"
                align="center"
                header-align="center"
                sortable
                resizable
                ><template #default="{ row }"
                  ><el-tag
                    :type="extensionType(row.file_extension)"
                    effect="plain"
                    size="small"
                    >{{ row.file_extension }}</el-tag
                  ></template
                ></el-table-column
              ><el-table-column
                prop="uploader_name"
                label="上传人"
                width="112"
                align="center"
                header-align="center"
                sortable
                resizable
                ><template #default="{ row }">{{
                  row.uploader_name || "—"
                }}</template></el-table-column
              ><el-table-column
                prop="uploaded_at"
                label="更新时间"
                width="170"
                align="center"
                header-align="center"
                sortable
                resizable
                ><template #default="{ row }">{{
                  formatTime(row.uploaded_at)
                }}</template></el-table-column
              ><el-table-column
                label="操作"
                width="142"
                fixed="right"
                align="center"
                header-align="center"
                :resizable="false"
                ><template #default="{ row }"
                  ><div class="plan-table-actions">
                    <el-tooltip content="预览" placement="top"
                      ><el-button
                        link
                        type="primary"
                        aria-label="预览方案"
                        @click="preview(row)"
                        ><el-icon><View /></el-icon></el-button></el-tooltip
                    ><el-tooltip content="下载" placement="top"
                      ><el-button
                        link
                        type="primary"
                        aria-label="下载方案"
                        @click="download(row)"
                        ><el-icon><Download /></el-icon></el-button></el-tooltip
                    ><el-tooltip content="历史版本" placement="top"
                      ><el-button
                        link
                        type="primary"
                        aria-label="查看历史版本"
                        @click="showVersions(row)"
                        ><el-icon><Clock /></el-icon></el-button></el-tooltip
                    ><el-tooltip
                      v-if="canUpdate"
                      content="上传新版本"
                      placement="top"
                      ><el-button
                        link
                        type="primary"
                        aria-label="上传新版本"
                        @click="openUpload(row)"
                        ><el-icon><Upload /></el-icon></el-button></el-tooltip
                    ><el-tooltip
                      v-if="canDelete"
                      content="删除方案"
                      placement="top"
                      ><el-button
                        link
                        type="danger"
                        aria-label="删除方案"
                        @click="removePlan(row)"
                        ><el-icon><Delete /></el-icon></el-button
                    ></el-tooltip></div></template></el-table-column
              ><template #footer
                ><el-pagination
                  v-model:current-page="page"
                  :total="total"
                  :page-size="20"
                  layout="total, prev, pager, next"
                  @current-change="loadNode" /></template
            ></UiDataTable>
            <section class="plan-mobile-list">
              <article v-for="plan in plans" :key="plan.id">
                <header>
                  <strong>{{ plan.plan_name }}</strong
                  ><el-tag type="primary" size="small"
                    >V{{ plan.version_no }}</el-tag
                  >
                </header>
                <p>{{ plan.version_note }}</p>
                <small
                  >{{ plan.file_extension }} · {{ plan.uploader_name || "—" }} ·
                  {{ formatTime(plan.uploaded_at) }}</small
                >
                <footer>
                  <el-button link type="primary" @click="preview(plan)"
                    >预览</el-button
                  ><el-button link type="primary" @click="download(plan)"
                    >下载</el-button
                  ><el-button link type="primary" @click="showVersions(plan)"
                    >历史版本</el-button
                  ><el-button
                    v-if="canUpdate"
                    link
                    type="primary"
                    @click="openUpload(plan)"
                    >上传新版本</el-button
                  ><el-button
                    v-if="canDelete"
                    link
                    type="danger"
                    @click="removePlan(plan)"
                    >删除</el-button
                  >
                </footer>
              </article>
            </section>
            <UiEmptyState
              v-if="!loading && !plans.length"
              title="当前节点暂无测试方案"
              description="上传后的方案将自动归入当前选择的方案级别和节点。"
              ><template #action
                ><el-button
                  v-if="canCreate"
                  type="primary"
                  @click="openUpload()"
                  >上传第一个方案</el-button
                ></template
              ></UiEmptyState
            >
          </section>
        </template>
      </main>
    </section>
    <el-dialog
      v-model="uploadVisible"
      width="min(560px, calc(100vw - 24px))"
      destroy-on-close
      :close-on-click-modal="false"
      ><template #header>{{
        uploadForm.planId ? "上传新版本" : "上传方案"
      }}</template
      ><el-form label-position="top"
        ><el-form-item label="方案文件" required
          ><el-upload
            :auto-upload="false"
            :show-file-list="false"
            accept=".docx,.xlsx"
            @change="chooseFile"
            ><el-button
              ><el-icon><Upload /></el-icon
              >{{ uploadForm.file ? "重新选择文件" : "选择文件" }}</el-button
            ></el-upload
          >
          <p v-if="uploadForm.file" class="plan-file-selected">
            {{ uploadForm.file.name }} ·
            {{ formatFileSize(uploadForm.file.size) }}
          </p>
          <p class="plan-form-hint">
            仅支持 .docx / .xlsx，单文件不超过 50 MB。
          </p></el-form-item
        ><el-form-item label="方案名称" required
          ><el-input
            v-model="uploadForm.planName"
            :disabled="Boolean(uploadForm.planId)"
            maxlength="100"
            show-word-limit
            placeholder="默认取文件名，可调整" /></el-form-item
        ><el-form-item label="当前节点"
          ><el-input
            :model-value="`${nodeLevel} · ${selectedTitle}`"
            disabled /></el-form-item
        ><el-form-item label="版本说明" required
          ><el-input
            v-model="uploadForm.versionNote"
            type="textarea"
            :rows="4"
            maxlength="200"
            show-word-limit
            placeholder="例如：根据第一轮评审意见修订" /></el-form-item></el-form
      ><template #footer
        ><el-button :disabled="uploadSaving" @click="uploadVisible = false"
          >取消</el-button
        ><el-button
          type="primary"
          :loading="uploadSaving"
          @click="submitUpload()"
          >确认上传</el-button
        ></template
      ></el-dialog
    >
    <el-dialog
      v-model="versionVisible"
      :title="versionTitle"
      width="min(820px, calc(100vw - 24px))"
      destroy-on-close
      ><UiDataTable
        :data="versions"
        row-key="version_id"
        border
        class="plan-table"
        ><el-table-column
          prop="version_no"
          label="版本号"
          width="90"
          align="center"
          header-align="center"
          sortable
          resizable
          ><template #default="{ row }"
            ><el-tag type="primary" size="small"
              >V{{ row.version_no }}</el-tag
            ></template
          ></el-table-column
        ><el-table-column
          prop="version_note"
          label="版本说明"
          min-width="200"
          resizable
        /><el-table-column
          prop="uploader_name"
          label="上传人"
          width="110"
          sortable
          resizable
        /><el-table-column
          prop="uploaded_at"
          label="上传时间"
          width="170"
          align="center"
          header-align="center"
          sortable
          resizable
          ><template #default="{ row }">{{
            formatTime(row.uploaded_at)
          }}</template></el-table-column
        ><el-table-column
          label="操作"
          width="104"
          align="center"
          header-align="center"
          :resizable="false"
          ><template #default="{ row }"
            ><el-button link type="primary" @click="preview(row)"
              >预览</el-button
            ><el-button link type="primary" @click="download(row)"
              >下载</el-button
            ></template
          ></el-table-column
        ></UiDataTable
      ></el-dialog
    >
    <el-dialog
      v-model="specialDialogVisible"
      :title="editingSpecial ? '重命名专项节点' : '新增专项节点'"
      width="min(420px, calc(100vw - 24px))"
      ><el-form label-position="top"
        ><el-form-item label="专项名称" required
          ><el-input
            v-model="specialName"
            maxlength="100"
            show-word-limit
            placeholder="例如：批量交易专项"
            @keyup.enter="saveSpecial" /></el-form-item></el-form
      ><template #footer
        ><el-button
          :disabled="specialSaving"
          @click="specialDialogVisible = false"
          >取消</el-button
        ><el-button type="primary" :loading="specialSaving" @click="saveSpecial"
          >保存</el-button
        ></template
      ></el-dialog
    >
    <UiFilePreview
      v-model="previewVisible"
      :url="previewUrl"
      :file-name="previewName"
    />
    <el-dialog
      v-model="sheetVisible"
      :title="sheetPreview?.name || 'Excel 预览'"
      width="min(94vw, 1240px)"
      top="4vh"
      destroy-on-close
      ><el-alert
        v-if="sheetPreview && sheetPreview.sheetCount > 1"
        type="warning"
        :closable="false"
        show-icon
        :title="`共 ${sheetPreview.sheetCount} 个 Sheet，请下载查看完整内容`"
      />
      <div class="plan-sheet-scroll plan-sheet-dialog">
        <table>
          <tbody>
            <tr
              v-for="(row, rowIndex) in sheetPreview?.rows || []"
              :key="rowIndex"
            >
              <td v-for="(cell, cellIndex) in row" :key="cellIndex">
                {{ cell }}
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <template #footer
        ><el-button @click="sheetVisible = false">关闭</el-button></template
      ></el-dialog
    >
  </section>
</template>
