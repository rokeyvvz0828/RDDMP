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
import { useRoute, useRouter } from "vue-router";
import UiDataTable from "../../../components/ui/UiDataTable.vue";
import UiEmptyState from "../../../components/ui/UiEmptyState.vue";
import UiPageHeader from "../../../components/ui/UiPageHeader.vue";
import DeliveryChart from "../../delivery-showcase/components/DeliveryChart.vue";
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
  getTestAnalyticsTree,
  listTestProjects,
  publishTestAnalyticsReport,
  saveTestAnalyticsReport,
  type TestAnalyticsTree,
  type TestDomain,
} from "../api";
const route = useRoute(),
  router = useRouter(),
  context = useProjectContextStore();
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
    key: "SCOPE_COVERAGE",
    name: "范围覆盖度",
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
  activeCustomId = ref<number>();
const config = reactive({
  report_name: "",
  report_key: "SCOPE_COVERAGE",
  row_dimensions: ["系统"],
  column_dimensions: [] as string[],
  metrics: ["范围总数", "覆盖率"],
  charts: ["TABLE", "BAR"],
  filters: ["系统", "轮次", "周期"],
});
const presetNames: Record<string, string> = {
  SCOPE_COVERAGE: "范围覆盖度",
  EXECUTION_PROGRESS: "执行进度与成功率",
  DEFECT_DISTRIBUTION: "缺陷多维统计",
  PERSONNEL_WORKLOAD: "人员工作量",
};
const columns = computed(() => Object.keys(model.value.rows?.[0] || {}));
const viewOptions = computed(() =>
  active.key === "SCOPE_COVERAGE"
    ? ["TABLE", "BAR"]
    : active.key === "EXECUTION_PROGRESS"
      ? ["TABLE", "BAR", "LINE"]
      : active.key === "DEFECT_DISTRIBUTION"
        ? ["STATUS", "CATEGORY", "SYSTEM", "MATRIX", "OVERDUE"]
        : ["EXECUTOR", "HANDLER"],
);
const chartOption = computed<EChartsOption>(() => {
  const rows = model.value.rows || [],
    dimension = (row: any) =>
      String(row.dimension || row.row_dimension || row.defect_code || "-"),
    value = (row: any) =>
      Number(
        row.value ??
          row.execution_total ??
          row.scope_total ??
          row.covered_total ??
          row.handled_defects ??
          0,
      );
  if (active.view === "LINE") {
    const trend = model.value.trend || [];
    return {
      tooltip: { trigger: "axis" },
      legend: { top: 0 },
      grid: { left: 52, right: 20, top: 38, bottom: 32 },
      xAxis: {
        type: "category",
        data: trend.map((row: any) => String(row.dimension || "")),
      },
      yAxis: { type: "value", minInterval: 1 },
      series: [
        {
          name: "完成数",
          type: "line",
          smooth: true,
          data: trend.map((row: any) =>
            Number(row.completed_count ?? row.raised_count ?? 0),
          ),
        },
        {
          name: "解决数",
          type: "line",
          smooth: true,
          data: trend.map((row: any) => Number(row.resolved_count ?? 0)),
        },
      ],
    };
  }
  if (active.key === "DEFECT_DISTRIBUTION" && active.view === "STATUS")
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
    model.value = (
      await getTestAnalyticsPreset(domain.value, projectId.value, active.key, {
        physicalSubsystemId: filters.systemId,
        roundId: filters.roundId,
        cycleId: filters.cycleId,
        view: active.view,
        perspective: active.perspective,
      })
    ).data.data;
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
  active.key = key;
  active.name = name || presetNames[key] || "自定义报表";
  active.view =
    key === "DEFECT_DISTRIBUTION"
      ? "STATUS"
      : key === "PERSONNEL_WORKLOAD"
        ? "EXECUTOR"
        : "TABLE";
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
      report_key: active.key,
      row_dimensions: ["系统"],
      column_dimensions: [],
      metrics: ["范围总数", "覆盖率"],
      charts: ["TABLE", "BAR"],
      filters: ["系统", "轮次", "周期"],
    });
  }
  designer.value = true;
}
async function drilldown() {
  if (!projectId.value) return;
  const entity =
    active.key === "SCOPE_COVERAGE"
      ? "SCOPE"
      : active.key === "EXECUTION_PROGRESS"
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
        return `<text x="10" y="${46 + i * 28}" font-size="12">${String(r.dimension || r.row_dimension || "-")}</text><rect x="180" y="${34 + i * 28}" width="${Math.min(440, v * 8)}" height="14" fill="#409eff" rx="3"/><text x="${190 + Math.min(440, v * 8)}" y="${46 + i * 28}" font-size="12">${v}</text>`;
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
        report_key: config.report_key,
        config: {
          row_dimensions: config.row_dimensions,
          column_dimensions: config.column_dimensions,
          metrics: config.metrics,
          charts: config.charts,
          filters: config.filters,
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
function report() {
  router.push({
    path: `/test-management/${domain.value}/reports`,
    query: {
      physicalSubsystemId: filters.systemId,
      roundId: filters.roundId,
      cycleId: filters.cycleId,
      source: "analytics",
    },
  });
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
          <el-radio-group v-model="active.view" size="small" @change="load"
            ><el-radio-button v-for="v in viewOptions" :key="v" :value="v">{{
              v === "TABLE"
                ? "表格"
                : v === "BAR"
                  ? "柱状图"
                  : v === "LINE"
                    ? "折线图"
                    : v === "STATUS"
                      ? "状态分布"
                      : v === "CATEGORY"
                        ? "分类分布"
                        : v === "SYSTEM"
                          ? "系统分布"
                          : v === "MATRIX"
                            ? "严重×紧急"
                            : v === "OVERDUE"
                              ? "超期清单"
                              : v === "HANDLER"
                                ? "处理人视角"
                                : "执行人视角"
            }}</el-radio-button></el-radio-group
          ><span /><el-button size="small" :icon="Edit" @click="openDesigner()"
            >编辑/另存为</el-button
          ><el-button size="small" :icon="Download" @click="exportXlsx"
            >导出 xlsx</el-button
          ><el-button size="small" @click="exportImage">导出图片</el-button
          ><el-button size="small" @click="compareOpen = true"
            >跨轮次对比</el-button
          ><el-button size="small" @click="archive">归档快照</el-button
          ><el-button size="small" type="primary" @click="report"
            >生成测试报告</el-button
          >
        </div>
        <div class="cards">
          <article
            v-for="(v, k) in model.rows?.[0] || {}"
            v-show="typeof v === 'number'"
            :key="String(k)"
          >
            <small>{{ k }}</small
            ><b>{{ v }}</b>
          </article>
        </div>
        <DeliveryChart
          v-if="active.view !== 'TABLE' && model.rows?.length"
          class="chart"
          :option="chartOption"
          :aria-label="active.name + '图表'"
        /><UiDataTable
          :data="model.rows || []"
          row-key="dimension"
          border
          class="table"
          ><el-table-column
            v-for="key in columns"
            :key="key"
            :prop="key"
            :label="key"
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
              ><span v-else>{{ row[key] }}</span></template
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
        ><el-form-item label="预置模型"
          ><el-select v-model="config.report_key"
            ><el-option
              v-for="(name, key) in presetNames"
              :key="key"
              :label="name"
              :value="key" /></el-select></el-form-item
        ><el-form-item label="行维度"
          ><el-checkbox-group v-model="config.row_dimensions"
            ><el-checkbox
              v-for="x in [
                '系统',
                '目录',
                '范围',
                '案例类型',
                '轮次',
                '周期',
                '状态',
                '处理人',
              ]"
              :key="x"
              :value="x"
              >{{ x }}</el-checkbox
            ></el-checkbox-group
          ></el-form-item
        ><el-form-item label="列维度"
          ><el-checkbox-group v-model="config.column_dimensions"
            ><el-checkbox
              v-for="x in ['状态', '严重程度', '紧急程度']"
              :key="x"
              :value="x"
              >{{ x }}</el-checkbox
            ></el-checkbox-group
          ></el-form-item
        ><el-form-item label="统计指标"
          ><el-checkbox-group v-model="config.metrics"
            ><el-checkbox
              v-for="x in [
                '范围总数',
                '案例总数',
                '覆盖率',
                '执行率',
                '成功率',
                '缺陷数',
                '超期未解决数',
              ]"
              :key="x"
              :value="x"
              >{{ x }}</el-checkbox
            ></el-checkbox-group
          ></el-form-item
        ><el-form-item label="图表形式"
          ><el-checkbox-group v-model="config.charts"
            ><el-checkbox
              v-for="x in ['TABLE', 'BAR', 'PIE', 'LINE']"
              :key="x"
              :value="x"
              >{{ x }}</el-checkbox
            ></el-checkbox-group
          ></el-form-item
        ><el-form-item label="运行时筛选"
          ><el-checkbox-group v-model="config.filters"
            ><el-checkbox
              v-for="x in [
                '系统',
                '轮次',
                '周期',
                '目录',
                '时间范围',
                '案例类型',
                '缺陷分类',
              ]"
              :key="x"
              :value="x"
              >{{ x }}</el-checkbox
            ></el-checkbox-group
          ></el-form-item
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
          :label="k"
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
          :label="k"
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
}
</style>
