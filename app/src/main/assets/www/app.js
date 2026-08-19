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

function init() {
loadHome();
checkAutoViolations();
$('expenseAmount').addEventListener('input', updateCopperEq);
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
if (name === 'home') loadHome();
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
loadStreaks();
loadTodayLog();
updateCheckStatus();
}
}

function loadStreaks() {
var r = parseResult(Android.getStreaks());
if (!r.ok) return;
var d = r.data;
var el = $('streakExercise'); if (el) el.textContent = d.exercise > 0 ? '连续' + d.exercise + '天' : '';
el = $('streakBreakfast'); if (el) el.textContent = d.breakfast > 0 ? '连续' + d.breakfast + '天' : '';
el = $('streakDinner'); if (el) el.textContent = d.dinner > 0 ? '连续' + d.dinner + '天' : '';
}

function updateCheckStatus() {
var r = parseResult(Android.getTransactions());
if (!r.ok) return;
var today = formatDate(new Date());
var done = { exercise:false, breakfast:false, dinner:false };
(r.data || []).forEach(function(t){
if (t.date === today && t.subtype) {
if (t.subtype === 'exercise') done.exercise = true;
else if (t.subtype === 'breakfast') done.breakfast = true;
else if (t.subtype === 'dinner') done.dinner = true;
}
});
$('btnExercise').classList.toggle('done', done.exercise);
$('btnExercise').classList.toggle('pending', !done.exercise);
$('btnBreakfast').classList.toggle('done', done.breakfast);
$('btnBreakfast').classList.toggle('pending', !done.breakfast);
$('btnDinner').classList.toggle('done', done.dinner);
$('btnDinner').classList.toggle('pending', !done.dinner);
}

function loadTodayLog() {
var r = parseResult(Android.getTransactions());
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
html += '<div class="record-item">' + icon + ' ' + note + '</div>';
});
}
$('todayLog').innerHTML = html;
}

function quickCheckin(type) {
if (window.Android) {
var r = parseResult(Android.checkin(type, '', 0, 0, false));
if (!r.ok) { toast(r.error); return; }
toast('+' + r.data.coins + '铜');
}
var btn = type === 'breakfast' ? $('btnBreakfast') : $('btnDinner');
btn.classList.add('done');
btn.classList.remove('pending');
if (window.Android) {
var r2 = parseResult(Android.getBalance());
if (r2.ok) renderPiggy(r2.data.gold, r2.data.silver, r2.data.copper);
loadStreaks();
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
var dist = parseFloat($('exDistance').value) || 0;
if (window.Android) {
var r = parseResult(Android.checkin('exercise', selectedExType, selectedExDur, dist, isManualRainy));
if (!r.ok) { toast(r.error); return; }
toast('+' + r.data.coins + '铜');
var r2 = parseResult(Android.getBalance());
if (r2.ok) renderPiggy(r2.data.gold, r2.data.silver, r2.data.copper);
loadStreaks();
loadTodayLog();
}
$('btnExercise').classList.add('done');
$('btnExercise').classList.remove('pending');
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
{ key:'exercise',  label:'运动', color:'green',  ico:'img/ico_run.png' },
{ key:'breakfast', label:'早餐', color:'yellow', ico:'img/ico_egg.png' },
{ key:'dinner',    label:'晚餐', color:'purple', ico:'img/ico_lunch.png' }
];
for (var ti = 0; ti < tasks.length; ti++) {
var t = tasks[ti];
html += '<div class="cg-row">';
html += '<div class="cg-label"><span class="cg-badge '+t.color+'"><img src="'+t.ico+'" alt="'+t.label+'"></span>' + t.label + '</div>';
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
var niceMax = 20;
var yTicks = [0, niceMax/4, niceMax/2, niceMax*3/4, niceMax];
var html = '';
for (var i = 0; i < yTicks.length; i++) {
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
var city = Android.getCity();
$('cityInput').value = city;
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
function doSetCity() {
var city = $('cityInput').value.trim();
if (!city) { toast('输入城市名'); return; }
if (window.Android) Android.setCity(city);
toast('城市已更新');
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