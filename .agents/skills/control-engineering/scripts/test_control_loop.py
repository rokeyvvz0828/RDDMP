#!/usr/bin/env python3

from __future__ import annotations

import copy
import json
import uuid
import unittest
from pathlib import Path
from types import SimpleNamespace

import control_loop


def approved_handoff() -> dict:
    return {
        'schema_version': 1,
        'topic': 'sample-feature',
        'handoff_status': 'approved',
        'created_at': '2026-07-22T00:00:00Z',
        'design': {
            'schema_version': 1,
            'topic': 'sample-feature',
            'design_revision': 2,
            'design_status': 'approved',
            'human_document': 'docs/engineering-control/designs/sample-design.md',
            'objective': '用户可以观察到样例功能成功输出',
            'users': ['项目使用者'],
            'requirements': [
                {
                    'id': 'R1',
                    'statement': '系统必须产生可验证的样例输出',
                    'acceptance': ['运行目标检查时退出码为 0'],
                    'priority': 'must',
                    'source': '用户确认',
                    'counterexample': ['命令成功但没有目标输出'],
                }
            ],
            'invariants': ['保留现有公开接口'],
            'constraints': ['不增加外部依赖'],
            'non_goals': ['不改变无关模块'],
            'selected_approach': {
                'name': '局部实现',
                'reason': '影响面最小',
                'alternatives': [],
            },
            'architecture': {
                'boundary': {'inside': ['目标模块'], 'outside': ['外部服务']},
                'components': [],
                'interfaces': [],
                'data_flows': [],
            },
            'error_handling': [],
            'quality_attributes': {
                'security': [],
                'performance': [],
                'compatibility': [],
                'operations': [],
            },
            'verification_strategy': [
                {
                    'requirement_ids': ['R1'],
                    'signal': '命令退出码为 0',
                    'method': '运行目标检查',
                }
            ],
            'assumptions': [
                {
                    'text': '现有入口可复用',
                    'basis': '只读勘察',
                    'falsifier': '建模发现入口不存在',
                }
            ],
            'unknowns': [
                {
                    'question': '是否存在隐藏消费者',
                    'impact': '可能改变接口边界',
                    'owner': '系统建模阶段',
                    'blocking': False,
                }
            ],
            'decisions': [
                {
                    'id': 'D1',
                    'decision': '采用局部实现',
                    'reason': '影响面最小',
                    'source': '用户确认',
                }
            ],
            'risks': [],
            'approval': {
                'status': 'approved',
                'evidence': '用户确认设计修订 2',
                'confirmed_at': '2026-07-22T00:00:00Z',
            },
        },
        'implementation_plan': {
            'plan_revision': 3,
            'human_document': 'docs/engineering-control/plans/sample-plan.md',
            'plan_status': 'ready',
            'global_constraints': ['不增加外部依赖'],
            'file_map': [
                {
                    'path': 'src/sample.py',
                    'status': 'existing',
                    'responsibility': '提供样例输出',
                    'evidence': '只读项目勘察',
                }
            ],
            'tasks': [
                {
                    'id': 'T1',
                    'goal': '实现并验证样例输出',
                    'requirement_ids': ['R1'],
                    'prerequisites': [],
                    'input_facts': ['src/sample.py 已存在'],
                    'files': {
                        'create': [],
                        'modify': ['src/sample.py'],
                        'test': ['tests/test_sample.py'],
                    },
                    'interfaces': {'consumes': [], 'produces': ['sample_output']},
                    'steps': [
                        {
                            'id': 'T1-S1',
                            'action': '运行目标检查并保留基线证据',
                            'command': 'python -m unittest tests.test_sample',
                            'expected': '退出码反映当前行为',
                            'evidence': '命令输出和退出码',
                        }
                    ],
                    'acceptance_checks': ['目标测试退出码为 0'],
                    'risks': [],
                    'rollback': '恢复 T1 开始前的工作区快照',
                    'stop_conditions': ['发现未建模公共接口'],
                    'escalation_conditions': ['需求与现有行为冲突'],
                }
            ],
            'dependency_edges': [],
            'parallel_groups': [['T1']],
            'integration_checks': [],
            'coverage': [{'requirement_id': 'R1', 'task_ids': ['T1']}],
            'approval': {
                'status': 'approved',
                'evidence': '用户许可按计划修订 3 进入开发',
                'confirmed_at': '2026-07-22T00:05:00Z',
            },
        },
        'control_seed': {
            'seed_status': 'hypotheses-only',
            'plant_boundary_candidates': ['目标模块'],
            'state_variable_candidates': [],
            'interface_candidates': ['sample_output'],
            'sensor_candidates': ['单元测试'],
            'actuator_candidates': ['局部代码修改'],
            'disturbance_candidates': [],
            'delay_candidates': [],
            'assumptions': [
                {
                    'text': '现有入口可复用',
                    'basis': '只读勘察',
                    'falsifier': '建模发现入口不存在',
                }
            ],
        },
    }


class ImportHandoffTests(unittest.TestCase):
    def make_paths(self) -> tuple[Path, Path]:
        token = uuid.uuid4().hex
        directory = Path(__file__).parent
        handoff_path = directory / f'.handoff-test-{token}.json'
        state_path = directory / f'.state-test-{token}.json'
        self.addCleanup(handoff_path.unlink, missing_ok=True)
        self.addCleanup(state_path.unlink, missing_ok=True)
        return handoff_path, state_path

    def write_handoff(self, value: dict) -> tuple[Path, Path]:
        handoff_path, state_path = self.make_paths()
        handoff_path.write_text(json.dumps(value, ensure_ascii=False), encoding='utf-8')
        return handoff_path, state_path

    def test_import_creates_baseline_without_skipping_modeling(self) -> None:
        handoff_path, state_path = self.write_handoff(approved_handoff())

        control_loop.command_import_handoff(
            SimpleNamespace(
                state=str(state_path),
                input=str(handoff_path),
                mode='standard',
                force=False,
            )
        )

        state = control_loop.read_state(state_path)
        self.assertEqual(control_loop.SCHEMA_VERSION, state['schema_version'])
        self.assertEqual('baseline', state['phase'])
        self.assertEqual([], state['tasks'])
        self.assertEqual('', state['control_model']['plant'])
        self.assertEqual('sample-feature', state['predevelopment']['topic'])
        self.assertEqual(1, len(state['stage_artifacts']['baseline']))

        control_loop.command_transition(
            SimpleNamespace(
                state=str(state_path),
                to='modeling',
                evidence='已复核导入的需求基准',
            )
        )
        self.assertEqual('modeling', control_loop.read_state(state_path)['phase'])

    def test_import_rejects_handoff_without_user_approval(self) -> None:
        handoff = approved_handoff()
        handoff['handoff_status'] = 'awaiting-user-approval'
        handoff_path, state_path = self.write_handoff(handoff)

        with self.assertRaisesRegex(control_loop.LedgerError, 'handoff_status=approved'):
            control_loop.command_import_handoff(
                SimpleNamespace(
                    state=str(state_path),
                    input=str(handoff_path),
                    mode='standard',
                    force=False,
                )
            )
        self.assertFalse(state_path.exists())

    def test_import_rejects_missing_must_requirement_coverage(self) -> None:
        handoff = approved_handoff()
        handoff['implementation_plan']['coverage'] = []
        with self.assertRaisesRegex(control_loop.LedgerError, '未覆盖必须需求'):
            control_loop.validate_predevelopment_handoff(handoff)

    def test_schema_three_state_preserves_phase_when_upgraded(self) -> None:
        legacy = {
            'schema_version': 3,
            'phase': 'planning',
            'phase_history': [],
            'stage_artifacts': {},
        }
        upgraded = control_loop.upgrade_legacy_state(copy.deepcopy(legacy), 3)
        self.assertEqual(control_loop.SCHEMA_VERSION, upgraded['schema_version'])
        self.assertEqual('planning', upgraded['phase'])
        self.assertIsNone(upgraded['predevelopment'])


if __name__ == '__main__':
    unittest.main()
