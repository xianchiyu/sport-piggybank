function $(id) { return document.getElementById(id); }
function toast(msg) {
  var t = $('toast');
  t.textContent = msg;
  t.classList.add('show');
  setTimeout(function(){ t.classList.remove('show'); }, 2500);
}
function parseResult(json) {
  try { return JSON.parse(json); }
  catch(e) { return { ok:false, error:'解析失败' }; }
}
function formatDate(d) {
  return d.getFullYear() + '-' + String(d.getMonth()+1).padStart(2,'0') + '-' + String(d.getDate()).padStart(2,'0');
}

function switchPage(name) {
  document.querySelectorAll('.page').forEach(function(p){ p.classList.remove('active'); });
  document.querySelectorAll('.tab').forEach(function(t){ t.classList.remove('active'); });
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
  try { checkAutoViolations(); } catch(e) {}
  $('expenseAmount').addEventListener('input', updateCopperEq);
}

function updateCopperEq() {
  var v = parseFloat($('expenseAmount').value) || 0;
  $('copperEq').textContent = '= ' + Math.round(v * 10) + '铜';
}

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

var BELLY = { cx: 0.50, cy: 0.60, rx: 0.20, ry: 0.14 };

function seedRand(seed) {
  return function() {
    seed = (seed * 9301 + 49297) % 233280;
    return seed / 233280;
  };
}

function renderPiggy(gold, silver, copper) {
  renderCoinStack(gold, silver, copper);
  $('goldCount').textContent = gold;
  $('silverCount').textContent = silver;
  $('copperCount').textContent = copper;
  $('ledgerGold').textContent = gold;
  $('ledgerSilver').textContent = silver;
  $('ledgerCopper').textContent = copper;
  $('totalYuan').textContent = '¥' + (gold * 10 + silver + copper * 0.1).toFixed(1);
}

function renderCoinStack(gold, silver, copper) {
  var layer = $('coinLayer');
  if (!layer) return;
  var maxShow = { gold: 8, silver: 12, copper: 16 };
  var html = '';
  html += buildCoinLayer('copper', Math.min(copper, maxShow.copper), copper, maxShow.copper, 0);
  html += buildCoinLayer('silver', Math.min(silver, maxShow.silver), silver, maxShow.silver, 1);
  html += buildCoinLayer('gold', Math.min(gold, maxShow.gold), gold, maxShow.gold, 2);
  layer.innerHTML = html;
}

function buildCoinLayer(type, show, total, max, tier) {
  if (show === 0) return '';
  var rand = seedRand(type.charCodeAt(0) * 100 + total);
  var html = '';
  var tierOffset = tier * 0.025;
  for (var i = 0; i < show; i++) {
    var angle = (i / show) * Math.PI * 4 + tier * 0.5;
    var radiusFactor = 0.45 + 0.2 * (1 - i / show);
    var rx = BELLY.rx * radiusFactor;
    var ry = BELLY.ry * radiusFactor;
    var jitterX = (rand() - 0.5) * 0.03;
    var jitterY = (rand() - 0.5) * 0.02;
    var x = (BELLY.cx + Math.cos(angle) * rx + jitterX) * 100;
    var y = (BELLY.cy + BELLY.ry - 0.01 - tierOffset - i * 0.007 + Math.sin(angle) * ry + jitterY) * 100;
    var rot = (rand() - 0.5) * 50;
    var scale = 0.85 + rand() * 0.25;
    var mark = type === 'gold' ? '金' : type === 'silver' ? '银' : '铜';
    html += '<div class="coin ' + type + '" style="' +
      'left:' + x + '%;top:' + y + '%;' +
      'transform:translate(-50%,-50%) rotate(' + rot + 'deg) scale(' + scale + ');' +
      'z-index:' + (tier * 100 + i) + ';">' + mark + '</div>';
  }
  if (total > max) {
    html += '<div class="coin-overflow ' + type + '" style="' +
      'left:' + (BELLY.cx * 100) + '%;top:' + ((BELLY.cy - 0.06) * 100) + '%;">+' + (total - max) + '</div>';
  }
  return html;
}

function coinDropAnimation(type, count) {
  var layer = $('coinLayer');
  if (!layer) return;
  var mark = type === 'gold' ? '金' : type === 'silver' ? '银' : '铜';
  for (var i = 0; i < count && i < 3; i++) {
    (function(idx) {
      var c = document.createElement('div');
      c.className = 'coin ' + type + ' coin-falling';
      var startX = (BELLY.cx + (idx - 1) * 0.04) * 100;
      c.style.left = startX + '%';
      c.style.top = (BELLY.cy * 100) + '%';
      c.style.zIndex = '9999';
      c.textContent = mark;
      layer.appendChild(c);
      setTimeout(function() {
        if (c.parentNode) c.parentNode.removeChild(c);
      }, 700);
    })(i);
  }
}

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
    toast('已扣! ' + name);
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
