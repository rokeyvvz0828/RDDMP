<!--
文件：web/src/modules/test-management/report/TestReportPage.vue
说明：测试报告页面或交互组件。
用途：承载用户可见的加载、空、失败、提交和交互状态。
作者：hengguan
-->
<script setup lang="ts">
// 关键逻辑：页面只消费现有全局项目上下文；当前测试大类、项目和实体选择共同决定请求范围，前端显隐不替代服务端校验。
import {
  computed,
  onBeforeUnmount,
  onMounted,
  reactive,
  ref,
  watch,
} from "vue";
import {
  Delete,
  Document,
  Download,
  Edit,
  FolderOpened,
  Plus,
  Refresh,
  Search,
  Tickets,
} from "@element-plus/icons-vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { Editor as WangEditor, Toolbar } from "@wangeditor/editor-for-vue";
import type { IDomEditor } from "@wangeditor/editor";
import "@wangeditor/editor/dist/css/style.css";
import { useRoute } from "vue-router";
import UiDataTable from "../../../components/ui/UiDataTable.vue";
import UiEmptyState from "../../../components/ui/UiEmptyState.vue";
import UiPageHeader from "../../../components/ui/UiPageHeader.vue";
import TestManagementFormDialog from "../components/TestManagementFormDialog.vue";
import { useAuthStore } from "../../../stores/auth";
import { useProjectContextStore } from "../../../stores/project-context";
import {
  deleteTestReport,
  downloadTestReport,
  generateTestReport,
  getTestReport,
  getTestReportOptions,
  getTestReportTree,
  listTestProjects,
  listTestReports,
  saveTestReportSupplement,
  type TestDomain,
  type TestReport,
  type TestReportDetail,
  type TestReportTree,
} from "../api";

type TreeNode = {
  key: string;
  label: string;
  type: "PROJECT" | "SYSTEM" | "SPECIAL" | "GROUP";
  systemId?: number;
  specialNodeId?: number;
  children?: TreeNode[];
};
const route = useRoute();
const auth = useAuthStore();
const context = useProjectContextStore();
const domain = computed(() => String(route.params.domain) as TestDomain);
const projects = ref<
  Array<{ id: number; project_code: string; project_name: string }>
>([]);
const projectId = computed(
  () => projects.value.find((x) => x.project_code === context.currentRef)?.id,
);
const projectName = computed(
  () =>
    projects.value.find((x) => x.id === projectId.value)?.project_name ||
    "当前项目",
);
const tree = ref<TestReportTree>();
const selectedNode = ref<TreeNode>({ key: "project", label: "项目级报告", type: "PROJECT" });
const rows = ref<TestReport[]>([]);
const total = ref(0);
const page = ref(1);
const keyword = ref("");
const loading = ref(false);
const generator = ref(false);
const generatorStep = ref(0);
const detailOpen = ref(false);
const detail = ref<TestReportDetail>({
  report: {} as TestReport,
  versions: [],
  version: {} as TestReportDetail["version"],
  snapshot: {},
  supplements: [],
});
const options = ref<{
  rounds: Array<{ id: number; round_name: string }>;
  cycles: Array<{ id: number; round_id: number; cycle_name: string }>;
  sections: string[];
}>({ rounds: [], cycles: [], sections: [] });
const editing = ref<TestReport>();
const editor = ref<IDomEditor>();
const supplement = reactive({ chapter_code: "OVERVIEW", content_html: "" });
const form = reactive({
  report_name: "",
  report_type: "PROJECT" as "PROJECT" | "ROUND" | "CYCLE",
  round_id: undefined as number | undefined,
  cycle_id: undefined as number | undefined,
  source_type: "LIVE",
  sections: [
    "OVERVIEW",
    "SCOPE_STAT",
    "EXECUTION_STAT",
    "DEFECT_STAT",
    "SCOPE_DETAIL",
    "DEFECT_DETAIL",
  ] as string[],
});
const domainLabel = computed(
  () =>
    (
      ({
        "application-assembly": "应用组装测试",
        "user-testing": "用户测试",
        "non-functional": "非功能测试",
        security: "安全测试",
      }) as Record<string, string>
    )[domain.value] || "测试管理",
);
const can = (action?: string) =>
  auth.hasPermission(
    `test-management:${domain.value}:reports${action ? ":" + action : ""}`,
  );
const treeData = computed<TreeNode[]>(() => [
  {
    key: "root",
    label: projectName.value,
    type: "GROUP",
    children: [
      {
        key: "project",
        label: "项目级报告",
        type: "PROJECT",
      },
      {
        key: "systems",
        label: "系统级报告",
        type: "GROUP",
        children: (tree.value?.systems || []).map((x) => ({
          key: "system:" + x.id,
          label: x.short_name || x.name,
          type: "SYSTEM",
          systemId: x.id,
        })),
      },
      {
        key: "specials",
        label: "专项报告",
        type: "GROUP",
        children: (tree.value?.specials || []).map((x) => ({
          key: "special:" + x.id,
          label: x.node_name,
          type: "SPECIAL",
          specialNodeId: x.id,
        })),
      },
    ],
  },
]);
const selectedKey = computed(() => selectedNode.value.key);
const selectedSystem = computed(() =>
  selectedNode.value.type === "SYSTEM" ? selectedNode.value.systemId : undefined,
);
const selectedSpecial = computed(() =>
  selectedNode.value.type === "SPECIAL"
    ? selectedNode.value.specialNodeId
    : undefined,
);
const selectedScope = computed<"PROJECT" | "SYSTEM" | "SPECIAL">(
  () => (selectedNode.value.type === "GROUP" ? "PROJECT" : selectedNode.value.type),
);
const selectedScopeLabel = computed(() => selectedNode.value.label);
function findReportNode(nodes: TreeNode[], key: string): TreeNode | undefined {
  for (const node of nodes) {
    if (node.key === key) return node;
    const found = node.children && findReportNode(node.children, key);
    if (found) return found;
  }
  return undefined;
}
function fail(e: any, text: string) {
  ElMessage.error(e?.response?.data?.message || text);
}
async function load() {
  if (!projectId.value) return;
  loading.value = true;
  try {
    tree.value = (await getTestReportTree(domain.value, projectId.value)).data.data;
    selectedNode.value =
      findReportNode(treeData.value, selectedNode.value.key) ||
      ({ key: "project", label: "项目级报告", type: "PROJECT" } as TreeNode);
    const l = await listTestReports(domain.value, {
      projectId: projectId.value,
      physicalSubsystemId: selectedSystem.value,
      specialNodeId: selectedSpecial.value,
      scopeType: selectedScope.value,
      keyword: keyword.value || undefined,
      page: page.value,
      size: 20,
    });
    rows.value = l.data.data.records || [];
    total.value = l.data.data.total || 0;
  } catch (e) {
    fail(e, "测试报告加载失败");
  } finally {
    loading.value = false;
  }
}
async function select(node: TreeNode) {
  if (node.type === "GROUP") return;
  selectedNode.value = node;
  page.value = 1;
  localStorage.setItem(
    "tm-report-node:" + domain.value,
    node.key,
  );
  await load();
}
async function openGenerate(item?: TestReport) {
  if (!projectId.value) return;
  editing.value = item;
  generatorStep.value = 0;
  const prefillRound = Number(route.query.roundId) || undefined;
  const prefillCycle = Number(route.query.cycleId) || undefined;
  Object.assign(form, {
    report_name: item?.report_name || "",
    report_type: item?.report_type || "PROJECT",
    round_id: item?.round_id || prefillRound,
    cycle_id: item?.cycle_id || prefillCycle,
    source_type: route.query.source === "analytics" ? "SNAPSHOT" : "LIVE",
    sections: [
      "OVERVIEW",
      "SCOPE_STAT",
      "EXECUTION_STAT",
      "DEFECT_STAT",
      "SCOPE_DETAIL",
      "DEFECT_DETAIL",
    ],
  });
  try {
    options.value = (
      await getTestReportOptions(
        domain.value,
        projectId.value,
        selectedSystem.value,
      )
    ).data.data;
    generator.value = true;
  } catch (e) {
    fail(e, "报告生成选项加载失败");
  }
}
async function generate() {
  if (
    !projectId.value ||
    !form.report_name ||
    (form.report_type !== "PROJECT" && !form.round_id) ||
    (form.report_type === "CYCLE" && !form.cycle_id)
  ) {
    ElMessage.warning("请填写报告名称并选择关联轮次/周期");
    return;
  }
  try {
    if (editing.value)
      await ElMessageBox.confirm(
        `将生成新版本 V${(editing.value.current_version_no || 0) + 1}，旧版本将保留，确认继续？`,
        "重新生成报告",
        { type: "warning" },
      );
    await generateTestReport(
      domain.value,
      projectId.value,
      {
        scopeType: selectedScope.value,
        physicalSubsystemId: selectedSystem.value,
        specialNodeId: selectedSpecial.value,
      },
      { ...form },
      editing.value?.id,
    );
    generator.value = false;
    ElMessage.success(editing.value ? "新版本已生成" : "报告已生成");
    await load();
  } catch (e: any) {
    if (e !== "cancel" && e !== "close") fail(e, "报告生成失败");
  }
}
async function advanceGenerate() {
  if (generatorStep.value === 0) {
    if (
      !form.report_name ||
      (form.report_type !== "PROJECT" && !form.round_id) ||
      (form.report_type === "CYCLE" && !form.cycle_id)
    ) {
      ElMessage.warning("请填写报告名称，并为轮次/周期报告选择关联时间范围");
      return;
    }
    generatorStep.value = 1;
    return;
  }
  if (generatorStep.value === 1) {
    if (!form.sections.length) {
      ElMessage.warning("请至少选择一个报告章节");
      return;
    }
    generatorStep.value = 2;
    return;
  }
  await generate();
}
async function openDetail(item: TestReport, versionId?: number) {
  if (!projectId.value) return;
  try {
    detail.value = (
      await getTestReport(domain.value, projectId.value, item.id, versionId)
    ).data.data;
    supplement.chapter_code = "OVERVIEW";
    supplement.content_html =
      detail.value.supplements.find((x) => x.chapter_code === "OVERVIEW")
        ?.content_html || "";
    detailOpen.value = true;
  } catch (e) {
    fail(e, "报告加载失败");
  }
}
async function switchVersion(versionId: number) {
  await openDetail(detail.value.report, versionId);
}
async function saveSupplement() {
  if (!projectId.value || !detail.value.report.id) return;
  try {
    await saveTestReportSupplement(
      domain.value,
      projectId.value,
      detail.value.report.id,
      detail.value.version.id,
      { ...supplement },
    );
    ElMessage.success("章节补充已保存");
    detail.value.supplements = detail.value.supplements
      .filter((x) => x.chapter_code !== supplement.chapter_code)
      .concat({
        chapter_code: supplement.chapter_code,
        content_html: supplement.content_html,
      });
  } catch (e) {
    fail(e, "补充说明保存失败");
  }
}
async function remove(item: TestReport) {
  if (!projectId.value) return;
  try {
    await ElMessageBox.confirm(
      `删除“${item.report_name}”将永久删除所有版本和补充说明，无法恢复。`,
      "删除报告",
      { type: "warning" },
    );
    await deleteTestReport(domain.value, projectId.value, item.id);
    ElMessage.success("报告已删除");
    await load();
  } catch (e: any) {
    if (e !== "cancel" && e !== "close") fail(e, "删除失败");
  }
}
async function download(item: TestReport, format: "docx" | "pdf") {
  if (!projectId.value) return;
  try {
    await downloadTestReport(domain.value, projectId.value, item.id, format);
  } catch (e) {
    fail(e, "导出失败");
  }
}
function chapterName(key: string) {
  return (
    (
      {
        OVERVIEW: "测试概况",
        SCOPE_STAT: "范围统计",
        EXECUTION_STAT: "执行统计",
        DEFECT_STAT: "缺陷统计",
        SCOPE_DETAIL: "范围明细",
        DEFECT_DETAIL: "缺陷明细",
      } as Record<string, string>
    )[key] || key
  );
}
onMounted(async () => {
  await context.initialize();
  projects.value = (await listTestProjects(domain.value)).data.data || [];
  const prefilled = Number(route.query.physicalSubsystemId);
  const stored = localStorage.getItem("tm-report-node:" + domain.value);
  selectedNode.value = prefilled
    ? { key: "system:" + prefilled, label: "当前系统", type: "SYSTEM", systemId: prefilled }
    : stored?.startsWith("system:")
      ? { key: stored, label: "当前系统", type: "SYSTEM", systemId: Number(stored.slice(7)) }
      : stored?.startsWith("special:")
        ? { key: stored, label: "当前专项", type: "SPECIAL", specialNodeId: Number(stored.slice(8)) }
        : { key: "project", label: "项目级报告", type: "PROJECT" };
  await load();
  if (route.query.source === "analytics") await openGenerate();
});
watch([() => context.currentRef, domain], async () => {
  selectedNode.value = { key: "project", label: "项目级报告", type: "PROJECT" };
  page.value = 1;
  await load();
});
watch(
  () => form.report_type,
  (type) => {
    if (type === "PROJECT") {
      form.round_id = undefined;
      form.cycle_id = undefined;
      form.source_type = "LIVE";
    } else if (type === "ROUND") {
      form.cycle_id = undefined;
    }
  },
);
onBeforeUnmount(() => editor.value?.destroy());
</script>
<template>
  <section class="report-page">
    <UiPageHeader eyebrow="测试管理" :title="domainLabel + ' · 测试报告'"
      ><template #actions
        ><el-button
          v-if="can('create')"
          type="primary"
          size="small"
          :icon="Plus"
          @click="openGenerate()"
          >生成报告</el-button
        ><el-tooltip content="刷新"
          ><el-button
            text
            circle
            size="small"
            :icon="Refresh"
            aria-label="刷新报告"
            @click="load" /></el-tooltip></template></UiPageHeader
    ><UiEmptyState
      v-if="!projectId"
      title="请先选择项目"
      description="请使用顶部全局项目选择器后查看测试报告。"
    />
    <section v-else class="report-workspace" v-loading="loading">
      <aside class="report-tree">
        <header>报告范围</header>
        <el-tree
          :data="treeData"
          node-key="key"
          :current-node-key="selectedKey"
          default-expand-all
          highlight-current
          :expand-on-click-node="false"
          @node-click="select"
          ><template #default="{ data }"
            ><span class="report-node"
              ><el-icon><FolderOpened /></el-icon
              ><span>{{ data.label }}</span></span
            ></template
          ></el-tree
        >
      </aside>
      <main class="report-content">
        <div class="report-toolbar">
          <el-input
            v-model="keyword"
            clearable
            size="small"
            placeholder="搜索报告名称"
            @keyup.enter="
              page = 1;
              load();
            "
            ><template #prefix><Search /></template></el-input
          ><el-button
            size="small"
            :icon="Search"
            @click="
              page = 1;
              load();
            "
            >查询</el-button
          >
        </div>
        <UiDataTable class="report-table" :data="rows" row-key="id" border
          ><el-table-column
            prop="report_name"
            label="报告名称"
            min-width="190"
            show-overflow-tooltip /><el-table-column
            prop="report_type"
            label="报告类型"
            width="92"
            align="center"
            ><template #default="{ row }">{{
              row.report_type === "PROJECT"
                ? "项目报告"
                : row.report_type === "CYCLE"
                  ? "周期报告"
                  : "轮次报告"
            }}</template></el-table-column
          ><el-table-column
            label="关联轮次/周期"
            min-width="150"
            show-overflow-tooltip
            ><template #default="{ row }"
              >{{ row.report_type === "PROJECT" ? "-" : row.round_name || "-"
              }}{{ row.cycle_name ? " / " + row.cycle_name : "" }}</template
            ></el-table-column
          ><el-table-column label="统计范围" width="100" align="center"
            ><template #default="{ row }">{{
              row.scope_type === "SPECIAL"
                ? "专项级"
                : row.scope_type === "SYSTEM"
                  ? "系统级"
                  : "项目级"
            }}</template></el-table-column
          ><el-table-column
            prop="current_version"
            label="当前版本"
            width="86"
            align="center" /><el-table-column
            prop="generator_name"
            label="生成人"
            width="90"
            align="center" /><el-table-column
            prop="generated_at"
            label="生成时间"
            min-width="130"
            ><template #default="{ row }">{{
              row.generated_at
                ? String(row.generated_at).slice(5, 16).replace("T", " ")
                : "-"
            }}</template></el-table-column
          ><el-table-column
            label="操作"
            width="158"
            fixed="right"
            align="center"
            ><template #default="{ row }"
              ><div class="report-actions">
                <el-tooltip content="查看"
                  ><el-button
                    text
                    :icon="Document"
                    aria-label="查看报告"
                    @click="openDetail(row)" /></el-tooltip
                ><el-tooltip content="Word 下载"
                  ><el-button
                    text
                    :icon="Download"
                    aria-label="下载 Word"
                    @click="download(row, 'docx')" /></el-tooltip
                ><el-tooltip v-if="can('delete')" content="删除报告"
                  ><el-button
                    text
                    type="danger"
                    :icon="Delete"
                    aria-label="删除报告"
                    @click="remove(row)" /></el-tooltip
                ><el-dropdown trigger="click"
                  ><el-button
                    text
                    :icon="Tickets"
                    aria-label="更多报告操作"
                  /><template #dropdown
                    ><el-dropdown-menu
                      ><el-dropdown-item @click="download(row, 'pdf')"
                        >下载 PDF</el-dropdown-item
                      ><el-dropdown-item @click="openDetail(row)"
                        >历史版本</el-dropdown-item
                      ><el-dropdown-item
                        v-if="can('create')"
                        @click="openGenerate(row)"
                        >重新生成</el-dropdown-item
                      ></el-dropdown-menu
                    ></template
                  ></el-dropdown
                >
              </div></template
            ></el-table-column
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
      v-model="generator"
      :title="editing ? '重新生成测试报告' : '生成测试报告'"
      width="min(700px,calc(100vw - 24px))"
      :confirm-text="
        generatorStep === 2 ? (editing ? '确认重新生成' : '确认生成') : '下一步'
      "
      @submit="advanceGenerate"
      ><el-steps :active="generatorStep" simple class="report-steps"
        ><el-step title="基本信息" /><el-step title="数据来源" /><el-step
          title="确认生成" /></el-steps
      ><el-form label-width="106px"
        ><el-form-item label="统计范围"
          ><el-input :model-value="selectedScopeLabel" disabled /></el-form-item
        ><el-form-item label="报告名称" required
          ><el-input v-model="form.report_name" maxlength="100" /></el-form-item
        ><el-form-item label="报告类型" required
          ><el-radio-group v-model="form.report_type"
            ><el-radio value="PROJECT">项目报告</el-radio
            ><el-radio value="ROUND">轮次报告</el-radio
            ><el-radio value="CYCLE">周期报告</el-radio></el-radio-group
          ></el-form-item
        ><el-form-item v-if="form.report_type !== 'PROJECT'" label="关联轮次" required
          ><el-select v-model="form.round_id" style="width: 100%"
            ><el-option
              v-for="item in options.rounds"
              :key="item.id"
              :label="item.round_name"
              :value="item.id" /></el-select></el-form-item
        ><el-form-item
          v-if="form.report_type === 'CYCLE'"
          label="关联周期"
          required
          ><el-select v-model="form.cycle_id" style="width: 100%"
            ><el-option
              v-for="item in options.cycles.filter(
                (x) => x.round_id === form.round_id,
              )"
              :key="item.id"
              :label="item.cycle_name"
              :value="item.id" /></el-select></el-form-item
        ><el-form-item label="数据来源"
          ><el-radio-group v-model="form.source_type"
            ><el-radio value="LIVE">当前实时数据</el-radio
            ><el-radio v-if="form.report_type !== 'PROJECT'" value="SNAPSHOT">轮次统计快照</el-radio></el-radio-group
          >
          <p class="form-hint">
            {{ form.report_type === 'PROJECT' ? '项目报告汇总当前项目的实时数据。' : '选择统计快照时，系统会固定使用该轮次已经归档的数据。' }}
          </p></el-form-item
        ><el-form-item label="报告章节"
          ><el-checkbox-group v-model="form.sections"
            ><el-checkbox
              v-for="item in options.sections"
              :key="item"
              :value="item"
              >{{ chapterName(item) }}</el-checkbox
            ></el-checkbox-group
          ></el-form-item
        ></el-form
      ></TestManagementFormDialog
    ><el-dialog
      v-model="detailOpen"
      width="min(1060px,calc(100vw - 24px))"
      top="4vh"
      destroy-on-close
      ><template #header
        ><div class="detail-title">
          <strong>{{ detail?.report.report_name }}</strong
          ><span>{{ detail?.report.current_version }}</span>
        </div></template
      >
      <div v-if="detail" class="report-detail">
        <aside>
          <strong>历史版本</strong
          ><el-menu :default-active="String(detail.version.id)"
            ><el-menu-item
              v-for="item in detail.versions"
              :key="item.id"
              :index="String(item.id)"
              @click="switchVersion(item.id)"
              >{{ "V" + item.version_no }} ·
              {{
                item.generated_at ? String(item.generated_at).slice(5, 16) : ""
              }}</el-menu-item
            ></el-menu
          >
        </aside>
        <main>
          <section class="facts">
            <el-descriptions :column="4" border size="small"
              ><el-descriptions-item label="范围数量">{{
                detail.snapshot.scope_total || 0
              }}</el-descriptions-item
              ><el-descriptions-item label="案例数量">{{
                detail.snapshot.case_total || 0
              }}</el-descriptions-item
              ><el-descriptions-item label="执行成功率"
                >{{ detail.snapshot.success_rate || 0 }}%</el-descriptions-item
              ><el-descriptions-item label="未关闭缺陷">{{
                detail.snapshot.defect_open || 0
              }}</el-descriptions-item></el-descriptions
            >
          </section>
          <section
            v-for="chapter in [
              'OVERVIEW',
              'SCOPE_STAT',
              'EXECUTION_STAT',
              'DEFECT_STAT',
              'SCOPE_DETAIL',
              'DEFECT_DETAIL',
            ]"
            :key="chapter"
            class="chapter"
          >
            <header>
              <strong>{{ chapterName(chapter) }}</strong
              ><el-button
                text
                size="small"
                :icon="Edit"
                @click="
                  supplement.chapter_code = chapter;
                  supplement.content_html =
                    detail.supplements.find((x) => x.chapter_code === chapter)
                      ?.content_html || '';
                "
                >编辑补充</el-button
              >
            </header>
            <p v-if="chapter === 'OVERVIEW'">
              快照时间：{{ detail.snapshot.snapshot_at || "-" }}；范围
              {{ detail.snapshot.scope_total || 0 }}，案例
              {{ detail.snapshot.case_total || 0 }}。
            </p>
            <p v-else-if="chapter === 'EXECUTION_STAT'">
              执行 {{ detail.snapshot.execution_total || 0 }}，成功
              {{ detail.snapshot.execution_success || 0 }}，失败
              {{ detail.snapshot.execution_failed || 0 }}，阻塞
              {{ detail.snapshot.execution_blocked || 0 }}。
            </p>
            <p v-else-if="chapter === 'DEFECT_STAT'">
              缺陷 {{ detail.snapshot.defect_total || 0 }}，未关闭
              {{ detail.snapshot.defect_open || 0 }}。
            </p>
            <div
              v-if="
                detail.supplements.find((x) => x.chapter_code === chapter)
                  ?.content_html
              "
              class="supplement-view"
              v-html="
                detail.supplements.find((x) => x.chapter_code === chapter)
                  ?.content_html
              "
            />
          </section>
          <section class="supplement-edit">
            <header>
              编辑 {{ chapterName(supplement.chapter_code) }} 补充说明
            </header>
            <Toolbar :editor="editor" mode="default" /><WangEditor
              v-model="supplement.content_html"
              class="supplement-editor"
              mode="default"
              @on-created="(value: IDomEditor) => (editor = value)"
            />
            <div class="supplement-save">
              <el-button type="primary" size="small" @click="saveSupplement"
                >保存补充说明</el-button
              >
            </div>
          </section>
        </main>
      </div>
      <template #footer
        ><el-button @click="detailOpen = false">关闭</el-button
        ><el-button type="primary" @click="download(detail.report, 'docx')"
          >下载 Word</el-button
        ><el-button @click="download(detail.report, 'pdf')"
          >下载 PDF</el-button
        ></template
      ></el-dialog
    >
  </section>
</template>
<style scoped>
.report-page {
  min-width: 0;
  max-width: 1440px;
  margin: 0 auto;
}
.report-workspace {
  display: grid;
  grid-template-columns: 248px minmax(0, 1fr);
  min-height: 590px;
  overflow: hidden;
  border: 1px solid var(--line);
  border-radius: 6px;
  background: var(--panel-bg);
}
.report-tree {
  padding: 9px;
  overflow: auto;
  border-right: 1px solid var(--line);
  background: color-mix(in srgb, var(--panel-muted) 58%, var(--panel-bg));
}
.report-tree > header {
  padding: 0 5px 7px;
  font-size: 13px;
  font-weight: 650;
}
.report-tree :deep(.el-tree) {
  background: transparent;
  font-size: 12px;
}
.report-tree :deep(.el-tree-node__content) {
  height: 29px;
  border-radius: 4px;
}
.report-tree :deep(.el-tree-node.is-current > .el-tree-node__content) {
  color: var(--brand-strong);
  background: color-mix(in srgb, var(--brand) 14%, var(--panel-bg));
  font-weight: 650;
}
.report-node {
  display: flex;
  gap: 5px;
  align-items: center;
  min-width: 0;
}
.report-content {
  min-width: 0;
  padding: 10px;
}
.report-toolbar {
  display: flex;
  gap: 6px;
  margin-bottom: 8px;
}
.report-toolbar .el-input {
  width: 220px;
}
.report-table :deep(.el-table__cell),
.report-table :deep(.el-table__header th .cell) {
  font-size: 12px;
  white-space: nowrap;
}
.report-actions {
  display: flex;
  justify-content: center;
  gap: 0;
}
.report-actions .el-button {
  min-width: 24px;
  height: 24px;
  margin: 0;
  padding: 0 3px;
}
.report-steps {
  margin-bottom: 16px;
}
.form-hint {
  margin: 6px 0 0;
  color: var(--text-muted);
  font-size: 12px;
  line-height: 1.5;
}
.detail-title {
  display: flex;
  align-items: center;
  gap: 9px;
}
.detail-title span {
  color: var(--text-muted);
  font-size: 12px;
}
.report-detail {
  display: grid;
  grid-template-columns: 174px minmax(0, 1fr);
  max-height: 70vh;
}
.report-detail > aside {
  padding: 4px 10px;
  border-right: 1px solid var(--line);
  overflow: auto;
  font-size: 12px;
}
.report-detail > main {
  overflow: auto;
  padding: 0 16px;
}
.facts {
  margin: 0 0 12px;
}
.chapter {
  padding: 9px 0;
  border-bottom: 1px solid var(--line);
}
.chapter header {
  display: flex;
  justify-content: space-between;
}
.chapter p {
  margin: 7px 0;
  color: var(--text-muted);
  font-size: 12px;
}
.supplement-view {
  font-size: 13px;
}
.supplement-edit {
  margin-top: 14px;
  border: 1px solid var(--line);
  border-radius: 5px;
}
.supplement-edit > header {
  padding: 8px 10px;
  font-size: 13px;
  font-weight: 650;
}
.supplement-editor {
  min-height: 260px;
}
.supplement-save {
  padding: 8px;
  text-align: right;
}
@media (max-width: 760px) {
  .report-workspace {
    display: block;
    overflow: visible;
    border: 0;
    background: transparent;
  }
  .report-tree {
    max-height: 240px;
    margin-bottom: 9px;
    border: 1px solid var(--line);
    border-radius: 6px;
  }
  .report-content {
    padding: 9px;
    border: 1px solid var(--line);
    border-radius: 6px;
  }
  .report-detail {
    display: block;
  }
  .report-detail > aside {
    max-height: 120px;
    border-right: 0;
    border-bottom: 1px solid var(--line);
  }
  .report-detail > main {
    padding: 10px;
  }
  .report-toolbar .el-input {
    flex: 1;
    width: auto;
  }
}
</style>
