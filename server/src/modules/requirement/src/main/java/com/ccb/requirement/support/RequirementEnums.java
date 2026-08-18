package com.ccb.requirement.support;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 需求管理平台受控枚举（口径来自附件1 list sheet 与《需求管理基线-业务组填写》）。 */
public final class RequirementEnums {
    public static final List<String> PROJECT_TYPES = List.of("0~1 新建");
    public static final List<String> PROJECT_STATUSES = List.of("进行中", "已基线", "已关闭");
    public static final List<String> REVIEW_STATUSES = List.of("待评审", "评审中", "已评审", "已退回");
    public static final List<String> DEV_STATUSES = List.of("未开始", "开发中", "已完成", "已上线");
    public static final List<String> TEST_STATUSES = List.of("未开始", "测试中", "已通过");
    public static final List<String> CATEGORIES = List.of("功能", "流程", "产品", "批处理", "报表", "岗位", "菜单", "核算", "其他");
    public static final List<String> DIFFERENCE_TYPES = List.of("无差异", "金科有-蒙商无", "金科有-蒙商手工", "蒙商有-金科无", "双方作法有差异");
    public static final List<String> ADAPT_MODES = List.of("按原型", "参数配置", "业务清理", "少量改造", "保留现状");
    public static final List<String> HANDLE_STATUSES = List.of("双方已确认", "待行方确认", "待架构确认", "跨组沟通", "待决策");
    public static final List<String> DECISION_LEVELS = List.of("版块内", "总体组", "领导小组");
    public static final List<String> YES_NO = List.of("是", "否");
    public static final List<String> SYSTEM_STATUSES = List.of("启用", "停用");
    public static final List<String> STAGE_STATUSES = List.of("未开始", "进行中", "已完成");
    public static final List<String> REQUIREMENT_TYPES = List.of("监管", "业务", "技术");
    public static final List<String> REGULATION_CATEGORIES = List.of("国家级", "地方级", "处罚整改");
    public static final List<String> REQUIREMENT_STATUSES = List.of(
            "需求分析", "业需修订", "业需评审通过", "立项中", "软需编制", "软需评审通过", "已投产", "需求终止");
    public static final List<String> LAUNCH_MODES = List.of("常规版本", "紧急版本");
    public static final List<String> CHANGE_REVIEW_CONCLUSIONS = List.of("评审通过", "评审不通过");
    public static final List<String> CHANGE_CONCLUSION_STATUSES = List.of("审核通过", "评估工作量", "蒙商立项完成");
    public static final List<String> LEGACY_STAGES = List.of("PROPOSE", "DOCKING", "WORKLOAD", "PROJECT", "SOFT", "LAUNCH");
    public static final Map<String, String> LEGACY_STAGE_LABELS = Map.of(
            "PROPOSE", "需求提出", "DOCKING", "需求对接", "WORKLOAD", "工作量评估",
            "PROJECT", "立项", "SOFT", "软需", "LAUNCH", "投产");
    public static final Map<String, String> LEGACY_STAGE_COLUMNS = Map.of(
            "PROPOSE", "propose_stage_status", "DOCKING", "docking_stage_status",
            "WORKLOAD", "workload_stage_status", "PROJECT", "project_stage_status",
            "SOFT", "soft_stage_status", "LAUNCH", "launch_stage_status");

    public static final Map<String, Object> OPTIONS = new LinkedHashMap<>();

    static {
        OPTIONS.put("projectTypes", PROJECT_TYPES);
        OPTIONS.put("projectStatuses", PROJECT_STATUSES);
        OPTIONS.put("reviewStatuses", REVIEW_STATUSES);
        OPTIONS.put("devStatuses", DEV_STATUSES);
        OPTIONS.put("testStatuses", TEST_STATUSES);
        OPTIONS.put("categories", CATEGORIES);
        OPTIONS.put("differenceTypes", DIFFERENCE_TYPES);
        OPTIONS.put("adaptModes", ADAPT_MODES);
        OPTIONS.put("handleStatuses", HANDLE_STATUSES);
        OPTIONS.put("decisionLevels", DECISION_LEVELS);
        OPTIONS.put("yesNo", YES_NO);
        OPTIONS.put("systemStatuses", SYSTEM_STATUSES);
        OPTIONS.put("stageStatuses", STAGE_STATUSES);
        OPTIONS.put("legacyStages", LEGACY_STAGES);
        OPTIONS.put("legacyStageLabels", List.copyOf(LEGACY_STAGE_LABELS.values()));
        OPTIONS.put("legacyStageLabelMap", LEGACY_STAGE_LABELS);
        OPTIONS.put("requirementTypes", REQUIREMENT_TYPES);
        OPTIONS.put("regulationCategories", REGULATION_CATEGORIES);
        OPTIONS.put("requirementStatuses", REQUIREMENT_STATUSES);
        OPTIONS.put("launchModes", LAUNCH_MODES);
        OPTIONS.put("changeReviewConclusions", CHANGE_REVIEW_CONCLUSIONS);
        OPTIONS.put("changeConclusionStatuses", CHANGE_CONCLUSION_STATUSES);
    }

    public static final Map<String, String> FIELD_LABELS = fieldLabels();

    private RequirementEnums() {
    }

    public static boolean isOption(String field, String value) {
        Object raw = OPTIONS.get(field);
        return value == null || value.isBlank()
                || (raw instanceof List<?> options && options.contains(value));
    }

    private static Map<String, String> fieldLabels() {
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put("seq_no", "序号");
        labels.put("business_conglomerate", "事业群");
        labels.put("business_section", "业务板块");
        labels.put("business_group", "业务组");
        labels.put("requirement_no", "需求编号");
        labels.put("category", "分类");
        labels.put("name", "名称");
        labels.put("system_id", "涉及系统");
        labels.put("jinke_practice", "金科做法");
        labels.put("difference_type", "差异类型");
        labels.put("monshang_practice", "蒙商作法");
        labels.put("difference_desc", "差异描述");
        labels.put("monshang_dept", "蒙商分析部门");
        labels.put("monshang_analyst", "蒙商分析人");
        labels.put("jinke_analyst", "金科分析人");
        labels.put("adapt_mode", "适配方式");
        labels.put("handle_status", "处理状态");
        labels.put("coord_group", "协同组");
        labels.put("solution", "解决方案");
        labels.put("is_special", "是否专题");
        labels.put("decision_level", "上升决策层级");
        labels.put("decision_conclusion", "决策结论");
        labels.put("monshang_confirm_dept", "蒙商确认部门");
        labels.put("jinke_confirmer", "金科确认人");
        labels.put("review_status", "差异状态");
        labels.put("review_comment", "评审意见");
        labels.put("dev_status", "开发状态");
        labels.put("test_status", "测试状态");
        labels.put("source", "来源");
        labels.put("legacy_doc_name", "业需文档名称");
        labels.put("requirement_name", "需求名称");
        labels.put("content_summary", "需求内容简述");
        labels.put("propose_dept", "需求提出部门");
        labels.put("proposer", "需求提出人及电话");
        labels.put("monshang_ba", "蒙商 BA");
        labels.put("monshang_architect", "蒙商架构");
        labels.put("expected_launch_date", "业务期望上线时间");
        labels.put("regulator", "外部监管单位");
        labels.put("regulation_doc_no", "监管文件名称+文号");
        labels.put("regulation_desc", "监管文件内容描述");
        labels.put("regulation_launch_date", "监管要求上线时间");
        labels.put("requirement_received_date", "业需入手日");
        labels.put("requirement_type", "需求类型");
        labels.put("regulation_category", "监管分类");
        labels.put("sub_group", "分组");
        labels.put("jinke_contact", "金科对接人及电话");
        labels.put("need_jinke_arch_decision", "是否需要金科架构决策");
        labels.put("jinke_architect", "金科架构人员");
        labels.put("unified_managed", "是否纳入蒙商统一管理");
        labels.put("ba_review_date", "业需评审完成日");
        labels.put("workload_date", "工作量评估完成日");
        labels.put("finance_project_date", "财务立项完成日");
        labels.put("soft_doc_name", "软需文档名称");
        labels.put("owner_conglomerate", "主责事业群");
        labels.put("owner_system", "主责物理子系统");
        labels.put("owner_contact", "主责项目组联系人及电话");
        labels.put("involve_cooperation", "是否涉及金科引入组件协同");
        labels.put("coord_conglomerate", "协同事业群");
        labels.put("coord_system", "协同系统名称");
        labels.put("soft_submit_date", "软需提交日");
        labels.put("soft_review_date", "软需评审完成日");
        labels.put("planned_launch_date", "计划上线时间");
        labels.put("actual_launch_date", "实际上线时间");
        labels.put("launch_mode", "上线形式");
        labels.put("requirement_status", "需求状态");
        labels.put("remark", "备注");
        labels.put("change_involved", "是否涉及需求变更");
        labels.put("change_info", "需求变更信息");
        labels.put("change_review_conclusion", "变更评审结论");
        labels.put("change_conclusion_status", "变更结论及状态");
        labels.put("change_remark", "需求变更备注");
        labels.put("not_project_developed", "未立项已开发");
        labels.put("current_stage", "当前阶段");
        labels.put("propose_stage_status", "需求提出阶段状态");
        labels.put("docking_stage_status", "需求对接阶段状态");
        labels.put("workload_stage_status", "工作量评估阶段状态");
        labels.put("project_stage_status", "立项阶段状态");
        labels.put("soft_stage_status", "软需阶段状态");
        labels.put("launch_stage_status", "投产阶段状态");
        return Map.copyOf(labels);
    }
}
