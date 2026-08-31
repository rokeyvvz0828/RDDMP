#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
合并 docs/original/tech/ 下全部原始需求文档为一个文件，并在末尾追加平台能力备注。

- 原始文件内容拼接，除删除失效图片引用（kdocs 外链、本地 file:// 路径）外不做其他修改。
- 每个源文件前插入 HTML 注释分隔头，不影响 Markdown 渲染。
- 末尾追加平台能力备注（见 PLATFORM_NOTES，基于项目 README / governance/modules.yaml / web/package.json 事实整理）。
- 用法：python3 merge-original.py             （删除图片引用，默认）
       python3 merge-original.py --keep-images （保留图片引用）
"""
import os
import re
import sys
from datetime import date

# 是否删除 Markdown 图片引用（默认删除；--keep-images 可保留）
STRIP_IMAGES = "--keep-images" not in sys.argv

IMAGE_RE = re.compile(r"!\[[^\]]*\]\([^)]*\)")


def strip_images(text: str) -> str:
    """删除 Markdown 图片引用，并压缩因此产生的连续空行（3+ 换行 -> 2 个）。"""
    if not STRIP_IMAGES:
        return text
    text = IMAGE_RE.sub("", text)
    text = re.sub(r"\n{3,}", "\n\n", text)
    return text

BASE = os.path.dirname(os.path.abspath(__file__))
OUTPUT = os.path.join(BASE, "原始文档合并.md")
SCRIPT_NAME = os.path.basename(__file__)
EXCLUDE = {OUTPUT, SCRIPT_NAME, "需求整理汇总.md"}

# 固定顺序：INDEX 总索引第一，其余按文件名排序（可复现）
FIRST = "INDEX.md"


def list_sources():
    files = []
    for name in sorted(os.listdir(BASE)):
        if name == FIRST:
            continue
        if name.endswith(".md") and os.path.abspath(os.path.join(BASE, name)) not in EXCLUDE and name not in {os.path.basename(OUTPUT), SCRIPT_NAME, "需求整理汇总.md"}:
            files.append(name)
    return [FIRST] + files


def build_merged(files):
    parts = []
    parts.append("# 原始需求文档合并（自动生成）\n")
    parts.append("> 本文件由脚本自动生成：将 `docs/original/tech/` 下全部原始需求文档合并；除**删除失效图片引用**（kdocs 外链、本地 file:// 路径）外，原始内容未做其他修改；末尾追加现有平台能力备注。\n")
    parts.append(f"> 生成日期：{date.today().isoformat()}；源文件共 {len(files)} 份；图片引用：{'已删除' if STRIP_IMAGES else '保留'}。\n")
    parts.append("\n---\n")
    for f in files:
        path = os.path.join(BASE, f)
        with open(path, encoding="utf-8") as fh:
            content = fh.read()
        content = strip_images(content)
        tag = "内容原样，仅删除图片引用" if STRIP_IMAGES else "原样，未修改"
        parts.append(f"\n\n<!-- ==================== 源文件：{f}（{tag}） ==================== -->\n\n")
        parts.append(content)
    parts.append("\n\n---\n\n")
    parts.append(PLATFORM_NOTES.strip())
    return "\n".join(parts)


PLATFORM_NOTES = """
## 附：现有平台能力备注（自动生成合并后追加，非原始需求内容）

> 备注整理日期：2026-08-19。依据项目 `README.md`、`governance/modules.yaml`、`web/package.json` 等事实整理，供后续需求立项与实现评估参考。

### 1. 平台技术现状

- 后端：JDK 17、Spring Boot 3.4.4、Spring Security、MyBatis-Plus 3.5.12、Flyway（MySQL 8.4）
- 工作流：Flowable 7.0.1，业务模型到 BPMN 2.0 编译
- 前端：Vue 3、TypeScript、Vite、Element Plus、Pinia、Vue Router、Vue Flow、ECharts
- 基础设施：Docker Compose、MinIO、kkFileView 5.0.1、JWT
- 定位：**单租户**企业级管理平台基础框架，前后端分离

### 2. 可直接复用的平台能力

| 能力 | 说明 | 对应原始需求 |
|------|------|-------------|
| 认证与会话 | JWT 登录、登录超时续期、登出、登录审计 | 全部需求 |
| 组织与权限 | 组织树、用户、角色关联、RBAC 权限并集、菜单树动态路由、页面级增删改查权限、数据范围 | 全部需求（各需求角色/数据范围控制） |
| 参数管理 | 自定义参数类别、参数项、状态、登录时效配置 | 各需求字典/规则/阈值配置 |
| 主题与布局 | 多套配色、科技蓝、深浅模式、侧边/顶部/混合布局、多页签 | 前端统一风格 |
| 统一前端组件 | 表格、工具栏、表单抽屉、状态标签、空状态、组织树、用户身份等 | 各需求列表/详情/状态呈现 |
| 受控文件 | MinIO 上传 + kkFileView 在线预览、公共预览弹窗、受控下载 | 附件上传/预览/归档类需求（D3/D4/D6/D7/D9/D10/D11） |
| 工作流引擎 | Flowable 7.0.1：审批、条件网关、并行分支/汇聚、会签、同意、拒绝、退回、加签、抄送、转交、委托、终止 | 审批流类需求（D3/D4/D6/D7/D9/D11） |
| 站内消息通知 | platform/system 提供站内消息通知契约 | 各需求"站内消息通知" |
| AI 能力 | AI 提供商、模型、能力路由、统一执行入口（business/ai） | D2 物理子系统智能补全（需确认规则驱动 or 模型驱动） |
| 数据库基础设施 | Flyway 迁移、中文表/字段注释、结构快照、可重复执行导出脚本 | 全部需求建表 |
| 图表与拓扑 | 前端已有 ECharts 5.6、@vue-flow/core 1.48 | D10 仪表盘图表、D3 部署单元拓扑 |

### 3. 需求与平台能力的差距（需新建/评估）

| 需求 | 差距项 |
|------|--------|
| D3 部署单元及资源管理 | 部署单元/资源清单/架构基线/版本差异等业务实体与计算规则需新建；按环境/申请类型/资源阈值的**动态流程模板**需评估 Flowable 能力边界；资源统计异步汇总 |
| D4 网络权限管理 | 网络申请/实施工单/有效期回收业务闭环需新建；到期自动回收的定时任务 |
| D6 测试环境搭建跟踪 | 搭建计划/多级任务/依赖校验需新建；**甘特图视图**平台无现成组件，需评估（Vue Flow 可作拓扑/时序基础）；看板 |
| D7 环境变更跟踪与审核 | 变更单/参数动态配置/多级审核链路匹配/变更窗口需新建；高亮对比展示 |
| D8 批量作业管理 | 作业/执行记录/慢作业/问题闭环业务需新建；鲁班/百川无 API 对接（手动登记） |
| D9 加密机入池申请 | 申请单/办理流转/接口清单/字典需新建；重复校验、单号生成规则待确认 |
| D10 投产流程管控 | 需求/开发/测试/评审/投产全链路业务模型需新建；编号规则、投产日历、权限矩阵配置；**原始文档技术栈（Next.js/React）与平台 Vue 技术栈冲突，需立项前决策** |
| D11 架构决策管理 | 决策单/会议排期/7 日时效预警/结果发布需新建；邮件外发通道平台未接入 |
| 通用 | 邮件/企业微信通知通道未接入（多需求待确认）；报表导出 Excel 能力需评估；审计操作日志留存策略 |

### 4. 关键限制与风险提示

1. **单租户定位**：本批需求均按单租户实施；"多租户多环境部署单元"需求中的租户（客户）按可选业务属性处理，不做平台级隔离。
2. **技术栈统一**：D10 原始文档指定 Next.js 16/React 19/shadcn，与平台 Vue 3 + Element Plus 不一致，必须在立项前确认统一方案。
3. **主数据依赖**：物理子系统主数据（编号/名称/安全节点号）、环境清单为多个需求的依赖根，需先确定统一口径与维护模块。
4. **敏感信息**：SSL 证书等需加密存储；附件与表单不得包含明文密钥、口令等敏感凭据。
5. **原始文档中的"待确认"项**：未确认前不得作为开发默认值（详见 `需求整理汇总.md` 第十章）。
"""


def main():
    files = list_sources()
    merged = build_merged(files)
    with open(OUTPUT, "w", encoding="utf-8") as fh:
        fh.write(merged)
    print(f"合并完成：{OUTPUT}")
    print(f"源文件：{len(files)} 份 -> {files}")
    print(f"合并后总行数：{merged.count(chr(10)) + 1}")
    print("源文件未被修改（仅读取）")


if __name__ == "__main__":
    main()
