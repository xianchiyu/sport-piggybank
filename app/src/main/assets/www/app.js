/* 运动存钱罐 — 前端逻辑 */

// ── 工具 ────────────────────────────────────
function $(id) { return document.getElementById(id); }
function toast(msg) {
  const t = $('toast');
  t.textContent = msg;
  t.classList.add('show');
  setTimeout(() => t.classList.remove('show'), 2000);
}
function parseResult(json) {
  try {
    const r = JSON.parse(json);
    return r;
  } catch(e) {
    return { ok:false, error:'解析失败' };
  }
}

// ── 页面切换 ────────────────────────────────
function switchPage(name) {
  document.querySelectorAll('.page').forEach(p => p.classList.remove('active'));
  document.querySelectorAll('.tab').forEach(t => t.classList.remove('active'));
  $('page-' + name).classList.add('active');
  $('tab-' + name).classList.add('active');
  if (name === 'ledger') loadLedger();
  if (name === 'settings') loadSettings();
}

// ── 初始化 ──────────────────────────────────
let selectedExType = 'run';
let selectedExDur = 30;

function init() {
  const now = new Date();
  const w = ['日','一','二','三','四','五','六'];
  $('todayDate').textContent = `${now.getMonth()+1}月${now.getDate()}日 周${w[now.getDay()]}`;
  loadHome();
}

function loadHome() {
  loadBalance();
  loadStreaks();
  loadTodayLog();
}

// ── 余额 ────────────────────────────────────
function loadBalance() {
  const r = parseResult(Android.getBalance());
  if (!r.ok) return;
  const d = r.data;
  $('goldCount').textContent = d.gold;
  $('silverCount').textContent = d.silver;
  $('copperCount').textContent = d.copper;
  $('totalYuan').textContent = '¥' + d.yuan.toFixed(1);
  renderJar(d.gold, d.silver, d.copper);
}

function renderJar(g, s, c) {
  const el = $('jarVisual');
  let html = '';
  // 金币最多显示5个，银币最多显示8个，铜币最多显示12个
  for (let i = 0; i < Math.min(g, 5); i++) html += '<span class="coin-dot gold"></span>';
  for (let i = 0; i < Math.min(s, 8); i++) html += '<span class="coin-dot silver"></span>';
  for (let i = 0; i < Math.min(c, 12); i++) html += '<span class="coin-dot copper"></span>';
  el.innerHTML = html;
}

// ── 连续天数 ────────────────────────────────
function loadStreaks() {
  const r = parseResult(Android.getStreaks());
  if (!r.ok) return;
  const d = r.data;
  $('streakExercise').textContent = d.exercise > 0 ? `连续${d.exercise}天` : '';
  $('streakBreakfast').textContent = d.breakfast > 0 ? `连续${d.breakfast}天` : '';
  $('streakDinner').textContent = d.dinner > 0 ? `连续${d.dinner}天` : '';

  // 检查今天是否已打卡
  const today = formatDate(new Date());
  // 通过余额接口间接判断，实际通过checkin返回判断
}

function formatDate(d) {
  return d.getFullYear() + '-' + String(d.getMonth()+1).padStart(2,'0') + '-' + String(d.getDate()).padStart(2,'0');
}

// ── 打卡 ────────────────────────────────────
function quickCheckin(type) {
  const r = parseResult(Android.checkin(type, '', 0, 0));
  if (r.ok) {
    const d = r.data;
    toast(`+${d.coins}铜 (x${d.multiplier}) 连续${d.streak}天`);
    const btn = type === 'breakfast' ? $('btnBreakfast') : $('btnDinner');
    btn.classList.add('done');
    loadBalance();
    loadStreaks();
    loadTodayLog();
  } else {
    toast(r.error);
  }
}

function openExercise() {
  $('exerciseModal').classList.add('show');
}
function closeExercise() {
  $('exerciseModal').classList.remove('show');
}

function selectExType(type) {
  selectedExType = type;
  document.querySelectorAll('.ex-opt').forEach(b => b.classList.remove('active'));
  document.querySelector(`.ex-opt[data-type="${type}"]`).classList.add('active');
  // 走路时隐藏距离输入
  $('exDistance').parentElement.style.display = type === 'walk' ? 'none' : 'block';
}

function selectExDur(dur) {
  selectedExDur = dur;
  document.querySelectorAll('.ex-dur').forEach(b => b.classList.remove('active'));
  document.querySelector(`.ex-dur[data-dur="${dur}"]`).classList.add('active');
}

function doExerciseCheckin() {
  const dist = parseFloat($('exDistance').value) || 0;
  const r = parseResult(Android.checkin('exercise', selectedExType, selectedExDur, dist));
  if (r.ok) {
    const d = r.data;
    toast(`+${d.coins}铜 (x${d.multiplier}) 连续${d.streak}天`);
    $('btnExercise').classList.add('done');
    closeExercise();
    loadBalance();
    loadStreaks();
    loadTodayLog();
  } else {
    toast(r.error);
  }
}

// ── 今日记录 ────────────────────────────────
function loadTodayLog() {
  const r = parseResult(Android.getTransactions());
  if (!r.ok) return;
  const today = formatDate(new Date());
  const txns = r.data || [];
  const todayTxns = txns.filter(t => t.date === today);
  let html = '';
  if (todayTxns.length === 0) {
    html = '<div class="hint" style="text-align:center;padding:8px">今天还没有记录</div>';
  } else {
    todayTxns.forEach(t => {
      const cls = t.amount >= 0 ? 'pos' : 'neg';
      const sign = t.amount >= 0 ? '+' : '';
      html += `<div class="log-item"><span>${t.note}</span><span class="amt ${cls}">${sign}¥${Math.abs(t.amount).toFixed(1)}</span></div>`;
    });
  }
  $('todayLog').innerHTML = html;
}

// ── 账本页 ──────────────────────────────────
function loadLedger() {
  const r = parseResult(Android.getQuarterSummary());
  if (r.ok) {
    const d = r.data;
    $('qIncome').textContent = '¥' + d.income.toFixed(1);
    $('qExpense').textContent = '¥' + d.expense.toFixed(1);
    $('qBalance').textContent = '¥' + d.balance.toFixed(1);
  }
  loadTxnList();
}

function loadTxnList() {
  const r = parseResult(Android.getTransactions());
  if (!r.ok) return;
  const txns = (r.data || []).slice().reverse().slice(0, 50);
  let html = '';
  if (txns.length === 0) {
    html = '<div class="hint" style="text-align:center;padding:8px">还没有记录</div>';
  } else {
    txns.forEach(t => {
      const cls = t.amount >= 0 ? 'pos' : 'neg';
      const sign = t.amount >= 0 ? '+' : '';
      html += `<div class="log-item"><span>${t.date} ${t.note}</span><span class="amt ${cls}">${sign}¥${Math.abs(t.amount).toFixed(1)}</span></div>`;
    });
  }
  $('txnList').innerHTML = html;
}

function doConsume() {
  const name = $('expenseName').value.trim();
  const amount = parseFloat($('expenseAmount').value);
  if (!name) { toast('输入买了什么'); return; }
  if (!amount || amount <= 0) { toast('输入金额'); return; }
  const r = parseResult(Android.consume(amount, name));
  if (r.ok) {
    toast(`消费 ¥${amount.toFixed(1)} 已扣币`);
    $('expenseName').value = '';
    $('expenseAmount').value = '';
    loadBalance();
    loadLedger();
  } else {
    toast(r.error);
  }
}

function doQuarterWithdraw() {
  if (!confirm('确定季度提现？余额将清零。')) return;
  const r = parseResult(Android.quarterWithdraw());
  if (r.ok) {
    const d = r.data;
    toast(`提现 ¥${d.balance.toFixed(1)} 到余额宝`);
    loadBalance();
    loadLedger();
  } else {
    toast('提现失败');
  }
}

// ── 设置页 ──────────────────────────────────
function loadSettings() {
  // 城市
  const city = Android.getCity();
  $('citySelect').value = city;
  // 社交豁免
  const sr = parseResult(Android.getSocialExemptStatus());
  if (sr.ok) $('socUsed').textContent = `${sr.data.used}/3`;
  // 违规状态
  const pr = parseResult(Android.getPenaltyStatus());
  if (pr.ok) {
    $('penCount').textContent = `${pr.data.count}次`;
    $('penNext').textContent = `¥${pr.data.nextPenalty}`;
    $('penDays').textContent = `${pr.data.daysLeft}天`;
  }
}

function doSetCity() {
  const city = $('citySelect').value;
  Android.setCity(city);
  toast('城市已更新');
}

function doSocialExempt() {
  const r = parseResult(Android.useSocialExempt());
  if (r.ok) {
    toast(`已使用 ${r.data.used}/3，剩余${r.data.remaining}次`);
    loadSettings();
  } else {
    toast(r.error);
  }
}

function doReportViolation(type, desc) {
  if (!confirm(`确认上报违规：${desc}？\n将触发现金罚金+连续天数清零`)) return;
  const r = parseResult(Android.reportViolation(type, desc));
  if (r.ok) {
    const d = r.data;
    toast(`违规！罚金¥${d.cashPenalty}，连续天数已清零`);
    loadSettings();
    loadBalance();
    loadStreaks();
  } else {
    toast('操作失败');
  }
}

// 启动
init();
