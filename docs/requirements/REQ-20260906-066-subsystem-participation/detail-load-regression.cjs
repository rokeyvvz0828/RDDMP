// 从实际页面提取加载函数验证异步边界，不替代完整Vue挂载与浏览器验收。
const fs = require('node:fs');
const vm = require('node:vm');
const assert = require('node:assert/strict');
const source = fs.readFileSync('web/src/modules/architecture/PlanDetailPage.vue','utf8');
const code = source.slice(source.indexOf('let loadSequence = 0'), source.indexOf('\nonMounted(', source.indexOf('let loadSequence = 0')));
const ref = value => ({value});
let pending=[]; let calls=0;
const c={projectContext:{currentRef:''},loading:ref(false),loadError:ref(''),detail:ref(null),dashboard:ref(null),timeline:ref(null),suggestions:ref([]),currentTask:ref(null),taskDrawerVisible:ref(false),openStages:ref([]),boardAll:ref(false),planId:1,flowInitialized:ref(false),getPlan:()=>{calls++;return new Promise((resolve,reject)=>pending.push({resolve,reject}));},loadUserMap:()=>{},getPlanDashboard:async()=>({ok:true}),getPlanTimeline:async()=>({ok:true}),listPlanSuggestions:async()=>[],buildFlowchart:()=>{},nextTick:()=>{},apiErrorMessage:()=> 'failed'};
vm.createContext(c); vm.runInContext(code,c);
(async()=>{
 await c.loadAll(); assert.equal(calls,0);
 c.projectContext.currentRef='A'; const first=c.loadAll();
 c.projectContext.currentRef='B'; const second=c.loadAll();
 pending[1].resolve({name:'B',stages:[]}); await second;
 pending[0].resolve({name:'A',stages:[]}); await first;
 assert.equal(c.detail.value.name,'B'); assert.equal(c.loading.value,false);
 const stale=c.loadAll(); const latest=c.loadAll();
 pending[2].reject(Error('stale')); await stale;
 assert.equal(c.loadError.value,''); assert.equal(c.loading.value,true);
 pending[3].resolve({name:'latest',stages:[]}); await latest;
 assert.equal(c.detail.value.name,'latest'); assert.equal(c.loading.value,false);
 const failed=c.loadAll(); pending[4].reject(Error('failure')); await failed;
 assert.equal(c.loadError.value,'failed'); assert.equal(c.loading.value,false);
 assert.match(source,/watch\(\(\) => projectContext.currentRef/);
 console.log('通过5项回归：等待项目、忽略旧成功响应、忽略旧失败响应、采用最新响应、失败可恢复');
})().catch(e=>{console.error(e); process.exitCode=1});
