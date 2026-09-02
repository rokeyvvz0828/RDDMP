<!--
文件：web/src/modules/test-management/defect/TestDefectPage.vue
说明：测试缺陷页面或交互组件。
用途：承载用户可见的加载、空、失败、提交和交互状态。
作者：hengguan
-->
<script setup lang="ts">
// 关键逻辑：页面只消费现有全局项目上下文；当前测试大类、项目和实体选择共同决定请求范围，前端显隐不替代服务端校验。
import {
  computed,
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
  Link,
  Plus,
  Refresh,
  Search,
  Upload,
  View,
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
  associateTestDefectExecutions,
  deleteTestDefect,
  detachTestDefectExecution,
  downloadTestDefectFile,
  getTestDefect,
  getTestDefectTree,
  listTestDefects,
  listTestExecutions,
  listTestProjects,
  restoreTestDefect,
  saveTestDefect,
  transitionTestDefect,
  type TestDefect,
  type TestDefectTree,
  type TestDomain,
} from "../api";

type TreeNode = {
  key: string;
  type: "project" | "system";
  id?: number;
  label: string;
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
const tree = ref<TestDefectTree>();
const projectId = computed(
  () =>
    projects.value.find((item) => item.project_code === context.currentRef)?.id,
);
const projectName = computed(
  () =>
    projects.value.find((item) => item.id === projectId.value)?.project_name ||
    "当前项目",
);
const system = ref<number>();
const keyword = ref("");
const status = ref<string[]>([
  "RAISED",
  "CAUSE_IDENTIFIED",
  "FIX_PLAN_CONFIRMED",
  "PENDING_VERIFICATION",
]);
const category = ref<string[]>([]);
const severity = ref<string[]>([]);
const priority = ref<string[]>([]);
const urgency = ref<string[]>([]);
const handlerId = ref<number>();
const proposerId = ref<number>();
const executionLink = ref("");
const recycle = ref(false);
const quick = ref<"SUBMITTED" | "HANDLED" | "VERIFY">();
const rows = ref<TestDefect[]>([]);
const total = ref(0);
const page = ref(1);
const loading = ref(false);
const error = ref("");
const sort = ref({
  prop: "proposed_at",
  order: "descending" as "ascending" | "descending",
});
const dialog = ref(false);
const saving = ref(false);
const detail = ref<any>();
const form = ref<any>({ attachment_ids: [], execution_ids: [] });
const isNew = ref(false);
const executionPicker = ref(false);
const executionCandidates = ref<any[]>([]);
const executionKeyword = ref("");
const defectEditor = shallowRef<IDomEditor>();
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
const name = (value: string) =>
  ({
    RAISED: "提出",
    CAUSE_IDENTIFIED: "已查明原因",
    FIX_PLAN_CONFIRMED: "已明确修复方案",
    PENDING_VERIFICATION: "待验证",
    RESOLVED: "已解决",
  })[value] || value;
const categoryName = (value: string) =>
  ({
    FUNCTION: "功能",
    DATA: "数据",
    UI: "界面",
    PERFORMANCE: "性能",
    SECURITY: "安全",
    ENVIRONMENT: "环境",
    REQUIREMENT: "需求",
    OPERATION: "操作",
    OTHER: "其他",
  })[value] || value;
const levelName = (value: string) =>
  ({
    FATAL: "致命",
    SERIOUS: "严重",
    GENERAL: "一般",
    MINOR: "轻微",
    HIGH: "高",
    MEDIUM: "中",
    LOW: "低",
  })[value] || value;
const stamp = (value?: string) =>
  value ? value.slice(5, 16).replace("T", " ") : "—";
const treeData = computed<TreeNode[]>(() => [
  {
    key: "project",
    type: "project",
    label: projectName.value,
    children: (tree.value?.systems || []).map((item) => ({
      key: `system:${item.id}`,
      type: "system",
      id: item.id,
      label: item.short_name || item.name,
    })),
  },
]);
const selectedTreeKey = computed(() =>
  system.value ? `system:${system.value}` : "project",
);
async function load() {
  if (!projectId.value) return;
  loading.value = true;
  error.value = "";
  try {
    const [treeResponse, listResponse] = await Promise.all([
      getTestDefectTree(domain.value, projectId.value),
      listTestDefects(domain.value, {
        projectId: projectId.value,
        page: page.value,
        size: 20,
        physicalSubsystemId: system.value,
        keyword: keyword.value || undefined,
        status: status.value,
        category: category.value,
        severity: severity.value,
        priority: priority.value,
        urgency: urgency.value,
        handlerId: handlerId.value,
        proposerId: proposerId.value,
        executionLink: executionLink.value || undefined,
        recycle: recycle.value,
        quick: quick.value,
        sortBy: sort.value.prop,
        sortOrder: sort.value.order,
      }),
    ]);
    tree.value = treeResponse.data.data;
    rows.value = listResponse.data.data.records || [];
    total.value = listResponse.data.data.total || 0;
  } catch (cause: any) {
    error.value =
      cause?.response?.data?.message || "测试缺陷加载失败，请稍后重试。";
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
  system.value = node.id;
  page.value = 1;
  void load();
}
function create() {
  if (!system.value) return ElMessage.warning("请先在左侧选择所属系统");
  isNew.value = true;
  detail.value = undefined;
  form.value = {
    physical_subsystem_id: system.value,
    defect_category: "FUNCTION",
    severity: "GENERAL",
    priority: "MEDIUM",
    urgency: "MEDIUM",
    summary: "",
    description_html: "",
    attachment_ids: [],
    execution_ids: [],
  };
  dialog.value = true;
}
async function open(row: TestDefect) {
  if (!projectId.value) return;
  try {
    isNew.value = false;
    detail.value = (
      await getTestDefect(domain.value, projectId.value, row.id)
    ).data.data;
    form.value = {
      ...detail.value,
      attachment_ids: (detail.value.attachments || []).map(
        (item: any) => item.id,
      ),
      execution_ids: [],
    };
    dialog.value = true;
  } catch (cause: any) {
    ElMessage.error(cause?.response?.data?.message || "加载缺陷详情失败");
  }
}
async function save() {
  if (!projectId.value) return;
  if (
    !form.value.summary?.trim() ||
    !String(form.value.description_html || "")
      .replace(/<[^>]+>/g, "")
      .trim() ||
    !form.value.physical_subsystem_id
  )
    return ElMessage.warning("请填写概述、所属系统和缺陷描述");
  saving.value = true;
  try {
    const result = await saveTestDefect(
      domain.value,
      projectId.value,
      form.value,
      isNew.value ? undefined : form.value.id,
    );
    detail.value = result.data.data;
    form.value = {
      ...detail.value,
      attachment_ids: (detail.value.attachments || []).map(
        (item: any) => item.id,
      ),
      execution_ids: [],
    };
    isNew.value = false;
    ElMessage.success("缺陷已保存");
    await load();
  } catch (cause: any) {
    ElMessage.error(cause?.response?.data?.message || "保存缺陷失败");
  } finally {
    saving.value = false;
  }
}
async function transition(target: string) {
  if (!projectId.value || !form.value.id) return;
  try {
    detail.value = (
      await transitionTestDefect(
        domain.value,
        projectId.value,
        form.value.id,
        target,
      )
    ).data.data;
    form.value = {
      ...detail.value,
      attachment_ids: (detail.value.attachments || []).map(
        (item: any) => item.id,
      ),
      execution_ids: [],
    };
    ElMessage.success(`已流转为${name(target)}`);
    await load();
  } catch (cause: any) {
    ElMessage.error(cause?.response?.data?.message || "状态流转失败");
  }
}
async function remove(row: TestDefect) {
  if (!projectId.value) return;
  try {
    await ElMessageBox.confirm(
      `确认删除缺陷“${row.defect_code}”？存在关联执行记录时不可删除。`,
      "删除缺陷",
      { type: "warning" },
    );
    await deleteTestDefect(domain.value, projectId.value, row.id);
    ElMessage.success("已删除");
    await load();
  } catch (cause: any) {
    if (cause !== "cancel" && cause !== "close")
      ElMessage.error(cause?.response?.data?.message || "删除缺陷失败");
  }
}
async function restore(row: TestDefect) {
  if (!projectId.value) return;
  try {
    await restoreTestDefect(domain.value, projectId.value, row.id);
    ElMessage.success("缺陷已恢复");
    await load();
  } catch (cause: any) {
    ElMessage.error(cause?.response?.data?.message || "恢复缺陷失败");
  }
}
function resetFilters() {
  keyword.value = "";
  status.value = [
    "RAISED",
    "CAUSE_IDENTIFIED",
    "FIX_PLAN_CONFIRMED",
    "PENDING_VERIFICATION",
  ];
  category.value = [];
  severity.value = [];
  priority.value = [];
  urgency.value = [];
  handlerId.value = undefined;
  proposerId.value = undefined;
  executionLink.value = "";
  recycle.value = false;
  quick.value = undefined;
  page.value = 1;
  void load();
}
function setQuick(value?: "SUBMITTED" | "HANDLED" | "VERIFY") {
  quick.value = quick.value === value ? undefined : value;
  recycle.value = false;
  page.value = 1;
  void load();
}
function sortChange(value: {
  prop: string;
  order: "ascending" | "descending" | null;
}) {
  sort.value = {
    prop: value.prop || "proposed_at",
    order: value.order || "descending",
  };
  void load();
}
async function upload(file: File) {
  if (file.size > 50 * 1024 * 1024)
    return ElMessage.warning("单个附件不能超过50MB");
  try {
    const attachment = (await uploadAttachment(file)).data.data;
    form.value.attachment_ids = [
      ...new Set([...(form.value.attachment_ids || []), attachment.id]),
    ];
    form.value.attachments = [
      ...(form.value.attachments || []),
      {
        id: attachment.id,
        file_name: attachment.fileName,
        file_size: attachment.fileSize,
      },
    ];
    ElMessage.success("附件已添加，保存后生效");
  } catch (cause: any) {
    ElMessage.error(cause?.response?.data?.message || "附件上传失败");
  }
}
async function uploadRequest(options: any) {
  await upload(options.file);
  options.onSuccess?.();
}
async function attachmentLink(id: number, preview = false) {
  try {
    if (preview)
      window.open(
        (await getAttachmentPreview(id)).data.data.previewUrl,
        "_blank",
        "noopener",
      );
    else
      window.open(
        (await getAttachmentDownload(id)).data.data.downloadUrl,
        "_blank",
        "noopener",
      );
  } catch (cause: any) {
    ElMessage.error(cause?.response?.data?.message || "附件访问失败");
  }
}
async function loadExecutionCandidates() {
  if (!projectId.value || !form.value.physical_subsystem_id) return;
  try {
    const result = await listTestExecutions(domain.value, {
      projectId: projectId.value,
      page: 1,
      size: 100,
      physicalSubsystemId: form.value.physical_subsystem_id,
      keyword: executionKeyword.value || undefined,
    });
    executionCandidates.value = result.data.data.records || [];
  } catch (cause: any) {
    ElMessage.error(cause?.response?.data?.message || "加载执行记录失败");
  }
}
async function associateExecutions() {
  if (
    !projectId.value ||
    !detail.value?.id ||
    !(form.value.execution_ids || []).length
  )
    return;
  try {
    await associateTestDefectExecutions(
      domain.value,
      projectId.value,
      detail.value.id,
      form.value.execution_ids,
    );
    await open({ id: detail.value.id } as TestDefect);
    executionPicker.value = false;
  } catch (cause: any) {
    ElMessage.error(cause?.response?.data?.message || "关联执行记录失败");
  }
}
async function detachExecution(executionId: number) {
  if (!projectId.value || !detail.value?.id) return;
  try {
    await detachTestDefectExecution(
      domain.value,
      projectId.value,
      detail.value.id,
      executionId,
    );
    await open({ id: detail.value.id } as TestDefect);
  } catch (cause: any) {
    ElMessage.error(cause?.response?.data?.message || "解除关联失败");
  }
}
async function exportRows() {
  if (!projectId.value) return;
  try {
    await downloadTestDefectFile(domain.value, {
      projectId: projectId.value,
      physicalSubsystemId: system.value,
      keyword: keyword.value || undefined,
      status: status.value,
      category: category.value,
      severity: severity.value,
      priority: priority.value,
      urgency: urgency.value,
    });
  } catch (cause: any) {
    ElMessage.error(cause?.response?.data?.message || "导出失败");
  }
}
watch([() => context.currentRef, domain], () => {
  page.value = 1;
  system.value = undefined;
  void initialize();
});
onMounted(initialize);
onBeforeUnmount(() => defectEditor.value?.destroy());
</script>

<template>
  <section class="defect-page">
    <UiPageHeader eyebrow="测试管理" :title="domainName + ' · 测试缺陷'"
      ><template #actions
        ><el-button type="primary" size="small" :icon="Plus" @click="create"
          >新增缺陷</el-button
        ><el-tooltip content="刷新"
          ><el-button
            size="small"
            text
            circle
            :icon="Refresh"
            aria-label="刷新缺陷"
            @click="load" /></el-tooltip></template
    ></UiPageHeader>
    <UiEmptyState
      v-if="!projectId"
      title="请先选择项目"
      description="请使用顶部全局项目选择器后查看测试缺陷。"
    />
    <UiEmptyState
      v-else-if="error"
      title="测试缺陷加载失败"
      :description="error"
      ><template #action
        ><el-button type="primary" @click="load">重新加载</el-button></template
      ></UiEmptyState
    >
    <section v-else class="defect-workspace" v-loading="loading">
      <aside class="defect-tree">
        <header><span>参测系统</span></header>
        <el-tree
          :data="treeData"
          node-key="key"
          :current-node-key="selectedTreeKey"
          default-expand-all
          highlight-current
          :expand-on-click-node="false"
          @node-click="selectTree"
          ><template #default="{ data }"
            ><span class="defect-tree-node"
              ><el-icon
                ><FolderOpened v-if="data.type === 'project'" /><Folder
                  v-else /></el-icon
              ><span class="defect-tree-label">{{ data.label }}</span></span
            ></template
          ></el-tree
        >
      </aside>
      <main class="defect-content">
        <div class="defect-toolbar">
          <el-input
            v-model="keyword"
            clearable
            size="small"
            placeholder="缺陷编号、概述"
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
            placeholder="状态"
            ><el-option
              v-for="value in [
                'RAISED',
                'CAUSE_IDENTIFIED',
                'FIX_PLAN_CONFIRMED',
                'PENDING_VERIFICATION',
                'RESOLVED',
              ]"
              :key="value"
              :label="name(value)"
              :value="value" /></el-select
          ><el-select
            v-model="category"
            multiple
            collapse-tags
            size="small"
            clearable
            placeholder="缺陷分类"
            ><el-option
              v-for="value in [
                'FUNCTION',
                'DATA',
                'UI',
                'PERFORMANCE',
                'SECURITY',
                'ENVIRONMENT',
                'REQUIREMENT',
                'OPERATION',
                'OTHER',
              ]"
              :key="value"
              :label="categoryName(value)"
              :value="value" /></el-select
          ><el-select
            v-model="severity"
            multiple
            collapse-tags
            size="small"
            clearable
            placeholder="严重程度"
            ><el-option
              v-for="value in ['FATAL', 'SERIOUS', 'GENERAL', 'MINOR']"
              :key="value"
              :label="levelName(value)"
              :value="value" /></el-select
          ><el-select
            v-model="priority"
            multiple
            collapse-tags
            size="small"
            clearable
            placeholder="优先级"
            ><el-option
              v-for="value in ['HIGH', 'MEDIUM', 'LOW']"
              :key="value"
              :label="levelName(value)"
              :value="value" /></el-select
          ><el-select
            v-model="urgency"
            multiple
            collapse-tags
            size="small"
            clearable
            placeholder="紧急程度"
            ><el-option
              v-for="value in ['HIGH', 'MEDIUM', 'LOW']"
              :key="value"
              :label="levelName(value)"
              :value="value" /></el-select
          ><el-select
            v-model="handlerId"
            filterable
            clearable
            size="small"
            placeholder="处理人"
            ><el-option
              v-for="item in (tree?.handlers || []).filter(
                (item) => !system || item.physical_subsystem_id === system,
              )"
              :key="item.id"
              :label="item.display_name || item.username"
              :value="item.id" /></el-select
          ><el-select
            v-model="executionLink"
            clearable
            size="small"
            placeholder="执行关联"
            ><el-option label="已关联执行" value="LINKED" /><el-option
              label="未关联执行"
              value="UNLINKED" /></el-select
          ><el-checkbox
            v-model="recycle"
            size="small"
            @change="
              page = 1;
              load();
            "
            >回收站</el-checkbox
          ><el-button
            size="small"
            :type="quick === 'SUBMITTED' ? 'primary' : 'default'"
            @click="setQuick('SUBMITTED')"
            >我提出的</el-button
          ><el-button
            size="small"
            :type="quick === 'HANDLED' ? 'primary' : 'default'"
            @click="setQuick('HANDLED')"
            >我处理的</el-button
          ><el-button
            size="small"
            :type="quick === 'VERIFY' ? 'primary' : 'default'"
            @click="setQuick('VERIFY')"
            >待我验证</el-button
          ><el-button
            size="small"
            :icon="Search"
            @click="
              page = 1;
              load();
            "
            >查询</el-button
          ><el-button size="small" text @click="resetFilters">重置</el-button
          ><span class="toolbar-spacer" /><el-tooltip content="导出当前结果"
            ><el-button
              size="small"
              text
              circle
              :icon="Download"
              aria-label="导出测试缺陷"
              @click="exportRows"
          /></el-tooltip>
        </div>
        <div class="defect-result-bar">
          <span>默认显示未解决缺陷；请在左侧选择系统后新建缺陷。</span>
        </div>
        <UiDataTable
          :data="rows"
          :loading="loading"
          row-key="id"
          border
          class="defect-table"
          @sort-change="sortChange"
          ><el-table-column
            prop="defect_code"
            label="缺陷编号"
            min-width="170"
            resizable
            sortable="custom"
            show-overflow-tooltip
            ><template #default="scope"
              ><el-button link size="small" @click="open(scope.row)">{{
                scope.row.defect_code
              }}</el-button></template
            ></el-table-column
          ><el-table-column
            prop="summary"
            label="概述"
            min-width="180"
            resizable
            sortable="custom"
            show-overflow-tooltip /><el-table-column
            prop="physical_system_name"
            label="所属系统"
            min-width="126"
            resizable
            show-overflow-tooltip /><el-table-column
            prop="defect_category"
            label="分类"
            min-width="88"
            resizable
            ><template #default="scope">{{
              categoryName(scope.row.defect_category)
            }}</template></el-table-column
          ><el-table-column
            prop="severity"
            label="严重程度"
            min-width="86"
            resizable
            ><template #default="scope">{{
              levelName(scope.row.severity)
            }}</template></el-table-column
          ><el-table-column prop="priority" label="优先级" width="68" resizable
            ><template #default="scope">{{
              levelName(scope.row.priority)
            }}</template></el-table-column
          ><el-table-column prop="urgency" label="紧急程度" width="78" resizable
            ><template #default="scope">{{
              levelName(scope.row.urgency)
            }}</template></el-table-column
          ><el-table-column
            prop="status"
            label="状态"
            min-width="104"
            resizable
            sortable="custom"
            ><template #default="scope"
              ><el-tag
                :type="scope.row.status === 'RESOLVED' ? 'success' : 'warning'"
                size="small"
                >{{ name(scope.row.status) }}</el-tag
              ></template
            ></el-table-column
          ><el-table-column
            prop="handler_name"
            label="处理人"
            min-width="92"
            resizable
            show-overflow-tooltip
            ><template #default="scope">{{
              scope.row.handler_name || "待分派"
            }}</template></el-table-column
          ><el-table-column
            prop="proposed_at"
            label="提出时间"
            min-width="126"
            resizable
            sortable="custom"
            ><template #default="scope">{{
              stamp(scope.row.proposed_at)
            }}</template></el-table-column
          ><el-table-column
            prop="proposer_name"
            label="提出人"
            min-width="92"
            resizable
            show-overflow-tooltip
            ><template #default="scope">{{
              scope.row.proposer_name || "—"
            }}</template></el-table-column
          ><el-table-column
            prop="execution_count"
            label="关联案例"
            width="82"
            align="center"
            resizable /><el-table-column
            label="操作"
            width="58"
            fixed="right"
            align="center"
            header-align="center"
            :resizable="false"
            ><template #default="scope"
              ><div class="defect-actions">
                <el-tooltip v-if="!recycle" content="编辑"
                  ><el-button
                    link
                    :icon="Edit"
                    aria-label="编辑缺陷"
                    @click="open(scope.row)" /></el-tooltip
                ><el-tooltip v-if="!recycle" content="删除"
                  ><el-button
                    link
                    type="danger"
                    :icon="Delete"
                    aria-label="删除缺陷"
                    @click="remove(scope.row)" /></el-tooltip
                ><el-tooltip v-if="recycle" content="恢复"
                  ><el-button
                    link
                    type="success"
                    :icon="Refresh"
                    aria-label="恢复缺陷"
                    @click="restore(scope.row)"
                /></el-tooltip></div></template></el-table-column
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
      v-model="dialog"
      :title="isNew ? '新增缺陷' : '缺陷详情'"
      width="min(900px, calc(100vw - 24px))"
      :loading="saving"
      @submit="save"
      ><el-form label-width="96px" class="defect-form"
        ><el-form-item v-if="form.defect_code" label="缺陷编号"
          ><el-input :model-value="form.defect_code" disabled /></el-form-item
        ><el-form-item label="概述" required
          ><el-input
            v-model="form.summary"
            maxlength="100"
            show-word-limit /></el-form-item
        ><el-form-item label="所属系统" required
          ><el-select v-model="form.physical_subsystem_id" style="width: 100%"
            ><el-option
              v-for="item in tree?.systems || []"
              :key="item.id"
              :label="item.short_name || item.name"
              :value="item.id" /></el-select
        ></el-form-item>
        <div class="defect-form-grid">
          <el-form-item label="所属轮次"
            ><el-select v-model="form.round_id" clearable
              ><el-option
                v-for="item in tree?.rounds || []"
                :key="item.id"
                :label="item.round_name"
                :value="item.id" /></el-select
          ></el-form-item>
          <el-form-item label="所属周期"
            ><el-select v-model="form.cycle_id" clearable
              ><el-option
                v-for="item in (tree?.cycles || []).filter(
                  (item) => !form.round_id || item.round_id === form.round_id,
                )"
                :key="item.id"
                :label="item.cycle_name"
                :value="item.id" /></el-select
          ></el-form-item>
        </div>
        <div class="defect-form-grid">
          <el-form-item label="发现版本"
            ><el-input v-model="form.found_version" maxlength="50"
          /></el-form-item>
          <el-form-item label="测试环境"
            ><el-select
              v-model="form.test_environment_code"
              clearable
              filterable
              ><el-option
                v-for="item in tree?.environments || []"
                :key="item.env_code"
                :label="item.env_name"
                :value="item.env_code" /></el-select
          ></el-form-item>
        </div>
        ><el-form-item label="处理人"
          ><el-select
            v-model="form.handler_id"
            clearable
            filterable
            style="width: 100%"
            ><el-option
              v-for="item in (tree?.handlers || []).filter(
                (item) =>
                  item.physical_subsystem_id === form.physical_subsystem_id,
              )"
              :key="item.id"
              :label="item.display_name || item.username"
              :value="item.id" /></el-select></el-form-item
        ><el-form-item label="缺陷分类"
          ><el-select v-model="form.defect_category" style="width: 100%"
            ><el-option
              v-for="item in [
                'FUNCTION',
                'DATA',
                'UI',
                'PERFORMANCE',
                'SECURITY',
                'ENVIRONMENT',
                'REQUIREMENT',
                'OPERATION',
                'OTHER',
              ]"
              :key="item"
              :label="categoryName(item)"
              :value="item" /></el-select
        ></el-form-item>
        <div class="defect-form-grid">
          <el-form-item label="严重程度"
            ><el-select v-model="form.severity"
              ><el-option
                v-for="item in ['FATAL', 'SERIOUS', 'GENERAL', 'MINOR']"
                :key="item"
                :label="levelName(item)"
                :value="item" /></el-select></el-form-item
          ><el-form-item label="优先级"
            ><el-select v-model="form.priority"
              ><el-option
                v-for="item in ['HIGH', 'MEDIUM', 'LOW']"
                :key="item"
                :label="levelName(item)"
                :value="item" /></el-select
          ></el-form-item>
        </div>
        <el-form-item label="紧急程度"
          ><el-radio-group v-model="form.urgency"
            ><el-radio
              v-for="item in ['HIGH', 'MEDIUM', 'LOW']"
              :key="item"
              :value="item"
              >{{ levelName(item) }}</el-radio
            ></el-radio-group
          ></el-form-item
        ><el-form-item v-if="isNew" label="关联执行"
          ><el-button
            size="small"
            :icon="Link"
            @click="
              executionPicker = true;
              loadExecutionCandidates();
            "
            >选择执行记录</el-button
          ><span class="selected-count"
            >已选择 {{ form.execution_ids?.length || 0 }} 条</span
          ></el-form-item
        ><el-form-item label="缺陷描述" required
          ><div class="rich-editor">
            <Toolbar
              :editor="defectEditor"
              :default-config="toolbarConfig"
              mode="default"
            /><WangEditor
              v-model="form.description_html"
              :default-config="editorConfig"
              mode="default"
              @on-created="(editor: IDomEditor) => (defectEditor = editor)"
            /></div></el-form-item
        ><el-form-item label="附件"
          ><el-upload :show-file-list="false" :http-request="uploadRequest"
            ><el-button size="small" :icon="Upload"
              >添加附件</el-button
            ></el-upload
          >
          <div class="attachment-list">
            <span v-for="item in form.attachments || []" :key="item.id"
              ><el-button
                link
                size="small"
                @click="attachmentLink(item.id, true)"
                >{{ item.file_name || item.fileName }}</el-button
              ><el-button
                link
                size="small"
                :icon="Download"
                aria-label="下载附件"
                @click="attachmentLink(item.id)" /><el-button
                link
                size="small"
                type="danger"
                :icon="Delete"
                aria-label="移除附件"
                @click="
                  form.attachments = form.attachments.filter(
                    (attachment: any) => attachment.id !== item.id,
                  );
                  form.attachment_ids = form.attachment_ids.filter(
                    (id: number) => id !== item.id,
                  );
                "
            /></span></div></el-form-item></el-form
      ><template v-if="!isNew && detail"
        ><el-divider>状态流转</el-divider>
        <div class="transition-actions">
          <el-button
            v-if="form.status === 'RAISED'"
            @click="transition('CAUSE_IDENTIFIED')"
            >已查明原因</el-button
          ><el-button
            v-if="form.status === 'CAUSE_IDENTIFIED'"
            @click="transition('FIX_PLAN_CONFIRMED')"
            >已明确修复方案</el-button
          ><el-button
            v-if="form.status === 'FIX_PLAN_CONFIRMED'"
            @click="transition('PENDING_VERIFICATION')"
            >待验证</el-button
          ><el-button
            v-if="form.status === 'PENDING_VERIFICATION'"
            type="success"
            @click="transition('RESOLVED')"
            >验证通过并解决</el-button
          ><el-button
            v-if="
              form.status === 'PENDING_VERIFICATION' ||
              form.status === 'RESOLVED'
            "
            type="warning"
            @click="transition('RAISED')"
            >重现打回</el-button
          >
        </div>
        <el-divider>关联执行案例</el-divider>
        <div class="execution-link-actions">
          <el-button
            size="small"
            :icon="Link"
            @click="
              executionPicker = true;
              loadExecutionCandidates();
            "
            >关联执行记录</el-button
          >
        </div>
        <el-empty
          v-if="!detail.executions?.length"
          description="暂无关联执行案例"
          :image-size="55" /><el-table
          v-else
          :data="detail.executions"
          size="small"
          max-height="180"
          ><el-table-column prop="case_code" label="案例编号"
            ><template #default="scope">{{
              scope.row.case_code || scope.row.snapshot_case_code
            }}</template></el-table-column
          ><el-table-column prop="case_name" label="案例名称"
            ><template #default="scope">{{
              scope.row.case_name || scope.row.snapshot_case_name
            }}</template></el-table-column
          ><el-table-column
            prop="execution_status"
            label="执行状态" /><el-table-column
            label="操作"
            width="60"
            align="center"
            ><template #default="scope"
              ><el-tooltip v-if="scope.row.execution_id" content="解除关联"
                ><el-button
                  link
                  type="danger"
                  :icon="Delete"
                  aria-label="解除关联"
                  @click="
                    detachExecution(scope.row.execution_id)
                  " /></el-tooltip></template></el-table-column></el-table
        ><el-divider>处理轨迹</el-divider
        ><el-table :data="detail.traces || []" size="small" max-height="160"
          ><el-table-column
            prop="created_at"
            label="时间"
            width="170" /><el-table-column
            prop="operator_name"
            label="操作人"
            width="120" /><el-table-column
            prop="action_code"
            label="动作" /></el-table></template
    ></TestManagementFormDialog>
    <el-dialog
      v-model="executionPicker"
      title="关联执行记录"
      width="min(780px, 94vw)"
      align-center
      destroy-on-close
      ><div class="execution-picker-toolbar">
        <el-input
          v-model="executionKeyword"
          size="small"
          clearable
          placeholder="案例编号、名称"
          @keyup.enter="loadExecutionCandidates"
        /><el-button size="small" @click="loadExecutionCandidates"
          >查询</el-button
        >
      </div>
      <el-checkbox-group
        v-model="form.execution_ids"
        class="execution-checkboxes"
        ><el-checkbox
          v-for="item in executionCandidates"
          :key="item.id"
          :value="item.id"
          ><b>{{ item.case_code }}</b> · {{ item.case_name }}（{{
            item.execution_status
          }}）</el-checkbox
        ></el-checkbox-group
      ><el-empty
        v-if="!executionCandidates.length"
        description="未找到可关联的执行记录"
        :image-size="54"
      /><template #footer
        ><el-button @click="executionPicker = false">取消</el-button
        ><el-button
          type="primary"
          @click="isNew ? (executionPicker = false) : associateExecutions()"
          >{{ isNew ? "确认选择" : "关联" }}</el-button
        ></template
      ></el-dialog
    >
  </section>
</template>

<style scoped>
.defect-page {
  min-width: 0;
  max-width: 1440px;
  margin: 0 auto;
}
.defect-workspace {
  display: grid;
  grid-template-columns: 248px minmax(0, 1fr);
  min-height: 590px;
  overflow: hidden;
  border: 1px solid var(--line);
  border-radius: 6px;
  background: var(--panel-bg);
}
.defect-tree {
  min-width: 0;
  padding: 9px;
  overflow: auto;
  border-right: 1px solid var(--line);
  background: color-mix(in srgb, var(--panel-muted) 58%, var(--panel-bg));
}
.defect-tree > header {
  display: flex;
  min-height: 26px;
  align-items: center;
  justify-content: space-between;
  padding: 0 5px 7px;
  color: var(--text);
  font-size: 13px;
  font-weight: 650;
}
.defect-tree :deep(.el-tree) {
  --el-tree-node-hover-bg-color: var(--panel-bg);
  background: transparent;
  color: var(--text);
  font-size: 12px;
}
.defect-tree :deep(.el-tree-node__content) {
  height: 29px;
  border-radius: 4px;
}
.defect-tree :deep(.el-tree-node.is-current > .el-tree-node__content) {
  color: var(--brand-strong);
  background: color-mix(in srgb, var(--brand) 14%, var(--panel-bg));
  font-weight: 650;
}
.defect-tree-node {
  display: flex;
  min-width: 0;
  width: 100%;
  gap: 5px;
  align-items: center;
}
.defect-tree-label {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.defect-content {
  min-width: 0;
  padding: 10px;
  background: var(--panel-bg);
}
.defect-toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: center;
  margin-bottom: 7px;
}
.defect-toolbar .el-input {
  width: 188px;
}
.defect-toolbar .el-select {
  width: 118px;
}
.toolbar-spacer {
  flex: 1;
}
.defect-result-bar {
  display: flex;
  align-items: center;
  min-height: 25px;
  margin-bottom: 7px;
  color: var(--text-muted);
  font-size: 12px;
}
.defect-table :deep(.el-table__header th .cell) {
  white-space: nowrap;
  font-size: 12px;
}
.defect-table
  :deep(.el-table__header th:not(.ascending):not(.descending) .caret-wrapper) {
  display: none;
}
.defect-table :deep(.el-table__cell) {
  font-size: 12px;
}
.defect-actions {
  display: flex;
  justify-content: center;
  gap: 1px;
  align-items: center;
}
.defect-actions .el-button,
.defect-table :deep(.el-button) {
  min-width: 22px;
  height: 23px;
  margin: 0;
  padding: 0 3px;
  font-size: 12px;
}
.defect-form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 12px;
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
  min-height: 220px;
}
.transition-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
.execution-link-actions {
  display: flex;
  justify-content: flex-end;
  margin: -6px 0 7px;
}
.execution-picker-toolbar {
  display: flex;
  gap: 8px;
  margin-bottom: 10px;
}
.execution-picker-toolbar .el-input {
  flex: 1;
}
.execution-checkboxes {
  display: grid;
  gap: 8px;
  max-height: 280px;
  overflow: auto;
}
.selected-count {
  margin-left: 8px;
  color: var(--text-muted);
  font-size: 12px;
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
@media (max-width: 760px) {
  .defect-workspace {
    display: block;
    overflow: visible;
    border: 0;
    background: transparent;
  }
  .defect-tree {
    max-height: 250px;
    margin-bottom: 10px;
    border: 1px solid var(--line);
    border-radius: 6px;
  }
  .defect-content {
    padding: 9px;
    border: 1px solid var(--line);
    border-radius: 6px;
  }
  .defect-toolbar .el-input,
  .defect-toolbar .el-select {
    flex: 1 1 calc(50% - 4px);
    width: auto;
  }
  .toolbar-spacer {
    display: none;
  }
  .defect-form-grid {
    grid-template-columns: 1fr;
  }
}
</style>
