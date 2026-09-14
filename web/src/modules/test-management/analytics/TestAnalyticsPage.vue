<!--
文件：web/src/modules/test-management/analytics/TestAnalyticsPage.vue
说明：分析统计页面或交互组件。
用途：承载用户可见的加载、空、失败、提交和交互状态。
作者：hengguan
-->
<script setup lang="ts">
// 关键逻辑：页面只消费现有全局项目上下文；当前测试大类、项目和实体选择共同决定请求范围，前端显隐不替代服务端校验。
import { computed, onMounted, reactive, ref, watch } from "vue";
import type { EChartsOption } from "echarts";
import {
  DataAnalysis,
  Download,
  Edit,
  FolderOpened,
  Plus,
  Refresh,
  Search,
} from "@element-plus/icons-vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { useRoute } from "vue-router";
import UiDataTable from "../../../components/ui/UiDataTable.vue";
import UiEmptyState from "../../../components/ui/UiEmptyState.vue";
import UiPageHeader from "../../../components/ui/UiPageHeader.vue";
import TestAnalyticsChart from "./TestAnalyticsChart.vue";
import TestManagementFormDialog from "../components/TestManagementFormDialog.vue";
import { useProjectContextStore } from "../../../stores/project-context";
import {
  archiveTestAnalytics,
  compareTestAnalyticsSnapshots,
  deleteTestAnalyticsReport,
  downloadTestAnalytics,
  getTestAnalyticsDrilldown,
  getTestAnalyticsFilters,
  getTestAnalyticsPreset,
  getTestAnalytics,
  getTestAnalyticsTree,
  listTestProjects,
  publishTestAnalyticsReport,
  runSavedTestAnalytics,
  saveTestAnalyticsReport,
  type TestAnalyticsTree,
  type TestDomain,
} from "../api";
const route = useRoute(), context = useProjectContextStore();
const domain = computed(() => String(route.params.domain) as TestDomain);
const projects = ref<
    Array<{ id: number; project_code: string; project_name: string }>
  >([]),
  projectId = computed(
    () => projects.value.find((x) => x.project_code === context.currentRef)?.id,
  );
const label = computed(
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
const tree = ref<TestAnalyticsTree>(),
  meta = ref<any>({ systems: [], rounds: [], cycles: [] }),
  active = reactive({
    key: "RPT-001",
    name: "测试执行进度汇总表",
    view: "TABLE",
    perspective: "EXECUTOR",
  }),
  filters = reactive({
    systemId: undefined as number | undefined,
    roundId: undefined as number | undefined,
    cycleId: undefined as number | undefined,
  }),
  model = ref<any>({ rows: [], trend: [] }),
  loading = ref(false),
  drill = ref(false),
  drillRows = ref<any[]>([]),
  designer = ref(false),
  compareOpen = ref(false),
  compareRows = ref<any[]>([]),
  compareRounds = ref<number[]>([]),
  activeCustomId = ref<number>(),
  activeSavedId = ref<number>();
const config = reactive({
  report_name: "",
  report_key: "CUSTOM",
  dimensions: ["physical_subsystem_id"],
  metrics: ["execution_rate", "case_success_rate"],
  charts: ["TABLE", "BAR"],
});
const fieldLabels: Record<string, string> = {
  system_name: "参测系统",
  responsible_team_name: "责任团队组织",
  tester_name: "测试人员",
  statistic_date: "统计日期",
  statistic_week: "统计周",
  statistic_month: "统计月",
  accounting_category: "核算关联",
  defect_status: "缺陷状态",
  value: "统计值",
  scope_total: "测试范围数",
  covered_total: "已覆盖范围数",
  uncovered_total: "未覆盖范围数",
  coverage_rate: "范围覆盖率",
  case_total: "案例总数",
  effective_case_total: "有效案例数",
  invalid_case_total: "无效案例数",
  execution_total: "已执行案例数",
  execution_unexecuted: "未执行案例数",
  unexecuted_count: "未执行案例数",
  execution_in_progress: "执行中案例数",
  in_progress_count: "执行中案例数",
  execution_success: "成功案例数",
  success_count: "成功案例数",
  execution_failed: "失败案例数",
  failed_count: "失败案例数",
  execution_blocked: "阻塞案例数",
  blocked_count: "阻塞案例数",
  execution_rate: "执行率",
  success_rate: "案例成功率",
  case_success_rate: "案例成功率",
  executed_case_success_rate: "已执行案例成功率",
  defect_total: "缺陷总数",
  defect_open: "未关闭缺陷数",
  severe_defect_count: "严重缺陷数",
  defect_density: "缺陷密度",
  defect_repair_rate: "缺陷修复率",
  handled_defects: "已处理缺陷数",
  pending_defects: "待处理缺陷数",
  completed_count: "已完成数",
  raised_count: "新增缺陷数",
  resolved_count: "已解决缺陷数",
  closed_total: "已关闭缺陷数",
  average_days: "平均生命周期（天）",
  average_close_days: "平均关闭耗时（天）",
  cycle_name: "测试周期",
  defect_category: "缺陷分类",
  execution_record_total: "执行记录数",
  defect_code: "缺陷编号",
  summary: "缺陷摘要",
  status: "状态",
  severity: "严重程度",
  urgency: "紧急程度",
  handler_name: "处理人",
  overdue_days: "逾期天数",
  case_code: "案例编号",
  case_name: "案例名称",
  case_type: "案例类型",
  priority: "优先级",
  invalidated: "是否无效",
  execution_status: "执行状态",
  executed_at: "执行时间",
  proposed_at: "提出时间",
  round_name: "测试轮次",
  archived_at: "归档时间",
  snapshot_at: "统计时间",
};
const valueLabels: Record<string, string> = {
  UNEXECUTED: "未执行", IN_PROGRESS: "执行中", RUNNING: "执行中",
  SUCCESS: "成功", FAILED: "失败", BLOCKED: "阻塞",
  RAISED: "已提出", ANALYZING: "分析中", CAUSE_IDENTIFIED: "已定位原因",
  FIX_PLAN_CONFIRMED: "修复方案已确认", PENDING_VERIFICATION: "待验证",
  RESOLVED: "已解决", CLOSED: "已关闭",
  FATAL: "致命", SERIOUS: "严重", NORMAL: "一般", MINOR: "轻微",
  HIGH: "高", MEDIUM: "中", LOW: "低", true: "是", false: "否", 1: "是", 0: "否",
};
const chartLabels: Record<string, string> = {
  TABLE: "数据表", BAR: "柱状图", LINE: "趋势图", PIE: "分布图",
  STACKED_BAR: "堆叠对比图", RADAR: "质量雷达图", HEATMAP: "执行热力图",
};
const columnLabel = (key: string) => fieldLabels[key] || key;
const displayValue = (value: unknown) =>
  value === null || value === undefined || value === "" ? "-" : typeof value === "number" ? String(value) : valueLabels[String(value)] || String(value);
const columns = computed(() => Object.keys(model.value.rows?.[0] || {}));
const chartDimension = (row: any) => String(
  row.dimension || row.row_dimension || row.system_name || row.round_name || row.tester_name || row.case_type || row.severity || row.defect_status || row.execution_status || row.statistic_date || row.defect_code || "-",
);
const stackedDimension = (row: any) => String(
  row.row_dimension || [row.system_name, row.cycle_name].filter(Boolean).join(" / ") || row.round_name || row.severity || row.system_name || "未分类",
);
const stackedSeries = (row: any) => String(row.column_dimension || row.execution_status || row.defect_status || "数量");
/** 汇总卡展示当前表的总计；不能把第一条分组记录误当作项目总计。 */
const summaryCards = computed(() => {
  const rows = model.value.rows || [];
  if (active.key.startsWith("CHT-") || !rows.length || !("effective_case_total" in rows[0])) return [];
  const sum = (key: string) => rows.reduce((total: number, row: any) => total + Number(row[key] || 0), 0);
  const effective = sum("effective_case_total"), execution = sum("execution_total"), success = sum("success_count");
  const rate = (part: number, total: number) => total ? Math.round(part * 10000 / total) / 100 : 0;
  return [
    ["effective_case_total", effective], ["execution_total", execution], ["success_count", success],
    ["failed_count", sum("failed_count")], ["blocked_count", sum("blocked_count")],
    ["in_progress_count", sum("in_progress_count")], ["unexecuted_count", sum("unexecuted_count")],
    ["execution_rate", rate(execution, effective)], ["case_success_rate", rate(success, effective)],
    ["executed_case_success_rate", rate(success, execution)],
  ] as Array<[string, number]>;
});
const chartView = (type: unknown) => {
  const value = String(type || "BAR");
  return ["BAR", "LINE", "PIE", "STACKED_BAR", "RADAR", "HEATMAP"].includes(value) ? value : "BAR";
};
const viewOptions = computed(() =>
  active.key.startsWith("CHT-") ? ["TABLE", chartView(model.value.chart_type)] : ["TABLE"],
);
const presentation = computed(() => {
  if (["RPT-006", "RPT-007"].includes(active.key)) return "detail";
  if (["RPT-008", "RPT-009", "RPT-013", "RPT-017", "RPT-026"].includes(active.key)) return "distribution";
  if (["RPT-011"].includes(active.key)) return "coverage";
  if (["RPT-012"].includes(active.key)) return "workload";
  if (["RPT-010", "RPT-019", "RPT-024"].includes(active.key)) return "quality";
  if (["RPT-020", "RPT-021", "RPT-022", "RPT-023", "RPT-025"].includes(active.key)) return "timeline";
  return "execution";
});
const presentationHint = computed(() => ({
  detail: "逐条展示业务事实，可按数值进入下钻明细。",
  distribution: "按状态、严重程度或处理效率聚合，适合比较各分类数量与占比。",
  coverage: "展示测试范围是否已纳入有效案例，覆盖率按范围计算。",
  workload: "展示测试人员的执行工作量与执行结果分布。",
  quality: "对比各系统或责任团队的执行率、成功率和缺陷密度。",
  timeline: "按日、周或月连续呈现执行进展，便于跟踪变化。",
  execution: "按当前统计维度汇总有效案例及执行状态，不将执行中计入已执行。",
} as Record<string, string>)[presentation.value]);
const chartOption = computed<EChartsOption>(() => {
  const rows = model.value.rows || [],
    chartType = active.view === "TABLE" ? chartView(model.value.chart_type) : active.view,
    dimension = chartDimension,
    value = (row: any) =>
      Number(
        row.value ??
          row.execution_total ??
          row.scope_total ??
          row.covered_total ??
          row.handled_defects ??
          0,
      );
  if (chartType === "RADAR") {
    const indicators = ["执行率", "案例成功率", "已执行案例成功率", "缺陷密度"];
    return {
      tooltip: {},
      legend: { bottom: 0 },
      radar: { indicator: indicators.map((name) => ({ name, max: 100 })) },
      series: [{ type: "radar", data: rows.slice(0, 8).map((row: any) => ({ name: dimension(row), value: [Number(row.execution_rate || 0), Number(row.case_success_rate || 0), Number(row.executed_case_success_rate || 0), Math.max(0, 100 - Number(row.defect_density || 0))] })) }],
    };
  }
  if (chartType === "HEATMAP") {
    const dates = [...new Set(rows.map((row: any) => String(row.column_dimension || row.statistic_date || row.dimension || "未执行")))];
    const systems = [...new Set(rows.map((row: any) => String(row.row_dimension || row.system_name || "未设置系统")))];
    return {
      tooltip: { position: "top" },
      grid: { left: 82, right: 24, top: 20, bottom: 72 },
      xAxis: { type: "category", data: dates, axisLabel: { rotate: dates.length > 6 ? 30 : 0 } },
      yAxis: { type: "category", data: systems },
      visualMap: { min: 0, max: Math.max(1, ...rows.map(value)), calculable: true, orient: "horizontal", left: "center", bottom: 0 },
      series: [{ type: "heatmap", data: rows.map((row: any) => [dates.indexOf(String(row.column_dimension || row.statistic_date || row.dimension || "未执行")), systems.indexOf(String(row.row_dimension || row.system_name || "未设置系统")), value(row)]), label: { show: true } }],
    };
  }
  if (chartType === "STACKED_BAR") {
    const dimensions = [...new Set(rows.map(stackedDimension))];
    const seriesKeys = [...new Set(rows.map(stackedSeries))];
    return {
      tooltip: { trigger: "axis" }, legend: { top: 0 }, grid: { left: 52, right: 20, top: 42, bottom: 62 },
      xAxis: { type: "category", data: dimensions, axisLabel: { rotate: dimensions.length > 6 ? 30 : 0 } }, yAxis: { type: "value", minInterval: 1 },
      series: seriesKeys.map((name) => ({ name, type: "bar", stack: "总量", data: dimensions.map((item) => value(rows.find((row: any) => stackedDimension(row) === item && stackedSeries(row) === name) || {})) })),
    };
  }
  if (chartType === "LINE") {
    const trend = model.value.trend || [];
    const fields = active.key === "CHT-004"
      ? [["execution_rate", "执行率"], ["case_success_rate", "案例成功率"], ["executed_case_success_rate", "已执行案例成功率"]]
      : active.key === "CHT-013"
        ? [["execution_total", "日执行量"], ["execution_rate", "累计执行率"]]
        : [["completed_count", "完成数"], ["raised_count", "新增缺陷数"], ["resolved_count", "已解决缺陷数"]];
    return {
      tooltip: { trigger: "axis" },
      legend: { top: 0 },
      grid: { left: 52, right: 20, top: 38, bottom: 32 },
      xAxis: {
        type: "category",
        data: trend.map((row: any) => String(row.statistic_date || row.dimension || "")),
      },
      yAxis: { type: "value", minInterval: 1 },
      series: fields.filter(([key]) => trend.some((row: any) => row[key] !== undefined)).map(([key, name]) => ({ name, type: "line", smooth: true, data: trend.map((row: any) => Number(row[key] ?? 0)) })),
    };
  }
  if (chartType === "PIE")
    return {
      tooltip: { trigger: "item" },
      legend: { bottom: 0 },
      series: [
        {
          type: "pie",
          radius: ["42%", "68%"],
          data: rows.map((row: any) => ({
            name: dimension(row),
            value: value(row),
          })),
        },
      ],
    };
  return {
    tooltip: { trigger: "axis" },
    grid: { left: 52, right: 20, top: 24, bottom: 56 },
    xAxis: {
      type: "category",
      axisLabel: { rotate: rows.length > 7 ? 30 : 0 },
      data: rows.map(dimension),
    },
    yAxis: { type: "value", minInterval: 1 },
    series: [
      {
        name: active.name,
        type: "bar",
        barMaxWidth: 36,
        data: rows.map(value),
      },
    ],
  };
});
function err(e: any, s: string) {
  ElMessage.error(e?.response?.data?.message || s);
}
async function load() {
  if (!projectId.value) return;
  loading.value = true;
  try {
    const response = (
      await (activeSavedId.value
        ? runSavedTestAnalytics(domain.value, projectId.value, activeSavedId.value, {
            physicalSubsystemId: filters.systemId,
            roundId: filters.roundId,
            cycleId: filters.cycleId,
          })
        : active.key === "CUSTOM"
          ? getTestAnalytics(domain.value, projectId.value, "CUSTOM", {
              physicalSubsystemId: filters.systemId,
              roundId: filters.roundId,
              cycleId: filters.cycleId,
            })
        : getTestAnalyticsPreset(domain.value, projectId.value, active.key, {
            physicalSubsystemId: filters.systemId,
            roundId: filters.roundId,
            cycleId: filters.cycleId,
            view: active.view,
            perspective: active.perspective,
          }))
    ).data.data as any;
    model.value = { ...response, rows: response.rows || response.table || [] };
    if (active.key.startsWith("CHT-") && active.view === "TABLE") active.view = chartView(response.chart_type);
  } catch (e) {
    err(e, "统计数据加载失败");
  } finally {
    loading.value = false;
  }
}
async function setup() {
  if (!projectId.value) return;
  const [a, b] = await Promise.all([
    getTestAnalyticsTree(domain.value, projectId.value),
    getTestAnalyticsFilters(domain.value, projectId.value),
  ]);
  tree.value = a.data.data;
  meta.value = b.data.data;
  await load();
}
function select(key: string, name?: string) {
  activeCustomId.value = undefined;
  activeSavedId.value = undefined;
  active.key = key;
  active.name = name || "固定分析";
  active.view = "TABLE";
  void load();
}
function selectCustom(
  report: {
    id: number;
    report_key: string;
    report_name: string;
    config_json?: string;
  },
  editable = false,
) {
  activeCustomId.value = editable ? report.id : undefined;
  activeSavedId.value = report.id;
  active.key = report.report_key;
  active.name = report.report_name;
  try {
    const saved = JSON.parse(report.config_json || "{}");
    Object.assign(config, saved, {
      report_name: report.report_name,
      report_key: report.report_key,
    });
    active.view =
      Array.isArray(saved.charts) && saved.charts[0]
        ? saved.charts[0]
        : "TABLE";
  } catch {
    active.view = "TABLE";
  }
  void load();
}
function openDesigner(create = false) {
  if (create) {
    activeCustomId.value = undefined;
    Object.assign(config, {
      report_name: "",
      report_key: "CUSTOM",
      dimensions: ["physical_subsystem_id"],
      metrics: ["execution_rate", "case_success_rate"],
      charts: ["TABLE", "BAR"],
    });
  }
  designer.value = true;
}
async function drilldown() {
  if (!projectId.value) return;
  const entity =
    active.key === "RPT-004"
      ? "SCOPE"
      : ["RPT-005", "RPT-006", "RPT-007", "CHT-001"].includes(active.key)
        ? "EXECUTION"
        : "DEFECT";
  try {
    drillRows.value = (
      await getTestAnalyticsDrilldown(domain.value, projectId.value, entity, {
        physicalSubsystemId: filters.systemId,
        roundId: filters.roundId,
        cycleId: filters.cycleId,
      })
    ).data.data;
    drill.value = true;
  } catch (e) {
    err(e, "明细加载失败");
  }
}
async function exportXlsx() {
  if (projectId.value)
    try {
      await downloadTestAnalytics(domain.value, projectId.value, active.key, {
        physicalSubsystemId: filters.systemId,
        roundId: filters.roundId,
        cycleId: filters.cycleId,
        view: active.view,
        perspective: active.perspective,
      });
    } catch (e) {
      err(e, "导出失败");
    }
}
function exportImage() {
  const rows = (model.value.rows || []).slice(0, 20),
    height = Math.max(120, rows.length * 28 + 42),
    svg = `<svg xmlns="http://www.w3.org/2000/svg" width="720" height="${height}"><rect width="100%" height="100%" fill="white"/><text x="10" y="20" font-size="16">${active.name}</text>${rows
      .map((r: any, i: number) => {
        const v = Number(r.value ?? r.execution_total ?? r.scope_total ?? 0);
        return `<text x="10" y="${46 + i * 28}" font-size="12">${chartDimension(r)}</text><rect x="180" y="${34 + i * 28}" width="${Math.min(440, v * 8)}" height="14" fill="#409eff" rx="3"/><text x="${190 + Math.min(440, v * 8)}" y="${46 + i * 28}" font-size="12">${v}</text>`;
      })
      .join("")}</svg>`;
  const url = URL.createObjectURL(new Blob([svg], { type: "image/svg+xml" })),
    a = document.createElement("a");
  a.href = url;
  a.download = `${active.name}.svg`;
  a.click();
  URL.revokeObjectURL(url);
}
async function archive() {
  if (!projectId.value || !filters.roundId) {
    ElMessage.warning("请选择轮次后归档");
    return;
  }
  try {
    await ElMessageBox.confirm(
      "将固化当前轮次四张预置报表，重复归档会覆盖旧快照。",
      "归档统计快照",
      { type: "warning" },
    );
    await archiveTestAnalytics(domain.value, projectId.value, filters.roundId);
    ElMessage.success("统计快照已归档");
  } catch (e: any) {
    if (e !== "cancel") err(e, "归档失败");
  }
}
async function compare() {
  if (!projectId.value || compareRounds.value.length < 2) {
    ElMessage.warning("请选择至少两个轮次");
    return;
  }
  try {
    compareRows.value = (
      await compareTestAnalyticsSnapshots(
        domain.value,
        projectId.value,
        compareRounds.value,
      )
    ).data.data;
  } catch (e) {
    err(e, "快照对比失败");
  }
}
async function save() {
  if (!projectId.value || !config.report_name) {
    ElMessage.warning("请填写报表名称");
    return;
  }
  try {
    await saveTestAnalyticsReport(
      domain.value,
      projectId.value,
      {
        report_name: config.report_name,
        report_key: "CUSTOM",
        config: {
          dimensions: config.dimensions,
          metrics: config.metrics,
          charts: config.charts,
        },
      },
      activeCustomId.value,
    );
    designer.value = false;
    ElMessage.success("已保存到我的报表");
    await setup();
  } catch (e) {
    err(e, "保存失败");
  }
}
onMounted(async () => {
  await context.initialize();
  projects.value = (await listTestProjects(domain.value)).data.data || [];
  await setup();
});
watch([() => context.currentRef, domain], setup);
</script>
<template>
  <section class="analytics">
    <UiPageHeader eyebrow="测试管理" :title="label + ' · 分析统计'"
      ><template #actions
        ><el-button
          size="small"
          type="primary"
          :icon="Plus"
          @click="openDesigner(true)"
          >新建报表</el-button
        ><el-button
          text
          circle
          size="small"
          :icon="Refresh"
          aria-label="刷新"
          @click="load" /></template></UiPageHeader
    ><UiEmptyState
      v-if="!projectId"
      title="请先选择项目"
      description="请使用顶部全局项目选择器。"
    />
    <section v-else class="workspace" v-loading="loading">
      <aside>
        <header>统计报表</header>
        <el-menu :default-active="active.key"
          ><el-menu-item
            v-for="p in tree?.presets || []"
            :key="p.key"
            :index="p.key"
            @click="select(p.key, p.name)"
            ><el-icon><DataAnalysis /></el-icon>{{ p.name }}</el-menu-item
          ></el-menu
        >
        <h4>公共报表</h4>
        <el-menu
          ><el-menu-item
            v-for="p in tree?.shared || []"
            :key="'s' + p.id"
            @click="selectCustom(p)"
            >{{ p.report_name }}</el-menu-item
          ></el-menu
        >
        <h4>我的报表</h4>
        <el-menu
          ><el-menu-item
            v-for="p in tree?.mine || []"
            :key="'m' + p.id"
            @click="selectCustom(p, true)"
            >{{ p.report_name
            }}<el-button
              text
              size="small"
              @click.stop="
                publishTestAnalyticsReport(
                  domain,
                  projectId!,
                  p.id,
                  !p.shared,
                ).then(setup)
              "
              >{{ p.shared ? "取消共享" : "共享" }}</el-button
            ><el-button
              text
              type="danger"
              size="small"
              @click.stop="
                ElMessageBox.confirm(
                  `删除“${p.report_name}”后无法恢复。`,
                  '删除自定义报表',
                  { type: 'warning' },
                )
                  .then(() =>
                    deleteTestAnalyticsReport(domain, projectId!, p.id),
                  )
                  .then(setup)
                  .catch(() => undefined)
              "
              >删除</el-button
            ></el-menu-item
          ></el-menu
        >
      </aside>
      <main>
        <div class="filters">
          <el-select
            v-model="filters.systemId"
            clearable
            size="small"
            placeholder="参测系统"
            ><el-option
              v-for="x in meta.systems"
              :key="x.id"
              :label="x.name"
              :value="x.id" /></el-select
          ><el-select
            v-model="filters.roundId"
            clearable
            size="small"
            placeholder="轮次"
            ><el-option
              v-for="x in meta.rounds"
              :key="x.id"
              :label="x.round_name"
              :value="x.id" /></el-select
          ><el-select
            v-model="filters.cycleId"
            clearable
            size="small"
            placeholder="周期"
            ><el-option
              v-for="x in meta.cycles.filter(
                (c: any) => !filters.roundId || c.round_id === filters.roundId,
              )"
              :key="x.id"
              :label="x.cycle_name"
              :value="x.id" /></el-select
          ><el-button size="small" :icon="Search" @click="load">查询</el-button>
        </div>
        <div class="toolbar">
          <el-radio-group v-model="active.view" size="small">
            <el-radio-button v-for="v in viewOptions" :key="v" :value="v">{{ chartLabels[v] }}</el-radio-button>
          </el-radio-group>
          ><span /><el-button size="small" :icon="Edit" @click="openDesigner()"
            >编辑/另存为</el-button
          ><el-button size="small" :icon="Download" @click="exportXlsx"
            >导出 xlsx</el-button
          ><el-button size="small" @click="exportImage">导出图片</el-button
          ><el-button size="small" @click="compareOpen = true"
            >跨轮次对比</el-button
          ><el-button size="small" @click="archive">归档快照</el-button>
        </div>
        <section class="analysis-intro" :class="`analysis-intro--${presentation}`">
          <strong>{{ active.name }}</strong>
          <span>{{ presentationHint }}</span>
        </section>
        <div v-if="summaryCards.length" class="cards">
          <article
            v-for="[key, value] in summaryCards"
            :key="key"
          >
            <small>{{ columnLabel(key) }}</small
            ><b>{{ displayValue(value) }}</b>
          </article>
        </div>
        <TestAnalyticsChart
          v-if="active.key.startsWith('CHT-') && active.view !== 'TABLE' && model.rows?.length"
          class="chart"
          :option="chartOption"
          :aria-label="active.name + '图表'"
        /><UiDataTable
          :data="model.rows || []"
          border
          :class="['table', `table--${presentation}`]"
          ><el-table-column
            v-for="key in columns"
            :key="key"
            :prop="key"
            :label="columnLabel(key)"
            min-width="120"
            show-overflow-tooltip
            resizable
            ><template #default="{ row }"
              ><el-button
                v-if="typeof row[key] === 'number'"
                text
                size="small"
                @click="drilldown"
                >{{ row[key] }}</el-button
              ><span v-else>{{ displayValue(row[key]) }}</span></template
            ></el-table-column
          ></UiDataTable
        >
      </main>
    </section>
    <TestManagementFormDialog
      v-model="designer"
      title="统计报表设计器"
      width="min(720px,calc(100vw - 24px))"
      @submit="save"
      ><el-form label-width="104px"
        ><el-form-item label="报表名称" required
          ><el-input
            v-model="config.report_name"
            maxlength="50" /></el-form-item
        ><el-form-item label="分析维度"
          ><el-checkbox-group v-model="config.dimensions"
            ><el-checkbox
              v-for="x in [
                ['physical_subsystem_id', '物理子系统'],
                ['responsible_team_org_id', '责任团队组织'],
                ['round_id', '测试轮次'],
                ['cycle_id', '测试周期'],
                ['severity', '缺陷严重程度'],
                ['status', '状态'],
                ['executor_id', '执行人员'],
                ['handler_id', '处理人员'],
              ]"
              :key="x[0]"
              :value="x[0]"
              >{{ x[1] }}</el-checkbox
            ></el-checkbox-group
          ></el-form-item
        ><el-form-item label="统计指标"
          ><el-checkbox-group v-model="config.metrics"
            ><el-checkbox
              v-for="x in [
                ['execution_rate', '执行率'],
                ['case_success_rate', '案例成功率'],
                ['executed_case_success_rate', '已执行案例成功率'],
                ['defect_density', '缺陷密度'],
                ['defect_repair_rate', '缺陷修复率'],
                ['severe_defect_count', '严重缺陷数'],
                ['blocked_case_count', '阻塞案例数'],
                ['defect_total', '缺陷总数'],
                ['effective_case_total', '有效案例数'],
              ]"
              :key="x[0]"
              :value="x[0]"
              >{{ x[1] }}</el-checkbox
            ></el-checkbox-group
          ></el-form-item
        ><el-form-item label="图表形式"
          ><el-checkbox-group v-model="config.charts"
            ><el-checkbox
              v-for="x in ['TABLE', 'BAR', 'PIE', 'LINE']"
              :key="x"
              :value="x"
              >{{ chartLabels[x] }}</el-checkbox
            ></el-checkbox-group
          ></el-form-item
        ><el-alert type="info" :closable="false" show-icon title="运行时仅按当前入口固定的测试大类、项目、系统、轮次和周期过滤；不提供测试大类筛选。" />
        ></el-form
      ></TestManagementFormDialog
    ><el-dialog
      v-model="drill"
      title="统计下钻明细"
      width="min(980px,calc(100vw - 24px))"
      ><el-table :data="drillRows" size="small" max-height="520"
        ><el-table-column
          v-for="k in Object.keys(drillRows[0] || {})"
          :key="k"
          :prop="k"
          :label="columnLabel(k)"
          min-width="120"
          show-overflow-tooltip /></el-table
      ><template #footer
        ><el-button @click="drill = false">关闭</el-button></template
      ></el-dialog
    ><el-dialog
      v-model="compareOpen"
      title="跨轮次快照对比"
      width="min(980px,calc(100vw - 24px))"
      ><el-select
        v-model="compareRounds"
        multiple
        style="width: 100%"
        placeholder="选择已归档轮次"
        ><el-option
          v-for="x in meta.rounds"
          :key="x.id"
          :label="x.round_name"
          :value="x.id" /></el-select
      ><el-button
        class="compare-button"
        size="small"
        type="primary"
        @click="compare"
        >开始对比</el-button
      ><el-table :data="compareRows" size="small" max-height="420"
        ><el-table-column
          v-for="k in Object.keys(compareRows[0] || {})"
          :key="k"
          :prop="k"
          :label="columnLabel(k)"
          min-width="120" /></el-table
    ></el-dialog>
  </section>
</template>
<style scoped>
.analytics {
  max-width: 1440px;
  margin: auto;
}
.workspace {
  display: grid;
  grid-template-columns: 248px minmax(0, 1fr);
  min-height: 590px;
  border: 1px solid var(--line);
  border-radius: 6px;
  overflow: hidden;
}
.workspace > aside {
  padding: 9px;
  background: color-mix(in srgb, var(--panel-muted) 58%, var(--panel-bg));
  border-right: 1px solid var(--line);
  overflow: auto;
}
.workspace > aside header {
  padding: 0 5px 7px;
  font-size: 13px;
  font-weight: 650;
}
.workspace h4 {
  margin: 13px 6px 4px;
  font-size: 12px;
  color: var(--text-muted);
}
.workspace :deep(.el-menu) {
  background: transparent;
  border: 0;
}
.workspace :deep(.el-menu-item) {
  height: 30px;
  line-height: 30px;
  padding-left: 9px !important;
  font-size: 12px;
}
.workspace :deep(.el-menu-item .el-button) {
  margin-left: auto;
}
.workspace > main {
  min-width: 0;
  padding: 10px;
}
.filters,
.toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 9px;
}
.filters .el-select {
  width: 140px;
}
.toolbar span {
  flex: 1;
}
.analysis-intro {
  display: grid;
  gap: 3px;
  padding: 10px 12px;
  margin-bottom: 9px;
  border: 1px solid var(--line);
  border-left: 3px solid var(--brand);
  border-radius: 5px;
  background: var(--panel-muted);
}
.analysis-intro strong { font-size: 14px; }
.analysis-intro span { font-size: 12px; color: var(--text-muted); }
.analysis-intro--detail { border-left-color: var(--accent, var(--brand)); }
.analysis-intro--quality { border-left-color: var(--success); }
.analysis-intro--timeline { border-left-color: var(--warning); }
.analysis-intro--distribution { border-left-color: var(--danger); }
.cards {
  display: flex;
  gap: 7px;
  overflow: auto;
  margin-bottom: 9px;
}
.cards article {
  min-width: 105px;
  padding: 8px 10px;
  border: 1px solid var(--line);
  border-radius: 5px;
}
.cards small {
  display: block;
  font-size: 11px;
  color: var(--text-muted);
}
.cards b {
  font-size: 16px;
}
.chart {
  padding: 10px;
  border: 1px solid var(--line);
  border-radius: 5px;
  margin-bottom: 9px;
}
.bar {
  display: grid;
  grid-template-columns: 150px minmax(70px, 1fr) 54px;
  gap: 8px;
  align-items: center;
  margin: 7px 0;
  font-size: 12px;
}
.bar i {
  height: 11px;
  border-radius: 99px;
  background: var(--panel-muted);
}
.bar b {
  display: block;
  height: 100%;
  border-radius: 99px;
  background: var(--brand);
}
.bar em {
  font-style: normal;
}
.table :deep(.el-table__cell),
.table :deep(.el-table__header th .cell) {
  font-size: 12px;
  white-space: nowrap;
}
.table--detail :deep(.el-table__cell) { vertical-align: top; }
.table--detail :deep(.el-table__cell:nth-child(2) .cell) { white-space: normal; line-height: 1.5; }
.table--quality :deep(.el-table__body tr:first-child) { background: color-mix(in srgb, var(--success) 9%, var(--panel-bg)); }
.table--coverage :deep(.el-table__body tr) { background: color-mix(in srgb, var(--brand) 5%, var(--panel-bg)); }
.compare-button {
  margin: 10px 0;
}
@media (max-width: 760px) {
  .workspace {
    display: block;
    border: 0;
    overflow: visible;
  }
  .workspace > aside {
    max-height: 250px;
    border: 1px solid var(--line);
    border-radius: 6px;
    margin-bottom: 9px;
  }
  .workspace > main {
    border: 1px solid var(--line);
    border-radius: 6px;
    padding: 9px;
  }
  .filters .el-select {
    flex: 1;
    width: auto;
  }
  .bar {
    grid-template-columns: 100px minmax(50px, 1fr) 40px;
  }
  .analysis-intro { padding: 9px; }
  .cards { padding-bottom: 2px; }
}
</style>
