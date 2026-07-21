/* 运动存钱罐 — 逻辑 v5 (设计稿风格) */

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

function switchPage(name) {
  document.querySelectorAll('.page').forEach(function(p){ p.classList.remove('active'); });
  document.querySelectorAll('.nav-item').forEach(function(t){ t.classList.remove('active'); });
  $('page-' + name).classList.add('active');
  $('tab-' + name).classList.add('active');
  if (name === 'ledger') loadLedger();
  if (name === 'settings') loadSettings();
}

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

// ═══ 硬币渲染（卡片风格）══════

function renderPiggy(gold, silver, copper) {
  $('goldCount').textContent = gold;
  $('silverCount').textContent = silver;
  $('copperCount').textContent = copper;
  $('ledgerGold').textContent = gold;
  $('ledgerSilver').textContent = silver;
  $('ledgerCopper').textContent = copper;
}

function coinDropAnimation(type, count) {
  var card = document.querySelector('.coin-card.' + type);
  if (!card) return;
  card.classList.remove('drop');
  void card.offsetWidth; // 触发重绘
  card.classList.add('drop');
}

// ── 主页 ────────────────────────────────────
function loadHome() {
  loadBalance();
  loadStreaks();
  loadTodayLog();
  updateCheckStatus();
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

function updateCheckStatus() {
  // 检查今日打卡状态，显示/隐藏勾选标记
  var r = parseResult(Android.getTransactions());
  if (!r.ok) return;
  var today = formatDate(new Date());
  var done = { exercise:false, breakfast:false, dinner:false };
  (r.data || []).forEach(function(t){
    if (t.date === today) {
      if (t.note.indexOf('运动') >= 0) done.exercise = true;
      if (t.note.indexOf('早餐') >= 0 || t.note.indexOf('早饭') >= 0) done.breakfast = true;
      if (t.note.indexOf('晚餐') >= 0 || t.note.indexOf('晚饭') >= 0) done.dinner = true;
    }
  });
  $('btnExercise').classList.toggle('done', done.exercise);
  $('btnBreakfast').classList.toggle('done', done.breakfast);
  $('btnDinner').classList.toggle('done', done.dinner);
}

function loadTodayLog() {
  var r = parseResult(Android.getTransactions());
  if (!r.ok) return;
  var today = formatDate(new Date());
  var txns = (r.data || []).filter(function(t){ return t.date === today; });
  var html = '';
  if (txns.length === 0) {
    html = '<div class="hint" style="width:100%;text-align:center;padding:8px">今天还没有记录</div>';
  } else {
    txns.forEach(function(t){
      var icon = '🟢';
      if (t.note.indexOf('违规') >= 0 || t.note.indexOf('罚') >= 0) icon = '🔴';
      if (t.note.indexOf('消费') >= 0 || t.note.indexOf('扣') >= 0) icon = '🟡';
      html += '<div class="record-item">' + icon + ' ' + t.note + '</div>';
    });
  }
  $('todayLog').innerHTML = html;
}

// ── 打卡 ────────────────────────────────────
function quickCheckin(type) {
  var r = parseResult(Android.checkin(type, '', 0, 0, false));
  if (r.ok) {
    var d = r.data;
    toast('+' + d.coins + '铜 (x' + d.multiplier + ')');
    var btn = type === 'breakfast' ? $('btnBreakfast') : $('btnDinner');
    btn.classList.add('done');
    loadBalance();
    loadStreaks();
    loadTodayLog();
    updateCheckStatus();
    // 铜币投币动画
    setTimeout(function(){ coinDropAnimation('copper', 1); }, 100);
  } else {
    toast(r.error);
  }
}

var isManualRainy = false;

function openExercise() {
  $('exerciseModal').classList.add('show');
  isManualRainy = false;
  $('manualRainy').checked = false;

  // 查天气，雨天自动锁成室内运动
  var r = parseResult(Android.getWeatherStatus());
  if (r.ok && r.data.rainy) {
    // API 判断雨天 → 自动锁定
    $('rainyHint').style.display = 'block';
    $('manualRainyWrap').style.display = 'none';
    selectedExType = 'indoor';
    Array.prototype.forEach.call(document.querySelectorAll('.ex-opt'), function(b){ b.disabled = true; b.style.opacity = 0.4; });
    Array.prototype.forEach.call(document.querySelectorAll('.ex-dur'), function(b){ b.disabled = true; b.style.opacity = 0.4; });
    $('exDistanceWrap').style.display = 'none';
  } else {
    // API 判断非雨天 → 允许手动覆盖
    $('rainyHint').style.display = 'none';
    $('manualRainyWrap').style.display = 'flex';
    selectExType('run');
    Array.prototype.forEach.call(document.querySelectorAll('.ex-opt'), function(b){ b.disabled = false; b.style.opacity = 1; });
    Array.prototype.forEach.call(document.querySelectorAll('.ex-dur'), function(b){ b.disabled = false; b.style.opacity = 1; });
    $('exDistanceWrap').style.display = 'block';
  }
}
function closeExercise() { $('exerciseModal').classList.remove('show'); }

function onManualRainy() {
  isManualRainy = $('manualRainy').checked;
  if (isManualRainy) {
    selectedExType = 'indoor';
    Array.prototype.forEach.call(document.querySelectorAll('.ex-opt'), function(b){ b.disabled = true; b.style.opacity = 0.4; });
    Array.prototype.forEach.call(document.querySelectorAll('.ex-dur'), function(b){ b.disabled = true; b.style.opacity = 0.4; });
    $('exDistanceWrap').style.display = 'none';
  } else {
    selectExType('run');
    Array.prototype.forEach.call(document.querySelectorAll('.ex-opt'), function(b){ b.disabled = false; b.style.opacity = 1; });
    Array.prototype.forEach.call(document.querySelectorAll('.ex-dur'), function(b){ b.disabled = false; b.style.opacity = 1; });
    $('exDistanceWrap').style.display = 'block';
  }
}

function selectExType(type) {
  selectedExType = type;
  document.querySelectorAll('.ex-opt').forEach(function(b){ b.classList.remove('active'); });
  document.querySelector('.ex-opt[data-type="' + type + '"]').classList.add('active');
  $('exDistanceWrap').style.display = (type === 'walk' || type === 'indoor') ? 'none' : 'block';
}

function selectExDur(dur) {
  selectedExDur = dur;
  document.querySelectorAll('.ex-dur').forEach(function(b){ b.classList.remove('active'); });
  document.querySelector('.ex-dur[data-dur="' + dur + '"]').classList.add('active');
}

function doExerciseCheckin() {
  var dist = parseFloat($('exDistance').value) || 0;
  var r = parseResult(Android.checkin('exercise', selectedExType, selectedExDur, dist, isManualRainy));
  if (r.ok) {
    var d = r.data;
    toast('+' + d.coins + '铜 (x' + d.multiplier + ')');
    $('btnExercise').classList.add('done');
    closeExercise();
    loadBalance();
    loadStreaks();
    loadTodayLog();
    updateCheckStatus();
    setTimeout(function(){ coinDropAnimation('copper', 2); }, 100);
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
    html = '<div class="hint" style="width:100%;text-align:center;padding:8px">还没有记录</div>';
  } else {
    txns.forEach(function(t){
      var icon = '🟢';
      if (t.note.indexOf('违规') >= 0 || t.note.indexOf('罚') >= 0) icon = '🔴';
      if (t.note.indexOf('消费') >= 0 || t.note.indexOf('扣') >= 0) icon = '🟡';
      html += '<div class="record-item">' + icon + ' ' + t.date.slice(5) + ' ' + t.note + '</div>';
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
    toast('已扣 ' + name);
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
  $('cityInput').value = city;

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
  var city = $('cityInput').value.trim();
  if (!city) { toast('输入城市名'); return; }
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
    updateCheckStatus();
  } else {
    toast('操作失败');
  }
}

init();