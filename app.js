/* ============================================================
   记一笔 · 收支记账
   纯前端实现，数据保存在本机 localStorage，不联网上传
   ============================================================ */
(function () {
  'use strict';

  /* ============================================================
     一、常量定义
     ============================================================ */

  var STORE_KEY = 'jiyibi.v1';

  var CATEGORIES = {
    expense: [
      { id: 'food',      name: '餐饮', icon: '🍜', color: '#f97066' },
      { id: 'transport', name: '交通', icon: '🚌', color: '#4f8bff' },
      { id: 'shopping',  name: '购物', icon: '🛍️', color: '#b176f0' },
      { id: 'home',      name: '居家', icon: '🏠', color: '#f7a23b' },
      { id: 'telecom',   name: '通讯', icon: '📱', color: '#22a2c9' },
      { id: 'entertain', name: '娱乐', icon: '🎮', color: '#e5639c' },
      { id: 'medical',   name: '医疗', icon: '💊', color: '#39b980' },
      { id: 'study',     name: '学习', icon: '📚', color: '#6d5ef8' },
      { id: 'social',    name: '人情', icon: '🎁', color: '#ef6b7b' },
      { id: 'sport',     name: '运动', icon: '🏃', color: '#0ea5b7' },
      { id: 'pet',       name: '宠物', icon: '🐾', color: '#c58a4a' },
      { id: 'other_e',   name: '其他', icon: '📦', color: '#8b95a8' }
    ],
    income: [
      { id: 'salary',     name: '工资', icon: '💰', color: '#12a669' },
      { id: 'bonus',      name: '奖金', icon: '🏆', color: '#f0a020' },
      { id: 'parttime',   name: '兼职', icon: '💼', color: '#4f8bff' },
      { id: 'invest',     name: '投资', icon: '📈', color: '#e5484d' },
      { id: 'redpack',    name: '红包', icon: '🧧', color: '#e5484d' },
      { id: 'reimburse',  name: '报销', icon: '🧾', color: '#6d5ef8' },
      { id: 'refund',     name: '退款', icon: '↩️', color: '#22a2c9' },
      { id: 'other_i',    name: '其他', icon: '📦', color: '#8b95a8' }
    ]
  };

  var ACCOUNTS = ['微信', '支付宝', '银行卡', '信用卡', '现金', '其他'];

  /* 齿轮轮廓（设置图标，描边与填充两种状态共用） */
  var GEAR_PATH = 'M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 ' +
    '1.65 1.65 0 0 0-1 1.51V21a2 2 0 1 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06' +
    'A1.65 1.65 0 0 0 4.6 15a1.65 1.65 0 0 0-1.51-1H3a2 2 0 1 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06' +
    'a2 2 0 1 1 2.83-2.83l.06.06A1.65 1.65 0 0 0 9 4.6a1.65 1.65 0 0 0 1-1.51V3a2 2 0 1 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 ' +
    '1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06A1.65 1.65 0 0 0 19.4 9v.09a1.65 1.65 0 0 0 1.51 1H21a2 2 0 1 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z';

  /* M3 导航栏图标：未选中用描边态，选中用填充态 */
  var TABS = [
    {
      id: 'list', name: '明细',
      outlined: '<path d="M9 6.5h11M9 12h11M9 17.5h11" stroke-width="1.8" stroke-linecap="round"/>' +
        '<circle cx="4.6" cy="6.5" r="1.15" fill="currentColor"/><circle cx="4.6" cy="12" r="1.15" fill="currentColor"/>' +
        '<circle cx="4.6" cy="17.5" r="1.15" fill="currentColor"/>',
      filled: '<path d="M9 6.5h11M9 12h11M9 17.5h11" stroke-width="2.5" stroke-linecap="round"/>' +
        '<circle cx="4.6" cy="6.5" r="1.5" fill="currentColor"/><circle cx="4.6" cy="12" r="1.5" fill="currentColor"/>' +
        '<circle cx="4.6" cy="17.5" r="1.5" fill="currentColor"/>'
    },
    {
      id: 'stats', name: '统计',
      outlined: '<rect x="4" y="12" width="4" height="8" rx="1.3" fill="none" stroke-width="1.8"/>' +
        '<rect x="10" y="7" width="4" height="13" rx="1.3" fill="none" stroke-width="1.8"/>' +
        '<rect x="16" y="14" width="4" height="6" rx="1.3" fill="none" stroke-width="1.8"/>',
      filled: '<rect x="4" y="12" width="4" height="8" rx="1.3" fill="currentColor"/>' +
        '<rect x="10" y="7" width="4" height="13" rx="1.3" fill="currentColor"/>' +
        '<rect x="16" y="14" width="4" height="6" rx="1.3" fill="currentColor"/>'
    },
    {
      id: 'settings', name: '设置',
      outlined: '<circle cx="12" cy="12" r="3.1" fill="none" stroke-width="1.8"/>' +
        '<path d="' + GEAR_PATH + '" fill="none" stroke-width="1.6"/>',
      filled: '<circle cx="12" cy="12" r="3.5" fill="currentColor"/>' +
        '<path d="' + GEAR_PATH + '" fill="none" stroke-width="2.3"/>'
    }
  ];

  /* 自动分类关键词：用于账单导入时猜测分类 */
  var KEYWORDS = [
    [/餐|饭|美团|饿了么|外卖|肯德基|麦当劳|星巴克|咖啡|奶茶|零食|水果|生鲜|食堂|烧烤|火锅|小吃|早餐|午餐|晚餐/, 'food'],
    [/地铁|公交|出租|滴滴|打车|网约车|加油|停车|高速|etc|高铁|火车|动车|机票|航空|单车|摩拜|哈啰|青桔|车费|交通/, 'transport'],
    [/淘宝|天猫|京东|拼多多|唯品会|苏宁|商场|百货|服饰|衣|鞋|帽|包|化妆品|护肤|数码|购物|超市|便利店/, 'shopping'],
    [/房租|租金|水费|电费|燃气|物业|家居|家具|家电|装修|宽带安装/, 'home'],
    [/话费|流量|宽带|中国移动|中国联通|中国电信|通信|联通|移动话费/, 'telecom'],
    [/电影|游戏|娱乐|ktv|视频会员|腾讯视频|爱奇艺|优酷|芒果|音乐|演出|门票|剧本|桌游|酒吧/, 'entertain'],
    [/医院|药店|药房|诊所|挂号|体检|医疗|门诊|口腔|牙科|医药/, 'medical'],
    [/书店|图书|课程|培训|学费|教育|得到|知识|考试|报名费|文具/, 'study'],
    [/红包|转账|礼金|人情|份子|贺礼|随礼/, 'social'],
    [/健身|运动|游泳|球馆|跑步|瑜伽|keep|体育/, 'sport'],
    [/宠物|猫|狗|宠物医院|猫粮|狗粮/, 'pet']
  ];

  var INCOME_KEYWORDS = [
    [/工资|薪资|薪酬|代发工资|劳务/, 'salary'],
    [/奖金|绩效|年终|提成|奖励/, 'bonus'],
    [/兼职|稿费|酬劳|外快|私活/, 'parttime'],
    [/理财|基金|股票|收益|利息|分红|余额宝|零钱通/, 'invest'],
    [/红包|礼金|压岁/, 'redpack'],
    [/报销|差旅|补贴|退款报销/, 'reimburse'],
    [/退款|退货|返现/, 'refund']
  ];

  /* ============================================================
     二、工具函数
     ============================================================ */

  function $(sel, root) { return (root || document).querySelector(sel); }
  function $$(sel, root) { return Array.prototype.slice.call((root || document).querySelectorAll(sel)); }

  function esc(str) {
    return String(str == null ? '' : str)
      .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
  }

  var NF = new Intl.NumberFormat('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });

  /** 分 → 带千分位的金额字符串 */
  function fmt(cent) { return NF.format((Number(cent) || 0) / 100); }

  /** 分 → 紧凑显示（用于图表，超过 1 万用「万」） */
  function fmtShort(cent) {
    var v = (Number(cent) || 0) / 100;
    if (Math.abs(v) >= 10000) return (v / 10000).toFixed(v % 10000 === 0 ? 0 : 1) + '万';
    return String(Math.round(v));
  }

  function pad2(n) { return n < 10 ? '0' + n : String(n); }

  function todayStr(d) {
    d = d || new Date();
    return d.getFullYear() + '-' + pad2(d.getMonth() + 1) + '-' + pad2(d.getDate());
  }

  /** 解析 YYYY-MM-DD 为本地时间 Date */
  function parseDate(dateStr) {
    var p = String(dateStr).split('-');
    return new Date(Number(p[0]), Number(p[1]) - 1, Number(p[2]));
  }

  /** M/D 形式的短日期 */
  function mdLabel(dateStr) {
    var p = String(dateStr).split('-');
    return Number(p[1]) + '/' + Number(p[2]);
  }

  /** 所在周的区间：以周一为第一天、周日为最后一天 */
  function weekRange(dateStr) {
    var d = parseDate(dateStr || todayStr());
    var wd = d.getDay();                                    // 0 表示周日
    var mon = new Date(d.getFullYear(), d.getMonth(), d.getDate() + (wd === 0 ? -6 : 1 - wd));
    var sun = new Date(mon.getFullYear(), mon.getMonth(), mon.getDate() + 6);
    return { start: todayStr(mon), end: todayStr(sun) };
  }

  /** 周区间的显示文本，如「9/7–9/13」；跨年时补上年份 */
  function weekRangeLabel(start, end) {
    if (String(start).slice(0, 4) !== String(end).slice(0, 4)) {
      return start.slice(0, 4) + '/' + mdLabel(start) + '–' + end.slice(0, 4) + '/' + mdLabel(end);
    }
    return mdLabel(start) + '–' + mdLabel(end);
  }

  /** 前后移动若干周，返回新的周一日期 */
  function shiftWeek(start, delta) {
    var d = parseDate(start);
    d.setDate(d.getDate() + delta * 7);
    return todayStr(d);
  }

  /** 星期几 */
  function weekdayOf(dateStr) {
    return '日一二三四五六'.charAt(parseDate(dateStr).getDay());
  }

  function dayLabel(dateStr) {
    var t = todayStr();
    var y = todayStr(new Date(Date.now() - 86400000));
    var p = String(dateStr).split('-');
    var base = Number(p[1]) + '月' + Number(p[2]) + '日';
    if (dateStr === t) return '今天 · ' + base;
    if (dateStr === y) return '昨天 · ' + base;
    return base + ' 周' + weekdayOf(dateStr);
  }

  function uid() {
    return Date.now().toString(36) + Math.random().toString(36).slice(2, 7);
  }

  function sum(arr, fn) {
    var t = 0;
    for (var i = 0; i < arr.length; i++) t += fn ? fn(arr[i]) : arr[i];
    return t;
  }

  function catOf(type, id) {
    var list = CATEGORIES[type] || CATEGORIES.expense;
    for (var i = 0; i < list.length; i++) if (list[i].id === id) return list[i];
    return { id: id, name: '其他', icon: '📦', color: '#8b95a8' };
  }

  /* ---------- 轻提示 ---------- */
  var toastTimer = null;
  function toast(msg) {
    var el = $('#toast');
    el.textContent = msg;
    el.classList.add('show');
    clearTimeout(toastTimer);
    toastTimer = setTimeout(function () { el.classList.remove('show'); }, 1900);
  }

  /* ---------- 通用对话框 ---------- */
  var dialogResolve = null;

  function closeDialog() {
    $('#dialogMask').classList.remove('show');
    var r = dialogResolve; dialogResolve = null;
    if (r) r(null);
  }

  /**
   * 打开对话框
   * opts: { title, message, html, input, placeholder, value, confirmText, cancelText, danger, hideCancel }
   * 返回 Promise：确认 → 输入值(无输入框时返回 true)；取消 → null
   */
  function dialog(opts) {
    opts = opts || {};
    var mask = $('#dialogMask'), box = $('#dialog');
    var hasInput = !!opts.input;
    var html = '';
    html += '<h3>' + esc(opts.title || '提示') + '</h3>';
    if (opts.message) html += '<p>' + opts.message + '</p>';
    if (opts.html) html += opts.html;
    if (hasInput) {
      html += '<input id="dlgInput" type="' + (opts.input === true ? 'text' : opts.input) + '" ' +
        (opts.placeholder ? 'placeholder="' + esc(opts.placeholder) + '" ' : '') +
        (opts.min != null ? 'min="' + opts.min + '" ' : '') +
        'value="' + esc(opts.value == null ? '' : opts.value) + '">';
    }
    html += '<div class="btns">';
    if (!opts.hideCancel) html += '<button id="dlgCancel">' + esc(opts.cancelText || '取消') + '</button>';
    html += '<button class="' + (opts.danger ? 'danger' : 'primary') + '" id="dlgOk">' + esc(opts.confirmText || '确定') + '</button>';
    html += '</div>';
    box.innerHTML = html;

    mask.classList.add('show');
    var inputEl = hasInput ? $('#dlgInput') : null;
    if (inputEl) setTimeout(function () { inputEl.focus(); inputEl.select && inputEl.select(); }, 120);

    return new Promise(function (resolve) {
      dialogResolve = resolve;
      $('#dlgOk').onclick = function () {
        var v = inputEl ? inputEl.value : true;
        if (opts.validate && !opts.validate(v)) return;
        mask.classList.remove('show');
        dialogResolve = null;
        resolve(v);
      };
      var c = $('#dlgCancel');
      if (c) c.onclick = function () {
        mask.classList.remove('show');
        dialogResolve = null;
        resolve(null);
      };
      if (inputEl) inputEl.onkeydown = function (e) { if (e.key === 'Enter') $('#dlgOk').click(); };
    });
  }

  function confirmBox(title, message, opts) {
    opts = opts || {};
    return dialog(Object.assign({
      title: title,
      message: message,
      confirmText: opts.confirmText || '确定',
      danger: opts.danger
    }, opts.extra || {}));
  }

  /* ---------- 文件下载 ---------- */
  function download(filename, content, mime) {
    var blob = new Blob([content], { type: (mime || 'text/plain') + ';charset=utf-8' });
    var url = URL.createObjectURL(blob);
    var a = document.createElement('a');
    a.href = url; a.download = filename;
    document.body.appendChild(a);
    a.click();
    setTimeout(function () { URL.revokeObjectURL(url); a.remove(); }, 1500);
  }

  /* ============================================================
     三、数据层
     ============================================================ */

  /** 预算金额归一化：非法或负数一律归零 */
  function normalizeBudget(v) {
    var n = Math.round(Number(v) || 0);
    return n > 0 ? n : 0;
  }

  function loadState() {
    var raw = null;
    try { raw = localStorage.getItem(STORE_KEY); } catch (e) { raw = null; }
    var base = { records: [], budget: 0, weekly: 0 };
    if (!raw) return base;
    try {
      var obj = JSON.parse(raw);
      if (!obj || typeof obj !== 'object') return base;
      var recs = Array.isArray(obj.records) ? obj.records : [];
      recs = recs.map(normalizeRecord).filter(Boolean);
      return {
        records: recs,
        budget: normalizeBudget(obj.budget),
        weekly: normalizeBudget(obj.weekly)
      };
    } catch (e) {
      return base;
    }
  }

  function normalizeRecord(r) {
    if (!r || typeof r !== 'object') return null;
    var type = r.type === 'income' ? 'income' : 'expense';
    var amount = Math.round(Number(r.amount));
    if (!isFinite(amount) || amount <= 0) return null;
    var date = String(r.date || '');
    if (!/^\d{4}-\d{2}-\d{2}$/.test(date)) date = todayStr();
    return {
      id: r.id || uid(),
      type: type,
      amount: amount,
      category: r.category || (type === 'income' ? 'other_i' : 'other_e'),
      date: date,
      note: String(r.note || '').slice(0, 60),
      account: ACCOUNTS.indexOf(r.account) >= 0 ? r.account : '',
      createdAt: Number(r.createdAt) || Date.now()
    };
  }

  var S = loadState();
  var saveFailed = false;

  function save() {
    try {
      localStorage.setItem(STORE_KEY, JSON.stringify(S));
      saveFailed = false;
    } catch (e) {
      if (!saveFailed) { toast('保存失败：本机存储空间不足'); saveFailed = true; }
    }
  }

  /* ============================================================
     四、界面状态
     ============================================================ */

  var ui = {
    tab: 'list',
    weekStart: weekRange(todayStr()).start,   // 正在浏览的一周（该周的周一）
    statsType: 'expense',
    sheet: null
  };

  /** 当前是否停留在本周 */
  function isCurrentWeek() {
    return ui.weekStart === weekRange(todayStr()).start;
  }

  /** 区间内的全部记录 */
  function recordsInRange(start, end) {
    return S.records.filter(function (r) { return r.date >= start && r.date <= end; });
  }

  /** 以 start 为周一的那一周的收支合计 */
  function statOfWeek(start) {
    var end = weekRange(start).end;
    var recs = recordsInRange(start, end);
    var expense = sum(recs.filter(function (r) { return r.type === 'expense'; }), function (r) { return r.amount; });
    var income = sum(recs.filter(function (r) { return r.type === 'income'; }), function (r) { return r.amount; });
    return {
      start: start, end: end,
      expense: expense, income: income, balance: income - expense,
      count: recs.length, list: recs
    };
  }

  /** 指定日期所在月份的支出合计（用于月预算进度） */
  function monthExpenseOf(dateStr) {
    var ym = String(dateStr).slice(0, 7);
    var recs = S.records.filter(function (r) { return r.date.slice(0, 7) === ym; });
    return sum(recs.filter(function (r) { return r.type === 'expense'; }), function (r) { return r.amount; });
  }

  /* ============================================================
     五、渲染：骨架
     ============================================================ */

  function renderAppbar() {
    var thisMonday = weekRange(todayStr()).start;
    var label = weekRangeLabel(ui.weekStart, weekRange(ui.weekStart).end);
    if (ui.weekStart === thisMonday) label = '本周 · ' + label;
    else if (ui.weekStart === shiftWeek(thisMonday, -1)) label = '上周 · ' + label;
    $('#weekLabel').textContent = label;
    /* 不允许浏览未来的周 */
    $('#nextWeek').style.visibility = ui.weekStart === thisMonday ? 'hidden' : 'visible';
  }

  function renderTabbar() {
    $('#tabbar').innerHTML = TABS.map(function (t) {
      var on = ui.tab === t.id;
      return '<button class="tab' + (on ? ' on' : '') + '" data-tab="' + t.id + '">' +
        '<span class="ind">' +
        '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-linejoin="round" ' +
        'stroke-linecap="round">' + (on ? t.filled : t.outlined) + '</svg>' +
        '</span>' +
        '<span class="lbl">' + t.name + '</span></button>';
    }).join('');
    $('#fab').style.display = ui.tab === 'settings' ? 'none' : 'flex';
  }

  /* 系统若开启了「减弱动态效果」，则跳过所有动画 */
  var reduceMotion = false;
  try {
    reduceMotion = !!(window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches);
  } catch (e) { reduceMotion = false; }

  function render(keepScroll) {
    var main = $('#main');
    var top = keepScroll ? main.scrollTop : 0;
    renderAppbar();
    renderTabbar();
    /* 重绘会重建 DOM，各区块的进入动画随之从头播放 */
    if (ui.tab === 'list') main.innerHTML = viewList();
    else if (ui.tab === 'stats') main.innerHTML = viewStats();
    else main.innerHTML = viewSettings();
    main.scrollTop = top;
    afterRender();
  }

  /** 重绘后的收尾处理：概览金额的数字滚动 */
  function afterRender() {
    if (ui.tab !== 'list' || reduceMotion) return;
    var el = $('#ovMain');
    if (!el) return;
    countUp(el, statOfWeek(ui.weekStart).expense);
  }

  /** 金额由 0 滚动至目标值 */
  function countUp(el, cents) {
    var dur = 620, t0 = 0;
    el.textContent = fmt(0);
    function frame(now) {
      if (!t0) t0 = now;
      var t = Math.min(1, (now - t0) / dur);
      var eased = 1 - Math.pow(1 - t, 3);          // easeOutCubic
      el.textContent = fmt(Math.round(cents * eased));
      if (t < 1) requestAnimationFrame(frame);
    }
    requestAnimationFrame(frame);
  }

  /* ============================================================
     六、视图：明细
     ============================================================ */

  function viewList() {
    var st = statOfWeek(ui.weekStart);
    var h = '';

    /* — 概览卡 — */
    h += '<div class="overview">';
    h += '<div class="ov-label">' + (isCurrentWeek() ? '本周支出' : '该周支出') + '</div>';
    h += '<div class="ov-main"><small>¥</small><span id="ovMain">' + fmt(st.expense) + '</span></div>';
    h += '<div class="ov-sub">' +
      '<div><div class="k">收入</div><div class="v">¥' + fmt(st.income) + '</div></div>' +
      '<div><div class="k">结余</div><div class="v">' + (st.balance < 0 ? '-' : '') + '¥' + fmt(Math.abs(st.balance)) + '</div></div>' +
      '<div><div class="k">笔数</div><div class="v">' + st.count + '</div></div>' +
      '</div>';
    /* 周预算为主视角，月预算作为补充（以该周所在月份累计） */
    if (S.weekly > 0) h += budgetBar(isCurrentWeek() ? '本周预算' : '该周预算', S.weekly, st.expense, st.start, st.end);
    if (S.budget > 0) h += budgetBar('本月预算', S.budget, monthExpenseOf(st.start));
    h += '</div>';

    /* — 交易列表 — */
    if (!st.count) {
      h += '<div class="empty"><div class="big">🧾</div>' +
        '<div class="t1">' + (isCurrentWeek() ? '本周还没有记录' : '这一周没有记录') + '</div>' +
        '<div class="t2">点击右下角 <b>+</b> 记一笔<br>也可以到「设置」导入微信 / 支付宝账单</div></div>';
      return h;
    }

    /* 按日期倒序分组 */
    var byDate = {};
    st.list.forEach(function (r) { (byDate[r.date] = byDate[r.date] || []).push(r); });
    var dates = Object.keys(byDate).sort().reverse();

    h += '<div class="section-title"><span>' + (isCurrentWeek() ? '本周明细' : '该周明细') +
      '</span><span class="cnt">共 ' + st.count + ' 笔</span></div>';

    var seq = 0;
    dates.forEach(function (d) {
      var list = byDate[d].slice().sort(function (a, b) { return b.createdAt - a.createdAt; });
      var de = sum(list.filter(function (r) { return r.type === 'expense'; }), function (r) { return r.amount; });
      var di = sum(list.filter(function (r) { return r.type === 'income'; }), function (r) { return r.amount; });
      h += '<div class="day-head" style="--i:' + Math.min(seq++, 14) + '">' +
        '<span class="d">' + esc(dayLabel(d)) + '</span><span class="s">' +
        (di ? '<span>收 ' + fmt(di) + '</span>' : '') +
        (de ? '<span>支 ' + fmt(de) + '</span>' : '') +
        '</span></div>';
      h += '<div class="card stagger">';
      list.forEach(function (r) {
        var c = catOf(r.type, r.category);
        h += '<div class="item" data-id="' + r.id + '" style="--i:' + Math.min(seq++, 14) + '">' +
          '<div class="ico" style="background:' + c.color + '22">' + c.icon + '</div>' +
          '<div class="mid"><div class="name">' + esc(c.name) + '</div>' +
          (r.note ? '<div class="note">' + esc(r.note) + '</div>' : '') + '</div>' +
          '<div class="amt ' + (r.type === 'income' ? 'in' : 'out') + '">' +
          (r.type === 'income' ? '+' : '-') + fmt(r.amount) +
          (r.account ? '<span class="tag">' + esc(r.account) + '</span>' : '') +
          '</div></div>';
      });
      h += '</div>';
    });

    return h;
  }

  /**
   * 预算进度条
   * label 预算名称；budget 预算金额（分）；used 已用金额（分）；start / end 可选，为统计区间
   */
  function budgetBar(label, budget, used, start, end) {
    var pct = budget > 0 ? used / budget * 100 : 0;
    var left = budget - used;
    var range = (start && end) ? '（' + mdLabel(start) + '–' + mdLabel(end) + '）' : '';
    return '<div class="budget-wrap">' +
      '<div class="budget-head"><span>' + label + range + ' ¥' + fmt(budget) + '</span>' +
      '<span>' + (left >= 0 ? '剩余 ¥' + fmt(left) : '已超支 ¥' + fmt(-left)) + '</span></div>' +
      '<div class="budget-bar' + (pct > 100 ? ' over' : '') + '">' +
      '<span style="width:' + Math.min(100, Math.max(0, pct)) + '%"></span></div>' +
      '</div>';
  }

  /* ============================================================
     七、视图：统计
     ============================================================ */

  function viewStats() {
    var st = statOfWeek(ui.weekStart);
    var type = ui.statsType;
    var total = type === 'income' ? st.income : st.expense;
    var list = st.list.filter(function (r) { return r.type === type; });

    var h = '';
    h += '<div class="seg" id="statsSeg">' +
      '<button data-type="expense" class="' + (type === 'expense' ? 'on' : '') + '">支出</button>' +
      '<button data-type="income" class="' + (type === 'income' ? 'on' : '') + '">收入</button>' +
      '</div>';

    if (!list.length) {
      h += '<div class="empty"><div class="big">📊</div><div class="t1">' +
        (isCurrentWeek() ? '本周' : '该周') + '没有' + (type === 'income' ? '收入' : '支出') + '记录</div>' +
        '<div class="t2">记几笔之后这里会生成分类占比</div></div>';
      return h;
    }

    /* 按分类聚合 */
    var map = {};
    list.forEach(function (r) {
      if (!map[r.category]) map[r.category] = { value: 0, count: 0 };
      map[r.category].value += r.amount;
      map[r.category].count++;
    });
    var rows = Object.keys(map).map(function (k) {
      var c = catOf(type, k);
      return { id: k, name: c.name, icon: c.icon, color: c.color, value: map[k].value, count: map[k].count };
    }).sort(function (a, b) { return b.value - a.value; });

    /* — 环形图 + 图例（图例最多 5 项） — */
    h += '<div class="card donut-card anim-card">';
    h += '<div class="donut">' + donutSVG(rows, total, 132, 15) +
      '<div class="center"><div class="l">' + (type === 'income' ? '总收入' : '总支出') + '</div>' +
      '<div class="n num">' + fmtShort(total) + '</div></div></div>';
    h += '<div class="legend">';
    rows.slice(0, 5).forEach(function (r) {
      h += '<div class="row"><span class="dot" style="background:' + r.color + '"></span>' +
        '<span class="nm">' + r.icon + ' ' + esc(r.name) + '</span>' +
        '<span class="pc">' + (r.value / total * 100).toFixed(0) + '%</span>' +
        '<span class="vl num">' + fmtShort(r.value) + '</span></div>';
    });
    if (rows.length > 5) h += '<div class="row" style="color:var(--text-3)"><span class="nm">其余 ' + (rows.length - 5) + ' 类</span></div>';
    h += '</div></div>';

    /* — 分类排行 — */
    h += '<div class="section-title"><span>分类排行</span><span class="cnt">' + rows.length + ' 类</span></div>';
    h += '<div class="card rank">';
    rows.forEach(function (r, i) {
      var pc = r.value / total * 100;
      h += '<div class="row" style="--i:' + Math.min(i, 10) + '"><div class="r1">' +
        '<span class="nm"><i>' + r.icon + '</i>' + esc(r.name) + '</span>' +
        '<span class="vl num" style="color:' + (type === 'income' ? 'var(--income)' : 'var(--expense)') + '">¥' + fmt(r.value) + '</span>' +
        '<span class="pc">' + pc.toFixed(1) + '%</span></div>' +
        '<div class="bar"><span style="width:' + Math.max(2, pc) + '%;background:' + r.color + '"></span></div>' +
        '</div>';
    });
    h += '</div>';

    /* — 近 6 周趋势 — */
    h += '<div class="section-title"><span>近 6 周趋势</span></div>';
    h += '<div class="card anim-card">' + barsSVG(ui.weekStart) + '</div>';

    return h;
  }

  /** 环形图（SVG） */
  function donutSVG(rows, total, size, width) {
    var r = (size - width) / 2;
    var C = 2 * Math.PI * r;
    var acc = 0;
    var segs = rows.map(function (row) {
      var len = total > 0 ? (row.value / total) * C : 0;
      var gap = rows.length > 1 ? 2 : 0;
      var seg = '<circle cx="' + (size / 2) + '" cy="' + (size / 2) + '" r="' + r + '" fill="none" ' +
        'stroke="' + row.color + '" stroke-width="' + width + '" ' +
        'stroke-dasharray="' + Math.max(len - gap, 0.6) + ' ' + C + '" ' +
        'stroke-dashoffset="' + (-acc) + '" stroke-linecap="round"></circle>';
      acc += len;
      return seg;
    }).join('');
    return '<svg viewBox="0 0 ' + size + ' ' + size + '">' +
      '<circle cx="' + (size / 2) + '" cy="' + (size / 2) + '" r="' + r + '" fill="none" ' +
      'stroke="var(--card-2)" stroke-width="' + width + '"></circle>' + segs + '</svg>';
  }

  /** 近 6 周收支对比柱状图 */
  function barsSVG(endWeekStart) {
    var weeks = [];
    for (var i = 5; i >= 0; i--) weeks.push(shiftWeek(endWeekStart, -i));

    var data = weeks.map(function (ws) {
      var s = statOfWeek(ws);
      return { start: ws, label: mdLabel(ws), expense: s.expense, income: s.income };
    });
    var max = Math.max(1, Math.max.apply(null, data.map(function (d) { return Math.max(d.expense, d.income); })));

    var h = '<div class="bars">';
    data.forEach(function (d, i) {
      var eh = Math.round(d.expense / max * 100);
      var ih = Math.round(d.income / max * 100);
      h += '<div class="col' + (d.start === endWeekStart ? ' now' : '') + '" style="--i:' + i + '">' +
        '<div class="pair">' +
        '<i class="ex" style="height:' + (d.expense ? Math.max(3, eh) : 2) + '%" title="支出 ' + fmt(d.expense) + '"></i>' +
        '<i class="in" style="height:' + (d.income ? Math.max(3, ih) : 2) + '%" title="收入 ' + fmt(d.income) + '"></i>' +
        '</div>' +
        '<div class="lb">' + d.label + '</div></div>';
    });
    h += '</div>';
    h += '<div class="bars-legend">' +
      '<span><i style="background:var(--expense)"></i>支出</span>' +
      '<span><i style="background:var(--income)"></i>收入</span>' +
      '<span style="color:var(--text-3)">峰值 ¥' + fmtShort(max) + '</span></div>';
    return h;
  }

  /* ============================================================
     八、视图：设置
     ============================================================ */

  function viewSettings() {
    var h = '';
    var recCount = S.records.length;
    var totalExpense = sum(S.records.filter(function (r) { return r.type === 'expense'; }), function (r) { return r.amount; });

    h += '<div class="section-title"><span>记账偏好</span></div>';
    h += '<div class="setting-group anim-card">';
    h += setRow('budget', '🎯', '月度预算', S.budget > 0 ? '¥' + fmt(S.budget) : '未设置', '按月控制支出，首页按该周所在月份显示进度');
    h += setRow('budget-week', '📅', '每周预算', S.weekly > 0 ? '¥' + fmt(S.weekly) : '未设置', '按周（周一至周日）控制支出，首页显示本周进度');
    h += '</div>';

    h += '<div class="section-title"><span>数据管理</span></div>';
    h += '<div class="setting-group anim-card">';
    h += setRow('export-json', '💾', '导出备份', 'JSON', '导出全部数据，可再次导入恢复');
    h += setRow('export-csv', '📄', '导出表格', 'CSV', '可用 Excel / WPS 打开查看');
    h += setRow('import-json', '📥', '导入备份', 'JSON', '从备份文件恢复数据');
    h += setRow('import-bill', '🧾', '导入账单', '微信 / 支付宝', '自动识别 CSV 账单并归类');
    h += setRow('clear', '🗑️', '清空全部数据', '', '共 ' + recCount + ' 笔记录，清空后不可恢复', true);
    h += '</div>';

    h += '<div class="section-title"><span>关于</span></div>';
    h += '<div class="card anim-card"><div class="about">' +
      '<div class="logo">💰</div>' +
      '<div><b>记一笔 · 收支记账</b></div>' +
      '<div>版本 1.2.0 · 以周为周期 · 纯本地存储</div>' +
      '<div>累计记录 ' + recCount + ' 笔，支出合计 ¥' + fmt(totalExpense) + '</div>' +
      '<div style="margin-top:8px">所有数据仅保存在本机浏览器中，不会上传到任何服务器。<br>' +
      '建议定期使用「导出备份」保存一份副本。</div>' +
      '</div></div>';

    return h;
  }

  function setRow(action, icon, title, value, desc, danger) {
    return '<button class="setrow' + (danger ? ' danger' : '') + '" data-action="' + action + '">' +
      '<span class="si">' + icon + '</span>' +
      '<span class="st"><b>' + esc(title) + '</b>' + (desc ? '<small>' + esc(desc) + '</small>' : '') + '</span>' +
      (value ? '<span class="sv">' + esc(value) + '</span>' : '') +
      '<svg class="chev" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><polyline points="9 18 15 12 9 6"/></svg>' +
      '</button>';
  }

  /* ============================================================
     九、记账面板
     ============================================================ */

  function openSheet(record) {
    ui.sheet = record ? {
      mode: 'edit',
      editingId: record.id,
      type: record.type,
      category: record.category,
      amountRaw: (record.amount / 100).toFixed(2),
      date: record.date,
      note: record.note || '',
      account: record.account || ACCOUNTS[0]
    } : {
      mode: 'new',
      editingId: null,
      type: 'expense',
      category: null,
      amountRaw: '',
      /* 浏览本周时默认今天，浏览历史周时默认该周周一 */
      date: isCurrentWeek() ? todayStr() : ui.weekStart,
      note: '',
      account: ACCOUNTS[0]
    };

    $('#dateInput').value = ui.sheet.date;
    $('#noteInput').value = ui.sheet.note;
    $('#acctInput').innerHTML = ACCOUNTS.map(function (a) {
      return '<option value="' + a + '"' + (a === ui.sheet.account ? ' selected' : '') + '>' + a + '</option>';
    }).join('');
    $('#sheetActions').style.display = record ? 'flex' : 'none';

    renderSheetCats();
    renderSheetAmount();
    renderSheetType();

    $('#mask').classList.add('show');
    $('#sheet').classList.add('show');
    $('#sheet').setAttribute('aria-hidden', 'false');
    document.body.classList.add('locked');
  }

  function closeSheet() {
    $('#mask').classList.remove('show');
    $('#sheet').classList.remove('show');
    $('#sheet').setAttribute('aria-hidden', 'true');
    document.body.classList.remove('locked');
    ui.sheet = null;
  }

  function renderSheetType() {
    $$('#typeSeg button').forEach(function (b) {
      b.classList.toggle('on', b.dataset.type === ui.sheet.type);
    });
    $('#amountRow').className = 'amount-row ' + ui.sheet.type;
  }

  function renderSheetCats() {
    var list = CATEGORIES[ui.sheet.type];
    var cats = $('#cats');
    cats.innerHTML = list.map(function (c, i) {
      return '<button class="cat' + (ui.sheet.category === c.id ? ' on' : '') + '" data-cat="' + c.id + '" style="--ci:' + i + '">' +
        '<span class="ci" style="background:' + c.color + '22">' + c.icon + '</span>' +
        '<span>' + c.name + '</span></button>';
    }).join('');
    if (ui.sheet.category) {
      var on = $('.cat.on', cats);
      if (on && on.scrollIntoView) {
        try { on.scrollIntoView({ block: 'nearest' }); } catch (e) { /* 忽略 */ }
      }
    }
  }

  function amountDisplay() {
    var raw = ui.sheet.amountRaw;
    if (!raw || raw === '.') return null;
    var parts = raw.split('.');
    var int = (parts[0] || '0').replace(/\B(?=(\d{3})+(?!\d))/g, ',');
    return parts.length > 1 ? int + '.' + parts[1] : int;
  }

  function renderSheetAmount() {
    var txt = amountDisplay();
    var el = $('#amountText');
    el.innerHTML = txt ? esc(txt) : '<span class="placeholder">0.00</span>';
    var cents = Math.round(parseFloat(ui.sheet.amountRaw || '0') * 100);
    $('#saveBtn').disabled = !(cents > 0 && ui.sheet.category);

    /* 按键时轻微回弹，提供输入反馈 */
    if (!reduceMotion && txt) {
      el.classList.remove('pop');
      void el.offsetWidth;
      el.classList.add('pop');
    }
  }

  function keyPress(k) {
    var s = ui.sheet;
    if (!s) return;
    var raw = s.amountRaw;

    if (k === 'del') { s.amountRaw = raw.slice(0, -1); }
    else if (k === 'clr') { s.amountRaw = ''; }
    else if (k === '.') {
      if (raw.indexOf('.') >= 0) return;
      s.amountRaw = raw === '' ? '0.' : raw + '.';
    } else {
      var dot = raw.indexOf('.');
      if (dot >= 0 && raw.length - dot > 2) return;          // 最多两位小数
      var intLen = (dot >= 0 ? raw.slice(0, dot) : raw).replace(/^0(?=\d)/, '').length;
      if (intLen >= 8) return;                                // 整数最多 8 位
      if (raw === '0') raw = '';
      s.amountRaw = raw + k;
    }
    renderSheetAmount();
  }

  function saveSheet() {
    var s = ui.sheet;
    if (!s) return;
    var cents = Math.round(parseFloat(s.amountRaw || '0') * 100);
    if (!(cents > 0)) { toast('请输入金额'); return; }
    if (!s.category) { toast('请选择分类'); return; }

    var date = $('#dateInput').value || todayStr();
    if (!/^\d{4}-\d{2}-\d{2}$/.test(date)) date = todayStr();

    var data = {
      type: s.type,
      amount: cents,
      category: s.category,
      date: date,
      note: $('#noteInput').value.trim().slice(0, 60),
      account: $('#acctInput').value
    };

    if (s.mode === 'edit') {
      var idx = -1;
      for (var i = 0; i < S.records.length; i++) if (S.records[i].id === s.editingId) { idx = i; break; }
      if (idx >= 0) S.records[idx] = Object.assign({}, S.records[idx], data);
      toast('已更新');
    } else {
      data.id = uid();
      data.createdAt = Date.now();
      S.records.push(data);
      toast((data.type === 'income' ? '收入' : '支出') + ' ¥' + fmt(cents) + ' 已记录');
    }

    save();
    closeSheet();
    if (ui.tab === 'settings') ui.tab = 'list';
    ui.weekStart = weekRange(date).start;      // 跳到该记录所在的一周
    render();
  }

  function deleteSheet() {
    var s = ui.sheet;
    if (!s || !s.editingId) return;
    confirmBox('删除这笔记录？', '删除后无法恢复。', { danger: true, confirmText: '删除' }).then(function (ok) {
      if (!ok) return;
      S.records = S.records.filter(function (r) { return r.id !== s.editingId; });
      save();
      closeSheet();
      render();
      toast('已删除');
    });
  }

  /* ============================================================
     十、导入 / 导出
     ============================================================ */

  function exportJSON() {
    if (!S.records.length) { toast('暂无数据可导出'); return; }
    var payload = {
      app: 'jiyibi',
      version: 1,
      exportedAt: new Date().toISOString(),
      budget: S.budget,
      weekly: S.weekly,
      records: S.records.slice().sort(function (a, b) { return a.date < b.date ? -1 : 1; })
    };
    download('记一笔-备份-' + todayStr() + '.json', JSON.stringify(payload, null, 2), 'application/json');
    toast('备份已导出');
  }

  function exportCSV() {
    if (!S.records.length) { toast('暂无数据可导出'); return; }
    var rows = [['日期', '类型', '分类', '金额(元)', '账户', '备注']];
    S.records.slice().sort(function (a, b) { return a.date < b.date ? -1 : 1; }).forEach(function (r) {
      rows.push([
        r.date,
        r.type === 'income' ? '收入' : '支出',
        catOf(r.type, r.category).name,
        (r.amount / 100).toFixed(2),
        r.account || '',
        r.note || ''
      ]);
    });
    var csv = rows.map(function (row) {
      return row.map(function (cell) {
        var s = String(cell == null ? '' : cell);
        return /[",\n]/.test(s) ? '"' + s.replace(/"/g, '""') + '"' : s;
      }).join(',');
    }).join('\r\n');
    download('记一笔-明细-' + todayStr() + '.csv', '\ufeff' + csv, 'text/csv');
    toast('表格已导出');
  }

  function pickFile(accept) {
    return new Promise(function (resolve) {
      var input = document.createElement('input');
      input.type = 'file';
      input.accept = accept;
      input.style.display = 'none';
      document.body.appendChild(input);
      input.onchange = function () {
        var f = input.files && input.files[0];
        input.remove();
        resolve(f || null);
      };
      input.click();
    });
  }

  /** 按指定编码读取文本文件 */
  function readAsText(file, encoding) {
    return new Promise(function (resolve, reject) {
      var fr = new FileReader();
      fr.onload = function () { resolve(fr.result); };
      fr.onerror = function () { reject(new Error('读取文件失败')); };
      fr.readAsText(file, encoding);
    });
  }

  /** 读取账单文本：先按 UTF-8，出现乱码则回退 GBK（微信及部分银行账单为该编码） */
  function readText(file) {
    return readAsText(file, 'utf-8').then(function (text) {
      if (text.indexOf('\uFFFD') === -1) return text;
      return readAsText(file, 'gbk').catch(function () { return text; });
    });
  }

  function importJSON() {
    pickFile('.json,application/json').then(function (f) {
      if (!f) return;
      return readText(f).then(function (text) {
        var obj;
        try { obj = JSON.parse(text); } catch (e) { toast('文件格式不正确'); return; }
        var recs = Array.isArray(obj) ? obj : (obj && obj.records);
        recs = (recs || []).map(normalizeRecord).filter(Boolean);
        if (!recs.length) { toast('文件中没有可用记录'); return; }
        return confirmBox('导入备份？',
          '文件包含 <b>' + recs.length + '</b> 条记录。' +
          '导入后与现有 ' + S.records.length + ' 条记录合并，重复记录可能会重复计入。',
          { confirmText: '合并导入' }).then(function (ok) {
            if (!ok) return;
            var seen = {};
            S.records.forEach(function (r) { seen[r.date + '|' + r.type + '|' + r.amount + '|' + r.note] = 1; });
            var added = 0;
            recs.forEach(function (r) {
              var k = r.date + '|' + r.type + '|' + r.amount + '|' + r.note;
              if (seen[k]) return;
              seen[k] = 1;
              r.id = r.id || uid();
              S.records.push(r);
              added++;
            });
            if (obj) {
              if (Number(obj.budget) > 0) S.budget = normalizeBudget(obj.budget);
              if (Number(obj.weekly) > 0) S.weekly = normalizeBudget(obj.weekly);
            }
            save(); render();
            toast('已导入 ' + added + ' 条' + (recs.length - added ? '，跳过重复 ' + (recs.length - added) + ' 条' : ''));
          });
      });
    }).catch(function () { toast('读取文件失败'); });
  }

  /** 解析 CSV 行（支持引号转义） */
  function parseCSVLine(line, delim) {
    var out = [], cur = '', inQ = false;
    for (var i = 0; i < line.length; i++) {
      var ch = line[i];
      if (inQ) {
        if (ch === '"') {
          if (line[i + 1] === '"') { cur += '"'; i++; }
          else inQ = false;
        } else cur += ch;
      } else {
        if (ch === '"') inQ = true;
        else if (ch === delim) { out.push(cur); cur = ''; }
        else cur += ch;
      }
    }
    out.push(cur);
    return out;
  }

  function parseCSV(text) {
    var lines = text.replace(/\r\n?/g, '\n').split('\n').filter(function (l) { return l.trim() !== ''; });
    if (!lines.length) return [];
    var delim = (lines[0].split('\t').length > lines[0].split(',').length) ? '\t' : ',';
    return lines.map(function (l) { return parseCSVLine(l, delim); });
  }

  /** 在表头行中定位各列索引 */
  function locateHeader(rows) {
    for (var i = 0; i < Math.min(rows.length, 30); i++) {
      var row = rows[i];
      if (row.length < 3) continue;
      var joined = row.join(',');
      if (!/(交易时间|交易日期|交易创建时间|记账日期|日期)/.test(joined)) continue;
      var idx = { head: i, date: -1, amount: -1, flow: -1, party: -1, goods: -1, type: -1, status: -1, method: -1 };
      for (var j = 0; j < row.length; j++) {
        var c = String(row[j]).replace(/\s/g, '');
        if (idx.date < 0 && /(交易时间|交易日期|交易创建时间|记账日期|^日期$|^时间$)/.test(c)) idx.date = j;
        if (idx.amount < 0 && /金额/.test(c)) idx.amount = j;
        if (idx.flow < 0 && /(收\/支|收支|收付|借贷标志|资金状态)/.test(c)) idx.flow = j;
        if (idx.party < 0 && /(交易对方|对方|商户名称|收款方)/.test(c)) idx.party = j;
        if (idx.goods < 0 && /(商品|商品说明|商品名称|摘要|备注说明)/.test(c)) idx.goods = j;
        if (idx.type < 0 && /(交易类型|类型|业务类型)/.test(c)) idx.type = j;
        if (idx.status < 0 && /(当前状态|交易状态|状态)/.test(c)) idx.status = j;
        if (idx.method < 0 && /(支付方式|付款方式|收付款方式|资金渠道)/.test(c)) idx.method = j;
      }
      if (idx.date >= 0 && idx.amount >= 0) return idx;
    }
    return null;
  }

  function cleanAmount(v) {
    var s = String(v == null ? '' : v).replace(/[^\d.\-]/g, '');
    var n = parseFloat(s);
    return isFinite(n) ? Math.round(Math.abs(n) * 100) : 0;
  }

  function guessCategory(text, type) {
    var t = String(text || '');
    var list = type === 'income' ? INCOME_KEYWORDS : KEYWORDS;
    for (var i = 0; i < list.length; i++) if (list[i][0].test(t)) return list[i][1];
    return type === 'income' ? 'other_i' : 'other_e';
  }

  /** 账单中的支付方式 → 本应用的账户名称 */
  function mapAccount(text) {
    var m = String(text || '');
    if (!m) return '';
    if (/零钱|微信/.test(m)) return '微信';
    if (/支付宝|余额宝|花呗|借呗/.test(m)) return '支付宝';
    if (/信用卡/.test(m)) return '信用卡';
    if (/储蓄卡|借记卡|银行卡|银行/.test(m)) return '银行卡';
    if (/现金/.test(m)) return '现金';
    for (var i = 0; i < ACCOUNTS.length; i++) if (m.indexOf(ACCOUNTS[i]) >= 0) return ACCOUNTS[i];
    return '';
  }

  function importBill() {
    pickFile('.csv,.txt,text/csv').then(function (f) {
      if (!f) return;
      return readText(f).then(function (text) {
        var rows = parseCSV(text);
        var idx = locateHeader(rows);
        if (!idx) { toast('未识别到账单表头，请确认是微信/支付宝导出的 CSV'); return; }

        var picked = [], skipped = 0, expense = 0, income = 0;

        for (var i = idx.head + 1; i < rows.length; i++) {
          var row = rows[i];
          if (!row || row.length <= idx.amount) continue;

          var dateRaw = String(row[idx.date] || '').trim();
          var m = dateRaw.match(/(\d{4})[-\/年](\d{1,2})[-\/月](\d{1,2})/);
          if (!m) continue;
          var date = m[1] + '-' + pad2(Number(m[2])) + '-' + pad2(Number(m[3]));

          var amount = cleanAmount(row[idx.amount]);
          if (!(amount > 0)) continue;

          var typeRaw = idx.type >= 0 ? String(row[idx.type] || '') : '';
          var flowRaw = idx.flow >= 0 ? String(row[idx.flow] || '').trim() : '';
          var party = idx.party >= 0 ? String(row[idx.party] || '').trim() : '';
          var goods = idx.goods >= 0 ? String(row[idx.goods] || '').trim() : '';
          var status = idx.status >= 0 ? String(row[idx.status] || '') : '';

          /* 跳过未成功 / 已退款关闭的交易 */
          if (status && /(已全额退款|已退款|交易关闭|失败|已撤销)/.test(status)) { skipped++; continue; }

          /* 判断收支方向 */
          var isIncome;
          if (/收入|收/.test(flowRaw) && !/支出/.test(flowRaw)) isIncome = true;
          else if (/支出|支/.test(flowRaw)) isIncome = false;
          else if (/收入/.test(typeRaw)) isIncome = true;
          else if (/不计收支/.test(flowRaw)) { skipped++; continue; }
          else { skipped++; continue; }

          /* 跳过内部资金流转，避免重复计账 */
          var all = typeRaw + ' ' + goods + ' ' + party;
          if (/(转账|还款|零钱通转出|零钱通转入|余额宝转入|余额宝转出|信用卡还款|还花呗|亲情卡|提现)/.test(all)) { skipped++; continue; }

          var text4cat = typeRaw + ' ' + goods + ' ' + party;
          var cat = guessCategory(text4cat, isIncome ? 'income' : 'expense');

          var note = (goods || party || typeRaw || '').slice(0, 40);
          var acct = idx.method >= 0 ? mapAccount(row[idx.method]) : '';

          picked.push({
            id: uid(),
            type: isIncome ? 'income' : 'expense',
            amount: amount,
            category: cat,
            date: date,
            note: note,
            account: acct,
            createdAt: new Date(date + 'T12:00:00').getTime() + i
          });

          if (isIncome) income += amount; else expense += amount;
        }

        if (!picked.length) { toast('没有解析到可导入的记录'); return; }

        var preview = '<div style="background:var(--card-2);border-radius:12px;padding:12px 14px;font-size:13.5px;line-height:2">' +
          '识别记录：<b>' + picked.length + '</b> 笔<br>' +
          '支出合计：<b style="color:var(--expense)">¥' + fmt(expense) + '</b><br>' +
          '收入合计：<b style="color:var(--income)">¥' + fmt(income) + '</b>' +
          (skipped ? '<br><span style="color:var(--text-3)">已跳过 ' + skipped + ' 条（转账/还款/退款等）</span>' : '') +
          '</div>' +
          '<p style="margin:12px 0 0;color:var(--text-3);font-size:12.5px">导入后将与现有记录合并，可在明细中逐条修改分类。</p>';

        return dialog({
          title: '确认导入账单',
          html: preview,
          confirmText: '导入 ' + picked.length + ' 笔'
        }).then(function (ok) {
          if (!ok) return;
          S.records = S.records.concat(picked);
          save();
          var dates = picked.map(function (r) { return r.date; }).sort();
          var latest = dates[dates.length - 1];
          if (latest) ui.weekStart = weekRange(latest).start;
          ui.tab = 'list';
          render();
          toast('已导入 ' + picked.length + ' 笔记录');
        });
      });
    }).catch(function () { toast('读取文件失败'); });
  }

  function clearAll() {
    confirmBox('清空全部数据？',
      '将删除本机保存的全部 <b>' + S.records.length + '</b> 笔记录与预算设置，<b>此操作不可恢复</b>。<br>建议先执行一次「导出备份」。',
      { danger: true, confirmText: '确认清空' }).then(function (ok) {
        if (!ok) return;
        return confirmBox('再次确认', '真的要清空吗？', { danger: true, confirmText: '清空' });
      }).then(function (ok2) {
        if (!ok2) return;
        S.records = [];
        S.budget = 0;
        S.weekly = 0;
        save();
        render();
        toast('已清空');
      });
  }

  function setBudget(kind) {
    var isWeek = kind === 'week';
    var cur = isWeek ? S.weekly : S.budget;
    dialog({
      title: isWeek ? '每周预算' : '月度预算',
      message: isWeek
        ? '设置每周支出上限，统计范围为周一至周日，首页显示本周使用进度。填 0 表示不设预算。'
        : '设置每月支出上限，首页会显示使用进度。填 0 表示不设预算。',
      input: 'number',
      min: 0,
      placeholder: isWeek ? '例如 1200' : '例如 5000',
      value: cur > 0 ? String(cur / 100) : '',
      confirmText: '保存'
    }).then(function (v) {
      if (v === null) return;
      var n = Math.round(Number(v) * 100);
      if (!isFinite(n) || n < 0) return;
      if (isWeek) S.weekly = n; else S.budget = n;
      save(); render();
      var name = isWeek ? '每周预算' : '月度预算';
      toast(n > 0 ? name + '已设为 ¥' + fmt(n) : '已取消' + name);
    });
  }

  /* ============================================================
     十一、事件绑定
     ============================================================ */

  function bind() {
    /* 顶部周切换 */
    $('#prevWeek').onclick = function () { ui.weekStart = shiftWeek(ui.weekStart, -1); render(); };
    $('#nextWeek').onclick = function () {
      var thisMonday = weekRange(todayStr()).start;
      if (ui.weekStart >= thisMonday) return;
      var next = shiftWeek(ui.weekStart, 1);
      ui.weekStart = next > thisMonday ? thisMonday : next;   // 不允许越过本周
      render();
    };
    $('#weekPick').onclick = function () {
      dialog({
        title: '选择日期',
        message: '选择任意一天，将跳转到该天所在的一周。',
        input: 'date',
        value: ui.weekStart,
        confirmText: '前往'
      }).then(function (v) {
        if (!v || !/^\d{4}-\d{2}-\d{2}$/.test(v)) return;
        var thisMonday = weekRange(todayStr()).start;
        var ws = weekRange(v).start;
        ui.weekStart = ws > thisMonday ? thisMonday : ws;
        render();
      });
    };

    /* 底部导航 */
    $('#tabbar').onclick = function (e) {
      var btn = e.target.closest('.tab');
      if (!btn) return;
      ui.tab = btn.dataset.tab;
      render();
    };

    /* 记账面板 */
    $('#fab').onclick = function () { openSheet(null); };
    $('#mask').onclick = closeSheet;
    $('#cancelBtn').onclick = closeSheet;
    $('#deleteBtn').onclick = deleteSheet;
    $('#saveBtn').onclick = saveSheet;

    $('#typeSeg').onclick = function (e) {
      var b = e.target.closest('button');
      if (!b || !ui.sheet) return;
      ui.sheet.type = b.dataset.type;
      /* 类别跨类型不通用，切换后重置 */
      if (CATEGORIES[ui.sheet.type].every(function (c) { return c.id !== ui.sheet.category; })) ui.sheet.category = null;
      renderSheetType();
      renderSheetCats();
      renderSheetAmount();
    };

    $('#cats').onclick = function (e) {
      var b = e.target.closest('.cat');
      if (!b || !ui.sheet) return;
      ui.sheet.category = b.dataset.cat;
      renderSheetCats();
      renderSheetAmount();
    };

    $('#keypad').onclick = function (e) {
      var b = e.target.closest('button');
      if (!b || !ui.sheet || b.id === 'saveBtn') return;
      keyPress(b.dataset.k);
    };

    /* 主区域委托 */
    $('#main').onclick = function (e) {
      /* 点击交易项 → 编辑 */
      var item = e.target.closest('.item');
      if (item) {
        var rec = S.records.filter(function (r) { return r.id === item.dataset.id; })[0];
        if (rec) openSheet(rec);
        return;
      }
      /* 统计页类型切换 */
      var seg = e.target.closest('#statsSeg button');
      if (seg) {
        ui.statsType = seg.dataset.type;
        render(true);          // 保留滚动位置
        return;
      }
      /* 设置页操作 */
      var row = e.target.closest('.setrow');
      if (row) {
        var act = row.dataset.action;
        if (act === 'budget') setBudget('month');
        else if (act === 'budget-week') setBudget('week');
        else if (act === 'export-json') exportJSON();
        else if (act === 'export-csv') exportCSV();
        else if (act === 'import-json') importJSON();
        else if (act === 'import-bill') importBill();
        else if (act === 'clear') clearAll();
      }
    };

    /* 对话框遮罩点击关闭 */
    $('#dialogMask').onclick = function (e) {
      if (e.target === $('#dialogMask')) closeDialog();
    };

    /* 面板内输入框同步 */
    $('#noteInput').oninput = function () { if (ui.sheet) ui.sheet.note = this.value; };
    $('#dateInput').onchange = function () { if (ui.sheet) ui.sheet.date = this.value; };
    $('#acctInput').onchange = function () { if (ui.sheet) ui.sheet.account = this.value; };

    /* M3 水波纹：按压时的状态层反馈 */
    document.addEventListener('pointerdown', function (e) {
      if (reduceMotion || !e.target || !e.target.closest) return;
      var el = e.target.closest(
        '.tab, .item, .setrow, .iconbtn, .fab, .keypad button, .cat, ' +
        '.seg button, .dialog .btns button, .sheet-actions button'
      );
      if (!el || el.disabled) return;

      var rect = el.getBoundingClientRect();
      if (!rect.width || !rect.height) return;

      if (getComputedStyle(el).position === 'static') el.style.position = 'relative';
      el.classList.add('ripple');

      var size = Math.max(rect.width, rect.height) * 1.6;
      var wave = document.createElement('span');
      wave.className = 'ripple-wave';
      wave.style.width = wave.style.height = size + 'px';
      wave.style.left = (e.clientX - rect.left - size / 2) + 'px';
      wave.style.top = (e.clientY - rect.top - size / 2) + 'px';
      el.appendChild(wave);
      setTimeout(function () {
        if (wave.parentNode) wave.parentNode.removeChild(wave);
      }, 560);
    });

    /* 桌面端键盘输入支持 */
    document.addEventListener('keydown', function (e) {
      if (e.key === 'Escape') {
        if ($('#dialogMask').classList.contains('show')) closeDialog();
        else if (ui.sheet) closeSheet();
        return;
      }
      if (!ui.sheet) return;
      var tag = (e.target.tagName || '').toLowerCase();
      if (tag === 'input' || tag === 'select') return;
      if (/^[0-9]$/.test(e.key)) keyPress(e.key);
      else if (e.key === '.') keyPress('.');
      else if (e.key === 'Backspace') { e.preventDefault(); keyPress('del'); }
      else if (e.key === 'Enter') saveSheet();
    });
  }

  /* ============================================================
     十二、启动
     ============================================================ */

  var booted = false;

  function boot() {
    if (booted) return;               // 初始化保持幂等，避免重复绑定事件
    booted = true;
    bind();
    render();

    /* 统计页切换类型后需要保留滚动位置，这里统一置顶即可 */

    /* 注册 Service Worker，实现离线可用；应用外壳（APK）中资源本身即本地文件，无需注册 */
    var isAppShell = location.hostname === 'appassets.androidplatform.net';
    if ('serviceWorker' in navigator && location.protocol !== 'file:' && !isAppShell) {
      window.addEventListener('load', function () {
        navigator.serviceWorker.register('./sw.js').catch(function () { /* 离线能力不可用时静默降级 */ });
      });
    }
  }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', boot);
  else boot();

})();
