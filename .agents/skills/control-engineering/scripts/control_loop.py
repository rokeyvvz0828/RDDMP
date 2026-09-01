#!/usr/bin/env python3
'''维护用于 AI 项目闭环工程控制的可审计状态账本。'''

from __future__ import annotations

import argparse
import json
import os
import sys
import tempfile
from datetime import datetime, timezone
from pathlib import Path
from typing import Any


# 这些常量共同定义账本协议和阶段状态机；修改它们等同于修改控制流程契约。
SCHEMA_VERSION = 4
LEGACY_SCHEMA_VERSIONS = (2, 3)
HANDOFF_SCHEMA_VERSION = 1
MODES = ('light', 'standard', 'high-assurance')
PHASES = (
    'baseline',
    'modeling',
    'planning',
    'executing',
    'observing',
    'correcting',
    'verifying',
    'converged',
)
ALLOWED_TRANSITIONS = {
    'baseline': ('modeling',),
    'modeling': ('baseline', 'planning'),
    'planning': ('modeling', 'executing'),
    'executing': ('modeling', 'planning', 'observing'),
    'observing': ('modeling', 'executing', 'correcting', 'verifying'),
    'correcting': ('modeling', 'planning', 'executing', 'observing', 'verifying'),
    'verifying': ('modeling', 'observing', 'correcting', 'converged'),
    'converged': (),
}
# converged 是终态而不是工作阶段，因此不接收独立的阶段产物。
ARTIFACT_PHASES = PHASES[:-1]
# 阶段产物必须先满足完整结构，才能进入共享账本并参与后续门禁判断。
ARTIFACT_REQUIRED_FIELDS = {
    'baseline': (
        'objective',
        'requirements',
        'invariants',
        'constraints',
        'non_goals',
        'assumptions',
        'unknowns',
        'decisions',
        'measurement_intents',
        'baseline_status',
    ),
    'modeling': (
        'baseline_revision',
        'plant_boundary',
        'state_variables',
        'causal_paths',
        'interfaces',
        'sensors',
        'actuators',
        'measurements',
        'disturbances',
        'delays',
        'assumptions',
        'control_assessment',
        'model_status',
    ),
    'planning': (
        'baseline_revision',
        'model_revision',
        'tasks',
        'dependency_edges',
        'parallel_groups',
        'integration_points',
        'sampling_plan',
        'coverage',
        'plan_status',
    ),
    'executing': (
        'task_id',
        'status',
        'changed_surfaces',
        'requirements_addressed',
        'commands',
        'local_checks',
        'diff_summary',
        'invariants_checked',
        'disturbances',
        'assumptions_falsified',
        'unresolved_items',
        'recommended_next',
    ),
    'observing': (
        'sample_label',
        'task_ids',
        'baseline_revision',
        'measurements',
        'feedback',
        'disturbances',
        'coverage_gaps',
        'error_counts',
        'observation_status',
    ),
    'correcting': (
        'feedback_id',
        'decision',
        'decision_reason',
        'dynamic_pattern',
        'causal_assessment',
        'control_action',
        'resolution',
        'residual_risk',
    ),
    'verifying': (
        'baseline_revision',
        'requirement_results',
        'task_results',
        'feedback_summary',
        'sample_trend',
        'regression_checks',
        'invariant_results',
        'scope_audit',
        'disturbance_resilience',
        'residual_risks',
        'gate_result',
        'route_reason',
    ),
}
# 执行前必须明确任务边界、验收、回滚和停止条件，防止任务 Agent 自行扩大范围。
TASK_CONTRACT_FIELDS = (
    'write_scope',
    'invariants',
    'interface_contracts',
    'non_goals',
    'action_bounds',
    'acceptance',
    'evidence_required',
    'rollback',
    'stop_conditions',
    'escalation_conditions',
)
TASK_STATUSES = ('planned', 'active', 'observing', 'correcting', 'verified', 'blocked')
DECISIONS = ('accept', 'reject', 'defer', 'escalate')
SEVERITIES = ('P0', 'P1', 'P2', 'P3')
CONFIDENCES = ('low', 'medium', 'high')
ERROR_WEIGHTS = {'P0': 100, 'P1': 20, 'P2': 5, 'P3': 1}


class LedgerError(Exception):
    pass


def now() -> str:
    return datetime.now(timezone.utc).isoformat(timespec='seconds')


def infer_legacy_phase(data: dict[str, Any]) -> str:
    # schema v2 没有显式阶段，只能根据任务、反馈和模型完成度保守推断。
    tasks = data.get('tasks', [])
    feedback = data.get('feedback', [])
    if tasks:
        actionable = any(
            item.get('resolved_at') is None
            and item.get('decision') in (None, 'accept', 'escalate')
            for item in feedback
        )
        if actionable:
            return 'correcting'
        if all(task.get('status') == 'verified' for task in tasks):
            return 'verifying'
        if any(task.get('status') in ('observing', 'correcting') for task in tasks):
            return 'observing'
        return 'executing'
    model = data.get('control_model', {})
    if (
        model.get('plant')
        and model.get('sensors')
        and model.get('actuators')
        and model.get('measurements')
    ):
        return 'planning'
    if any(model.get(key) for key in ('plant', 'sensors', 'actuators', 'measurements')):
        return 'modeling'
    return 'baseline'


def upgrade_legacy_state(data: dict[str, Any], version: int) -> dict[str, Any]:
    if version == 3:
        data['schema_version'] = SCHEMA_VERSION
        data.setdefault('predevelopment', None)
        return data

    phase = infer_legacy_phase(data)
    data['schema_version'] = SCHEMA_VERSION
    data['phase'] = phase
    data['phase_history'] = [
        {
            'from': None,
            'to': phase,
            'evidence': f'从 schema_version={version} 自动推断阶段',
            'recorded_at': now(),
        }
    ]
    data['stage_artifacts'] = {phase_name: [] for phase_name in ARTIFACT_PHASES}
    data['predevelopment'] = None
    return data


def read_state(path: Path) -> dict[str, Any]:
    # 所有命令都从这里装载账本，以便集中处理编码、版本迁移和结构归一化。
    try:
        data = json.loads(path.read_text(encoding='utf-8-sig'))
    except FileNotFoundError as exc:
        raise LedgerError(f'状态文件不存在：{path}') from exc
    except json.JSONDecodeError as exc:
        raise LedgerError(f'状态文件不是有效 JSON：{exc}') from exc
    version = data.get('schema_version')
    if version in LEGACY_SCHEMA_VERSIONS:
        return upgrade_legacy_state(data, version)
    if version != SCHEMA_VERSION:
        raise LedgerError(f'不支持的 schema_version：{version!r}，当前需要 {SCHEMA_VERSION}')
    phase = data.get('phase')
    if phase not in PHASES:
        raise LedgerError(f'状态文件包含未知阶段：{phase!r}')
    if not isinstance(data.get('phase_history'), list):
        raise LedgerError('状态文件缺少有效的 phase_history')
    if not isinstance(data.get('stage_artifacts'), dict):
        data['stage_artifacts'] = {phase_name: [] for phase_name in ARTIFACT_PHASES}
    for phase_name in ARTIFACT_PHASES:
        data['stage_artifacts'].setdefault(phase_name, [])
    data.setdefault('predevelopment', None)
    return data


def write_state(path: Path, data: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    data['revision'] = int(data.get('revision', 0)) + 1
    data['updated_at'] = now()
    payload = json.dumps(data, indent=2, ensure_ascii=False) + '\n'
    # 先写同目录临时文件并刷盘，再原子替换，避免进程中断留下半个 JSON。
    fd, temporary = tempfile.mkstemp(prefix=f'.{path.name}.', suffix='.tmp', dir=path.parent)
    try:
        with os.fdopen(fd, 'w', encoding='utf-8', newline='\n') as handle:
            handle.write(payload)
            handle.flush()
            os.fsync(handle.fileno())
        os.replace(temporary, path)
    finally:
        if os.path.exists(temporary):
            os.unlink(temporary)


def parse_requirements(values: list[str]) -> list[dict[str, str]]:
    requirements: list[dict[str, str]] = []
    seen: set[str] = set()
    for index, value in enumerate(values, start=1):
        if '=' in value:
            requirement_id, statement = value.split('=', 1)
        else:
            requirement_id, statement = f'R{index}', value
        requirement_id = requirement_id.strip()
        statement = statement.strip()
        if not requirement_id or not statement:
            raise LedgerError('需求必须使用 ID=内容，或提供非空内容')
        if requirement_id in seen:
            raise LedgerError(f'需求 ID 重复：{requirement_id}')
        seen.add(requirement_id)
        requirements.append({'id': requirement_id, 'statement': statement, 'priority': 'must'})
    if not requirements:
        raise LedgerError('至少需要一条需求')
    return requirements


def parse_error_counts(values: list[str]) -> dict[str, int]:
    counts = {severity: 0 for severity in SEVERITIES}
    for value in values:
        if '=' not in value:
            raise LedgerError('误差计数必须使用 P0=数量 到 P3=数量 的格式')
        severity, raw_count = value.split('=', 1)
        severity = severity.strip().upper()
        if severity not in counts:
            raise LedgerError(f'未知严重度：{severity}')
        try:
            count = int(raw_count)
        except ValueError as exc:
            raise LedgerError(f'误差数量必须是整数：{value}') from exc
        if count < 0:
            raise LedgerError(f'误差数量不能为负数：{value}')
        counts[severity] += count
    return counts


def parse_measurements(values: list[str], requirement_ids: set[str]) -> list[dict[str, str]]:
    measurements: list[dict[str, str]] = []
    for value in values:
        if '=' not in value:
            raise LedgerError('需求测量必须使用 需求ID=传感器或验收方式')
        requirement_id, sensor = value.split('=', 1)
        requirement_id = requirement_id.strip()
        sensor = sensor.strip()
        if requirement_id not in requirement_ids:
            raise LedgerError(f'需求测量引用未知 ID：{requirement_id}')
        if not sensor:
            raise LedgerError(f'需求 {requirement_id} 的测量方式不能为空')
        measurement = {'requirement': requirement_id, 'sensor': sensor}
        if measurement not in measurements:
            measurements.append(measurement)
    return measurements


def find(items: list[dict[str, Any]], key: str, value: str, label: str) -> dict[str, Any]:
    for item in items:
        if item.get(key) == value:
            return item
    raise LedgerError(f'未知{label}：{value}')


def require_fields(value: dict[str, Any], fields: tuple[str, ...], label: str) -> None:
    missing = [field for field in fields if field not in value]
    if missing:
        missing_text = ', '.join(missing)
        raise LedgerError(f'{label}缺少字段：{missing_text}')


def require_list_fields(value: dict[str, Any], fields: tuple[str, ...], label: str) -> None:
    for field in fields:
        if not isinstance(value.get(field), list):
            raise LedgerError(f'{label}.{field} 必须是数组')


# 以下验证器在修改账本前快速失败，避免部分合法的数据污染共享事实源。
def validate_requirement_artifact(requirement: Any, index: int) -> None:
    if not isinstance(requirement, dict):
        raise LedgerError(f'baseline.requirements[{index}] 必须是对象')
    require_fields(
        requirement,
        ('id', 'statement', 'acceptance', 'priority', 'source', 'counterexample'),
        f'baseline.requirements[{index}]',
    )
    require_list_fields(
        requirement,
        ('acceptance', 'counterexample'),
        f'baseline.requirements[{index}]',
    )
    if not str(requirement['id']).strip() or not str(requirement['statement']).strip():
        raise LedgerError(f'baseline.requirements[{index}] 的 id 和 statement 不能为空')


def validate_task_spec(task: Any, index: int) -> None:
    if not isinstance(task, dict):
        raise LedgerError(f'planning.tasks[{index}] 必须是对象')
    require_fields(
        task,
        (
            'id',
            'goal',
            'requirement_ids',
            'prerequisites',
            'input_facts',
            'write_scope',
            'invariants',
            'interface_contracts',
            'non_goals',
            'action_bounds',
            'acceptance_checks',
            'evidence_required',
            'rollback',
            'stop_conditions',
            'escalation_conditions',
        ),
        f'planning.tasks[{index}]',
    )
    require_list_fields(
        task,
        (
            'requirement_ids',
            'prerequisites',
            'input_facts',
            'write_scope',
            'invariants',
            'interface_contracts',
            'non_goals',
            'action_bounds',
            'acceptance_checks',
            'evidence_required',
            'stop_conditions',
            'escalation_conditions',
        ),
        f'planning.tasks[{index}]',
    )
    if not str(task['id']).strip() or not str(task['goal']).strip():
        raise LedgerError(f'planning.tasks[{index}] 的 id 和 goal 不能为空')
    if not task['requirement_ids'] or not task['write_scope'] or not task['acceptance_checks']:
        raise LedgerError(
            f'planning.tasks[{index}] 至少需要 requirement_ids、write_scope 和 acceptance_checks'
        )
    if not task['action_bounds'] or not task['evidence_required']:
        raise LedgerError(
            f'planning.tasks[{index}] 至少需要 action_bounds 和 evidence_required'
        )
    if not str(task['rollback']).strip():
        raise LedgerError(f'planning.tasks[{index}].rollback 不能为空')
    if not task['stop_conditions'] or not task['escalation_conditions']:
        raise LedgerError(
            f'planning.tasks[{index}] 至少需要 stop_conditions 和 escalation_conditions'
        )


def validate_stage_artifact(phase: str, artifact: Any) -> dict[str, Any]:
    if not isinstance(artifact, dict):
        raise LedgerError('阶段产物必须是 JSON 对象')
    require_fields(artifact, ARTIFACT_REQUIRED_FIELDS[phase], phase)
    # 先检查各阶段共有的必需字段，再检查该阶段特有的语义和枚举值。
    if phase == 'baseline':
        requirements = artifact['requirements']
        if not isinstance(requirements, list) or not requirements:
            raise LedgerError('baseline.requirements 必须是非空数组')
        for index, requirement in enumerate(requirements):
            validate_requirement_artifact(requirement, index)
        require_list_fields(
            artifact,
            (
                'invariants',
                'constraints',
                'non_goals',
                'assumptions',
                'unknowns',
                'decisions',
                'measurement_intents',
            ),
            phase,
        )
        if artifact['baseline_status'] not in ('ready', 'blocked'):
            raise LedgerError('baseline_status 必须是 ready 或 blocked')
    elif phase == 'modeling':
        require_list_fields(
            artifact,
            (
                'state_variables',
                'causal_paths',
                'interfaces',
                'sensors',
                'actuators',
                'measurements',
                'disturbances',
                'delays',
                'assumptions',
                'control_assessment',
            ),
            phase,
        )
        if not isinstance(artifact['plant_boundary'], dict):
            raise LedgerError('modeling.plant_boundary 必须是对象')
        if artifact['model_status'] not in ('ready', 'blocked'):
            raise LedgerError('model_status 必须是 ready 或 blocked')
    elif phase == 'planning':
        require_list_fields(
            artifact,
            (
                'tasks',
                'dependency_edges',
                'parallel_groups',
                'integration_points',
                'sampling_plan',
                'coverage',
            ),
            phase,
        )
        for index, task in enumerate(artifact['tasks']):
            validate_task_spec(task, index)
        if artifact['plan_status'] not in ('ready', 'blocked'):
            raise LedgerError('plan_status 必须是 ready 或 blocked')
    elif phase == 'executing':
        require_list_fields(
            artifact,
            (
                'changed_surfaces',
                'requirements_addressed',
                'commands',
                'local_checks',
                'diff_summary',
                'invariants_checked',
                'disturbances',
                'assumptions_falsified',
                'unresolved_items',
            ),
            phase,
        )
        if artifact['status'] not in ('implemented', 'partial', 'blocked', 'boundary-invalid'):
            raise LedgerError('executing.status 值无效')
    elif phase == 'observing':
        require_list_fields(
            artifact,
            ('task_ids', 'measurements', 'feedback', 'disturbances', 'coverage_gaps'),
            phase,
        )
        counts = artifact['error_counts']
        if not isinstance(counts, dict) or any(severity not in counts for severity in SEVERITIES):
            raise LedgerError('observing.error_counts 必须包含 P0 到 P3')
        if artifact['observation_status'] not in ('complete', 'incomplete', 'sensor-invalid'):
            raise LedgerError('observation_status 值无效')
    elif phase == 'correcting':
        require_list_fields(artifact, ('residual_risk',), phase)
        if artifact['decision'] not in DECISIONS:
            raise LedgerError('correcting.decision 值无效')
    elif phase == 'verifying':
        require_list_fields(
            artifact,
            (
                'requirement_results',
                'task_results',
                'sample_trend',
                'regression_checks',
                'invariant_results',
                'disturbance_resilience',
                'residual_risks',
            ),
            phase,
        )
        allowed = (
            'pass',
            'return-to-observing',
            'return-to-correcting',
            'return-to-modeling',
            'blocked',
        )
        if artifact['gate_result'] not in allowed:
            raise LedgerError('verifying.gate_result 值无效')
    return artifact


def require_non_empty_string(value: Any, label: str) -> str:
    if not isinstance(value, str) or not value.strip():
        raise LedgerError(f'{label} 必须是非空字符串')
    return value.strip()


def validate_approval(value: Any, label: str) -> None:
    if not isinstance(value, dict):
        raise LedgerError(f'{label} 必须是对象')
    require_fields(value, ('status', 'evidence', 'confirmed_at'), label)
    if value['status'] != 'approved':
        raise LedgerError(f'{label}.status 必须是 approved')
    require_non_empty_string(value['evidence'], f'{label}.evidence')
    require_non_empty_string(value['confirmed_at'], f'{label}.confirmed_at')


def validate_handoff_task(task: Any, index: int) -> None:
    label = f'implementation_plan.tasks[{index}]'
    if not isinstance(task, dict):
        raise LedgerError(f'{label} 必须是对象')
    require_fields(
        task,
        (
            'id',
            'goal',
            'requirement_ids',
            'prerequisites',
            'input_facts',
            'files',
            'interfaces',
            'steps',
            'acceptance_checks',
            'risks',
            'rollback',
            'stop_conditions',
            'escalation_conditions',
        ),
        label,
    )
    require_list_fields(
        task,
        (
            'requirement_ids',
            'prerequisites',
            'input_facts',
            'steps',
            'acceptance_checks',
            'risks',
            'stop_conditions',
            'escalation_conditions',
        ),
        label,
    )
    require_non_empty_string(task['id'], f'{label}.id')
    require_non_empty_string(task['goal'], f'{label}.goal')
    if not task['requirement_ids']:
        raise LedgerError(f'{label}.requirement_ids 不能为空')
    if not task['steps'] or not task['acceptance_checks']:
        raise LedgerError(f'{label} 必须包含步骤和验收检查')
    if not task['stop_conditions'] or not task['escalation_conditions']:
        raise LedgerError(f'{label} 必须包含停止和升级条件')
    require_non_empty_string(task['rollback'], f'{label}.rollback')

    files = task['files']
    if not isinstance(files, dict):
        raise LedgerError(f'{label}.files 必须是对象')
    require_list_fields(files, ('create', 'modify', 'test'), f'{label}.files')
    if not any(files[field] for field in ('create', 'modify', 'test')):
        raise LedgerError(f'{label}.files 至少需要一个文件路径')

    interfaces = task['interfaces']
    if not isinstance(interfaces, dict):
        raise LedgerError(f'{label}.interfaces 必须是对象')
    require_list_fields(interfaces, ('consumes', 'produces'), f'{label}.interfaces')

    step_ids: set[str] = set()
    for step_index, step in enumerate(task['steps']):
        step_label = f'{label}.steps[{step_index}]'
        if not isinstance(step, dict):
            raise LedgerError(f'{step_label} 必须是对象')
        require_fields(step, ('id', 'action', 'command', 'expected', 'evidence'), step_label)
        step_id = require_non_empty_string(step['id'], f'{step_label}.id')
        if step_id in step_ids:
            raise LedgerError(f'{label} 包含重复步骤 ID：{step_id}')
        step_ids.add(step_id)
        require_non_empty_string(step['action'], f'{step_label}.action')
        if step['command'] is not None:
            require_non_empty_string(step['command'], f'{step_label}.command')
        require_non_empty_string(step['expected'], f'{step_label}.expected')
        require_non_empty_string(step['evidence'], f'{step_label}.evidence')


def dependency_cycle(task_ids: set[str], edges: list[dict[str, Any]]) -> bool:
    outgoing = {task_id: [] for task_id in task_ids}
    indegree = {task_id: 0 for task_id in task_ids}
    for edge in edges:
        source = str(edge['from'])
        target = str(edge['to'])
        outgoing[source].append(target)
        indegree[target] += 1
    # 使用 Kahn 拓扑排序；无法访问全部任务就说明依赖图中存在环。
    ready = [task_id for task_id, degree in indegree.items() if degree == 0]
    visited = 0
    while ready:
        source = ready.pop()
        visited += 1
        for target in outgoing[source]:
            indegree[target] -= 1
            if indegree[target] == 0:
                ready.append(target)
    return visited != len(task_ids)


def validate_predevelopment_handoff(value: Any) -> dict[str, Any]:
    # 交接包视为外部输入：同时校验批准证据、引用完整性、依赖图和需求覆盖。
    label = 'PredevelopmentHandoff'
    if not isinstance(value, dict):
        raise LedgerError(f'{label} 必须是 JSON 对象')
    require_fields(
        value,
        ('schema_version', 'topic', 'handoff_status', 'created_at', 'design',
         'implementation_plan', 'control_seed'),
        label,
    )
    if value['schema_version'] != HANDOFF_SCHEMA_VERSION:
        raise LedgerError(
            f'不支持的交接包 schema_version：{value["schema_version"]!r}，'
            f'当前需要 {HANDOFF_SCHEMA_VERSION}'
        )
    topic = require_non_empty_string(value['topic'], f'{label}.topic')
    require_non_empty_string(value['created_at'], f'{label}.created_at')
    if value['handoff_status'] != 'approved':
        raise LedgerError('只有 handoff_status=approved 的交接包可以导入')

    design = value['design']
    if not isinstance(design, dict):
        raise LedgerError('design 必须是对象')
    require_fields(
        design,
        (
            'schema_version',
            'topic',
            'design_revision',
            'design_status',
            'human_document',
            'objective',
            'users',
            'requirements',
            'invariants',
            'constraints',
            'non_goals',
            'selected_approach',
            'architecture',
            'error_handling',
            'quality_attributes',
            'verification_strategy',
            'assumptions',
            'unknowns',
            'decisions',
            'risks',
            'approval',
        ),
        'design',
    )
    if design['schema_version'] != HANDOFF_SCHEMA_VERSION:
        raise LedgerError('design.schema_version 必须为 1')
    if design['topic'] != topic:
        raise LedgerError('design.topic 必须与交接包 topic 一致')
    if design['design_status'] != 'approved':
        raise LedgerError('design.design_status 必须是 approved')
    if not isinstance(design['design_revision'], int) or design['design_revision'] < 1:
        raise LedgerError('design.design_revision 必须是正整数')
    require_non_empty_string(design['human_document'], 'design.human_document')
    require_non_empty_string(design['objective'], 'design.objective')
    require_list_fields(
        design,
        (
            'users',
            'requirements',
            'invariants',
            'constraints',
            'non_goals',
            'error_handling',
            'verification_strategy',
            'assumptions',
            'unknowns',
            'decisions',
            'risks',
        ),
        'design',
    )
    if not design['requirements']:
        raise LedgerError('design.requirements 必须是非空数组')
    requirement_ids: set[str] = set()
    must_ids: set[str] = set()
    for index, requirement in enumerate(design['requirements']):
        validate_requirement_artifact(requirement, index)
        requirement_id = str(requirement['id'])
        if requirement_id in requirement_ids:
            raise LedgerError(f'design.requirements 包含重复 ID：{requirement_id}')
        requirement_ids.add(requirement_id)
        if requirement['priority'] not in ('must', 'should', 'could'):
            raise LedgerError(f'需求 {requirement_id} 的 priority 值无效')
        if not requirement['acceptance'] or not requirement['counterexample']:
            raise LedgerError(f'需求 {requirement_id} 必须包含验收条件和反例')
        if requirement['priority'] == 'must':
            must_ids.add(requirement_id)
    if not must_ids:
        raise LedgerError('design 至少需要一条 must 需求')
    for index, unknown in enumerate(design['unknowns']):
        if not isinstance(unknown, dict) or 'blocking' not in unknown:
            raise LedgerError(f'design.unknowns[{index}] 必须包含 blocking')
        if unknown['blocking'] is True:
            raise LedgerError('design 存在 blocking=true 的未知项，不能导入')
    if not isinstance(design['selected_approach'], dict):
        raise LedgerError('design.selected_approach 必须是对象')
    if not isinstance(design['architecture'], dict):
        raise LedgerError('design.architecture 必须是对象')
    if not isinstance(design['quality_attributes'], dict):
        raise LedgerError('design.quality_attributes 必须是对象')
    validate_approval(design['approval'], 'design.approval')

    plan = value['implementation_plan']
    if not isinstance(plan, dict):
        raise LedgerError('implementation_plan 必须是对象')
    require_fields(
        plan,
        (
            'plan_revision',
            'human_document',
            'plan_status',
            'global_constraints',
            'file_map',
            'tasks',
            'dependency_edges',
            'parallel_groups',
            'integration_checks',
            'coverage',
            'approval',
        ),
        'implementation_plan',
    )
    if not isinstance(plan['plan_revision'], int) or plan['plan_revision'] < 1:
        raise LedgerError('implementation_plan.plan_revision 必须是正整数')
    require_non_empty_string(plan['human_document'], 'implementation_plan.human_document')
    if plan['plan_status'] != 'ready':
        raise LedgerError('implementation_plan.plan_status 必须是 ready')
    require_list_fields(
        plan,
        (
            'global_constraints',
            'file_map',
            'tasks',
            'dependency_edges',
            'parallel_groups',
            'integration_checks',
            'coverage',
        ),
        'implementation_plan',
    )
    validate_approval(plan['approval'], 'implementation_plan.approval')
    if not plan['tasks']:
        raise LedgerError('implementation_plan.tasks 必须是非空数组')

    for index, item in enumerate(plan['file_map']):
        file_label = f'implementation_plan.file_map[{index}]'
        if not isinstance(item, dict):
            raise LedgerError(f'{file_label} 必须是对象')
        require_fields(item, ('path', 'status', 'responsibility', 'evidence'), file_label)
        require_non_empty_string(item['path'], f'{file_label}.path')
        require_non_empty_string(item['responsibility'], f'{file_label}.responsibility')
        require_non_empty_string(item['evidence'], f'{file_label}.evidence')
        if item['status'] not in ('existing', 'candidate-new'):
            raise LedgerError(f'{file_label}.status 必须是 existing 或 candidate-new')

    task_ids: set[str] = set()
    for index, task in enumerate(plan['tasks']):
        validate_handoff_task(task, index)
        task_id = str(task['id'])
        if task_id in task_ids:
            raise LedgerError(f'implementation_plan.tasks 包含重复 ID：{task_id}')
        task_ids.add(task_id)
        unknown_requirements = set(task['requirement_ids']) - requirement_ids
        if unknown_requirements:
            raise LedgerError(
                f'任务 {task_id} 引用未知需求：{", ".join(sorted(unknown_requirements))}'
            )
    for task in plan['tasks']:
        unknown_prerequisites = set(task['prerequisites']) - task_ids
        if unknown_prerequisites:
            raise LedgerError(
                f'任务 {task["id"]} 引用未知前置任务：'
                f'{", ".join(sorted(unknown_prerequisites))}'
            )
        if task['id'] in task['prerequisites']:
            raise LedgerError(f'任务 {task["id"]} 不得依赖自身')

    edges: list[dict[str, Any]] = []
    for index, edge in enumerate(plan['dependency_edges']):
        edge_label = f'implementation_plan.dependency_edges[{index}]'
        if not isinstance(edge, dict):
            raise LedgerError(f'{edge_label} 必须是对象')
        require_fields(edge, ('from', 'to', 'reason'), edge_label)
        source = str(edge['from'])
        target = str(edge['to'])
        if source not in task_ids or target not in task_ids:
            raise LedgerError(f'{edge_label} 引用了未知任务')
        if source == target:
            raise LedgerError(f'{edge_label} 不得形成自依赖')
        require_non_empty_string(edge['reason'], f'{edge_label}.reason')
        edges.append(edge)
    if dependency_cycle(task_ids, edges):
        raise LedgerError('implementation_plan.dependency_edges 存在依赖环')

    for index, group in enumerate(plan['parallel_groups']):
        if not isinstance(group, list) or not group:
            raise LedgerError(f'implementation_plan.parallel_groups[{index}] 必须是非空数组')
        unknown_tasks = set(group) - task_ids
        if unknown_tasks:
            raise LedgerError(
                f'并行组引用未知任务：{", ".join(sorted(unknown_tasks))}'
            )

    for index, check in enumerate(plan['integration_checks']):
        check_label = f'implementation_plan.integration_checks[{index}]'
        if not isinstance(check, dict):
            raise LedgerError(f'{check_label} 必须是对象')
        require_fields(check, ('after_tasks', 'command', 'expected'), check_label)
        if not isinstance(check['after_tasks'], list) or not check['after_tasks']:
            raise LedgerError(f'{check_label}.after_tasks 必须是非空数组')
        if set(check['after_tasks']) - task_ids:
            raise LedgerError(f'{check_label} 引用了未知任务')
        require_non_empty_string(check['command'], f'{check_label}.command')
        require_non_empty_string(check['expected'], f'{check_label}.expected')

    # coverage 声明必须与任务自身的 requirement_ids 一致，不能只靠表格宣称覆盖。
    covered_must: set[str] = set()
    for index, item in enumerate(plan['coverage']):
        coverage_label = f'implementation_plan.coverage[{index}]'
        if not isinstance(item, dict):
            raise LedgerError(f'{coverage_label} 必须是对象')
        require_fields(item, ('requirement_id', 'task_ids'), coverage_label)
        requirement_id = str(item['requirement_id'])
        if requirement_id not in requirement_ids:
            raise LedgerError(f'{coverage_label} 引用未知需求：{requirement_id}')
        if not isinstance(item['task_ids'], list) or not item['task_ids']:
            raise LedgerError(f'{coverage_label}.task_ids 必须是非空数组')
        unknown_tasks = set(item['task_ids']) - task_ids
        if unknown_tasks:
            raise LedgerError(f'{coverage_label} 引用未知任务')
        mapped = [
            task_id
            for task_id in item['task_ids']
            if requirement_id in next(
                task['requirement_ids'] for task in plan['tasks'] if task['id'] == task_id
            )
        ]
        if not mapped:
            raise LedgerError(f'{coverage_label} 没有任务实际声明该需求')
        if requirement_id in must_ids:
            covered_must.add(requirement_id)
    missing_coverage = must_ids - covered_must
    if missing_coverage:
        raise LedgerError(
            f'实施计划未覆盖必须需求：{", ".join(sorted(missing_coverage))}'
        )

    # 开发前只能提供建模候选；未经 modeling 验证的内容不得升级为控制事实。
    seed = value['control_seed']
    if not isinstance(seed, dict):
        raise LedgerError('control_seed 必须是对象')
    require_fields(
        seed,
        (
            'seed_status',
            'plant_boundary_candidates',
            'state_variable_candidates',
            'interface_candidates',
            'sensor_candidates',
            'actuator_candidates',
            'disturbance_candidates',
            'delay_candidates',
            'assumptions',
        ),
        'control_seed',
    )
    if seed['seed_status'] != 'hypotheses-only':
        raise LedgerError('control_seed.seed_status 必须是 hypotheses-only')
    require_list_fields(
        seed,
        (
            'plant_boundary_candidates',
            'state_variable_candidates',
            'interface_candidates',
            'sensor_candidates',
            'actuator_candidates',
            'disturbance_candidates',
            'delay_candidates',
            'assumptions',
        ),
        'control_seed',
    )
    return value


def artifact_text(value: Any) -> str:
    if isinstance(value, str):
        return value
    return json.dumps(value, ensure_ascii=False, sort_keys=True)


# 阶段产物既是审计记录，也是账本当前视图的来源；这些函数负责同步可查询状态。
def sync_baseline_artifact(data: dict[str, Any], artifact: dict[str, Any]) -> None:
    data['objective'] = str(artifact['objective']).strip()
    data['requirements'] = artifact['requirements']
    data['invariants'] = artifact['invariants']
    data['constraints'] = artifact['constraints']
    data['non_goals'] = artifact['non_goals']


def sync_model_artifact(data: dict[str, Any], artifact: dict[str, Any]) -> None:
    model = data['control_model']
    model['plant'] = artifact_text(artifact['plant_boundary'])
    model['sensors'] = [artifact_text(item) for item in artifact['sensors']]
    model['actuators'] = [artifact_text(item) for item in artifact['actuators']]
    model['disturbances'] = [artifact_text(item) for item in artifact['disturbances']]
    model['assumptions'] = [artifact_text(item) for item in artifact['assumptions']]
    measurements: list[dict[str, str]] = []
    for item in artifact['measurements']:
        if not isinstance(item, dict) or 'requirement_id' not in item:
            raise LedgerError('modeling.measurements 每项必须包含 requirement_id')
        sensor_value = item.get('sensor_ids', item.get('sensor', item))
        measurements.append(
            {
                'requirement': str(item['requirement_id']),
                'sensor': artifact_text(sensor_value),
            }
        )
    model['measurements'] = measurements


def task_from_spec(spec: dict[str, Any], previous: dict[str, Any] | None) -> dict[str, Any]:
    task = {
        'id': str(spec['id']),
        'goal': str(spec['goal']),
        'requirements': spec['requirement_ids'],
        'prerequisites': spec['prerequisites'],
        'input_facts': spec['input_facts'],
        'write_scope': spec['write_scope'],
        'invariants': spec['invariants'],
        'interface_contracts': spec['interface_contracts'],
        'non_goals': spec['non_goals'],
        'action_bounds': spec['action_bounds'],
        'acceptance': spec['acceptance_checks'],
        'evidence_required': spec['evidence_required'],
        'rollback': spec['rollback'],
        'stop_conditions': spec['stop_conditions'],
        'escalation_conditions': spec['escalation_conditions'],
    }
    # 任务契约完全未变时保留执行状态和证据；任何边界变化都会退回 planned。
    previous_evidence = previous.get('evidence', []) if previous else []
    previous_status = previous.get('status', 'planned') if previous else 'planned'
    comparable = dict(task)
    unchanged = previous is not None and all(previous.get(key) == value for key, value in comparable.items())
    task['status'] = previous_status if unchanged else 'planned'
    task['evidence'] = previous_evidence
    task['updated_at'] = now()
    return task


def sync_plan_artifact(data: dict[str, Any], artifact: dict[str, Any]) -> None:
    previous = {task['id']: task for task in data['tasks']}
    planned_ids = {str(task['id']) for task in artifact['tasks']}
    # 带反馈的任务不能被新计划静默删除，否则反馈会失去可追踪对象。
    for task_id in set(previous) - planned_ids:
        if any(item['task'] == task_id for item in data['feedback']):
            raise LedgerError(f'计划不能删除仍有关联反馈的任务：{task_id}')
    known_requirements = {item['id'] for item in data['requirements']}
    synchronized: list[dict[str, Any]] = []
    for spec in artifact['tasks']:
        task_id = str(spec['id'])
        unknown = sorted(set(spec['requirement_ids']) - known_requirements)
        if unknown:
            unknown_text = ', '.join(unknown)
            raise LedgerError(f'任务 {task_id} 引用未知需求：{unknown_text}')
        synchronized.append(task_from_spec(spec, previous.get(task_id)))
    data['tasks'] = synchronized


def command_record_artifact(args: argparse.Namespace) -> None:
    path = Path(args.state)
    data = read_state(path)
    # 只接收当前阶段产物，防止先写后续结论再反向补证据。
    if data['phase'] != args.phase:
        raise LedgerError(f'当前阶段为 {data["phase"]}，不能记录 {args.phase} 产物')
    artifact_path = Path(args.input)
    try:
        artifact = json.loads(artifact_path.read_text(encoding='utf-8-sig'))
    except FileNotFoundError as exc:
        raise LedgerError(f'阶段产物文件不存在：{artifact_path}') from exc
    except json.JSONDecodeError as exc:
        raise LedgerError(f'阶段产物不是有效 JSON：{exc}') from exc
    artifact = validate_stage_artifact(args.phase, artifact)
    if args.phase == 'baseline':
        sync_baseline_artifact(data, artifact)
    elif args.phase == 'modeling':
        sync_model_artifact(data, artifact)
    elif args.phase == 'planning':
        sync_plan_artifact(data, artifact)
    records = data['stage_artifacts'].setdefault(args.phase, [])
    records.append(
        {
            'revision': len(records) + 1,
            'evidence': args.evidence,
            'recorded_at': now(),
            'data': artifact,
        }
    )
    write_state(path, data)
    print(f'已记录 {args.phase} 阶段产物：revision={len(records)}')


def command_init(args: argparse.Namespace) -> None:
    path = Path(args.state)
    if path.exists() and not args.force:
        raise LedgerError(f'状态文件已存在：{path}；使用 --force 才能替换')
    timestamp = now()
    requirements = parse_requirements(args.requirement)
    requirement_ids = {requirement['id'] for requirement in requirements}
    data: dict[str, Any] = {
        'schema_version': SCHEMA_VERSION,
        'revision': 0,
        'created_at': timestamp,
        'updated_at': timestamp,
        'mode': args.mode,
        'phase': 'baseline',
        'phase_history': [
            {
                'from': None,
                'to': 'baseline',
                'evidence': '初始化需求基准阶段',
                'recorded_at': timestamp,
            }
        ],
        'stage_artifacts': {phase_name: [] for phase_name in ARTIFACT_PHASES},
        'objective': args.objective.strip(),
        'requirements': requirements,
        'invariants': args.invariant,
        'constraints': args.constraint,
        'non_goals': args.non_goal,
        'predevelopment': None,
        'control_model': {
            'plant': args.plant.strip(),
            'sensors': args.sensor,
            'actuators': args.actuator,
            'disturbances': args.disturbance,
            'assumptions': args.assumption,
            'measurements': parse_measurements(args.measurement, requirement_ids),
        },
        'tasks': [],
        'feedback': [],
        'samples': [],
    }
    if not data['objective']:
        raise LedgerError('控制目标不能为空')
    write_state(path, data)
    print(f'已初始化控制账本：{path}')


def command_import_handoff(args: argparse.Namespace) -> None:
    path = Path(args.state)
    if path.exists() and not args.force:
        raise LedgerError(f'状态文件已存在：{path}；使用 --force 才能替换')
    handoff_path = Path(args.input)
    try:
        handoff = json.loads(handoff_path.read_text(encoding='utf-8-sig'))
    except FileNotFoundError as exc:
        raise LedgerError(f'开发前交接包不存在：{handoff_path}') from exc
    except json.JSONDecodeError as exc:
        raise LedgerError(f'开发前交接包不是有效 JSON：{exc}') from exc

    handoff = validate_predevelopment_handoff(handoff)
    design = handoff['design']
    plan = handoff['implementation_plan']
    # 导入时只把已批准设计转换为需求基准；计划和控制种子仍属于候选输入。
    baseline = {
        'objective': design['objective'],
        'requirements': design['requirements'],
        'invariants': design['invariants'],
        'constraints': design['constraints'],
        'non_goals': design['non_goals'],
        'assumptions': design['assumptions'],
        'unknowns': design['unknowns'],
        'decisions': design['decisions'],
        'measurement_intents': design['verification_strategy'],
        'baseline_status': 'ready',
    }
    baseline = validate_stage_artifact('baseline', baseline)

    timestamp = now()
    evidence = (
        f'导入已批准开发前交接：design_revision={design["design_revision"]}, '
        f'plan_revision={plan["plan_revision"]}；'
        f'设计确认={design["approval"]["evidence"]}；'
        f'计划确认={plan["approval"]["evidence"]}'
    )
    data: dict[str, Any] = {
        'schema_version': SCHEMA_VERSION,
        'revision': 0,
        'created_at': timestamp,
        'updated_at': timestamp,
        'mode': args.mode,
        'phase': 'baseline',
        'phase_history': [
            {
                'from': None,
                'to': 'baseline',
                'evidence': evidence,
                'recorded_at': timestamp,
            }
        ],
        'stage_artifacts': {phase_name: [] for phase_name in ARTIFACT_PHASES},
        'objective': baseline['objective'],
        'requirements': baseline['requirements'],
        'invariants': baseline['invariants'],
        'constraints': baseline['constraints'],
        'non_goals': baseline['non_goals'],
        'predevelopment': {
            'source': str(handoff_path),
            'imported_at': timestamp,
            'topic': handoff['topic'],
            'design_revision': design['design_revision'],
            'plan_revision': plan['plan_revision'],
            'handoff': handoff,
        },
        # 不从 control_seed 直接生成模型或任务，确保 modeling/planning 门禁不会被跳过。
        'control_model': {
            'plant': '',
            'sensors': [],
            'actuators': [],
            'disturbances': [],
            'assumptions': [],
            'measurements': [],
        },
        'tasks': [],
        'feedback': [],
        'samples': [],
    }
    data['stage_artifacts']['baseline'].append(
        {
            'revision': 1,
            'evidence': evidence,
            'recorded_at': timestamp,
            'data': baseline,
        }
    )
    write_state(path, data)
    print(
        f'已导入开发前交接包：topic={handoff["topic"]}；'
        f'账本阶段=baseline；下一阶段=modeling'
    )


def append_unique(target: list[str], values: list[str]) -> int:
    added = 0
    for value in values:
        value = value.strip()
        if value and value not in target:
            target.append(value)
            added += 1
    return added


def command_update_model(args: argparse.Namespace) -> None:
    # 兼容性增量入口：可补充当前视图，但不能替代完整的 modeling 阶段产物。
    path = Path(args.state)
    data = read_state(path)
    model = data['control_model']
    changes = 0
    if args.plant is not None:
        plant = args.plant.strip()
        if not plant:
            raise LedgerError('被控对象描述不能为空')
        if plant != model['plant']:
            model['plant'] = plant
            changes += 1
    changes += append_unique(model['sensors'], args.sensor)
    changes += append_unique(model['actuators'], args.actuator)
    changes += append_unique(model['disturbances'], args.disturbance)
    changes += append_unique(model['assumptions'], args.assumption)
    requirement_ids = {requirement['id'] for requirement in data['requirements']}
    model.setdefault('measurements', [])
    for measurement in parse_measurements(args.measurement, requirement_ids):
        if measurement not in model['measurements']:
            model['measurements'].append(measurement)
            changes += 1
    if changes == 0:
        raise LedgerError('没有提供新的模型信息')
    write_state(path, data)
    print(f'已更新系统模型：新增或修改 {changes} 项')


def command_sample(args: argparse.Namespace) -> None:
    path = Path(args.state)
    data = read_state(path)
    counts = parse_error_counts(args.error)
    # 加权分数用于观察趋势；原始 P0-P3 计数仍保留，避免单一分数掩盖严重误差。
    score = sum(counts[severity] * ERROR_WEIGHTS[severity] for severity in SEVERITIES)
    sample_number = len(data['samples']) + 1
    sample_id = f'S-{sample_number:04d}'
    data['samples'].append(
        {
            'id': sample_id,
            'label': args.label,
            'errors': counts,
            'score': score,
            'evidence': args.evidence,
            'recorded_at': now(),
        }
    )
    write_state(path, data)
    print(f'已记录采样 {sample_id}：误差分数 {score}')


def command_add_task(args: argparse.Namespace) -> None:
    # 兼容性增量入口：只能创建简化任务，不能满足完整 planning 产物的执行门禁。
    path = Path(args.state)
    data = read_state(path)
    if any(task['id'] == args.id for task in data['tasks']):
        raise LedgerError(f'任务 ID 重复：{args.id}')
    known_requirements = {item['id'] for item in data['requirements']}
    unknown = sorted(set(args.requirement) - known_requirements)
    if unknown:
        unknown_text = ', '.join(unknown)
        raise LedgerError(f'未知需求 ID：{unknown_text}')
    if not args.requirement or not args.acceptance:
        raise LedgerError('任务至少需要一个需求 ID 和一个验收检查')
    data['tasks'].append(
        {
            'id': args.id,
            'goal': args.goal,
            'requirements': args.requirement,
            'acceptance': args.acceptance,
            'status': 'planned',
            'evidence': [],
            'updated_at': now(),
        }
    )
    write_state(path, data)
    print(f'已添加任务：{args.id}')


def command_set_task(args: argparse.Namespace) -> None:
    path = Path(args.state)
    data = read_state(path)
    task = find(data['tasks'], 'id', args.task, '任务')
    # verified 是带证据的受控状态；未关闭反馈会强制阻止任务提前验收。
    if args.status == 'verified':
        blockers = [
            item['id']
            for item in data['feedback']
            if item['task'] == args.task
            and item.get('resolved_at') is None
            and (
                item.get('decision') in (None, 'accept', 'escalate')
                or (item.get('decision') == 'defer' and item['severity'] in ('P0', 'P1'))
            )
        ]
        if blockers:
            blocker_text = ', '.join(blockers)
            raise LedgerError(f'任务存在未解决反馈，不能验证：{blocker_text}')
        if not args.evidence:
            raise LedgerError('设置 verified 必须提供 --evidence')
    task['status'] = args.status
    if args.evidence:
        task['evidence'].append({'recorded_at': now(), 'text': args.evidence})
    task['updated_at'] = now()
    write_state(path, data)
    print(f'任务 {args.task} -> {args.status}')


def command_add_feedback(args: argparse.Namespace) -> None:
    path = Path(args.state)
    data = read_state(path)
    task = find(data['tasks'], 'id', args.task, '任务')
    if args.requirement:
        find(data['requirements'], 'id', args.requirement, '需求')
    feedback_number = len(data['feedback']) + 1
    feedback_id = f'F-{feedback_number:04d}'
    data['feedback'].append(
        {
            'id': feedback_id,
            'task': args.task,
            'source': args.source,
            'requirement': args.requirement,
            'expected': args.expected,
            'observed': args.observed,
            'evidence': args.evidence,
            'severity': args.severity,
            'confidence': args.confidence,
            'sensor_limit': args.sensor_limit,
            'causal_hypothesis': args.causal_hypothesis,
            'correction_check': args.correction_check,
            'decision': None,
            'decision_reason': None,
            'created_at': now(),
            'resolved_at': None,
            'resolution_evidence': None,
        }
    )
    # 新证据可以推翻旧验收；已验证任务收到反馈后必须重新进入观察阶段。
    if task['status'] == 'verified':
        task['status'] = 'observing'
        task['updated_at'] = now()
    write_state(path, data)
    print(f'已添加负反馈：{feedback_id}')


def command_decide(args: argparse.Namespace) -> None:
    path = Path(args.state)
    data = read_state(path)
    item = find(data['feedback'], 'id', args.feedback, '反馈')
    if item.get('resolved_at'):
        raise LedgerError(f'反馈已经关闭：{args.feedback}')
    item['decision'] = args.decision
    item['decision_reason'] = args.reason
    item['decided_at'] = now()
    # reject 表示该信号不构成偏差，可直接关闭；accept 必须经纠正和 resolve 复验。
    if args.decision == 'reject':
        item['resolved_at'] = now()
        item['resolution_evidence'] = args.reason
    write_state(path, data)
    print(f'反馈 {args.feedback} -> {args.decision}')


def command_resolve(args: argparse.Namespace) -> None:
    path = Path(args.state)
    data = read_state(path)
    item = find(data['feedback'], 'id', args.feedback, '反馈')
    if item.get('decision') != 'accept':
        raise LedgerError('只有已接受反馈才能用纠正证据关闭')
    if item.get('resolved_at'):
        raise LedgerError(f'反馈已经关闭：{args.feedback}')
    item['resolved_at'] = now()
    item['resolution_evidence'] = args.evidence
    write_state(path, data)
    print(f'已关闭反馈：{args.feedback}')


def latest_artifact(data: dict[str, Any], phase: str) -> dict[str, Any] | None:
    records = data.get('stage_artifacts', {}).get(phase, [])
    if not records:
        return None
    return records[-1].get('data')


def task_contract_gaps(task: dict[str, Any]) -> list[str]:
    gaps: list[str] = []
    for field in TASK_CONTRACT_FIELDS:
        if field not in task:
            gaps.append(field)
            continue
        value = task[field]
        if field == 'rollback':
            if not isinstance(value, str) or not value.strip():
                gaps.append(field)
        elif not isinstance(value, list):
            gaps.append(field)
    for field in (
        'write_scope',
        'action_bounds',
        'acceptance',
        'evidence_required',
        'stop_conditions',
        'escalation_conditions',
    ):
        if field in task and isinstance(task[field], list) and not task[field]:
            gaps.append(field)
    return sorted(set(gaps))


def transition_blockers(
    data: dict[str, Any], current: str, target: str
) -> list[str]:
    blockers: list[str] = []
    must_ids = {
        requirement['id']
        for requirement in data['requirements']
        if requirement['priority'] == 'must'
    }
    model = data['control_model']
    tasks = data['tasks']
    feedback = data['feedback']

    # 门禁按“目标阶段需要什么”检查，而不是仅检查当前阶段是否声称完成。
    if target == 'modeling':
        baseline = latest_artifact(data, 'baseline')
        if not baseline:
            blockers.append('尚未记录 RequirementBaseline 阶段产物')
        elif baseline.get('baseline_status') != 'ready':
            blockers.append('需求基准状态不是 ready')
        if not data['objective'] or not must_ids:
            blockers.append('需求基准缺少控制目标或必须需求')
    elif target == 'planning':
        model_artifact = latest_artifact(data, 'modeling')
        if not model_artifact:
            blockers.append('尚未记录 EngineeringSystemModel 阶段产物')
        elif model_artifact.get('model_status') != 'ready':
            blockers.append('系统模型状态不是 ready')
        if not model['plant']:
            blockers.append('缺少被控对象边界')
        if not model['sensors']:
            blockers.append('缺少有效传感器')
        if not model['actuators']:
            blockers.append('缺少有效执行器')
        measured = {
            measurement['requirement']
            for measurement in model.get('measurements', [])
        }
        missing = sorted(must_ids - measured)
        if missing:
            missing_text = ', '.join(missing)
            blockers.append(f'必须需求尚不可观测：{missing_text}')
    elif target == 'executing':
        plan_artifact = latest_artifact(data, 'planning')
        if not plan_artifact:
            blockers.append('尚未记录 ControlPlan 阶段产物')
        elif plan_artifact.get('plan_status') != 'ready':
            blockers.append('控制计划状态不是 ready')
        if not tasks:
            blockers.append('尚未建立受控任务')
        covered = {
            requirement_id
            for task in tasks
            for requirement_id in task['requirements']
        }
        missing = sorted(must_ids - covered)
        if missing:
            missing_text = ', '.join(missing)
            blockers.append(f'必须需求没有任务覆盖：{missing_text}')
        for task in tasks:
            gaps = task_contract_gaps(task)
            if gaps:
                gap_text = ', '.join(gaps)
                blockers.append(f'任务 {task["id"]} 契约不完整：{gap_text}')
    elif target == 'observing':
        execution = latest_artifact(data, 'executing')
        if not execution:
            blockers.append('尚未记录 ExecutionReport 阶段产物')
        elif execution.get('status') not in ('implemented', 'partial'):
            blockers.append('执行产物尚未达到可观测状态')
        measurable = any(
            task['status'] in ('active', 'observing', 'correcting', 'verified')
            for task in tasks
        )
        if not measurable:
            blockers.append('没有已执行或可复验的任务输出')
    elif target == 'correcting':
        observation = latest_artifact(data, 'observing')
        if not observation:
            blockers.append('尚未记录 ObservationReport 阶段产物')
        elif observation.get('observation_status') != 'complete':
            blockers.append('观察产物尚未完成')
        actionable = any(
            item.get('resolved_at') is None
            and item.get('decision') in (None, 'accept', 'escalate')
            for item in feedback
        )
        if not actionable:
            blockers.append('没有待裁决或待纠正的有效反馈')
    elif target == 'verifying':
        observation = latest_artifact(data, 'observing')
        if not observation:
            blockers.append('尚未记录 ObservationReport 阶段产物')
        elif observation.get('observation_status') != 'complete':
            blockers.append('观察产物尚未完成')
        if not tasks or any(task['status'] != 'verified' for task in tasks):
            blockers.append('所有任务必须先达到 verified')
        for item in feedback:
            item_id = item['id']
            decision = item.get('decision')
            resolved = item.get('resolved_at') is not None
            if decision is None:
                blockers.append(f'反馈 {item_id} 尚未裁决')
            elif decision == 'accept' and not resolved:
                blockers.append(f'反馈 {item_id} 已接受但尚未复验关闭')
            elif decision == 'escalate':
                blockers.append(f'反馈 {item_id} 正在升级')
            elif decision == 'defer' and item['severity'] in ('P0', 'P1'):
                blockers.append(f'关键反馈 {item_id} 不得延期')
    elif target == 'converged':
        verification = latest_artifact(data, 'verifying')
        if not verification:
            blockers.append('尚未记录 ConvergenceReport 阶段产物')
        elif verification.get('gate_result') != 'pass':
            blockers.append('收敛报告结论不是 pass')
        gate_blockers, _ = gate_findings(data)
        blockers.extend(gate_blockers)
    if current == 'correcting':
        undecided = [
            item['id']
            for item in feedback
            if item.get('resolved_at') is None and item.get('decision') is None
        ]
        if undecided:
            blockers.append(f'纠偏阶段仍有未裁决反馈：{", ".join(undecided)}')
    return blockers


def command_transition(args: argparse.Namespace) -> None:
    path = Path(args.state)
    data = read_state(path)
    current = data['phase']
    target = args.to
    if target == current:
        raise LedgerError(f'当前已经处于 {target} 阶段')
    # 第一层限制阶段图，第二层再检查账本中的动态证据是否满足进入条件。
    allowed = ALLOWED_TRANSITIONS[current]
    if target not in allowed:
        allowed_text = ', '.join(allowed) if allowed else '无'
        raise LedgerError(f'非法阶段转移：{current} -> {target}；允许目标：{allowed_text}')
    blockers = transition_blockers(data, current, target)
    if blockers:
        detail = '；'.join(blockers)
        raise LedgerError(f'不能进入 {target}：{detail}')
    data['phase'] = target
    data['phase_history'].append(
        {
            'from': current,
            'to': target,
            'evidence': args.evidence,
            'recorded_at': now(),
        }
    )
    write_state(path, data)
    print(f'控制阶段 {current} -> {target}')


def gate_findings(data: dict[str, Any]) -> tuple[list[str], list[str]]:
    # 最终门禁比普通阶段转移更严格：它重新审计全链路，而不只相信 verifying 结论。
    blockers: list[str] = []
    warnings: list[str] = []
    phase = data['phase']
    if phase not in ('verifying', 'converged'):
        blockers.append(f'当前阶段为 {phase}，必须先进入 verifying')
    required_artifacts = ('baseline', 'modeling', 'planning', 'executing', 'observing', 'verifying')
    for artifact_phase in required_artifacts:
        if latest_artifact(data, artifact_phase) is None:
            blockers.append(f'缺少 {artifact_phase} 阶段结构化产物')
    if data['feedback'] and latest_artifact(data, 'correcting') is None:
        blockers.append('存在反馈但缺少 correcting 阶段结构化产物')
    verification = latest_artifact(data, 'verifying')
    if verification and verification.get('gate_result') != 'pass':
        blockers.append('ConvergenceReport 的 gate_result 不是 pass')
    mode = data['mode']
    model = data['control_model']
    samples = data['samples']
    if mode in ('standard', 'high-assurance'):
        if not model['plant']:
            blockers.append('缺少被控对象描述，无法判断系统边界')
        if not model['sensors']:
            blockers.append('缺少传感器，必须需求不可观测')
        if not model['actuators']:
            blockers.append('缺少执行器，需求偏差不可控')
        measured_requirements = {
            measurement['requirement'] for measurement in model.get('measurements', [])
        }
        for requirement in data['requirements']:
            if requirement['priority'] == 'must' and requirement['id'] not in measured_requirements:
                requirement_id = requirement['id']
                blockers.append(f'需求 {requirement_id} 未绑定传感器或验收测量')
        required_samples = 3 if mode == 'high-assurance' else 2
        if len(samples) < required_samples:
            blockers.append(f'{mode} 模式至少需要 {required_samples} 次采样')
        if mode == 'high-assurance' and len(model['sensors']) < 2:
            blockers.append('high-assurance 模式至少需要两类独立传感器')
    if samples:
        final_sample = samples[-1]
        final_errors = final_sample['errors']
        p0_count = final_errors['P0']
        p1_count = final_errors['P1']
        p2_count = final_errors['P2']
        p3_count = final_errors['P3']
        if p0_count or p1_count:
            blockers.append(f'最终采样仍有严重误差：P0={p0_count}, P1={p1_count}')
        if p2_count or p3_count:
            warnings.append(f'最终采样仍有非关键误差：P2={p2_count}, P3={p3_count}')
        scores = [sample['score'] for sample in samples]
        # 连续三次不下降不一定是失败，但足以提示停滞、振荡或反馈时延。
        if len(scores) >= 3 and scores[-3] <= scores[-2] <= scores[-1] and scores[-1] > 0:
            warnings.append('最近三次采样误差未下降，闭环可能停滞或振荡')
    covered = {requirement for task in data['tasks'] for requirement in task['requirements']}
    for requirement in data['requirements']:
        requirement_id = requirement['id']
        if requirement['priority'] == 'must' and requirement_id not in covered:
            blockers.append(f'需求 {requirement_id} 没有任务覆盖')
    for task in data['tasks']:
        task_id = task['id']
        task_status = task['status']
        gaps = task_contract_gaps(task)
        if gaps:
            gap_text = ', '.join(gaps)
            blockers.append(f'任务 {task_id} 契约不完整：{gap_text}')
        if task_status != 'verified':
            blockers.append(f'任务 {task_id} 状态为 {task_status}，尚未验证')
    if not data['tasks']:
        blockers.append('尚未建立任务')
    for item in data['feedback']:
        feedback_id = item['id']
        decision = item.get('decision')
        resolved = item.get('resolved_at') is not None
        if decision is None:
            blockers.append(f'反馈 {feedback_id} 尚未裁决')
        elif decision == 'accept' and not resolved:
            blockers.append(f'已接受反馈 {feedback_id} 尚未关闭')
        elif decision == 'escalate':
            blockers.append(f'反馈 {feedback_id} 正在升级等待决策')
        elif decision == 'defer' and item['severity'] in ('P0', 'P1'):
            blockers.append(f'关键反馈 {feedback_id} 不得延期')
        elif decision == 'defer':
            severity = item['severity']
            warnings.append(f'反馈 {feedback_id} 已延期（{severity}）')
    return blockers, warnings


def print_status(data: dict[str, Any]) -> None:
    blockers, warnings = gate_findings(data)
    counts = {status: 0 for status in TASK_STATUSES}
    for task in data['tasks']:
        counts[task['status']] += 1
    open_feedback = sum(1 for item in data['feedback'] if item.get('resolved_at') is None)
    model = data['control_model']
    samples = data['samples']
    print('控制模式：{}'.format(data['mode']))
    print('当前阶段：{}'.format(data['phase']))
    print('控制目标：{}'.format(data['objective']))
    print('账本版本：{}'.format(data['revision']))
    predevelopment = data.get('predevelopment')
    if predevelopment:
        print(
            '开发前交接：topic={}，design_revision={}，plan_revision={}'.format(
                predevelopment['topic'],
                predevelopment['design_revision'],
                predevelopment['plan_revision'],
            )
        )
    else:
        print('开发前交接：无')
    print('必须需求：{}'.format(len(data['requirements'])))
    print(
        '系统模型：传感器={}，执行器={}，需求测量={}，已知扰动={}，假设={}'.format(
            len(model['sensors']),
            len(model['actuators']),
            len(model.get('measurements', [])),
            len(model['disturbances']),
            len(model['assumptions']),
        )
    )
    if samples:
        scores = ' -> '.join(str(sample['score']) for sample in samples)
        print(f'离散采样：{len(samples)} 次，误差分数 {scores}')
    else:
        print('离散采样：0 次')
    task_summary = ', '.join(f'{name}={count}' for name, count in counts.items() if count)
    print(f'任务状态：{task_summary}')
    feedback_count = len(data['feedback'])
    print(f'负反馈：总数={feedback_count}，未关闭={open_feedback}')
    artifact_summary = ', '.join(
        f'{phase_name}={len(data["stage_artifacts"].get(phase_name, []))}'
        for phase_name in ARTIFACT_PHASES
        if data['stage_artifacts'].get(phase_name)
    )
    print(f'阶段产物：{artifact_summary or "无"}')
    gate_status = 'PASS（通过）' if not blockers else 'BLOCKED（阻塞）'
    print(f'收敛门禁：{gate_status}')
    for message in blockers:
        print(f'阻塞项：{message}')
    for message in warnings:
        print(f'警告：{message}')


def command_status(args: argparse.Namespace) -> None:
    # status 只展示诊断；即使存在阻塞项也保持成功退出，便于人工查看。
    print_status(read_state(Path(args.state)))


def command_gate(args: argparse.Namespace) -> None:
    data = read_state(Path(args.state))
    print_status(data)
    blockers, _ = gate_findings(data)
    # gate 面向自动化门禁：阻塞时使用退出码 2，让调用方无法误判为完成。
    if blockers:
        raise SystemExit(2)


def build_parser() -> argparse.ArgumentParser:
    # CLI 是唯一公共入口，每个子命令只负责一种账本变更或只读检查。
    parser = argparse.ArgumentParser(description=__doc__)
    subparsers = parser.add_subparsers(dest='command', required=True)

    init_parser = subparsers.add_parser('init', help='建立新的控制账本')
    init_parser.add_argument('--state', required=True, help='状态 JSON 路径')
    init_parser.add_argument('--mode', choices=MODES, default='standard', help='控制深度')
    init_parser.add_argument('--objective', required=True, help='唯一控制目标')
    init_parser.add_argument('--requirement', action='append', default=[], help='ID=必须需求')
    init_parser.add_argument('--invariant', action='append', default=[], help='必须保持的不变量')
    init_parser.add_argument('--constraint', action='append', default=[], help='工程约束')
    init_parser.add_argument('--non-goal', action='append', default=[], help='明确非目标')
    init_parser.add_argument('--plant', default='', help='被控对象与系统边界')
    init_parser.add_argument('--sensor', action='append', default=[], help='可观察需求的传感器')
    init_parser.add_argument('--actuator', action='append', default=[], help='可影响偏差的执行器')
    init_parser.add_argument(
        '--measurement', action='append', default=[], help='需求ID=传感器或验收方式'
    )
    init_parser.add_argument('--disturbance', action='append', default=[], help='已知扰动')
    init_parser.add_argument('--assumption', action='append', default=[], help='模型假设')
    init_parser.add_argument('--force', action='store_true', help='替换已有状态文件')
    init_parser.set_defaults(handler=command_init)

    import_parser = subparsers.add_parser(
        'import-handoff', help='导入已批准的开发前设计与实施计划'
    )
    import_parser.add_argument('--state', required=True, help='状态 JSON 路径')
    import_parser.add_argument('--input', required=True, help='PredevelopmentHandoff JSON 路径')
    import_parser.add_argument('--mode', choices=MODES, default='standard', help='控制深度')
    import_parser.add_argument('--force', action='store_true', help='替换已有状态文件')
    import_parser.set_defaults(handler=command_import_handoff)

    model_parser = subparsers.add_parser('update-model', help='用新事实更新系统模型')
    model_parser.add_argument('--state', required=True)
    model_parser.add_argument('--plant', help='替换被控对象描述')
    model_parser.add_argument('--sensor', action='append', default=[], help='增加传感器')
    model_parser.add_argument('--actuator', action='append', default=[], help='增加执行器')
    model_parser.add_argument(
        '--measurement', action='append', default=[], help='增加需求到测量方式的绑定'
    )
    model_parser.add_argument('--disturbance', action='append', default=[], help='增加已知扰动')
    model_parser.add_argument('--assumption', action='append', default=[], help='增加模型假设')
    model_parser.set_defaults(handler=command_update_model)

    artifact_parser = subparsers.add_parser(
        'record-artifact', help='把当前阶段的结构化输出写入共享账本'
    )
    artifact_parser.add_argument('--state', required=True)
    artifact_parser.add_argument('--phase', choices=ARTIFACT_PHASES, required=True)
    artifact_parser.add_argument('--input', required=True, help='阶段输出 JSON 文件')
    artifact_parser.add_argument('--evidence', required=True, help='产物来源或验证证据')
    artifact_parser.set_defaults(handler=command_record_artifact)

    sample_parser = subparsers.add_parser('sample', help='记录一次离散误差采样')
    sample_parser.add_argument('--state', required=True)
    sample_parser.add_argument('--label', required=True, help='采样点名称')
    sample_parser.add_argument('--error', action='append', default=[], help='P0=数量 到 P3=数量')
    sample_parser.add_argument('--evidence', required=True, help='原始测量证据')
    sample_parser.set_defaults(handler=command_sample)

    task_parser = subparsers.add_parser('add-task', help='增加有界工程任务')
    task_parser.add_argument('--state', required=True)
    task_parser.add_argument('--id', required=True)
    task_parser.add_argument('--goal', required=True)
    task_parser.add_argument('--requirement', action='append', default=[])
    task_parser.add_argument('--acceptance', action='append', default=[])
    task_parser.set_defaults(handler=command_add_task)

    set_task_parser = subparsers.add_parser('set-task', help='修改任务状态并记录证据')
    set_task_parser.add_argument('--state', required=True)
    set_task_parser.add_argument('--task', required=True)
    set_task_parser.add_argument('--status', choices=TASK_STATUSES, required=True)
    set_task_parser.add_argument('--evidence')
    set_task_parser.set_defaults(handler=command_set_task)

    feedback_parser = subparsers.add_parser('add-feedback', help='记录观察到的需求偏差')
    feedback_parser.add_argument('--state', required=True)
    feedback_parser.add_argument('--task', required=True)
    feedback_parser.add_argument('--source', required=True)
    feedback_parser.add_argument('--requirement')
    feedback_parser.add_argument('--expected', required=True)
    feedback_parser.add_argument('--observed', required=True)
    feedback_parser.add_argument('--evidence', required=True)
    feedback_parser.add_argument('--severity', choices=SEVERITIES, required=True)
    feedback_parser.add_argument('--confidence', choices=CONFIDENCES, required=True)
    feedback_parser.add_argument('--sensor-limit', help='当前测量可能遗漏的范围')
    feedback_parser.add_argument('--causal-hypothesis', help='明确标记的因果假设')
    feedback_parser.add_argument('--correction-check')
    feedback_parser.set_defaults(handler=command_add_feedback)

    decide_parser = subparsers.add_parser('decide', help='由主 Agent 裁决反馈')
    decide_parser.add_argument('--state', required=True)
    decide_parser.add_argument('--feedback', required=True)
    decide_parser.add_argument('--decision', choices=DECISIONS, required=True)
    decide_parser.add_argument('--reason', required=True)
    decide_parser.set_defaults(handler=command_decide)

    resolve_parser = subparsers.add_parser('resolve', help='用复验证据关闭已接受反馈')
    resolve_parser.add_argument('--state', required=True)
    resolve_parser.add_argument('--feedback', required=True)
    resolve_parser.add_argument('--evidence', required=True)
    resolve_parser.set_defaults(handler=command_resolve)

    transition_parser = subparsers.add_parser('transition', help='通过门禁后转移控制阶段')
    transition_parser.add_argument('--state', required=True)
    transition_parser.add_argument('--to', choices=PHASES, required=True)
    transition_parser.add_argument('--evidence', required=True, help='阶段退出门禁证据')
    transition_parser.set_defaults(handler=command_transition)

    status_parser = subparsers.add_parser('status', help='显示中文闭环状态')
    status_parser.add_argument('--state', required=True)
    status_parser.set_defaults(handler=command_status)

    gate_parser = subparsers.add_parser('gate', help='检查收敛；阻塞时退出码为 2')
    gate_parser.add_argument('--state', required=True)
    gate_parser.set_defaults(handler=command_gate)
    return parser


def main() -> int:
    parser = build_parser()
    args = parser.parse_args()
    try:
        args.handler(args)
    except LedgerError as exc:
        parser.error(str(exc))
    return 0


if __name__ == '__main__':
    sys.exit(main())
