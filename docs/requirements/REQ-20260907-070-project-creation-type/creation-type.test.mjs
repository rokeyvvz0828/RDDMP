import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { createRequire } from 'node:module'
import { resolve } from 'node:path'
const root = resolve(import.meta.dirname, '../../..')
const require = createRequire(resolve(root, 'web/package.json'))
const ts = require('typescript')
const { createPinia, setActivePinia } = require('pinia')
const { parse } = require('@vue/compiler-sfc')
const { baseParse } = require('@vue/compiler-dom')
const read = path => readFileSync(resolve(root, path), 'utf8')
const compile = path => ts.transpileModule(read(path), { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 } }).outputText
const types = {}
new Function('exports', compile('web/src/types/project.ts'))(types)
assert.deepEqual(types.projectCreationTypeLabels, { NEW: '新建', CONTINUATION: '续建' })
let response, requests = 0, selected = 'A'
const storage = { getItem: () => selected, setItem: (_key, value) => { selected = value } }
const mockedRequire = id => id === '../api/project' ? { getProjectWorkbench: () => { requests++; return response } }
  : id === '../api/error' ? { apiErrorMessage: () => 'failed' }
  : id === '../types/project' ? types : require(id)
const exported = {}
new Function('require', 'exports', 'localStorage', compile('web/src/stores/project-context.ts'))(mockedRequire, exported, storage)
const project = (code, type) => ({ id: code === 'A' ? 1 : 2, project_code: code, project_name: code, status: 'PLANNING', creation_type: type })
const data = rows => ({ data: { data: rows } })
setActivePinia(createPinia())
const store = exported.useProjectContextStore()
response = Promise.resolve(data([project('A'), project('B', 'CONTINUATION')]))
await store.initialize()
assert.equal(store.current.creationType, 'NEW')
assert.equal(store.projects[1].creationType, 'CONTINUATION')
store.syncProject(project('A', 'CONTINUATION'))
assert.equal(store.current.creationType, 'CONTINUATION')
assert.equal(store.currentRef, 'A')
assert.equal(selected, 'A')
assert.equal(requests, 1, 'metadata sync must not request or select')
let finish
response = new Promise(resolve => { finish = resolve })
const pending = store.initialize(true)
store.syncProject(project('A', 'NEW'))
store.syncProject(project('B', 'NEW'))
finish(data([project('A', 'CONTINUATION'), project('B', 'CONTINUATION')]))
await pending
assert.equal(store.current.creationType, 'NEW', 'old list must not overwrite saved metadata')
assert.equal(store.projects[1].creationType, 'NEW')
assert.equal(store.currentRef, 'A')
response = Promise.reject(new Error('failed'))
await store.initialize(true)
assert.equal(store.current.creationType, 'NEW')
assert.equal(store.error, 'failed')
assert.equal(store.select('missing'), false)
assert.equal(store.select('B'), true)
assert.equal(store.currentRef, 'B')
store.syncProject(project('C', 'CONTINUATION'))
assert.equal(store.currentRef, 'B', 'creating another project does not switch')
const nodes = ast => { const result = []; const visit = n => { if(n.type === 1) result.push(n); n.children?.forEach(visit) }; visit(ast); return result }
const shell = parse(read('web/src/views/AppLayout.vue')).descriptor
const selectors = nodes(baseParse(shell.template.content)).filter(n => n.tag === 'el-select' && n.props.some(p => p.value?.content?.includes('project-context-select')))
assert.equal(selectors.length, 3)
for(const select of selectors) {
  const nested = nodes(select)
  assert.ok(nested.some(n => n.tag === 'template' && n.props.some(p => p.name === 'slot' && p.arg?.content === 'label')))
  assert.equal(nested.filter(n => n.tag === 'UiStatusTag').length, 2)
  assert.ok(select.props.some(p => p.name === 'on' && p.exp?.content === 'confirmProjectSwitch'))
}
const view = read('web/src/views/ProjectView.vue')
assert.ok(view.includes('v-model="projectForm.creation_type"'))
assert.ok(view.includes('projectContext.syncProject(response.data.data)'))
console.log('PASS: type labels, defaults, sync, request race, failed refresh, selection preservation and 3 selector slots')
