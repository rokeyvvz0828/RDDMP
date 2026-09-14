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
  Upload,
} from "@element-plus/icons-vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { Editor as WangEditor, Toolbar } from "@wangeditor/editor-for-vue";
import type { IDomEditor } from "@wangeditor/editor";
import type { EChartsOption } from "echarts";
import "@wangeditor/editor/dist/css/style.css";
import { useRoute } from "vue-router";
import UiDataTable from "../../../components/ui/UiDataTable.vue";
import UiEmptyState from "../../../components/ui/UiEmptyState.vue";
import UiPageHeader from "../../../components/ui/UiPageHeader.vue";
import TestManagementFormDialog from "../components/TestManagementFormDialog.vue";
import TestAnalyticsChart from "../analytics/TestAnalyticsChart.vue";
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
  uploadTestReport,
  uploadTestReportVersion,
  type TestDomain,
  type TestReport,
  type TestReportDetail,
  type TestReportTree,
} from "../api";
import { getAttachmentDownload, uploadAttachment } from "../../../api/attachments";

type TreeNode = {
  key: string;
  label: string;
  type: "PROJECT" | "INSTITUTION" | "SYSTEM" | "SPECIAL" | "GROUP";
  systemId?: number;
  responsibleTeamOrgId?: number;
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
const uploadOpen = ref(false);
const uploadSaving = ref(false);
const uploadingVersionOf = ref<TestReport>();
const uploadForm = reactive({ report_name: "", version_note: "", file: undefined as File | undefined });
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
const supplementOpen = ref(false);
const supplementSaving = ref(false);
const readerMain = ref<HTMLElement>();
const chapters = [
  "OVERVIEW",
  "ENVIRONMENT_CONFIG",
  "SCOPE_STRATEGY",
  "EXECUTION_PROGRESS",
  "DEFECT_ANALYSIS",
  "QUALITY_ASSESSMENT",
  "RISKS_ISSUES",
  "CONCLUSION_RECOMMENDATION",
];
// 新格式将整段章节正文存入既有补充字段；标记使历史“追加补充”仍能兼容展示。
const chapterContentMarker = "<!-- tm-report-chapter-content-v2 -->";
const form = reactive({
  report_name: "",
  report_type: "LIFECYCLE" as "LIFECYCLE" | "ROUND",
  round_id: undefined as number | undefined,
  cycle_id: undefined as number | undefined,
  source_type: "LIVE",
  sections: [
    "OVERVIEW",
    "ENVIRONMENT_CONFIG",
    "SCOPE_STRATEGY",
    "EXECUTION_PROGRESS",
    "DEFECT_ANALYSIS",
    "QUALITY_ASSESSMENT",
    "RISKS_ISSUES",
    "CONCLUSION_RECOMMENDATION",
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
        key: "institutions",
        label: "责任团队组织报告",
        type: "GROUP",
        children: (tree.value?.institutions || []).map((x) => ({
          key: "institution:" + x.id,
          label: x.name,
          type: "INSTITUTION",
          responsibleTeamOrgId: x.id,
        })),
      },
      {
        key: "systems",
        label: "系统报告",
        type: "GROUP",
        children: (tree.value?.systems || []).map((x) => ({
          key: "system:" + x.id,
          label: x.short_name || x.name,
          type: "SYSTEM",
          systemId: x.id,
        })),
      },
      {
        key: "plan-directories",
        label: "专项报告",
        type: "GROUP",
        children: (tree.value?.planDirectories || []).map((x) => ({
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
const selectedTeam = computed(() =>
  selectedNode.value.type === "INSTITUTION"
    ? selectedNode.value.responsibleTeamOrgId
    : undefined,
);
const selectedSpecial = computed(() =>
  selectedNode.value.type === "SPECIAL"
    ? selectedNode.value.specialNodeId
    : undefined,
);
const selectedScope = computed<"PROJECT" | "INSTITUTION" | "SYSTEM" | "SPECIAL">(
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
      responsibleTeamOrgId: selectedTeam.value,
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
  if (selectedNode.value.type === "SPECIAL") {
    ElMessage.warning("专项报告目录仅展示已有归档报告；新报告请在项目、责任团队组织或系统范围生成");
    return;
  }
  editing.value = item;
  const prefillRound = Number(route.query.roundId) || undefined;
  const prefillCycle = Number(route.query.cycleId) || undefined;
  Object.assign(form, {
    report_name: item?.report_name || "",
    report_type: item?.report_type === "ROUND" ? "ROUND" : "LIFECYCLE",
    round_id: item?.round_id || prefillRound,
    cycle_id: item?.cycle_id || prefillCycle,
    source_type: route.query.source === "analytics" ? "SNAPSHOT" : "LIVE",
    sections: [
      "OVERVIEW",
      "ENVIRONMENT_CONFIG",
      "SCOPE_STRATEGY",
      "EXECUTION_PROGRESS",
      "DEFECT_ANALYSIS",
      "QUALITY_ASSESSMENT",
      "RISKS_ISSUES",
      "CONCLUSION_RECOMMENDATION",
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
function openUpload(item?: TestReport) {
  if (!projectId.value) return;
  if (!item && selectedNode.value.type === "SPECIAL") {
    ElMessage.warning("专项报告目录仅展示与导出已有报告，不能上传新报告");
    return;
  }
  uploadingVersionOf.value = item;
  Object.assign(uploadForm, { report_name: item?.report_name || "", version_note: "", file: undefined });
  uploadOpen.value = true;
}
function chooseManualFile(file: { raw?: File }) {
  const selected = file.raw;
  if (!selected) return;
  const extension = selected.name.split(".").pop()?.toLowerCase();
  if (!extension || !["docx", "xlsx"].includes(extension)) {
    ElMessage.warning("仅支持 .docx 或 .xlsx 格式的报告文件");
    return;
  }
  if (selected.size > 50 * 1024 * 1024) {
    ElMessage.warning("单个报告文件不能超过 50MB");
    return;
  }
  uploadForm.file = selected;
}
async function submitUpload(confirmVersion = false, existingAttachmentId?: number) {
  if (!projectId.value || !uploadForm.file || !uploadForm.version_note || (!uploadingVersionOf.value && !uploadForm.report_name)) {
    ElMessage.warning("请填写报告名称、版本说明并选择报告文件");
    return;
  }
  uploadSaving.value = true;
  try {
    const attachmentId = existingAttachmentId || (await uploadAttachment(uploadForm.file)).data.data.id;
    const payload = { attachment_id: attachmentId, version_note: uploadForm.version_note, report_name: uploadForm.report_name, confirm_version: confirmVersion };
    const result = uploadingVersionOf.value
      ? await uploadTestReportVersion(domain.value, projectId.value, uploadingVersionOf.value.id, payload)
      : await uploadTestReport(domain.value, projectId.value, { scopeType: selectedScope.value, physicalSubsystemId: selectedSystem.value, responsibleTeamOrgId: selectedTeam.value, specialNodeId: selectedSpecial.value }, payload);
    if (result.data.data.version_confirmation_required) {
      await ElMessageBox.confirm(`“${result.data.data.report_name}”已有手工报告，将作为 V${result.data.data.next_version} 保存，确认继续？`, "上传新版本", { type: "warning" });
      await submitUpload(true, attachmentId);
      return;
    }
    uploadOpen.value = false;
    ElMessage.success(uploadingVersionOf.value ? "报告新版本已上传" : "报告已上传");
    await load();
  } catch (e: any) {
    if (e !== "cancel" && e !== "close") fail(e, "报告上传失败");
  } finally {
    uploadSaving.value = false;
  }
}
async function generate() {
  if (
    !projectId.value ||
    !form.report_name ||
    (form.report_type === "ROUND" && !form.round_id) ||
    !form.sections.length
  ) {
    ElMessage.warning("请填写报告名称，选择轮次报告的关联轮次，并至少保留一个报告章节");
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
        responsibleTeamOrgId: selectedTeam.value,
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
  supplementSaving.value = true;
  try {
    const contentHtml = `${chapterContentMarker}${supplement.content_html.trim()}`;
    await saveTestReportSupplement(
      domain.value,
      projectId.value,
      detail.value.report.id,
      detail.value.version.id,
      { chapter_code: supplement.chapter_code, content_html: contentHtml },
    );
    detail.value.supplements = detail.value.supplements
      .filter((x) => x.chapter_code !== supplement.chapter_code)
      .concat({
        chapter_code: supplement.chapter_code,
        content_html: contentHtml,
      });
    supplementOpen.value = false;
    ElMessage.success("章节正文已保存并在当前版本生效");
  } catch (e) {
    fail(e, "章节正文保存失败");
  } finally {
    supplementSaving.value = false;
  }
}
function openSupplement(chapter: string) {
  supplement.chapter_code = chapter;
  const stored = String(supplementFor(chapter)?.content_html || "");
  supplement.content_html = stored.startsWith(chapterContentMarker)
    ? stored.slice(chapterContentMarker.length)
    : `<p>${chapterSummary(chapter)}</p>${stored ? `<p><br></p>${stored}` : ""}`;
  supplementOpen.value = true;
}
function supplementFor(chapter: string) {
  return detail.value.supplements.find((x) => x.chapter_code === chapter);
}
function hasChapterContentOverride(chapter: string) {
  return String(supplementFor(chapter)?.content_html || "").startsWith(
    chapterContentMarker,
  );
}
function chapterNarrative(chapter: string) {
  const content = String(supplementFor(chapter)?.content_html || "");
  return hasChapterContentOverride(chapter)
    ? content.slice(chapterContentMarker.length)
    : "";
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
async function downloadManual(item: TestReport) {
  if (!projectId.value) return;
  try {
    const report = item.id === detail.value.report.id ? detail.value : (await getTestReport(domain.value, projectId.value, item.id)).data.data;
    const file = report.manual_file;
    if (!file?.attachment_id || !file.file_name) throw new Error("未找到当前版本的报告文件");
    const response = await getAttachmentDownload(file.attachment_id);
    const link = document.createElement("a");
    link.href = response.data.data.downloadUrl;
    link.download = file.file_name;
    link.click();
  } catch (e) {
    fail(e, "报告下载失败");
  }
}
function chapterName(key: string) {
  return (
    (
      {
        OVERVIEW: "报告概述",
        ENVIRONMENT_CONFIG: "测试环境与配置",
        SCOPE_STRATEGY: "测试范围与策略",
        EXECUTION_PROGRESS: "测试执行与进度",
        DEFECT_ANALYSIS: "缺陷分析",
        QUALITY_ASSESSMENT: "质量评估",
        RISKS_ISSUES: "风险与问题",
        CONCLUSION_RECOMMENDATION: "结论与建议",
      } as Record<string, string>
    )[key] || key
  );
}
function fact(name: string) {
  return detail.value.snapshot?.[name] ?? 0;
}
function numberFact(name: string) {
  const value = Number(fact(name));
  return Number.isFinite(value) ? value : 0;
}
function percent(value: unknown) {
  const numeric = Number(value);
  return `${Number.isFinite(numeric) ? numeric : 0}%`;
}
function integerPercent(numerator: number, denominator: number) {
  return denominator > 0 ? Math.round((numerator / denominator) * 100) : 0;
}
function executionOutlook() {
  const remaining =
    numberFact("execution_unexecuted") + numberFact("execution_in_progress");
  const rate = numberFact("execution_rate");
  if (remaining === 0) return "执行已完成，可聚焦复测结论和质量收尾。";
  if (rate >= 80) return `执行已进入收尾阶段，仍有 ${remaining} 条案例待完成或确认结果。`;
  return `执行尚未完成，仍有 ${remaining} 条案例待完成或确认结果，应优先清除阻塞项。`;
}
function qualityOutlook() {
  const items = qualityItems();
  if (!items.length) return "尚未启用质量阈值，当前不能形成量化质量结论。";
  const qualified = items.filter((item) => item.result === "达标").length;
  const risk = items.filter((item) => item.result === "风险").length;
  const failed = items.length - qualified - risk;
  return `已启用 ${items.length} 项质量指标，其中 ${qualified} 项达标、${risk} 项风险、${failed} 项不达标。`;
}
function scopeName() {
  const report = detail.value.report;
  if (report.scope_type === "SYSTEM") return report.physical_system_name || "系统级";
  if (report.scope_type === "INSTITUTION") return report.responsible_team_name || "责任团队组织级";
  if (report.scope_type === "SPECIAL") return report.special_name || "专项级";
  return "项目级";
}
function formatTime(value: unknown) {
  return value ? String(value).replace("T", " ").slice(0, 16) : "-";
}
function reportValue(value: unknown): string {
  return String(({ UNEXECUTED: "未执行", IN_PROGRESS: "执行中", RUNNING: "执行中", SUCCESS: "成功", FAILED: "失败", BLOCKED: "阻塞", RAISED: "已提出", ANALYZING: "分析中", RESOLVED: "已解决", CLOSED: "已关闭", FATAL: "致命", SERIOUS: "严重", NORMAL: "一般", MINOR: "轻微", S1: "严重", S2: "高", S3: "一般", S4: "轻微" } as Record<string, string>)[String(value)] || value || "-");
}
function chapterSummary(chapter: string) {
  const effective = numberFact("effective_case_total");
  const caseTotal = numberFact("case_total");
  const executionTotal = numberFact("execution_total");
  const executionRate = numberFact("execution_rate");
  const failedAndBlocked =
    numberFact("execution_failed") + numberFact("execution_blocked");
  const defectTotal = numberFact("defect_total");
  const defectOpen = numberFact("defect_open");
  const severeDefect = numberFact("severe_defect_count");
  switch (chapter) {
    case "OVERVIEW":
      return `当前${scopeName()}已覆盖 ${fact("scope_total")} 个测试范围和 ${caseTotal} 条案例，其中有效案例 ${effective} 条。已执行 ${executionTotal} 条，执行率 ${percent(executionRate)}；${executionOutlook()}`;
    case "ENVIRONMENT_CONFIG":
      return `当前范围已形成 ${executionTotal} 条可用于质量判断的执行结果，占有效案例的 ${percent(executionRate)}。${numberFact("execution_in_progress") > 0 ? `仍有 ${numberFact("execution_in_progress")} 条案例处于执行中，环境和依赖条件需持续保持稳定。` : "执行过程未遗留进行中案例，当前环境条件能够支撑后续复测与收尾。"}`;
    case "SCOPE_STRATEGY":
      return `纳入范围的 ${caseTotal} 条案例中，有效案例占 ${percent(integerPercent(effective, caseTotal))}，无效案例 ${numberFact("invalid_case_total")} 条。有效案例覆盖 ${fact("scope_total")} 个测试范围，当前范围具备开展执行与质量评估的基础。`;
    case "EXECUTION_PROGRESS":
      return `已执行案例中成功 ${numberFact("execution_success")} 条，已执行案例成功率 ${percent(fact("executed_case_success_rate"))}。失败和阻塞合计 ${failedAndBlocked} 条，${failedAndBlocked > 0 ? "这些事项是拉低当前执行质量的直接因素，应优先定位原因并推进复测。" : "当前未出现失败或阻塞案例，执行结果整体稳定。"} ${executionOutlook()}`;
    case "DEFECT_ANALYSIS":
      return `当前发现缺陷 ${defectTotal} 个，其中 ${defectOpen} 个尚未关闭，占比 ${percent(integerPercent(defectOpen, defectTotal))}；缺陷密度为 ${percent(fact("defect_density"))}。${severeDefect > 0 ? `存在 ${severeDefect} 个严重缺陷，应纳入最高优先级处置并验证修复效果。` : "当前无严重缺陷，处置重点应放在未关闭问题的按期收敛。"}`;
    case "QUALITY_ASSESSMENT": {
      const quality = detail.value.snapshot?.quality_assessment as Record<string, unknown> | undefined;
      return quality?.overall === "未配置" ? qualityOutlook() : `${qualityOutlook()} 综合判定为“${quality?.overall || "未配置"}”，质量改进应优先围绕风险和不达标指标展开。`;
    }
    case "RISKS_ISSUES":
      return `当前主要风险集中在 ${numberFact("execution_failed")} 条失败案例、${numberFact("execution_blocked")} 条阻塞案例和 ${defectOpen} 个未关闭缺陷。${severeDefect > 0 ? "严重缺陷可能影响上线判断，应由责任团队明确处置时限。" : "建议按失败、阻塞、未关闭缺陷的优先级建立闭环跟踪，并在复测后更新结论。"}`;
    case "CONCLUSION_RECOMMENDATION": {
      const quality = detail.value.snapshot?.quality_assessment as Record<string, unknown> | undefined;
      return quality?.overall === "达标" ? `当前执行率 ${percent(executionRate)}，质量指标均达标；建议完成剩余 ${numberFact("execution_unexecuted") + numberFact("execution_in_progress")} 条案例的执行确认后按计划收尾。` : `当前质量结论为“${quality?.overall || "未配置"}”，失败或阻塞案例 ${failedAndBlocked} 条、未关闭缺陷 ${defectOpen} 个；建议优先完成问题处置和复测，再据结果更新质量结论。`;
    }
    default:
      return "";
  }
}
function qualityItems() {
  const assessment = detail.value.snapshot?.quality_assessment as Record<string, unknown> | undefined;
  return Array.isArray(assessment?.items) ? assessment.items as Array<Record<string, unknown>> : [];
}
const qualityAssessment = computed(() =>
  (detail.value.snapshot?.quality_assessment || {}) as Record<string, unknown>,
);
const qualityOverall = computed(() => String(qualityAssessment.value.overall || "未配置"));
const qualityTagType = computed(() =>
  qualityOverall.value === "达标"
    ? "success"
    : qualityOverall.value === "风险"
      ? "warning"
      : qualityOverall.value === "不达标"
        ? "danger"
        : "info",
);
const executionStatusItems = computed(() => [
  { name: "未执行", value: numberFact("execution_unexecuted") },
  { name: "执行中", value: numberFact("execution_in_progress") },
  { name: "成功", value: numberFact("execution_success") },
  { name: "失败", value: numberFact("execution_failed") },
  { name: "阻塞", value: numberFact("execution_blocked") },
]);
function distributionItems(name: string) {
  const raw = detail.value.snapshot?.[name];
  return Array.isArray(raw)
    ? raw.map((item) => ({
        name: reportValue((item as Record<string, unknown>).code),
        value: Number((item as Record<string, unknown>).value) || 0,
      }))
    : [];
}
const defectStatusItems = computed(() => distributionItems("defect_status_distribution"));
const defectSeverityItems = computed(() => distributionItems("defect_severity_distribution"));
const qualityResultItems = computed(() => distributionItems("quality_result_distribution"));
const executionOption = computed<EChartsOption>(() => ({
  tooltip: { trigger: "item", formatter: "{b}<br/>{c} 条（{d}%）" },
  legend: { bottom: 0, type: "scroll" },
  series: [{ type: "pie", radius: ["45%", "70%"], center: ["50%", "43%"], label: { formatter: "{b}\n{c} 条" }, data: executionStatusItems.value }],
}));
const scopeOption = computed<EChartsOption>(() => ({
  tooltip: { trigger: "axis" },
  grid: { left: 42, right: 20, top: 28, bottom: 34 },
  xAxis: { type: "category", data: ["有效案例", "无效案例"] },
  yAxis: { type: "value", minInterval: 1 },
  series: [{ name: "案例数", type: "bar", barMaxWidth: 48, data: [numberFact("effective_case_total"), numberFact("invalid_case_total")], itemStyle: { borderRadius: [5, 5, 0, 0] } }],
}));
function distributionOption(items: Array<{ name: string; value: number }>): EChartsOption {
  return {
    tooltip: { trigger: "item", formatter: "{b}<br/>{c} 项（{d}%）" },
    legend: { bottom: 0, type: "scroll" },
    series: [{ type: "pie", radius: ["42%", "68%"], center: ["50%", "43%"], label: { formatter: "{b}\n{c}" }, data: items }],
  };
}
const defectStatusOption = computed(() => distributionOption(defectStatusItems.value));
const defectSeverityOption = computed(() => distributionOption(defectSeverityItems.value));
const qualityResultOption = computed(() => distributionOption(qualityResultItems.value));
const riskItems = computed(() => [
  { label: "失败案例", value: numberFact("execution_failed"), text: "需安排复测并确认失败原因。" },
  { label: "阻塞案例", value: numberFact("execution_blocked"), text: "需明确依赖方和解除计划。" },
  { label: "未关闭缺陷", value: numberFact("defect_open"), text: "需持续跟踪处置与验证结论。" },
  { label: "严重缺陷", value: numberFact("severe_defect_count"), text: "需优先处理并评估发布影响。" },
].filter((item) => item.value > 0));
function scrollToChapter(chapter: string) {
  const target = document.getElementById(`report-chapter-${chapter}`);
  const container = readerMain.value;
  if (!target || !container) return;
  container.scrollTo({
    top: target.getBoundingClientRect().top - container.getBoundingClientRect().top + container.scrollTop - 12,
    behavior: "smooth",
  });
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
      : stored?.startsWith("institution:")
        ? { key: stored, label: "当前责任团队组织", type: "INSTITUTION", responsibleTeamOrgId: Number(stored.slice(12)) }
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
    if (type === "LIFECYCLE") {
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
        ><el-button
          v-if="can('create')"
          size="small"
          :icon="Upload"
          @click="openUpload()"
          >上传报告</el-button
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
              row.report_type === "MANUAL" ? "手工上传" : row.report_type === "LIFECYCLE" || row.report_type === "PROJECT"
                ? "全周期报告"
                : row.report_type === "CYCLE" ? "历史周期报告" : "轮次报告"
            }}</template></el-table-column
          ><el-table-column
            label="关联轮次/周期"
            min-width="150"
            show-overflow-tooltip
            ><template #default="{ row }"
              >{{ row.report_type === "MANUAL" || row.report_type === "LIFECYCLE" || row.report_type === "PROJECT" ? "-" : row.round_name || "-"
              }}{{ row.cycle_name ? " / " + row.cycle_name : "" }}</template
            ></el-table-column
          ><el-table-column label="统计范围" width="100" align="center"
            ><template #default="{ row }">{{
              row.scope_type === "SPECIAL"
                ? "专项级"
                : row.scope_type === "INSTITUTION"
                  ? "机构级"
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
            :label="'上传人/生成人'"
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
                ><el-tooltip :content="row.source_type === 'MANUAL' ? '下载报告' : 'Word 下载'"
                  ><el-button
                    text
                    :icon="Download"
                    :aria-label="row.source_type === 'MANUAL' ? '下载报告' : '下载 Word'"
                    @click="row.source_type === 'MANUAL' ? downloadManual(row) : download(row, 'docx')" /></el-tooltip
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
                      ><el-dropdown-item v-if="row.source_type !== 'MANUAL'" @click="download(row, 'pdf')"
                        >下载 PDF</el-dropdown-item
                      ><el-dropdown-item @click="openDetail(row)"
                        >历史版本</el-dropdown-item
                      ><el-dropdown-item v-if="row.source_type === 'MANUAL' && can('update')" @click="openUpload(row)"
                        >上传新版本</el-dropdown-item
                      ><el-dropdown-item
                        v-if="row.source_type !== 'MANUAL' && can('create')"
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
      :confirm-text="editing ? '重新生成报告' : '生成报告'"
      @submit="generate"
      ><el-form label-width="106px"
        ><el-form-item label="统计范围"
          ><el-input :model-value="selectedScopeLabel" disabled /></el-form-item
        ><el-form-item label="报告名称" required
          ><el-input v-model="form.report_name" maxlength="100" /></el-form-item
        ><el-form-item label="报告类型" required
          ><el-radio-group v-model="form.report_type"
            ><el-radio value="LIFECYCLE">全周期报告</el-radio
            ><el-radio value="ROUND">轮次报告</el-radio
            ></el-radio-group
          ></el-form-item
        ><el-form-item v-if="form.report_type === 'ROUND'" label="关联轮次" required
          ><el-select v-model="form.round_id" style="width: 100%"
            ><el-option
              v-for="item in options.rounds"
              :key="item.id"
              :label="item.round_name"
              :value="item.id" /></el-select></el-form-item
        ><el-form-item label="数据来源"
          ><el-radio-group v-model="form.source_type"
            ><el-radio value="LIVE">当前实时数据</el-radio
            ><el-radio v-if="form.report_type === 'ROUND' && selectedScope === 'PROJECT'" value="SNAPSHOT">轮次统计快照</el-radio></el-radio-group
          >
          <p class="form-hint">
            {{ form.report_type === 'LIFECYCLE' ? '全周期报告汇总当前范围的实时数据。' : '选择统计快照时，系统会固定使用该轮次已经归档的数据。' }}
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
    ><TestManagementFormDialog
      v-model="uploadOpen"
      :title="uploadingVersionOf ? '上传测试报告新版本' : '上传测试报告'"
      width="min(620px,calc(100vw - 24px))"
      :confirm-text="uploadingVersionOf ? '上传新版本' : '上传报告'"
      :loading="uploadSaving"
      @submit="() => submitUpload()"
      ><el-form label-width="96px"
        ><el-form-item label="统计范围"
          ><el-input :model-value="uploadingVersionOf ? scopeName() : selectedScopeLabel" disabled /></el-form-item
        ><el-form-item v-if="!uploadingVersionOf" label="报告名称" required
          ><el-input v-model="uploadForm.report_name" maxlength="100" placeholder="请输入报告名称" /></el-form-item
        ><el-form-item v-else label="报告名称"><el-input :model-value="uploadingVersionOf.report_name" disabled /></el-form-item
        ><el-form-item label="版本说明" required
          ><el-input v-model="uploadForm.version_note" maxlength="200" show-word-limit type="textarea" :rows="3" placeholder="说明本次报告的内容或版本变更" /></el-form-item
        ><el-form-item label="报告文件" required
          ><div class="manual-upload"><el-upload :auto-upload="false" :show-file-list="false" accept=".docx,.xlsx" :on-change="chooseManualFile"><el-button :icon="Upload">选择文件</el-button></el-upload><span>{{ uploadForm.file?.name || '仅支持 .docx、.xlsx，单个文件不超过 50MB' }}</span></div></el-form-item
        ></el-form
      ></TestManagementFormDialog
    ><el-dialog
      v-model="detailOpen"
      class="report-reader-dialog"
      width="min(1280px,calc(100vw - 24px))"
      top="2vh"
      destroy-on-close
      ><template #header><div class="detail-title"><span>测试报告</span><strong>{{ detail?.report.report_name }}</strong></div></template>
      <div v-if="detail?.report.source_type === 'MANUAL'" class="report-reader report-reader--manual">
        <aside class="reader-aside">
          <div class="reader-aside__group"><span>报告版本</span><el-menu :default-active="String(detail.version.id)">
            <el-menu-item v-for="item in detail.versions" :key="item.id" :index="String(item.id)" @click="switchVersion(item.id)">
              <b>V{{ item.version_no }}</b><small>{{ formatTime(item.generated_at) }}</small>
            </el-menu-item>
          </el-menu></div>
        </aside>
        <main class="reader-main manual-reader-main">
          <section class="manual-report-card">
            <el-tag type="info" effect="plain">手工上传报告</el-tag>
            <h2>{{ detail.report.report_name }}</h2>
            <p>该报告由用户手工上传，系统保留文件与版本记录，不补充生成统计快照、图表或质量判定。</p>
            <el-descriptions :column="1" border>
              <el-descriptions-item label="统计范围">{{ scopeName() }}</el-descriptions-item>
              <el-descriptions-item label="当前版本">V{{ detail.version.version_no }}</el-descriptions-item>
              <el-descriptions-item label="上传时间">{{ formatTime(detail.version.generated_at) }}</el-descriptions-item>
              <el-descriptions-item label="版本说明">{{ detail.manual_file?.version_note || '-' }}</el-descriptions-item>
              <el-descriptions-item label="报告文件">{{ detail.manual_file?.file_name || '-' }}</el-descriptions-item>
            </el-descriptions>
            <div class="manual-report-card__actions"><el-button type="primary" :icon="Download" @click="downloadManual(detail.report)">下载报告</el-button><el-button v-if="can('update')" :icon="Upload" @click="openUpload(detail.report)">上传新版本</el-button></div>
          </section>
        </main>
      </div>
      <div v-else-if="detail" class="report-reader">
        <aside class="reader-aside">
          <div class="reader-aside__group"><span>报告版本</span><el-menu :default-active="String(detail.version.id)">
            <el-menu-item v-for="item in detail.versions" :key="item.id" :index="String(item.id)" @click="switchVersion(item.id)">
              <b>V{{ item.version_no }}</b><small>{{ formatTime(item.generated_at) }}</small>
            </el-menu-item>
          </el-menu></div>
          <nav class="reader-aside__group" aria-label="报告章节"><span>报告目录</span><el-button v-for="chapter in chapters" :key="chapter" text @click="scrollToChapter(chapter)">{{ chapterName(chapter) }}</el-button></nav>
        </aside>
        <main ref="readerMain" class="reader-main">
          <section class="report-cover">
            <div><p class="report-cover__eyebrow">{{ domainLabel }} · {{ scopeName() }}</p><h2>{{ detail.report.report_name }}</h2></div>
            <div class="report-cover__quality"><span>总体质量结论</span><el-tag :type="qualityTagType" effect="dark" size="large">{{ qualityOverall }}</el-tag></div>
          </section>
          <section class="report-meta"><span>当前版本：V{{ detail.version.version_no }}</span><span>报告类型：{{ detail.report.report_type === 'ROUND' ? '轮次报告' : '全周期报告' }}</span><span>关联轮次：{{ detail.report.round_name || '-' }}</span><span>生成时间：{{ formatTime(detail.version.generated_at) }}</span></section>
          <section v-for="chapter in chapters" :id="`report-chapter-${chapter}`" :key="chapter" class="chapter">
            <header class="chapter__header"><div><span class="chapter__index">{{ String(chapters.indexOf(chapter) + 1).padStart(2, '0') }}</span><h3>{{ chapterName(chapter) }}</h3></div><el-button v-if="can('update')" text size="small" :icon="Edit" @click="openSupplement(chapter)">编辑本章正文</el-button></header>
            <div v-if="hasChapterContentOverride(chapter)" class="chapter__summary chapter__summary--edited" v-html="chapterNarrative(chapter)" />
            <p v-else class="chapter__summary">{{ chapterSummary(chapter) }}</p>

            <template v-if="chapter === 'OVERVIEW'"><div class="metric-grid"><article><span>有效案例</span><strong>{{ numberFact('effective_case_total') }}</strong><small>条</small></article><article><span>已执行案例</span><strong>{{ numberFact('execution_total') }}</strong><small>条</small></article><article><span>成功案例</span><strong>{{ numberFact('execution_success') }}</strong><small>条</small></article><article><span>未关闭缺陷</span><strong>{{ numberFact('defect_open') }}</strong><small>个</small></article></div><div class="chart-panel chart-panel--wide"><div><span>案例执行状态</span></div><TestAnalyticsChart :option="executionOption" height="300px" aria-label="案例执行状态分布图" /></div></template>
            <el-descriptions v-else-if="chapter === 'ENVIRONMENT_CONFIG'" :column="2" border class="chapter-descriptions"><el-descriptions-item label="测试大类">{{ domainLabel }}</el-descriptions-item><el-descriptions-item label="统计范围">{{ scopeName() }}</el-descriptions-item><el-descriptions-item label="关联轮次">{{ detail.report.round_name || '-' }}</el-descriptions-item><el-descriptions-item label="有效案例">{{ numberFact('effective_case_total') }} 条</el-descriptions-item><el-descriptions-item label="已执行案例">{{ numberFact('execution_total') }} 条</el-descriptions-item><el-descriptions-item label="执行率">{{ percent(fact('execution_rate')) }}</el-descriptions-item></el-descriptions>
            <template v-else-if="chapter === 'SCOPE_STRATEGY'"><div class="chart-panel"><div><span>案例有效性对比</span></div><TestAnalyticsChart :option="scopeOption" height="260px" aria-label="有效和无效案例对比图" /></div><div class="table-panel"><h4>测试范围明细</h4><el-table :data="Array.isArray(detail.snapshot.scope_details) ? detail.snapshot.scope_details : []" size="small" border max-height="260"><el-table-column prop="scope_code" label="范围编号" min-width="150" /><el-table-column prop="scope_name" label="范围名称" min-width="260" /></el-table></div></template>
            <template v-else-if="chapter === 'EXECUTION_PROGRESS'"><div class="metric-grid metric-grid--five"><article v-for="item in executionStatusItems" :key="item.name"><span>{{ item.name }}</span><strong>{{ item.value }}</strong><small>条</small></article></div><div class="chapter-split"><div class="chart-panel"><div><span>执行状态分布</span></div><TestAnalyticsChart :option="executionOption" height="280px" aria-label="测试执行状态分布图" /></div><div class="progress-panel"><h4>执行质量指标</h4><div v-for="item in [{ name: '执行率', value: fact('execution_rate') }, { name: '案例成功率', value: fact('case_success_rate') }, { name: '已执行案例成功率', value: fact('executed_case_success_rate') }]" :key="item.name"><span>{{ item.name }} <b>{{ percent(item.value) }}</b></span><el-progress :percentage="Math.min(100, Number(item.value) || 0)" :show-text="false" /></div></div></div></template>
            <template v-else-if="chapter === 'DEFECT_ANALYSIS'"><div class="metric-grid"><article><span>缺陷总数</span><strong>{{ numberFact('defect_total') }}</strong><small>个</small></article><article><span>未关闭缺陷</span><strong>{{ numberFact('defect_open') }}</strong><small>个</small></article><article><span>严重缺陷</span><strong>{{ numberFact('severe_defect_count') }}</strong><small>个</small></article><article><span>缺陷修复率</span><strong>{{ percent(fact('defect_repair_rate')) }}</strong></article></div><div v-if="defectStatusItems.length || defectSeverityItems.length" class="chapter-split"><div class="chart-panel"><div><span>缺陷处理状态</span></div><TestAnalyticsChart :option="defectStatusOption" height="280px" aria-label="缺陷处理状态分布图" /></div><div class="chart-panel"><div><span>缺陷严重程度</span></div><TestAnalyticsChart :option="defectSeverityOption" height="280px" aria-label="缺陷严重程度分布图" /></div></div><div class="table-panel"><h4>缺陷明细</h4><el-table :data="Array.isArray(detail.snapshot.defect_details) ? detail.snapshot.defect_details : []" size="small" border max-height="260"><el-table-column prop="defect_code" label="缺陷编号" min-width="140" /><el-table-column prop="summary" label="缺陷摘要" min-width="220" show-overflow-tooltip /><el-table-column label="状态" min-width="95"><template #default="{ row }">{{ reportValue(row.status) }}</template></el-table-column><el-table-column label="严重程度" min-width="95"><template #default="{ row }">{{ reportValue(row.severity) }}</template></el-table-column></el-table></div></template>
            <template v-else-if="chapter === 'QUALITY_ASSESSMENT'"><el-alert v-if="!qualityItems().length" title="尚未配置可用于质量判断的指标。" type="info" :closable="false" /><div v-else class="chapter-split"><div class="chart-panel"><div><span>质量指标判定</span></div><TestAnalyticsChart v-if="qualityResultItems.length" :option="qualityResultOption" height="280px" aria-label="质量指标判定分布图" /><el-empty v-else description="暂无质量指标判定数据" :image-size="72" /></div><div class="quality-summary"><span>总体质量结论</span><el-tag :type="qualityTagType" effect="dark" size="large">{{ qualityOverall }}</el-tag></div></div><div v-if="qualityItems().length" class="table-panel"><h4>启用质量指标</h4><el-table :data="qualityItems()" size="small" border><el-table-column prop="metric_name" label="质量指标" min-width="140" /><el-table-column prop="actual" label="实际值" min-width="80" /><el-table-column prop="qualified_threshold" label="达标阈值" min-width="90" /><el-table-column prop="risk_threshold" label="风险阈值" min-width="90" /><el-table-column prop="result" label="判定结果" min-width="90"><template #default="{ row }"><el-tag size="small" :type="row.result === '达标' ? 'success' : row.result === '风险' ? 'warning' : 'danger'">{{ row.result }}</el-tag></template></el-table-column></el-table></div></template>
            <template v-else-if="chapter === 'RISKS_ISSUES'"><div v-if="riskItems.length" class="risk-list"><article v-for="item in riskItems" :key="item.label"><strong>{{ item.value }}</strong><div><b>{{ item.label }}</b><span>{{ item.text }}</span></div></article></div><el-empty v-else description="当前版本未识别需要重点关注的风险事项" :image-size="82" /></template>
            <template v-else-if="chapter === 'CONCLUSION_RECOMMENDATION'"><div class="conclusion-card"><el-tag :type="qualityTagType" effect="light">质量结论：{{ qualityOverall }}</el-tag></div></template>
            <div v-if="supplementFor(chapter)?.content_html && !hasChapterContentOverride(chapter)" class="supplement-view"><span>人工补充</span><div v-html="supplementFor(chapter)?.content_html" /></div>
          </section>
        </main>
      </div>
      <template #footer><el-button @click="detailOpen = false">关闭</el-button><el-button v-if="detail.report.source_type === 'MANUAL'" type="primary" @click="downloadManual(detail.report)">下载报告</el-button><template v-else><el-button type="primary" @click="download(detail.report, 'docx')">下载 Word</el-button><el-button @click="download(detail.report, 'pdf')">下载 PDF</el-button></template></template>
    ></el-dialog>
    <el-dialog v-model="supplementOpen" :title="`编辑章节正文：${chapterName(supplement.chapter_code)}`" width="min(820px,calc(100vw - 24px))" destroy-on-close><p class="supplement-dialog-hint">已返显本章节的既有说明；可直接修改并扩充文字。数据指标、图表及质量阈值仍保持当前报告版本的冻结值。</p><Toolbar :editor="editor" mode="default" /><WangEditor v-model="supplement.content_html" class="supplement-editor" mode="default" @on-created="(value: IDomEditor) => (editor = value)" /><template #footer><el-button :disabled="supplementSaving" @click="supplementOpen = false">取消</el-button><el-button type="primary" :loading="supplementSaving" @click="saveSupplement">保存并生效</el-button></template></el-dialog>
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
.form-hint {
  margin: 6px 0 0;
  color: var(--text-muted);
  font-size: 12px;
  line-height: 1.5;
}
.manual-upload { display: flex; flex-wrap: wrap; align-items: center; gap: 10px; }
.manual-upload > span { color: var(--text-muted); font-size: 12px; line-height: 1.5; }
.detail-title {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 2px;
}
.detail-title > span {
  color: var(--brand-strong);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: .08em;
}
.detail-title strong {
  font-size: 18px;
}
.report-reader {
  display: grid;
  grid-template-columns: 196px minmax(0, 1fr);
  grid-template-rows: minmax(0, 1fr);
  height: min(760px, calc(96vh - 94px));
  min-height: 0;
  max-height: calc(96vh - 94px);
  overflow: hidden;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: var(--panel-bg);
}
.reader-aside {
  min-height: 0;
  overflow: auto;
  padding: 14px 10px;
  border-right: 1px solid var(--line);
  background: color-mix(in srgb, var(--panel-muted) 58%, var(--panel-bg));
}
.reader-aside__group + .reader-aside__group { margin-top: 20px; }
.reader-aside__group > span {
  display: block;
  padding: 0 8px 7px;
  color: var(--text-muted);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: .08em;
}
.reader-aside :deep(.el-menu) { border-right: 0; background: transparent; }
.reader-aside :deep(.el-menu-item) { display: grid; min-width: 0; height: auto; min-height: 46px; padding: 7px 8px !important; line-height: 1.25; border-radius: 5px; }
.reader-aside :deep(.el-menu-item small) { overflow: hidden; color: var(--text-muted); font-size: 11px; text-overflow: ellipsis; white-space: nowrap; }
.reader-aside__group[aria-label] { display: grid; }
.reader-aside__group > .el-button { justify-content: flex-start; margin: 0; padding: 7px 8px; color: var(--text); font-size: 12px; text-align: left; }
.reader-main {
  min-width: 0;
  min-height: 0;
  overflow: auto;
  padding: 20px clamp(16px, 3vw, 38px) 44px;
  scroll-behavior: smooth;
}
.manual-reader-main { display: grid; place-items: start center; }
.manual-report-card { width: min(680px, 100%); padding: clamp(20px, 4vw, 40px); border: 1px solid var(--line); border-radius: 10px; background: color-mix(in srgb, var(--panel-muted) 26%, var(--panel-bg)); }
.manual-report-card h2 { margin: 14px 0 8px; font-size: 24px; }
.manual-report-card > p { margin: 0 0 22px; color: var(--text-muted); font-size: 13px; line-height: 1.7; }
.manual-report-card__actions { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 20px; }
.report-cover {
  display: flex;
  justify-content: space-between;
  gap: 24px;
  padding: 24px clamp(18px, 3vw, 36px);
  border-radius: 10px;
  color: #f3fbfc;
  background: linear-gradient(122deg, #0f576a, #147d92 65%, #2b9274);
}
.report-cover__eyebrow { margin: 0 0 8px; color: #bfeaf0; font-size: 12px; font-weight: 650; letter-spacing: .06em; }
.report-cover h2 { margin: 0; font-size: clamp(22px, 2.4vw, 30px); line-height: 1.25; }
.report-cover p:not(.report-cover__eyebrow) { margin: 12px 0 0; color: #d8f0f3; font-size: 12px; }
.report-cover__quality { display: grid; flex: 0 0 auto; align-content: center; gap: 9px; min-width: 120px; text-align: right; }
.report-cover__quality > span { color: #c9edf1; font-size: 12px; }
.report-cover__quality :deep(.el-tag) { justify-content: center; font-weight: 700; }
.report-meta { display: flex; flex-wrap: wrap; gap: 8px 20px; padding: 14px 2px 20px; color: var(--text-muted); font-size: 12px; }
.chapter { scroll-margin-top: 12px; padding: 26px 0; border-top: 1px solid var(--line); }
.chapter__header { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.chapter__header > div { display: flex; align-items: center; gap: 10px; }
.chapter__header h3 { margin: 0; color: var(--text); font-size: 18px; }
.chapter__index { color: var(--brand-strong); font-family: ui-monospace, SFMono-Regular, Menlo, monospace; font-size: 12px; font-weight: 700; }
.chapter__summary { margin: 10px 0 18px; color: var(--text-muted); font-size: 13px; line-height: 1.7; }
.metric-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 10px; }
.metric-grid--five { grid-template-columns: repeat(5, minmax(0, 1fr)); }
.metric-grid article { display: grid; align-items: baseline; grid-template-columns: minmax(0, 1fr) auto; gap: 3px; min-height: 88px; padding: 15px; border: 1px solid var(--line); border-radius: 8px; background: color-mix(in srgb, var(--panel-muted) 30%, var(--panel-bg)); }
.metric-grid article > span { grid-column: 1 / -1; color: var(--text-muted); font-size: 12px; }
.metric-grid article strong { color: var(--text); font-size: 25px; line-height: 1; }
.metric-grid article small { color: var(--text-muted); font-size: 12px; }
.chart-panel, .progress-panel, .quality-summary, .table-panel, .conclusion-card { border: 1px solid var(--line); border-radius: 8px; background: var(--panel-bg); }
.chart-panel { min-width: 0; padding: 14px 14px 6px; }
.chart-panel--wide { margin-top: 12px; }
.chart-panel > div:first-child { display: flex; flex-wrap: wrap; justify-content: space-between; gap: 5px 12px; padding: 0 3px; }
.chart-panel > div:first-child > span, .table-panel h4, .progress-panel h4 { margin: 0; color: var(--text); font-size: 13px; font-weight: 700; }
.chart-panel > div:first-child > small { color: var(--text-muted); font-size: 12px; }
.chapter-split { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; }
.progress-panel, .quality-summary { display: grid; align-content: start; gap: 18px; padding: 18px; }
.progress-panel > div { display: grid; gap: 7px; }
.progress-panel span { display: flex; justify-content: space-between; color: var(--text-muted); font-size: 12px; }
.progress-panel b { color: var(--text); }
.quality-summary { justify-items: start; }
.quality-summary > span { color: var(--text-muted); font-size: 12px; }
.quality-summary p { margin: 0; color: var(--text-muted); font-size: 12px; line-height: 1.6; }
.table-panel { margin-top: 12px; padding: 14px; }
.table-panel h4 { margin-bottom: 12px; }
.chapter-descriptions { overflow: hidden; border-radius: 8px; }
.risk-list { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; }
.risk-list article { display: flex; gap: 13px; align-items: center; padding: 15px; border: 1px solid color-mix(in srgb, var(--warning, #c47a2c) 34%, var(--line)); border-radius: 8px; background: color-mix(in srgb, #f5bd63 8%, var(--panel-bg)); }
.risk-list article > strong { color: #b4621e; font-size: 27px; }
.risk-list article > div { display: grid; gap: 3px; }
.risk-list b { font-size: 13px; }
.risk-list span { color: var(--text-muted); font-size: 12px; line-height: 1.5; }
.conclusion-card { padding: 18px; }
.conclusion-card p { margin: 12px 0 0; color: var(--text-muted); font-size: 13px; line-height: 1.7; }
.supplement-view { margin-top: 14px; padding: 14px; border-left: 3px solid var(--brand); border-radius: 0 7px 7px 0; background: color-mix(in srgb, var(--brand) 6%, var(--panel-bg)); font-size: 13px; line-height: 1.7; }
.supplement-view > span { display: block; margin-bottom: 7px; color: var(--brand-strong); font-size: 12px; font-weight: 700; }
.supplement-dialog-hint { margin: 0 0 12px; color: var(--text-muted); font-size: 12px; line-height: 1.6; }
.supplement-editor { min-height: 260px; border: 1px solid var(--line); }
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
  .report-toolbar .el-input {
    flex: 1;
    width: auto;
  }
  .report-reader { display: block; max-height: none; overflow: visible; border: 0; }
  .reader-aside { display: grid; grid-template-columns: 1fr; gap: 10px; max-height: 180px; padding: 10px; border: 1px solid var(--line); border-radius: 8px; }
  .reader-aside__group + .reader-aside__group { margin-top: 0; }
  .reader-aside__group[aria-label] { display: flex; overflow: auto; }
  .reader-aside__group > .el-button { flex: 0 0 auto; }
  .reader-main { overflow: visible; padding: 14px 0 30px; }
  .report-cover { display: grid; gap: 16px; padding: 20px; }
  .report-cover__quality { justify-items: start; text-align: left; }
  .report-meta { display: grid; gap: 7px; }
  .metric-grid, .metric-grid--five { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .chapter-split, .risk-list { grid-template-columns: 1fr; }
  .chapter { padding: 20px 0; }
  .chapter__header h3 { font-size: 16px; }
  .chapter-descriptions :deep(.el-descriptions__body) { overflow-x: auto; }
}
</style>
