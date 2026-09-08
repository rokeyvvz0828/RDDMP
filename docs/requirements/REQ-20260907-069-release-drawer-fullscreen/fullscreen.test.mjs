import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { createRequire } from 'node:module'
import { resolve } from 'node:path'
const root = resolve(import.meta.dirname, '../../..')
const require = createRequire(resolve(root, 'web/package.json'))
const { ref, effectScope } = require('vue')
const ts = require('typescript')
const { parse } = require('@vue/compiler-sfc')
const { baseParse } = require('@vue/compiler-dom')
const read = path => readFileSync(resolve(root, path), 'utf8').replace(/\r\n/g, '\n')
const helper = read('web/src/modules/release/composables/useReleaseDrawerFullscreen.ts')
const js = ts.transpileModule(helper, { compilerOptions: { module: ts.ModuleKind.CommonJS } }).outputText
const exported = {}
new Function('require', 'exports', js)(require, exported)
const scope = effectScope()
scope.run(() => {
  const open = ref(false), otherOpen = ref(true)
  const a = exported.useReleaseDrawerFullscreen(open, '680px')
  const b = exported.useReleaseDrawerFullscreen(otherOpen, '980px')
  assert.equal(a.size.value, '680px')
  a.toggle()
  assert.equal(a.fullscreen.value, false)
  open.value = true
  a.toggle()
  assert.equal(a.size.value, '100vw')
  assert.equal(open.value, true)
  assert.equal(b.fullscreen.value, false)
  b.toggle()
  a.toggle()
  assert.equal(a.size.value, '680px')
  assert.equal(b.size.value, '100vw')
  a.toggle()
  open.value = false
  assert.equal(a.fullscreen.value, true, 'do not shrink during close animation')
  open.value = true
  assert.equal(a.fullscreen.value, false, 'reopen resets synchronously')
})
scope.stop()
effectScope().run(() => {
  assert.equal(exported.useReleaseDrawerFullscreen(ref(true), '680px').fullscreen.value, false)
})
const baseline = JSON.parse(read('docs/requirements/REQ-20260907-069-release-drawer-fullscreen/source-baseline.json'))
const names = process.argv.includes('T1') ? ['ReleaseDrillPlanView'] : Object.keys(baseline)
const prop = (node, name) => node.props.find(p => p.name === name || p.arg?.content === name)
const nodes = ast => {
  const result = []
  const visit = n => { if (n.type === 1) result.push(n); n.children?.forEach(visit) }
  visit(ast)
  return result
}
let count = 0
for (const name of names) {
  const source = read('web/src/modules/release/components/' + name + '.vue')
  const current = parse(source).descriptor, old = parse(baseline[name]).descriptor
  const stripped = current.scriptSetup.content
    .replace(/^import .*ReleaseDrawerHeader.*\n/gm, '')
    .replace(/^import .*useReleaseDrawerFullscreen.*\n/gm, '')
    .replace(/^const \{ fullscreen: .*\n  useReleaseDrawerFullscreen\([^\n]+\)\n/gm, '')
  assert.equal(stripped, old.scriptSetup.content, name + ': business script unchanged')
  const all = nodes(baseParse(current.template.content)), before = nodes(baseParse(old.template.content))
  const events = list => list.flatMap(n => n.props.filter(p => p.type === 7 && p.name === 'on')
    .map(p => [n.tag, p.arg?.content, p.exp?.content])).filter(event => event[0] !== 'ReleaseDrawerHeader')
  assert.deepEqual(events(all), events(before), name + ': original event handlers preserved')
  assert.deepEqual(all.filter(n => prop(n, 'model')).map(n => prop(n, 'model').exp?.content),
    before.filter(n => prop(n, 'model')).map(n => prop(n, 'model').exp?.content), name + ': all v-models preserved')
  const drawers = all.filter(n => n.tag === 'el-drawer')
  const originals = before.filter(n => n.tag === 'el-drawer')
  drawers.forEach((drawer, index) => {
    assert.equal(prop(drawer, 'size')?.type, 7, name + ': dynamic size required')
    const sizeName = prop(drawer, 'size').exp.content
    const prefix = sizeName.replace(/Size$/, '')
    assert.equal(drawer.props.find(p => p.arg?.content === 'class')?.exp?.content,
      "{ 'release-operations-fullscreen-drawer': " + prefix + 'Fullscreen }')
    const headerNode = nodes(drawer).find(n => n.tag === 'ReleaseDrawerHeader')
    assert.ok(headerNode, name + ': header required')
    assert.equal(prop(headerNode, 'fullscreen').exp.content, prefix + 'Fullscreen')
    assert.equal(prop(headerNode, 'title').exp.content, prop(drawer, 'title').exp.content)
    assert.ok(source.includes("useReleaseDrawerFullscreen(" + prop(drawer, 'model').exp.content
      + ", '" + prop(originals[index], 'size').value.content + "')"), 'original default size preserved')
    for (const key of ['before-close', 'close-on-click-modal', 'close-on-press-escape', 'show-close', 'destroy-on-close', 'title']) {
      const simplify = p => p ? [p.type, p.exp?.content, p.value?.content] : null
      assert.deepEqual(simplify(prop(drawer, key)), simplify(prop(originals[index], key)), key + ' preserved')
    }
    count++
  })
  assert.deepEqual(all.filter(n => n.tag === 'el-dialog').map(n => n.loc.source),
    before.filter(n => n.tag === 'el-dialog').map(n => n.loc.source), 'regular dialogs unchanged')
}
const header = read('web/src/modules/release/components/ReleaseDrawerHeader.vue')
assert.ok(header.includes('native-type="button"'))
assert.ok(header.includes(':id="titleId"'))
assert.ok(header.includes(':aria-label='))
assert.ok(!helper.includes('fetch(') && !helper.includes('api'))
assert.equal(count, process.argv.includes('T1') ? 2 : 5)
const css = require('postcss').parse(read('web/src/modules/release/release-operations.css'))
const fullscreenRule = css.nodes.find(n => n.selector === '.el-drawer.release-operations-fullscreen-drawer.rtl')
assert.ok(fullscreenRule, 'dedicated high specificity fullscreen selector')
const declarations = Object.fromEntries(fullscreenRule.nodes.map(n => [n.prop, n.value]))
assert.equal(declarations.width, '100vw')
assert.equal(declarations['max-width'], '100vw')
assert.equal(declarations.height, '100dvh')
assert.equal(declarations['max-height'], '100dvh')
console.log('PASS: reactive fullscreen/reset/isolation; ' + count + ' drawers; original scripts, bindings, guards and dialogs preserved')
