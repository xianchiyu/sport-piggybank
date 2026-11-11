function $(id) { return document.getElementById(id); }
function toast(msg) {
var t = $('toast');
t.textContent = msg;
t.classList.add('show');
setTimeout(function(){ t.classList.remove('show'); }, 2500);
}
function parseResult(json) {
try { var r = JSON.parse(json); return r; }
catch(e) { return {ok:false, error:'解析失败'}; }
}
function formatDate(d) {
return d.getFullYear() + '-' + String(d.getMonth()+1).padStart(2,'0') + '-' + String(d.getDate()).padStart(2,'0');
}

var selectedExType = 'run';
var selectedExDur = 30;
var isManualRainy = false;

// 五项任务的按钮 ID 映射
var TASK_BTNS = {
 exercise: 'btnExercise',
 breakfast: 'btnBreakfast',
 dinner: 'btnDinner',
 jingang: 'btnJingang',
 changshou: 'btnChangshou'
};


/** 让打卡卡片高度等于宽度（不依赖 aspect-ratio / padding-top） */
function squareTasks() {
  var cards = document.querySelectorAll('.task');
  for (var i = 0; i < cards.length; i++) {
    var el = cards[i];
    var w = el.offsetWidth;
    if (w > 0) el.style.height = w + 'px';
  }
}
window.addEventListener('resize', squareTasks);
// 图片加载完成后重新量一次，避免尺寸变化导致比例偏移
window.addEventListener('load', function(){ setTimeout(squareTasks, 60); });

function init() {
setTimeout(squareTasks, 0);
loadHome();
checkAutoViolations();
$('expenseAmount').addEventListener('input', updateCopperEq);
$('exerciseModal').addEventListener('click', function(e){ if (e.target === this) closeExercise(); });
var imp = $('importCopper');
if (imp) imp.addEventListener('input', updateImportPreview);
}


function updateCopperEq() {
var v = parseFloat($('expenseAmount').value) || 0;
$('copperEq').textContent = '= ' + Math.round(v * 10) + '铜';
}

function switchPage(name) {
document.querySelectorAll('.page').forEach(function(p){ p.classList.remove('active'); });
document.querySelectorAll('.nav-item').forEach(function(t){ t.classList.remove('active'); });
$('page-' + name).classList.add('active');
$('tab-' + name).classList.add('active');
if (name === 'home') { loadHome(); setTimeout(squareTasks, 0); }
if (name === 'stats') loadStats();
if (name === 'my') loadMy();
}

function checkAutoViolations() {
if (!window.Android) return;
var r = parseResult(Android.getAutoViolations());
if (!r.ok) return;
var list = r.data.violations;
if (!list || list.length === 0) return;
var html = '';
for (var i = 0; i < list.length; i++) {
var item = String(list[i]).replace(/</g,'&lt;').replace(/>/g,'&gt;');
html += '<div class="violation-item">' + item + '</div>';
}
$('violationList').innerHTML = html;
$('violationModal').classList.add('show');
}
function closeViolationModal() { $('violationModal').classList.remove('show'); }

function renderPiggy(gold, silver, copper) {
$('goldCount').textContent = gold;
$('silverCount').textContent = silver;
$('copperCount').textContent = copper;
$('ledgerGold').textContent = gold;
$('ledgerSilver').textContent = silver;
$('ledgerCopper').textContent = copper;
$('statGold').textContent = gold;
$('statSilver').textContent = silver;
$('statCopper').textContent = copper;
}
function coinDropAnimation(type) {
var card = document.querySelector('.coin-card.' + type);
if (!card) return;
card.classList.remove('drop');
void card.offsetWidth;
card.classList.add('drop');
}

function loadHome() {
if (window.Android) {
var r = parseResult(Android.getBalance());
if (r.ok) {
var d = r.data;
renderPiggy(d.gold, d.silver, d.copper);
}
var tr = parseResult(Android.getTransactions());
loadTodayLog(tr);
updateCheckStatus(tr);
}
}

function updateCheckStatus(r) {
r = r || parseResult(Android.getTransactions());
if (!r.ok) return;
var today = formatDate(new Date());
var KEYS = ['exercise','breakfast','dinner','jingang','changshou'];
var done = {};
KEYS.forEach(function(k){ done[k] = false; });
(r.data || []).forEach(function(t){
if (t.date === today && t.subtype && done.hasOwnProperty(t.subtype)) done[t.subtype] = true;
});
var btnMap = TASK_BTNS;
for (var k in btnMap) {
var el = $(btnMap[k]);
if (!el) continue;
el.classList.toggle('done', !!done[k]);
el.classList.toggle('pending', !done[k]);
}
refreshTaskTiers();
}

function loadTodayLog(r) {
r = r || parseResult(Android.getTransactions());
if (!r || !r.ok || !r.data) { $('todayLog').innerHTML = '<div class="hint" style="padding:4px 0;">暂无记录</div>'; return; }
var today = formatDate(new Date());
var txns = r.data.filter(function(t){ return t && t.date === today; });
var html = '';
if (txns.length === 0) {
html = '<div class="hint" style="padding:4px 0;">今天还没有记录</div>';
} else {
txns.forEach(function(t){
var note = (t.note || '').replace(/</g,'&lt;').replace(/>/g,'&gt;');
var icon = '<span style="color:#4caf50;font-weight:700;">✓</span>';
if (t.subtype === 'penalty_cash') icon = '<span style="color:#e65100;">⚠</span>';
else if (t.type === 'expense' || t.subtype === 'purchase') icon = '<span style="color:#f0a23a;">−</span>';
else if (t.subtype === 'social_exempt') icon = '<span style="color:#1565c0;">🛡</span>';
else if (t.type === 'card_bonus') icon = '<span style="color:#f2bd3f;">✦</span>';
html += '<div class="record-item">' + icon + ' ' + note + '</div>';
});
}
$('todayLog').innerHTML = html;
}

/** 该任务今天是否已打卡（以按钮的 done 状态为准） */
function isDoneToday(type) {
var b = $(TASK_BTNS[type]);
return !!(b && b.classList.contains('done'));
}

var checkinBusy = false;
function lockCheckin() {
if (checkinBusy) return false;
checkinBusy = true;
setTimeout(function(){ checkinBusy = false; }, 600);
return true;
}

function quickCheckin(type) {
if (isDoneToday(type)) { toast('今日已打卡'); return; }
if (!lockCheckin()) return;
if (window.Android) {
var r = parseResult(Android.checkin(type, '', 0, 0, false));
if (!r.ok) { toast(r.error); return; }
toast('+' + r.data.coins + '铜');
}
var btn = $(TASK_BTNS[type]);
if (btn) {
btn.classList.add('done');
btn.classList.remove('pending');
}
refreshTaskTiers();
if (window.Android) {
var r2 = parseResult(Android.getBalance());
if (r2.ok) renderPiggy(r2.data.gold, r2.data.silver, r2.data.copper);
loadTodayLog();
}
setTimeout(function(){ coinDropAnimation('copper'); }, 100);
}

function openExercise() {
$('exerciseModal').classList.add('show');
isManualRainy = false;
$('manualRainy').checked = false;
showNormalMode();
}
function showIndoorMode() {
selectedExType='indoor';
selectedExDur=0;
document.querySelectorAll('.ex-opt').forEach(function(b){b.disabled=true;b.style.opacity=0.4;});
document.querySelectorAll('.ex-dur').forEach(function(b){b.disabled=true;b.style.opacity=0.4;});
$('exDistanceWrap').style.display='none';
$('exDurationGroup').style.display='none';
$('exRopeGroup').style.display='none';
}
function showNormalMode() {
selectExType('run');
document.querySelectorAll('.ex-opt').forEach(function(b){b.disabled=false;b.style.opacity=1;});
document.querySelectorAll('.ex-dur').forEach(function(b){b.disabled=false;b.style.opacity=1;});
}
function closeExercise() { $('exerciseModal').classList.remove('show'); }
function onManualRainy() {
isManualRainy = $('manualRainy').checked;
if (isManualRainy) {
showIndoorMode();
} else {
showNormalMode();
}
}
function selectExType(type) {
selectedExType = type;
document.querySelectorAll('.ex-opt').forEach(function(b){b.classList.remove('active');});
document.querySelector('.ex-opt[data-type="'+type+'"]').classList.add('active');
$('exDistanceWrap').style.display = (type==='walk'||type==='rope') ? 'none' : 'block';
$('exDurationGroup').style.display = (type==='rope') ? 'none' : 'flex';
$('exRopeGroup').style.display = (type==='rope') ? 'flex' : 'none';
if (type === 'rope') selectExDur(800);
else if (type === 'run') selectExDur(30);
else if (type === 'walk') selectExDur(30);
}
function selectExDur(dur) {
selectedExDur = dur;
document.querySelectorAll('.ex-dur').forEach(function(b){b.classList.remove('active');});
document.querySelector('.ex-dur[data-dur="'+dur+'"]').classList.add('active');
}
function doExerciseCheckin() {
if (isDoneToday('exercise')) { toast('今日已打卡'); return; }
if (!lockCheckin()) return;
var dist = parseFloat($('exDistance').value) || 0;
if (window.Android) {
var r = parseResult(Android.checkin('exercise', selectedExType, selectedExDur, dist, isManualRainy));
if (!r.ok) { toast(r.error); return; }
toast('+' + r.data.coins + '铜');
var r2 = parseResult(Android.getBalance());
if (r2.ok) renderPiggy(r2.data.gold, r2.data.silver, r2.data.copper);
loadTodayLog();
}
$('btnExercise').classList.add('done');
$('btnExercise').classList.remove('pending');
refreshTaskTiers();
closeExercise();
setTimeout(function(){ coinDropAnimation('copper'); }, 100);
}

function loadStats() {
var txns;
if (window.Android) {
var r = parseResult(Android.getTransactions());
txns = (r.ok && r.data) ? r.data : [];
} else {
txns = [];
}
if (window.Android) {
var b = parseResult(Android.getBalance());
if (b.ok) renderPiggy(b.data.gold, b.data.silver, b.data.copper);
}
renderCheckinGrid(txns);
$('trendTitle').textContent = '最近7天趋势';
renderTrendChart(txns, 7);
}

var TASK_TIERS = ['tier-white', 'tier-copper', 'tier-silver', 'tier-gold', 'tier-shiny'];

/** 把本轮累计次数对应的档位挂到首页三个打卡按钮上 */
function refreshTaskTiers() {
if (!window.Android) return;
var r = parseResult(Android.getCardStatus());
if (!r.ok || !r.data) return;
var map = TASK_BTNS;
for (var key in map) {
var el = $(map[key]);
if (!el) continue;
var st = r.data[key] || {};
for (var i = 0; i < TASK_TIERS.length; i++) el.classList.remove(TASK_TIERS[i]);
if (st.checkedToday) el.classList.add('tier-' + (st.tier || 'white'));
}
}

function renderCheckinGrid(txns) {
var days = 7;
var dates = [];
for (var i = days - 1; i >= 0; i--) {
var d = new Date();
d.setDate(d.getDate() - i);
dates.push(d);
}
var html = '<div class="cg-header">';
html += '<div></div>';
for (var i = 0; i < dates.length; i++) {
html += '<div>' + (dates[i].getMonth()+1) + '/' + dates[i].getDate() + '</div>';
}
html += '</div>';
var tasks = [
{ key:'exercise',  label:'运动', color:'green',  ico:'img/ico_exercise.png' },
{ key:'breakfast', label:'早餐', color:'yellow', ico:'img/ico_breakfast.png' },
{ key:'dinner',    label:'晚餐', color:'purple', ico:'img/ico_dinner.png' },
{ key:'jingang',   label:'金刚功', color:'teal', ico:'img/ico_jingang.png' },
{ key:'changshou', label:'长寿功', color:'rose', ico:'img/ico_changshou.png' }
];
for (var ti = 0; ti < tasks.length; ti++) {
var t = tasks[ti];
html += '<div class="cg-row">';
var badgeInner = '<img src="' + t.ico + '" alt="' + t.label + '">';
html += '<div class="cg-label"><span class="cg-badge '+t.color+'">' + badgeInner + '</span>' + t.label + '</div>';
for (var di = 0; di < dates.length; di++) {
var dStr = formatDate(dates[di]);
var done = txns.some(function(x){
return x.date === dStr && x.subtype === t.key;
});
html += '<div class="cg-cell ' + (done ? 'ok' : 'no') + '">' + (done ? '✓' : '−') + '</div>';
}
html += '</div>';
}
$('checkinGrid').innerHTML = html;
}

function renderTrendChart(txns, days) {
var dates = [];
for (var i = days - 1; i >= 0; i--) {
var d = new Date();
d.setDate(d.getDate() - i);
dates.push(formatDate(d));
}
// 从 SharedPreferences 读每日余额快照
var snapshots = {};
if (window.Android) {
var r = parseResult(Android.getDailyBalances());
if (r.ok && r.data) snapshots = r.data;
}
// 有快照用快照，没有则用前一个有效值填充（余额未变）
var seriesG = [], seriesS = [], seriesC = [];
var lastG = 0, lastS = 0, lastC = 0;
for (var i = 0; i < dates.length; i++) {
var snap = snapshots[dates[i]];
if (snap) {
lastG = snap.gold || 0;
lastS = snap.silver || 0;
lastC = snap.copper || 0;
}
seriesG.push(lastG);
seriesS.push(lastS);
seriesC.push(lastC);
}
drawChart(seriesG, seriesS, seriesC, dates);
}

function drawChart(sG, sS, sC, dates) {
var svg = $('trendChart');
svg.innerHTML = '';
var W = 340, H = 220;
var padL = 30, padR = 10, padT = 10, padB = 30;
var cw = W - padL - padR, ch = H - padT - padB;
var maxV = 1;
for (var i = 0; i < sG.length; i++) if (sG[i] > maxV) maxV = sG[i];
for (var i = 0; i < sS.length; i++) if (sS[i] > maxV) maxV = sS[i];
for (var i = 0; i < sC.length; i++) if (sC[i] > maxV) maxV = sC[i];
// 纵轴固定 0-20：铜到 15 进银、银到 15 进金，单个币种到不了 20，留出天花板即可
var niceMax = 20;
var yTicks = [0, 5, 10, 15, 20];
var html = '';for (var i = 0; i < yTicks.length; i++) {
var y = padT + ch - (yTicks[i] / niceMax) * ch;
html += '<line x1="' + padL + '" y1="' + y + '" x2="' + (padL+cw) + '" y2="' + y + '" stroke="#eef0f3" stroke-width="1"/>';
html += '<text x="' + (padL-6) + '" y="' + (y+3) + '" text-anchor="end" font-size="10" fill="#b0b6bd">' + yTicks[i] + '</text>';
}
var stepX = cw / (sG.length - 1 || 1);
for (var i = 0; i < dates.length; i++) {
var x = padL + i * stepX;
var parts = dates[i].split('-');
var lbl = parts[1] + '/' + parts[2];
if (i % Math.ceil(dates.length / 7) === 0 || i === dates.length - 1) {
html += '<text x="' + x + '" y="' + (H - padB + 16) + '" text-anchor="middle" font-size="10" fill="#b0b6bd">' + lbl + '</text>';
}
}
function linePath(arr, color, width) {
var d = '';
for (var i = 0; i < arr.length; i++) {
var x = padL + i * stepX;
var y = padT + ch - (arr[i] / niceMax) * ch;
d += (i === 0 ? 'M' : 'L') + x + ' ' + y + ' ';
}
html += '<path d="' + d + '" stroke="' + color + '" stroke-width="' + width + '" fill="none" stroke-linecap="round" stroke-linejoin="round"/>';
for (var i = 0; i < arr.length; i++) {
var x = padL + i * stepX;
var y = padT + ch - (arr[i] / niceMax) * ch;
html += '<circle cx="' + x + '" cy="' + y + '" r="3" fill="' + color + '"/>';
}
}
linePath(sC, '#f08a4b', 2);
linePath(sS, '#7da3c2', 2);
linePath(sG, '#f2bd3f', 2);
svg.innerHTML = html;
}

function doImportLegacy() {
if (!window.Android) { toast('仅 App 内可用'); return; }
var copper = parseInt($('importCopper').value, 10);
var penalty = parseInt($('importPenalty').value, 10);
if (isNaN(copper) || copper < 0) { toast('请输入铜币总数'); return; }
if (isNaN(penalty) || penalty < 0) penalty = 0;
if (copper === 0 && penalty === 0) { toast('没有要导入的数据'); return; }
var r = parseResult(Android.importLegacy(copper, penalty));
if (!r.ok) { toast(r.error); return; }
var d = r.data;
$('importPreview').textContent = '已导入：金 ' + d.gold + ' / 银 ' + d.silver + ' / 铜 ' + d.copper;
toast('导入成功');
$('importCopper').value = '';
$('importPenalty').value = '';
var b = parseResult(Android.getBalance());
if (b.ok) renderPiggy(b.data.gold, b.data.silver, b.data.copper);
loadMy();
}

function updateImportPreview() {
var v = parseInt($('importCopper').value, 10);
if (isNaN(v) || v < 0) { $('importPreview').textContent = '输入后自动拆分金／银／铜'; return; }
var copper = v, silver = 0, gold = 0;
if (copper >= 15) { var n = Math.floor((copper - 5) / 10); silver = n; copper -= n * 10; }
if (silver >= 15) { var m = Math.floor((silver - 5) / 10); gold = m; silver -= m * 10; }
$('importPreview').textContent = '将拆分为：金 ' + gold + ' / 银 ' + silver + ' / 铜 ' + copper;
}

function loadMy() {
if (window.Android) {
var r = parseResult(Android.getQuarterSummary());
if (r.ok) {
var d = r.data;
$('qIncomeCopper').textContent = d.incomeCopper + '铜';
$('qExpenseCopper').textContent = d.expenseCopper + '铜';
$('qBalanceCopper').textContent = d.balanceCopper + '铜';
$('qPenaltyTotal').textContent = '¥' + d.penaltyTotal;
}
var b = parseResult(Android.getBalance());
if (b.ok) renderPiggy(b.data.gold, b.data.silver, b.data.copper);
var sr = parseResult(Android.getSocialExemptStatus());
if (sr.ok) {
$('socUsed').textContent = sr.data.used + '/3';
var todayRow = $('socTodayRow');
if (sr.data.todayUsed) {
todayRow.style.display = 'flex';
$('socToday').textContent = '已使用';
} else {
todayRow.style.display = 'none';
}
}
var pr = parseResult(Android.getPenaltyStatus());
if (pr.ok) {
$('penCount').textContent = pr.data.count + '次';
$('penNext').textContent = '¥' + pr.data.nextPenalty;
$('penDays').textContent = pr.data.daysLeft + '天';
}
// 已有数据时禁用导入入口
if (!window.Android) { /* 预览环境跳过 */ }
else {
var ci = parseResult(Android.canImportLegacy());
if (ci.ok && ci.data.hasData) {
var btn = document.querySelector('[onclick="doImportLegacy()"]');
if (btn) { btn.disabled = true; btn.textContent = '已有数据，不可导入'; btn.style.opacity = 0.45; }
if ($('importCopper')) $('importCopper').disabled = true;
if ($('importPenalty')) $('importPenalty').disabled = true;
$('importPreview').textContent = '当前已有数据，导入已关闭';
}
}
}
loadTxnList();
}
function loadTxnList() {
var txns;
if (window.Android) {
var r = parseResult(Android.getTransactions());
txns = (r.ok && r.data) ? r.data : [];
} else {
txns = [];
}
var list = txns.slice().reverse();
var RECENT_DAYS = 7;
var cutoff = new Date();
cutoff.setDate(cutoff.getDate() - RECENT_DAYS);
var cutoffStr = formatDate(cutoff);
list = list.filter(function(t){ return t.date >= cutoffStr; });
var html = '';
if (list.length === 0) {
html = '<div class="hint" style="padding:4px 0;">还没有记录</div>';
} else {
list.forEach(function(t){
var icon = '<span style="color:#4caf50;">●</span>';
if (t.subtype === 'penalty_cash') icon = '<span style="color:#e65100;">●</span>';
else if (t.type === 'expense' || t.subtype === 'purchase') icon = '<span style="color:#f0a23a;">●</span>';
else if (t.subtype === 'social_exempt') icon = '<span style="color:#1565c0;">●</span>';
else if (t.type === 'card_bonus') icon = '<span style="color:#f2bd3f;">●</span>';
var note = (t.note || '').replace(/</g,'&lt;').replace(/>/g,'&gt;');
html += '<div class="log-item">' + icon + ' ' + t.date.slice(5) + ' ' + note + '</div>';
});
}
$('txnList').innerHTML = html;
}
function doConsume() {
var name = $('expenseName').value.trim();
var amount = parseFloat($('expenseAmount').value);
if (!name) { toast('输入买了什么'); return; }
if (!amount || amount <= 0) { toast('输入金额'); return; }
if (window.Android) {
var r = parseResult(Android.consume(amount, name));
if (r.ok) {
toast('已扣 ' + name);
$('expenseName').value = '';
$('expenseAmount').value = '';
$('copperEq').textContent = '= 0铜';
var b = parseResult(Android.getBalance());
if (b.ok) renderPiggy(b.data.gold, b.data.silver, b.data.copper);
loadMy();
} else {
toast(r.error);
}
}
}
function doQuarterWithdraw() {
if (window.Android) {
var q = parseResult(Android.getQuarterSummary());
if (q.ok) {
var yuan = (q.data.balanceCopper / 10).toFixed(1);
if (!confirm('可导出金额：¥' + yuan + '\n确定季度提现？余额将清零。')) return;
var r = parseResult(Android.quarterWithdraw());
if (r.ok) {
toast('提现完成 ¥' + yuan);
var b = parseResult(Android.getBalance());
if (b.ok) renderPiggy(b.data.gold, b.data.silver, b.data.copper);
loadMy();
} else { toast('提现失败'); }
}
}
}

function doSocialExempt() {
if (window.Android) {
var r = parseResult(Android.useSocialExempt());
if (r.ok) {
toast('晚餐社交豁免已生效，今晚免罚保留连续');
loadMy();
} else { toast(r.error); }
}
}

init();