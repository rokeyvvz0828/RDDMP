# RDDMP 前端规则

适用于 `web/src`，并继承仓库根目录规则。

- 使用 Vue 3、TypeScript、Vite、Element Plus、Pinia 和 Vue Router 的现有模式。
- 优先复用 `components/ui`、语义主题变量、`api/http.ts`、stores 和公共类型；公共组件变更必须回归现有调用页面。
- 业务请求通过 `api/*.ts`，页面不拼装后端地址、不保存密钥、不把前端显隐当作权限校验。
- 页面覆盖加载、空、失败、无权限、提交中、重复提交和长文本；危险操作说明对象与后果。
- 桌面和移动视口均检查遮挡、溢出、弹层高度、按钮可达性和返回上下文；明暗主题保持可读。
- 完成前运行 `npm --prefix web run build`，用户流程变更还需真实浏览器验收并记录证据。
