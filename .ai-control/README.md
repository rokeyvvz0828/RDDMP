# AI Control 工程账本

`.ai-control` 只保存机器可读的工程控制证据，不替代需求文档、任务授权、PR 或发布审批。

## 目录结构

```text
.ai-control/
├── README.md
├── original/
│   ├── baseline.json
│   ├── state.json
│   └── ...
└── requirements/
    └── <control-prefix>/
        ├── design.json
        ├── state.json
        ├── control-plan.json
        ├── execution-T1.json
        ├── observation-T1.json
        └── convergence.json
```

- `original/` 保存初始平台建设的无需求前缀历史账本，只读保留。
- `requirements/<control-prefix>/` 保存单个需求的设计、计划、执行、观测和收敛证据。
- 根目录禁止新增 JSON；新需求不得把账本文件重新平铺。
- 历史 JSON 内容中的旧路径代表当时执行上下文，不因归档迁移而批量改写。

## 新任务

任务范围中的写入模式应为：

```text
.ai-control/requirements/<control-prefix>/*.json
```

提交前运行：

```bash
node scripts/check-ai-control-layout.mjs
node scripts/check-all-governance.mjs
```
