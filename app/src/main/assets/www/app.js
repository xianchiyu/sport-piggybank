/* 运动存钱罐 — 逻辑 v3 (3D硬币堆叠) */

function $(id) { return document.getElementById(id); }
function toast(msg) {
  var t = $('toast');
  t.textContent = msg;
  t.classList.add('show');
  setTimeout(function(){ t.classList.remove('show'); }, 2500);
}
function parseResult(json) {
  try { var r = JSON.parse(json); return r; }
  catch(e) { return { ok:false, error:'解析失败' }; }
}
function formatDate(d) {
  return d.getFullYear() + '-' + String(d.getMonth()+1).padStart(2,'0') + '-' + String(d.getDate()).padStart(2,'0');
}

// ── 页面切换 ────────────────────────────────
function switchPage(name) {
  document.querySelectorAll('.page').forEach(function(p){ p.classList.remove('active'); });
  document.querySelectorAll('.tab').forEach(function(t){ t.classList.remove('active'); });
  $('page-' + name).classList.add('active');
  $('tab-' + name).classList.add('active');
  if (name === 'ledger') loadLedger();
  if (name === 'settings') loadSettings();
}

// ── 初始化 ──────────────────────────────────
var selectedExType = 'run';
var selectedExDur = 30;

function init() {
  var now = new Date();
  var w = ['日','一','二','三','四','五','六'];
  $('todayDate').textContent = now.getMonth()+1 + '月' + now.getDate() + '日 周' + w[now.getDay()];
  loadHome();
  checkAutoViolations();
  $('expenseAmount').addEventListener('input', updateCopperEq);
}

function updateCopperEq() {
  var v = parseFloat($('expenseAmount').value) || 0;
  $('copperEq').textContent = '= ' + Math.round(v * 10) + '铜';
}

// ── 自动违规弹窗 ────────────────────────────
function checkAutoViolations() {
  var r = parseResult(Android.getAutoViolations());
  if (!r.ok) return;
  var list = r.data.violations;
  if (!list || list.length === 0) return;

  var html = '';
  for (var i = 0; i < list.length; i++) {
    html += '<div class="violation-item">' + list[i] + '</div>';
  }
  $('violationList').innerHTML = html;
  $('violationModal').classList.add('show');
}

function closeViolationModal() {
  $('violationModal').classList.remove('show');
}

// ═══ 3D 硬币堆叠渲染 ════════════════════════

// 肚子区域参数（基于 156x192 的容器）
var BELLY = { cx: 73, cy: 108, rx: 48, ry: 40 };

// 伪随机生成器（保证同一数量下布局稳定）
function seedRand(seed) {
  return function() {
    seed = (seed * 9301 + 49297) % 233280;
    return seed / 233280;
  };
}

function renderPiggy(gold, silver, copper) {
  render3DStack(gold, silver, copper);

  $('goldCount').textContent = gold;
  $('silverCount').textContent = silver;
  $('copperCount').textContent = copper;
  $('ledgerGold').textContent = gold;
  $('ledgerSilver').textContent = silver;
  $('ledgerCopper').textContent = copper;
  $('totalYuan').textContent = '¥' + (gold * 10 + silver + copper * 0.1).toFixed(1);
}

function render3DStack(gold, silver, copper) {
  var layer = $('coinLayer');
  if (!layer) return;

  // 显示数量上限（避免太密）
  var maxShow = { gold: 8, silver: 12, copper: 16 };
  var showGold = Math.min(gold, maxShow.gold);
  var showSilver = Math.min(silver, maxShow.silver);
  var showCopper = Math.min(copper, maxShow.copper);

  var html = '';

  // 底层：铜币（最分散，铺底）
  html += buildCoinLayer('copper', showCopper, copper, maxShow.copper, 0);
  // 中层：银币
  html += buildCoinLayer('silver', showSilver, silver, maxShow.silver, 1);
  // 顶层：金币（最少，最高）
  html += buildCoinLayer('gold', showGold, gold, maxShow.gold, 2);

  layer.innerHTML = html;
}

// type: copper/silver/gold, show: 显示枚数, total: 实际总数, max: 上限, tier: 0/1/2
function buildCoinLayer(type, show, total, max, tier) {
  if (show === 0) return '';

  var rand = seedRand(type.charCodeAt(0) * 100 + total);
  var html = '';
  // 每层的基础 Y 偏移（越高层越往上）
  var tierY = tier * 14;
  // 底部基准（肚子底部）
  var baseY = BELLY.cy + BELLY.ry - 12 - tierY;

  for (var i = 0; i < show; i++) {
    // 螺旋堆叠：每枚硬币沿椭圆螺旋上升
    var angle = (i / show) * Math.PI * 4 + tier * 0.5;
    var radiusFactor = 0.35 + 0.15 * (1 - i / show); // 底部稍宽，顶部收窄
    var rx = BELLY.rx * radiusFactor;
    var ry = BELLY.ry * radiusFactor * 0.5;

    // 加随机抖动
    var jitterX = (rand() - 0.5) * 10;
    var jitterY = (rand() - 0.5) * 6;

    var x = BELLY.cx + Math.cos(angle) * rx + jitterX;
    var y = baseY - i * 2.2 + Math.sin(angle) * ry + jitterY;

    // 随机旋转
    var rot = (rand() - 0.5) * 60;
    var scale = 0.85 + rand() * 0.3;

    html += '<div class="coin-3d ' + type + '" style="' +
      'left:' + (x - 11) + 'px;' +
      'top:' + (y - 11) + 'px;' +
      'transform:rotateX(70deg) rotateZ(' + rot + 'deg) scale(' + scale + ');' +
      'z-index:' + (tier * 100 + i) + ';">' +
      '<span class="coin-mark">' + (type === 'gold' ? '金' : type === 'silver' ? '银' : '铜') + '</span>' +
      '</div>';
  }

  // 超过上限时显示 +N
  if (total > max) {
    html += '<div class="coin-overflow ' + type + '" style="' +
      'left:' + (BELLY.cx - 15) + 'px;' +
      'top:' + (baseY - show * 2.2 - 20) + 'px;' +
      'z-index:' + (tier * 100 + 999) + ';">+' + (total - max) + '</div>';
  }

  return html;
}

// ── 投币掉落动画 ───────────────────────────
function coinDropAnimation(type, count) {
  var layer = $('coinLayer');
  if (!layer) return;
  var color = type === 'gold' ? '#ffa000' : type === 'silver' ? '#90a4ae' : '#bf6900';
  var mark = type === 'gold' ? '金' : type === 'silver' ? '银' : '铜';

  for (var i = 0; i < count && i < 3; i++) {
    (function(idx) {
      var c = document.createElement('div');
      c.className = 'coin-3d ' + type + ' coin-falling';
      c.style.left = (BELLY.cx - 11 + idx * 12 - 12) + 'px';
      c.style.top = '20px';
      c.style.zIndex = '9999';
      c.innerHTML = '<span class="coin-mark">' + mark + '</span>';
      layer.appendChild(c);
      setTimeout(function() {
        if (c.parentNode) c.parentNode.removeChild(c);
      }, 700);
    })(i);
  }
}

// ── 主页数据加载 ───────────────────────────
function loadHome() {
  loadBalance();
  loadStreaks();
  loadTodayLog();
}

function loadBalance() {
  var r = parseResult(Android.getBalance());
  if (!r.ok) return;
  var d = r.data;
  renderPiggy(d.gold, d.silver, d.copper);
}

function loadStreaks() {
  var r = parseResult(Android.getStreaks());
  if (!r.ok) return;
  var d = r.data;
  $('streakExercise').textContent = d.exercise > 0 ? '连续' + d.exercise + '天' : '';
  $('streakBreakfast').textContent = d.breakfast > 0 ? '连续' + d.breakfast + '天' : '';
  $('streakDinner').textContent = d.dinner > 0 ? '连续' + d.dinner + '天' : '';
}

function loadTodayLog() {
  var r = parseResult(Android.getTransactions());
  if (!r.ok) return;
  var today = formatDate(new Date());
  var txns = (r.data || []).filter(function(t){ return t.date === today; });
  var html = '';
  if (txns.length === 0) {
    html = '<div class="hint" style="text-align:center;padding:8px">今天还没有记录</div>';
  } else {
    txns.forEach(function(t){
      html += '<div class="log-item"><span>' + t.note + '</span></div>';
    });
  }
  $('todayLog').innerHTML = html;
}

// ── 打卡 ────────────────────────────────────
function quickCheckin(type) {
  var r = parseResult(Android.checkin(type, '', 0, 0));
  if (r.ok) {
    var d = r.data;
    toast('+' + d.coins + '铜 (x' + d.multiplier + ')');
    var btn = type === 'breakfast' ? $('btnBreakfast') : $('btnDinner');
    btn.classList.add('done');
    coinDropAnimation('copper', 1);
    loadBalance();
    loadStreaks();
    loadTodayLog();
  } else {
    toast(r.error);
  }
}

function openExercise() { $('exerciseModal').classList.add('show'); }
function closeExercise() { $('exerciseModal').classList.remove('show'); }

function selectExType(type) {
  selectedExType = type;
  document.querySelectorAll('.ex-opt').forEach(function(b){ b.classList.remove('active'); });
  document.querySelector('.ex-opt[data-type="' + type + '"]').classList.add('active');
  $('exDistanceWrap').style.display = type === 'walk' ? 'none' : 'block';
}

function selectExDur(dur) {
  selectedExDur = dur;
  document.querySelectorAll('.ex-dur').forEach(function(b){ b.classList.remove('active'); });
  document.querySelector('.ex-dur[data-dur="' + dur + '"]').classList.add('active');
}

function doExerciseCheckin() {
  var dist = parseFloat($('exDistance').value) || 0;
  var r = parseResult(Android.checkin('exercise', selectedExType, selectedExDur, dist));
  if (r.ok) {
    var d = r.data;
    toast('+' + d.coins + '铜 (x' + d.multiplier + ')');
    $('btnExercise').classList.add('done');
    closeExercise();
    coinDropAnimation('copper', 2);
    loadBalance();
    loadStreaks();
    loadTodayLog();
  } else {
    toast(r.error);
  }
}

// ── 账本 ────────────────────────────────────
function loadLedger() {
  var r = parseResult(Android.getQuarterSummary());
  if (r.ok) {
    var d = r.data;
    $('qIncomeCopper').textContent = d.incomeCopper + '铜';
    $('qExpenseCopper').textContent = d.expenseCopper + '铜';
    $('qBalanceCopper').textContent = d.balanceCopper + '铜';
  }
  loadTxnList();
}

function loadTxnList() {
  var r = parseResult(Android.getTransactions());
  if (!r.ok) return;
  var txns = (r.data || []).slice().reverse().slice(0, 50);
  var html = '';
  if (txns.length === 0) {
    html = '<div class="hint" style="text-align:center;padding:8px">还没有记录</div>';
  } else {
    txns.forEach(function(t){
      html += '<div class="log-item"><span>' + t.date + ' ' + t.note + '</span></div>';
    });
  }
  $('txnList').innerHTML = html;
}

function doConsume() {
  var name = $('expenseName').value.trim();
  var amount = parseFloat($('expenseAmount').value);
  if (!name) { toast('输入买了什么'); return; }
  if (!amount || amount <= 0) { toast('输入金额'); return; }
  var r = parseResult(Android.consume(amount, name));
  if (r.ok) {
    toast('已扣!' + name);
    $('expenseName').value = '';
    $('expenseAmount').value = '';
    $('copperEq').textContent = '= 0铜';
    loadBalance();
    loadLedger();
  } else {
    toast(r.error);
  }
}

function doQuarterWithdraw() {
  if (!confirm('确定季度提现？余额将清零。')) return;
  var r = parseResult(Android.quarterWithdraw());
  if (r.ok) {
    toast('提现完成');
    loadBalance();
    loadLedger();
  } else {
    toast('提现失败');
  }
}

// ── 设置 ────────────────────────────────────
function loadSettings() {
  var city = Android.getCity();
  $('citySelect').value = city;

  var sr = parseResult(Android.getSocialExemptStatus());
  if (sr.ok) $('socUsed').textContent = sr.data.used + '/3';

  var pr = parseResult(Android.getPenaltyStatus());
  if (pr.ok) {
    $('penCount').textContent = pr.data.count + '次';
    $('penNext').textContent = '¥' + pr.data.nextPenalty;
    $('penDays').textContent = pr.data.daysLeft + '天';
  }
}

function doSetCity() {
  var city = $('citySelect').value;
  Android.setCity(city);
  toast('城市已更新');
}

function doSocialExempt() {
  var r = parseResult(Android.useSocialExempt());
  if (r.ok) {
    toast('豁免已使用 ' + r.data.used + '/3');
    loadSettings();
  } else {
    toast(r.error);
  }
}

function doReportViolation(type, desc) {
  if (!confirm('确认上报违规：' + desc + '？')) return;
  var r = parseResult(Android.reportViolation(type, desc));
  if (r.ok) {
    toast('违规！罚金¥' + r.data.cashPenalty);
    loadSettings();
    loadBalance();
    loadStreaks();
  } else {
    toast('操作失败');
  }
}

init();