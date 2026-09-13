/* 记一笔 · 冒烟测试（回归测试脚本，不参与线上运行）
 *
 * 作用：在 jsdom 中加载真实的 index.html 与 app.js，模拟用户点击，
 *       校验以周为周期的记账、编辑、删除、统计、周/月预算、周切换、
 *       导入导出与动效执行等行为。
 *
 * 运行方式（需 Node.js 18+）：
 *   1) 在一个临时目录安装依赖：  npm install jsdom
 *   2) 执行：                    node tools/smoke-test.js
 *   3) 若脚本与本目录不在同一处，请先修改下方 DIR 为项目根目录绝对路径。
 *
 * 全部通过时退出码为 0，存在失败或运行时错误时退出码为 1。
 */
const fs = require('fs');
const path = require('path');
const { JSDOM } = require('jsdom');

const DIR = 'C:\\Users\\minec\\WorkBuddy\\记账软件';

/* ============================================================
   日期辅助（与 app.js 的周规则一致：周一为一周之始）
   ============================================================ */
const pad2 = n => (n < 10 ? '0' + n : String(n));
function dstr(d) { return d.getFullYear() + '-' + pad2(d.getMonth() + 1) + '-' + pad2(d.getDate()); }
function md(dateStr) { const p = dateStr.split('-'); return Number(p[1]) + '/' + Number(p[2]); }
function parse(dateStr) { const p = dateStr.split('-').map(Number); return new Date(p[0], p[1] - 1, p[2]); }
function mondayOf(dateStr) {
  const d = parse(dateStr);
  const wd = d.getDay();
  return dstr(new Date(d.getFullYear(), d.getMonth(), d.getDate() + (wd === 0 ? -6 : 1 - wd)));
}
function shiftDays(dateStr, n) {
  const d = parse(dateStr);
  return dstr(new Date(d.getFullYear(), d.getMonth(), d.getDate() + n));
}
function weekLabelOf(monday) { return md(monday) + '–' + md(shiftDays(monday, 6)); }

const TODAY = dstr(new Date());
const THIS_MON = mondayOf(TODAY);
const LAST_MON = shiftDays(THIS_MON, -7);
const THIS_LABEL = weekLabelOf(THIS_MON);
const LAST_LABEL = weekLabelOf(LAST_MON);
const OLD_MON = mondayOf(shiftDays(TODAY, -60));
const OLD_LABEL = weekLabelOf(OLD_MON);

const results = [];
let failed = 0;

function check(name, cond, extra) {
  const ok = !!cond;
  if (!ok) failed++;
  results.push((ok ? '  PASS  ' : '  FAIL  ') + name + (extra && !ok ? '  → ' + extra : ''));
}

const errors = [];

/* 等待 Promise 链与微任务清空 */
const flush = () => new Promise(r => setTimeout(r, 10));

function makeDom(html, appJs, storage, opts) {
  opts = opts || {};
  const dom = new JSDOM(html, {
    url: 'http://localhost/index.html',
    runScripts: 'dangerously',
    pretendToBeVisual: true
  });
  const w = dom.window;

  /* 默认禁用动效以保证断言稳定；opts.animations 为 true 时保留动效以验证其真实执行 */
  w.matchMedia = function (q) {
    return {
      matches: !opts.animations, media: q, onchange: null,
      addListener() {}, removeListener() {},
      addEventListener() {}, removeEventListener() {}, dispatchEvent() { return false; }
    };
  };

  if (storage) w.localStorage.setItem('jiyibi.v1', storage);
  w.addEventListener('error', e => errors.push('error 事件: ' + e.message));
  w.addEventListener('unhandledrejection', e => errors.push('未处理 Promise: ' + e.reason));
  const rawError = w.console.error;
  w.console.error = function () {
    errors.push('console.error: ' + Array.prototype.join.call(arguments, ' '));
    rawError.apply(w.console, arguments);
  };

  w.eval(appJs);
  if (w.document.readyState === 'loading') {
    w.document.dispatchEvent(new w.Event('DOMContentLoaded', { bubbles: true }));
  }
  return dom;
}

async function run() {
  const htmlRaw = fs.readFileSync(path.join(DIR, 'index.html'), 'utf8');
  const appJs = fs.readFileSync(path.join(DIR, 'app.js'), 'utf8');
  const html = htmlRaw.replace(/<script src="\.\/app\.js"><\/script>/, '');

  const dom = makeDom(html, appJs);
  const window = dom.window;
  const doc = window.document;

  const $ = s => doc.querySelector(s);
  const $$ = s => Array.prototype.slice.call(doc.querySelectorAll(s));

  const click = el => {
    if (!el) throw new Error('元素不存在，无法点击');
    el.dispatchEvent(new window.MouseEvent('click', { bubbles: true, cancelable: true }));
  };
  const key = k => click($$('#keypad button').filter(b => b.dataset.k === k)[0]);
  const typeAmount = s => String(s).split('').forEach(c => key(c));
  const store = () => JSON.parse(window.localStorage.getItem('jiyibi.v1') || 'null');

  try {
    /* ---------- 1. 初始渲染（周视角） ---------- */
    check('首页概览卡渲染', $('#main .overview'), $('#main').innerHTML.slice(0, 60));
    check('底部导航含 3 个页签', $$('#tabbar .tab').length === 3, '实际 ' + $$('#tabbar .tab').length);
    check('当前页签为「明细」', $('#tabbar .tab.on') && $('#tabbar .tab.on').textContent.trim() === '明细');
    check('空状态提示存在', $('#main .empty'), '无空状态');
    check('顶部标题显示本周区间', $('#weekLabel').textContent === '本周 · ' + THIS_LABEL, $('#weekLabel').textContent);
    check('概览卡标注本周支出', /本周支出/.test($('#main .ov-label').textContent), $('#main .ov-label').textContent);
    check('不允许浏览未来周：下一周按钮隐藏', $('#nextWeek').style.visibility === 'hidden');

    /* M3 导航栏结构 */
    check('导航项含胶囊指示器', $$('#tabbar .tab .ind').length === 3, $$('#tabbar .tab .ind').length + ' 个');
    check('选中项使用填充态图标', /stroke-width="2\.5"/.test($('#tabbar .tab.on .ind svg').innerHTML),
      $('#tabbar .tab.on .ind svg').innerHTML.slice(0, 70));
    check('未选中项使用描边态图标', /stroke-width="1\.8"/.test($$('#tabbar .tab:not(.on) .ind svg')[0].innerHTML),
      $$('#tabbar .tab:not(.on) .ind svg')[0].innerHTML.slice(0, 70));

    /* ---------- 2. 记账：支出 ---------- */
    click($('#fab'));
    check('记账面板已打开', $('#sheet').classList.contains('show'));
    check('支出分类 12 项', $('#cats').children.length === 12, '实际 ' + $('#cats').children.length);
    check('分类注入错峰动画序号', !!$('#cats .cat[style*="--ci"]'), '无 --ci');
    check('初始保存按钮禁用', $('#saveBtn').disabled === true);

    click($('#cats').children[0]);                 // 餐饮
    typeAmount('12.50');
    check('金额显示为 12.50', $('#amountText').textContent.trim() === '12.50', $('#amountText').textContent);
    check('选分类且金额有效后按钮启用', $('#saveBtn').disabled === false);

    $('#noteInput').value = '公司楼下快餐';
    click($('#saveBtn'));
    await flush();

    let st = store();
    check('记录已写入本机存储', st && st.records.length === 1, JSON.stringify(st));
    check('金额以「分」存储为 1250', st.records[0].amount === 1250, String(st.records[0].amount));
    check('分类为餐饮', st.records[0].category === 'food', st.records[0].category);
    check('类型为支出', st.records[0].type === 'expense', st.records[0].type);
    check('备注已保存', st.records[0].note === '公司楼下快餐', st.records[0].note);
    check('日期为今天', st.records[0].date === TODAY, st.records[0].date);
    check('面板已关闭', !$('#sheet').classList.contains('show'));
    check('列表出现新记录', /公司楼下快餐/.test($('#main').innerHTML));
    check('概览支出金额更新为 12.50', /^¥?12\.50$/.test($('#main .ov-main').textContent.trim()),
      $('#main .ov-main').textContent);
    check('列表项注入错峰动画序号', !!$('#main .item[style*="--i"]'), '无 --i');

    /* ---------- 3. 记账：收入 + 千分位 ---------- */
    click($('#fab'));
    click($$('#typeSeg button')[1]);              // 切到收入
    check('收入分类 8 项', $('#cats').children.length === 8, '实际 ' + $('#cats').children.length);
    check('切换类型后分类已重置', $('#saveBtn').disabled === true);
    click($('#cats').children[0]);                // 工资
    typeAmount('12345.6');
    check('千分位显示 12,345.6', $('#amountText').textContent.trim() === '12,345.6', $('#amountText').textContent);
    click($('#saveBtn'));
    await flush();

    st = store();
    check('共 2 条记录', st.records.length === 2, '实际 ' + st.records.length);
    check('收入金额 1234560 分', st.records[1].amount === 1234560, String(st.records[1].amount));
    check('概览显示收入与结余', /12,345\.60/.test($('#main').innerHTML), '未渲染收入');
    check('列表收入显示为 +12,345.60', /\+12,345\.60/.test($('#main').innerHTML));

    /* ---------- 4. 编辑已有记录 ---------- */
    click($('#main .item'));
    check('编辑面板打开', $('#sheet').classList.contains('show'));
    check('编辑态显示删除按钮', $('#sheetActions').style.display === 'flex');
    check('回填了原金额', $('#amountText').textContent.trim() === '12,345.60', $('#amountText').textContent);
    key('clr');
    typeAmount('99');
    click($('#saveBtn'));
    await flush();
    st = store();
    check('编辑后金额更新为 9900 分', st.records[1].amount === 9900, String(st.records[1].amount));
    check('记录总数仍为 2', st.records.length === 2);
    check('编辑后类型保持为收入', st.records[1].type === 'income', st.records[1].type);

    /* ---------- 5. 数字键盘校验 ---------- */
    click($('#fab'));
    click($('#cats').children[2]);
    typeAmount('1.234');
    check('小数位限制为两位', $('#amountText').textContent.trim() === '1.23', $('#amountText').textContent);
    for (let i = 0; i < 12; i++) key('0');
    check('整数位上限 8 位', $('#amountText').textContent.replace(/[,.]/g, '').length <= 10,
      $('#amountText').textContent);
    key('clr');
    check('清空后回到占位符', /0\.00/.test($('#amountText').textContent));
    click($('#cancelBtn'));
    check('取消后关闭面板且不新增记录', store().records.length === 2, String(store().records.length));

    /* ---------- 6. 统计页（周维度） ---------- */
    click($$('#tabbar .tab')[1]);
    check('统计页渲染环形图', $('#main .donut svg'), '无环形图');
    check('统计页渲染分类排行', $('#main .rank .row'), '无排行');
    check('趋势标题已改为「近 6 周趋势」', /近 6 周趋势/.test($('#main').textContent), '标题未更新');
    check('趋势图为 6 列', $$('#main .bars .col').length === 6, $$('#main .bars .col').length + ' 列');
    check('柱状图列注入错峰序号', $$('#main .bars .col[style*="--i"]').length === 6);
    check('统计卡片带动画类', $$('#main .anim-card').length >= 2, $$('#main .anim-card').length + ' 个');
    check('排行行注入错峰序号', !!$('#main .rank .row[style*="--i"]'), '无 --i');
    check('统计页含收支切换', $('#statsSeg') && $('#statsSeg').children.length === 2);
    check('环形图含描边分段', $$('#main .donut svg circle').length >= 2, '段数不足');
    click($('#statsSeg').children[1]);            // 切到收入
    check('切换收入后统计重绘', /总收入/.test($('#main').innerHTML), '未切换');
    click($('#statsSeg').children[0]);            // 切回支出

    /* ---------- 7. 设置页与预算 ---------- */
    click($$('#tabbar .tab')[2]);
    check('设置页渲染月度预算入口', /月度预算/.test($('#main').innerHTML));
    check('设置页渲染每周预算入口', /每周预算/.test($('#main').innerHTML));
    check('设置页渲染数据管理', /导出备份/.test($('#main').innerHTML) && /导入账单/.test($('#main').innerHTML));
    check('设置页隐藏悬浮按钮', $('#fab').style.display === 'none');
    check('设置分组带动画类', $$('#main .setting-group.anim-card').length === 2,
      $$('#main .setting-group.anim-card').length + ' 个');
    check('关于卡片带动画类', $$('#main .card.anim-card').length >= 1, '无动画类');

    click($$('#main .setrow').filter(r => r.dataset.action === 'budget')[0]);
    check('月预算对话框打开', $('#dialogMask').classList.contains('show'));
    $('#dlgInput').value = '5000';
    click($('#dlgOk'));
    await flush();
    check('月预算已保存为 500000 分', store().budget === 500000, String(store().budget));

    click($$('#main .setrow').filter(r => r.dataset.action === 'budget-week')[0]);
    check('周预算对话框标题为「每周预算」', /每周预算/.test($('#dialog').textContent),
      $('#dialog').textContent.slice(0, 40));
    $('#dlgInput').value = '500';
    click($('#dlgOk'));
    await flush();
    check('周预算已保存为 50000 分', store().weekly === 50000, String(store().weekly));
    check('月度预算未被周预算覆盖', store().budget === 500000, String(store().budget));

    click($$('#tabbar .tab')[0]);
    const ov = $('#main .overview').textContent;
    check('首页显示两条预算进度', $$('#main .budget-bar').length === 2,
      $$('#main .budget-bar').length + ' 条');
    check('周预算排在本月预算之前', ov.indexOf('本周预算') < ov.indexOf('本月预算'), ov.slice(0, 120));
    check('周预算显示统计区间', ov.indexOf('本周预算（' + THIS_LABEL + '）') >= 0, ov.slice(0, 90));
    check('周预算金额正确', /本周预算.*¥500\.00/.test(ov), ov.slice(0, 90));
    check('周预算剩余金额正确', /剩余 ¥487\.50/.test(ov), ov.slice(0, 140));
    check('月预算文案正确', /本月预算 ¥5,000\.00/.test(ov), ov.slice(0, 140));
    check('月预算按该周所在月份累计', /本月预算 ¥5,000\.00/.test(ov) && $$('#main .budget-bar').length === 2);

    /* ---------- 8. 周切换 ---------- */
    click($('#prevWeek'));
    check('切换到上一周', $('#weekLabel').textContent === '上周 · ' + LAST_LABEL, $('#weekLabel').textContent);
    check('上一周显示空状态', $('#main .empty'), '未显示空状态');
    check('历史周仍显示周预算进度', !!$('#main .budget-bar'), '预算条丢失');
    check('历史周文案改为「该周预算」并带上正确区间',
      $('#main .overview').textContent.indexOf('该周预算（' + LAST_LABEL + '）') >= 0,
      $('#main .overview').textContent.slice(0, 90));
    check('下一周按钮恢复显示', $('#nextWeek').style.visibility === 'visible');
    click($('#nextWeek'));
    check('切回本周', $('#weekLabel').textContent === '本周 · ' + THIS_LABEL, $('#weekLabel').textContent);
    check('下一周按钮重新隐藏', $('#nextWeek').style.visibility === 'hidden');

    /* ---------- 9. 日期选择器跳转 ---------- */
    click($('#weekPick'));
    check('日期选择对话框打开', $('#dialogMask').classList.contains('show'));
    $('#dlgInput').value = shiftDays(TODAY, -60);
    click($('#dlgOk'));
    await flush();
    check('跳转到所选日期所在周', $('#weekLabel').textContent === OLD_LABEL, $('#weekLabel').textContent);
    check('跳转后该周无记录', $('#main .empty'), '未显示空状态');

    click($('#weekPick'));
    $('#dlgInput').value = TODAY;
    click($('#dlgOk'));
    await flush();
    check('跳回本周', $('#weekLabel').textContent === '本周 · ' + THIS_LABEL, $('#weekLabel').textContent);

    /* ---------- 10. 删除记录 ---------- */
    click($('#main .item'));
    click($('#deleteBtn'));
    check('删除确认框出现', $('#dialogMask').classList.contains('show'));
    check('删除为危险操作样式', !!$('#dlgOk.danger'));
    click($('#dlgOk'));
    await flush();
    check('记录已删除', store().records.length === 1, String(store().records.length));

    /* ---------- 11. 持久化与重载 ---------- */
    const snapshot = window.localStorage.getItem('jiyibi.v1');
    check('数据已持久化到 localStorage', !!snapshot && JSON.parse(snapshot).records.length === 1,
      snapshot ? String(JSON.parse(snapshot).records.length) : 'null');

    const dom2 = makeDom(html, appJs, snapshot);
    const ov2 = dom2.window.document.querySelector('#main .overview').textContent;
    check('重新打开页面后数据恢复', /12\.50/.test(dom2.window.document.querySelector('#main .ov-main').textContent),
      dom2.window.document.querySelector('#main .ov-main').textContent);
    check('重载后周预算一并恢复', /本周预算/.test(ov2), ov2.slice(0, 90));
    check('重载后月预算一并恢复', /5,000\.00/.test(ov2), ov2.slice(0, 140));
    check('重载后停留在本周',
      dom2.window.document.querySelector('#weekLabel').textContent === '本周 · ' + THIS_LABEL,
      dom2.window.document.querySelector('#weekLabel').textContent);

    /* ---------- 12. 导出文件 ---------- */
    click($$('#tabbar .tab')[2]);
    let capturedBlob = null;
    const origCreateURL = window.URL.createObjectURL;
    const origAnchorClick = window.HTMLAnchorElement.prototype.click;
    window.URL.createObjectURL = function (b) { capturedBlob = b; return 'blob:mock'; };
    window.HTMLAnchorElement.prototype.click = function () { /* 阻止真实下载 */ };

    click($$('#main .setrow').filter(r => r.dataset.action === 'export-csv')[0]);
    await flush();
    check('导出表格生成文件', capturedBlob && capturedBlob.size > 0,
      capturedBlob ? capturedBlob.size + ' 字节' : '未生成');
    check('导出文件类型为 CSV', !!capturedBlob && /csv/.test(capturedBlob.type), capturedBlob && capturedBlob.type);
    capturedBlob = null;
    click($$('#main .setrow').filter(r => r.dataset.action === 'export-json')[0]);
    await flush();
    check('导出备份生成文件', capturedBlob && capturedBlob.size > 0,
      capturedBlob ? capturedBlob.size + ' 字节' : '未生成');
    check('导出备份类型为 JSON', !!capturedBlob && /json/.test(capturedBlob.type), capturedBlob && capturedBlob.type);

    /* ---------- 13. 导入微信账单（跳过转账） ---------- */
    const wechatCSV = [
      '微信支付账单明细',
      '微信昵称：[测试]',
      '起始时间：[2026-08-01 00:00:00] 终止时间：[2026-08-31 23:59:59]',
      '----------------------微信支付账单明细列表--------------------',
      '交易时间,交易类型,交易对方,商品,收/支,金额(元),支付方式,当前状态,交易单号,商户单号,备注',
      '2026-08-15 12:30:00,商户消费,某某餐厅,午餐,支出,¥28.00,零钱,支付成功,1001,2001,',
      '2026-08-15 09:00:00,转账,张三,转账,支出,¥100.00,零钱,已存入零钱,1002,2002,',
      '2026-08-16 10:00:00,微信红包,李四,红包,收入,¥50.00,零钱,已收钱,1003,2003,',
      '2026-08-17 18:00:00,商户消费,滴滴出行,打车,支出,¥35.50,零钱,支付成功,1004,2004,'
    ].join('\r\n');

    let pendingFile = null;
    const proto = window.HTMLInputElement.prototype;
    const origInputClick = proto.click;
    proto.click = function () {
      if (this.type === 'file' && pendingFile) {
        const f = pendingFile;
        pendingFile = null;
        Object.defineProperty(this, 'files', { value: [f], configurable: true });
        this.dispatchEvent(new window.Event('change', { bubbles: true }));
        return;
      }
      return origInputClick.apply(this, arguments);
    };

    function importBillFile(csvText, fileName) {
      pendingFile = new window.File([csvText], fileName, { type: 'text/csv' });
      click($$('#main .setrow').filter(r => r.dataset.action === 'import-bill')[0]);
    }

    importBillFile(wechatCSV, '微信支付账单.csv');
    await flush();
    check('账单导入弹出预览', /识别记录/.test($('#dialog').textContent), $('#dialog').textContent.slice(0, 80));
    check('识别出 3 笔有效记录', /3 笔/.test($('#dialog').textContent), $('#dialog').textContent.slice(0, 160));
    check('提示跳过转账记录', /已跳过 1 条/.test($('#dialog').textContent), $('#dialog').textContent.slice(0, 220));
    click($('#dlgOk'));
    await flush();

    let recs = store().records;
    const find = amt => recs.filter(r => r.amount === amt)[0];
    check('导入后记录总数为 4', recs.length === 4, String(recs.length));
    check('餐饮 28.00 归类正确', find(2800) && find(2800).category === 'food', find(2800) && find(2800).category);
    check('打车 35.50 归类正确', find(3550) && find(3550).category === 'transport', find(3550) && find(3550).category);
    check('红包 50.00 识别为收入', find(5000) && find(5000).type === 'income', find(5000) && find(5000).type);
    check('红包归类为红包', find(5000) && find(5000).category === 'redpack', find(5000) && find(5000).category);
    check('转账记录已跳过', !find(10000), '转账被误导入');
    check('账单日期解析正确', find(2800) && find(2800).date === '2026-08-15', find(2800) && find(2800).date);
    check('支付方式识别为微信', find(2800) && find(2800).account === '微信', find(2800) && find(2800).account);
    check('导入后跳到最新记录所在周',
      $('#weekLabel').textContent === weekLabelOf(mondayOf('2026-08-17')),
      $('#weekLabel').textContent);

    /* ---------- 14. 导入支付宝账单 ---------- */
    const alipayCSV = [
      '支付宝交易记录明细查询',
      '账号:[test@example.com]',
      '起始日期:[2026-08-01 00:00:00]    终止日期:[2026-08-31 23:59:59]',
      '---------------------------------交易记录明细列表------------------------------------',
      '交易号 ,商家订单号 ,交易创建时间 ,付款时间 ,最近修改时间 ,交易来源地 ,类型 ,交易对方 ,商品名称 ,金额（元） ,收/支 ,交易状态 ,服务费（元） ,成功退款（元） ,备注 ,资金状态 ,',
      '20260815001,,2026-08-15 08:20:00,2026-08-15 08:20:00,,支付宝网站,即时到账交易,某某超市,日用百货,56.80,支出,交易成功,0.00,0.00,,已支出,'
    ].join('\r\n');

    click($$('#tabbar .tab')[2]);
    importBillFile(alipayCSV, '支付宝账单.csv');
    await flush();
    check('支付宝账单可识别', /识别记录/.test($('#dialog').textContent), $('#dialog').textContent.slice(0, 80));
    check('支付宝识别 1 笔', /1 笔/.test($('#dialog').textContent), $('#dialog').textContent.slice(0, 160));
    click($('#dlgOk'));
    await flush();
    recs = store().records;
    check('导入后总数为 5', recs.length === 5, String(recs.length));
    const shop = recs.filter(r => r.amount === 5680)[0];
    check('超市消费归类为购物', shop && shop.category === 'shopping', shop && shop.category);
    check('支付宝金额解析正确', !!shop);

    /* ---------- 15. 清空数据 ---------- */
    proto.click = origInputClick;
    window.URL.createObjectURL = origCreateURL;
    window.HTMLAnchorElement.prototype.click = origAnchorClick;

    click($$('#tabbar .tab')[2]);
    click($$('#main .setrow').filter(r => r.dataset.action === 'clear')[0]);
    await flush();
    check('清空前弹出二次确认', $('#dialogMask').classList.contains('show'));
    click($('#dlgOk'));
    await flush();
    check('清空需要两次确认', $('#dialogMask').classList.contains('show') && /真的要清空/.test($('#dialog').textContent));
    click($('#dlgOk'));
    await flush();
    check('数据已清空', store().records.length === 0, String(store().records.length));
    check('月预算已重置', store().budget === 0, String(store().budget));
    check('周预算已重置', store().weekly === 0, String(store().weekly));
    check('清空后设置页统计归零', /累计记录 0 笔/.test($('#main').textContent), $('#main').textContent.slice(0, 60));
    click($$('#tabbar .tab')[0]);
    check('清空后明细页显示空状态', /没有记录/.test($('#main .empty').textContent),
      $('#main .empty') ? $('#main .empty').textContent : '无空状态');
    check('清空后不再显示预算条', $$('#main .budget-bar').length === 0, '预算条仍存在');

    /* ---------- 16. 动效真实执行（不禁用动画） ---------- */
    const animData = JSON.stringify({
      records: [{
        id: 'a1', type: 'expense', amount: 12345, category: 'food',
        date: TODAY, note: '', account: '微信', createdAt: Date.now()
      }],
      budget: 0, weekly: 0
    });
    const dom3 = makeDom(html, appJs, animData, { animations: true });
    const ovEl = dom3.window.document.querySelector('#ovMain');
    check('概览金额动画起始为 0.00', ovEl.textContent === '0.00', ovEl.textContent);
    await new Promise(r => setTimeout(r, 900));
    check('数字滚动动画结束于目标金额', ovEl.textContent === '123.45', ovEl.textContent);

    /* 水波纹：jsdom 不计算布局，需手动给定尺寸 */
    dom3.window.Element.prototype.getBoundingClientRect = function () {
      return { width: 120, height: 60, top: 0, left: 0, right: 120, bottom: 60, x: 0, y: 0 };
    };
    const fabEl = dom3.window.document.querySelector('#fab');
    fabEl.dispatchEvent(new dom3.window.MouseEvent('pointerdown', { bubbles: true, clientX: 60, clientY: 30 }));
    check('按压生成水波纹', dom3.window.document.querySelectorAll('.ripple-wave').length === 1,
      dom3.window.document.querySelectorAll('.ripple-wave').length + ' 个');
    check('按压元素获得 ripple 类', fabEl.classList.contains('ripple'), '未添加类');

  } catch (e) {
    errors.push('测试中断: ' + e.message + '\n' + String(e.stack).split('\n').slice(0, 4).join('\n'));
  }

  finish();
}

function finish() {
  console.log('\n================ 测试结果 ================');
  results.forEach(r => console.log(r));
  const pass = results.filter(r => r.startsWith('  PASS')).length;
  console.log('------------------------------------------');
  console.log('通过 ' + pass + ' / ' + results.length + '，失败 ' + failed);
  if (errors.length) {
    console.log('\n================ 运行时错误 ================');
    errors.forEach(e => console.log('  ! ' + e));
  } else {
    console.log('无 JavaScript 运行时错误。');
  }
  process.exit(failed || errors.length ? 1 : 0);
}

run();
