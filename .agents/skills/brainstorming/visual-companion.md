# 可视化伴侣指南

用浏览器展示原型、图表和视觉方案，辅助设计阶段的选择。

## 何时使用

按问题判断，而不是按会话判断。唯一标准是：用户看见内容是否比阅读文字更容易理解和选择。

适合浏览器：

- UI 线框、页面布局、导航结构和组件外观；
- 架构图、数据流、状态机和实体关系；
- 两种布局、配色或视觉方向的并排比较；
- 间距、视觉层级和空间关系。

适合对话：

- 需求、范围和成功标准；
- 用文字即可说明的 A/B/C 选择；
- 取舍表、API、数据模型和技术决策；
- 答案本身是文字的澄清问题。

讨论 UI 不代表必须可视化。“你想要哪类向导”是概念问题；“这两个向导布局哪个好”才是视觉问题。

## 工作方式

服务会监视一个 HTML 目录，并把最新文件展示给浏览器。用户点击产生的事件以 JSON Lines 写入状态目录。

默认写 HTML 片段。若文件以 `<!DOCTYPE` 或 `<html` 开头，服务按完整文档提供；否则自动套用 `scripts/frame-template.html`，注入主题、连接状态和交互脚本。

## 启动会话

仅在用户同意后启动：

```bash
scripts/start-server.sh --project-dir /path/to/project --open
```

启动输出类似：

```json
{
  "type": "server-started",
  "port": 52341,
  "url": "http://localhost:52341/?key=...",
  "screen_dir": "/path/to/project/.superpowers/brainstorm/session/content",
  "state_dir": "/path/to/project/.superpowers/brainstorm/session/state"
}
```

保存 `url`、`screen_dir` 和 `state_dir`。URL 包含会话密钥，必须完整提供，不能删除 `?key=...` 或只给裸端口。`state_dir/server-info` 保存启动信息；`server-stopped` 表示服务已经停止。

传入 `--project-dir` 会把产物保存在项目的 `.superpowers/brainstorm/`。若该路径不应提交，提醒用户将 `.superpowers/` 加入 `.gitignore`，但必须先遵循项目指令和用户授权。

### 平台说明

- Codex 或会回收后台进程的宿主：以前台模式启动，并使用宿主提供的异步会话能力维持进程。
- Windows：通过 Git Bash、WSL 或兼容 Bash 环境运行 `start-server.sh`；若不可用，改用 Codex 已提供的浏览器/可视化工具，不要临时安装运行时。
- 远程或容器环境无法访问回环地址时：

```bash
scripts/start-server.sh --project-dir /path/to/project --host 0.0.0.0 --url-host localhost
```

## 交互循环

1. 推送屏幕前确认 `server-info` 存在且 `server-stopped` 不存在。服务停止时，使用同一 `--project-dir` 重启；原浏览器标签会复用端口和密钥。
2. 用语义化且从未用过的文件名向 `screen_dir` 写入新 HTML，例如 `layout.html`、`layout-v2.html`。
3. 告诉用户当前页面展示什么，重复完整 URL，并请其查看或点击选择，然后结束当前回复等待反馈。
4. 用户回复后，读取 `state_dir/events`；以用户对话文本为主要反馈，将点击事件作为结构化补充。
5. 若反馈改变当前页面，写新版本；当前问题确认后再进入下一个问题。
6. 回到纯文字问题时推送等待页，避免浏览器停留在已经失效的选择：

```html
<div style="display:flex;align-items:center;justify-content:center;min-height:60vh">
  <p class="subtitle">继续在对话中讨论...</p>
</div>
```

## HTML 片段

最小单选示例：

```html
<h2>哪个布局更合适？</h2>
<p class="subtitle">请比较可读性和信息层级</p>

<div class="options">
  <div class="option" data-choice="a" onclick="toggleSelect(this)">
    <div class="letter">A</div>
    <div class="content">
      <h3>单栏</h3>
      <p>阅读路径集中，结构简单</p>
    </div>
  </div>
  <div class="option" data-choice="b" onclick="toggleSelect(this)">
    <div class="letter">B</div>
    <div class="content">
      <h3>双栏</h3>
      <p>侧边导航配合主内容区</p>
    </div>
  </div>
</div>
```

多选时给 `.options` 加 `data-multiselect`。

卡片：

```html
<div class="cards">
  <div class="card" data-choice="design1" onclick="toggleSelect(this)">
    <div class="card-image"><!-- 原型内容 --></div>
    <div class="card-body"><h3>方案名</h3><p>说明</p></div>
  </div>
</div>
```

原型与并排比较：

```html
<div class="split">
  <div class="mockup">
    <div class="mockup-header">方案 A</div>
    <div class="mockup-body"><!-- 内容 --></div>
  </div>
  <div class="mockup">
    <div class="mockup-header">方案 B</div>
    <div class="mockup-body"><!-- 内容 --></div>
  </div>
</div>
```

可用类还包括：`.pros-cons`、`.pros`、`.cons`、`.mock-nav`、`.mock-sidebar`、`.mock-content`、`.mock-button`、`.mock-input`、`.placeholder`、`.section` 和 `.label`。

## 浏览器事件

点击记录位于 `$STATE_DIR/events`，每行一个 JSON 对象：

```jsonl
{"type":"click","choice":"a","text":"方案 A - 单栏","timestamp":1706000101}
{"type":"click","choice":"b","text":"方案 B - 双栏","timestamp":1706000115}
```

事件序列可以显示用户的比较过程，最后一个选择通常是当前倾向，但不能覆盖用户在对话中的明确表述。文件不存在表示没有浏览器交互。

## 设计要求

- 让原型保真度匹配问题：布局用线框，视觉风格才需要精细表现。
- 每页写清楚正在回答的问题。
- 每屏最多 2-4 个方案。
- 内容影响判断时使用真实内容，不用无意义占位符掩盖问题。
- 每次迭代使用新文件名，服务按修改时间展示最新文件。

## 结束会话

```bash
scripts/stop-server.sh $SESSION_DIR
```

使用 `--project-dir` 的会话会保留原型，位于临时目录的会话会被清理。

实现参考：

- 页面模板：`scripts/frame-template.html`
- 浏览器辅助脚本：`scripts/helper.js`
- 本地服务：`scripts/server.cjs`
