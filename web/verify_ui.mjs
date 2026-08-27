import { chromium } from 'playwright';

const BASE = 'http://localhost:5173';
const API = 'http://localhost:8080/api';
const TOKEN = process.env.TOKEN;

const log = (...a) => console.log(...a);
const results = [];
function check(name, ok, detail = '') {
  results.push({ name, ok, detail });
  log(`${ok ? 'PASS' : 'FAIL'} | ${name}${detail ? ' | ' + detail : ''}`);
}
async function api(path, opts = {}) {
  const r = await fetch(API + path, {
    ...opts,
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${TOKEN}`, ...(opts.headers || {}) },
  });
  return { status: r.status, body: await r.json().catch(() => null) };
}
// 字段维护抽屉定位器（按标题 “X字段维护”）
function fieldDrawer(page) {
  return page.locator('.el-drawer', { hasText: '字段维护' });
}

(async () => {
  const browser = await chromium.launch();
  const ctx = await browser.newContext({ viewport: { width: 1280, height: 900 } });
  const page = await ctx.newPage();
  page.on('pageerror', (e) => log('PAGEERROR', e.message));
  page.on('console', (m) => { if (m.type() === 'error') log('CONSOLE.ERR', m.text()); });

  await page.goto(BASE + '/login', { waitUntil: 'networkidle' });
  await page.fill('input[type="text"]', 'admin');
  await page.fill('input[type="password"]', 'admin123');
  await page.click('button[type="submit"]');
  await page.waitForURL('**/dashboard**', { timeout: 15000 }).catch(() => {});
  check('登录成功跳转到仪表盘', page.url().includes('dashboard'), page.url());

  async function openDrawer(category, rowText) {
    await page.goto(BASE + `/data-migration/base/${category}-tables`, { waitUntil: 'networkidle' });
    await page.waitForTimeout(1200);
    const rows = await page.locator('.el-table__row').count();
    const btn = rowText
      ? page.locator('.el-table__row', { hasText: rowText }).locator('button', { hasText: '字段' }).first()
      : page.locator('.el-table__row').first().locator('button', { hasText: '字段' });
    await btn.click();
    await page.waitForTimeout(1800);
    return rows;
  }

  // ============ 中间表页面 UI ============
  const midRows = await openDrawer('intermediate');
  check('中间表结构列表渲染', midRows > 0, `rows=${midRows}`);
  const drawer = fieldDrawer(page);
  check('中间表字段抽屉打开', await drawer.isVisible());
  const selCol = await drawer.locator('.el-table__header .el-checkbox').count();
  check('字段抽屉含选择列(checkbox)', selCol > 0, `checkbox=${selCol}`);
  const batchBtn = drawer.locator('button', { hasText: /批量删除字段/ });
  check('字段抽屉含批量删除按钮', await batchBtn.count() > 0);
  const batchText = await batchBtn.first().innerText().catch(() => '');
  check('批量删除按钮显示计数', /批量删除字段（\d+）/.test(batchText), batchText);

  const fieldRowCount = await drawer.locator('.el-table__row').count();
  check('字段抽屉内渲染字段行', fieldRowCount > 0, `rows=${fieldRowCount}`);

  // ---- 浏览器内批量删除一个字段，验证表保留 ----
  if (fieldRowCount > 0) {
    await drawer.locator('.el-table__body .el-checkbox').first().click();
    await page.waitForTimeout(300);
    await batchBtn.first().click();
    await page.waitForTimeout(400);
    const confirmBtn = page.locator('.el-message-box button', { hasText: '确定' });
    if (await confirmBtn.count() > 0) await confirmBtn.click();
    await page.waitForTimeout(1800);
    const afterRows = await drawer.locator('.el-table__row').count();
    check('批量删除1个字段后抽屉内字段减少', afterRows === fieldRowCount - 1, `before=${fieldRowCount} after=${afterRows}`);
    await drawer.locator('.el-drawer__headerbtn, .el-drawer__close-btn').first().click().catch(() => {});
    await page.waitForTimeout(800);
    // 列表是字段粒度，删除字段后行数会减少；验证主列表仍有数据即可
    const midRowsAfter = await page.locator('.el-table__row').count();
    check('批量删除字段后中间表列表仍有数据', midRowsAfter > 0, `rows=${midRowsAfter}`);
  } else {
    check('批量删除1个字段后抽屉内字段减少', false, '无字段行可操作');
  }

  // ============ 目标表页面 ============
  const tgtRows = await openDrawer('target');
  check('目标表结构列表渲染', tgtRows > 0, `rows=${tgtRows}`);
  const tDrawer = fieldDrawer(page);
  check('目标表字段抽屉打开', await tDrawer.isVisible());
  check('目标表字段抽屉含批量删除按钮', (await tDrawer.locator('button', { hasText: /批量删除字段/ }).count()) > 0);
  check('目标表字段抽屉含选择列', (await tDrawer.locator('.el-table__header .el-checkbox').count()) > 0);
  await tDrawer.locator('.el-drawer__headerbtn, .el-drawer__close-btn').first().click().catch(() => {});

  // ============ 级联删除验证（建临时表，浏览器删末字段）============
  const pid = 1787357565278326;
  const tableNameEn = 'I_TMP_CASCADE_' + Date.now();
  const tableNameCn = '级联验收_' + Date.now();
  const mk = await api('/data-migration/target-tables?category=INTERMEDIATE', {
    method: 'POST',
    body: JSON.stringify({
      projectId: pid, systemCode: 'W0741Y',
      tableNameEn, tableNameCn,
      tableMeaning: 'verify', ownerId: 1,
      fields: [{ fieldNameEn: 'tmp_f1', fieldNameCn: '临时字段', fieldMeaning: 't', isKeyField: 1, isPrimaryKey: 1, mysqlType: 'VARCHAR(10)', oracleType: 'VARCHAR2(10)', isNullable: 0 }],
    }),
  });
  check('创建级联验收临时表', mk.status === 200, `status=${mk.status} msg=${mk.body?.message || ''}`);
  const allTables = await api('/data-migration/target-tables?category=INTERMEDIATE&pageSize=200');
  const tmpRec = (allTables.body?.data?.records || []).find(r => r.table_name_en === tableNameEn);
  const tmpTableId = tmpRec?.id || tmpRec?.table_id;
  check('定位临时表记录', Boolean(tmpTableId), `tableId=${tmpTableId}`);

  if (tmpTableId) {
    await openDrawer('intermediate', tableNameEn);
    const cDrawer = fieldDrawer(page);
    const onlyOne = await cDrawer.locator('.el-table__row').count();
    check('临时表抽屉内仅1个字段', onlyOne === 1, `rows=${onlyOne}`);
    if (onlyOne === 1) {
      await cDrawer.locator('.el-table__body .el-checkbox').first().click();
      await page.waitForTimeout(300);
      await cDrawer.locator('button', { hasText: /批量删除字段/ }).first().click();
      await page.waitForTimeout(400);
      const cb2 = page.locator('.el-message-box button', { hasText: '确定' });
      if (await cb2.count() > 0) await cb2.click();
      await page.waitForTimeout(2000);
      const drawerGone = !(await fieldDrawer(page).isVisible().catch(() => false));
      check('删除末字段后抽屉关闭(级联删除表)', drawerGone);
      const verify = await api(`/data-migration/target-tables/${tmpTableId}?category=INTERMEDIATE`);
      check('级联删除后临时表已不存在', verify.status === 400 || verify.status === 404, `status=${verify.status}`);
    }
  } else {
    check('临时表抽屉内仅1个字段', false, '临时表未找到，跳过级联删除测试');
  }

  // ============ 移动端视口验收 ============
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto(BASE + '/data-migration/base/intermediate-tables', { waitUntil: 'networkidle' });
  await page.waitForTimeout(2000);
  // 移动端使用卡片列表而非el-table
  const mobileCards = await page.locator('.dm-mobile-list article').count();
  const desktopRows = await page.locator('.el-table__row').count();
  const midRowsM = mobileCards || desktopRows;
  check('移动端(390px)中间表列表渲染', midRowsM > 0, `mobileCards=${mobileCards} desktopRows=${desktopRows}`);
  // 移动端点击卡片footer中的字段按钮
  const mFieldBtn = mobileCards > 0
    ? page.locator('.dm-mobile-list article').first().locator('button', { hasText: '字段' })
    : page.locator('.el-table__row').first().locator('button', { hasText: '字段' });
  if (await mFieldBtn.count() > 0) {
    await mFieldBtn.click({ timeout: 10000 }).catch(() => {});
    await page.waitForTimeout(2000);
    const mDrawer = fieldDrawer(page);
    if (await mDrawer.isVisible()) {
      check('移动端字段抽屉打开', true);
      check('移动端含批量删除按钮', (await mDrawer.locator('button', { hasText: /批量删除字段/ }).count()) > 0);
      const mFieldRows = await mDrawer.locator('.el-table__row').count();
      check('移动端字段抽屉渲染字段行', mFieldRows > 0, `rows=${mFieldRows}`);
    } else {
      check('移动端字段抽屉打开', false, '抽屉未打开');
    }
    await page.screenshot({ path: '/tmp/verify_mobile_drawer.png', fullPage: false });
  }
  await page.setViewportSize({ width: 1280, height: 900 });
  await page.goto(BASE + '/data-migration/base/intermediate-tables', { waitUntil: 'networkidle' });
  await page.waitForTimeout(1500);
  await page.screenshot({ path: '/tmp/verify_desktop.png', fullPage: false });

  await browser.close();

  const failed = results.filter(r => !r.ok);
  log('\n==== 验收汇总 ====');
  log(`总计 ${results.length}，通过 ${results.length - failed.length}，失败 ${failed.length}`);
  if (failed.length) { failed.forEach(f => log(' - FAIL ' + f.name + ' | ' + f.detail)); process.exit(1); }
  process.exit(0);
})().catch((e) => { console.error('SCRIPT ERROR', e); process.exit(2); });
