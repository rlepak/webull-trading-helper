'use strict';
const el = id => document.getElementById(id);
const number = (value, decimals = 0) => value == null ? '—' : Number(value).toLocaleString('en-US', {maximumFractionDigits: decimals});
const price = value => value == null ? '—' : '$' + Number(value).toLocaleString('en-US', {minimumFractionDigits: 2, maximumFractionDigits: 4});
const time = (value, zone) => value ? new Date(value).toLocaleTimeString('en-US', {timeZone: zone, hour12: false}) : '—';
function render(data) {
  el('clock').textContent = time(data.asOf, data.zone);
  el('session').textContent = {PRE_MARKET: 'Premarket', DAY_1: 'Regular market', AFTER_MARKET: 'Aftermarket'}[data.session] || data.session;
  el('connection').textContent = data.connected ? '● Feed connected' : '○ Feed disconnected';
  el('connection').className = data.connected ? 'live' : '';
  el('updated').textContent = 'Ranking updated ' + time(data.rankingUpdatedAt, data.zone);
  el('count').replaceChildren(document.createTextNode(data.rows.length + ' '));
  const total = document.createElement('small'); total.textContent = '/ 10'; el('count').append(total);
  el('surges').textContent = data.rows.filter(row => row.highlighted).length;
  el('rule').textContent = `10-second volume ≥ ${data.threshold}× the previous ${data.baselineBars}-bar average`;
  el('notice').textContent = !data.connected ? 'Feed disconnected. Signals are paused.' : data.rankingStale ? 'Ranking is stale. Waiting for a successful refresh; signals are paused.' : !data.aggregationEnabled ? 'Trade aggregation is disabled. Volume signals are paused.' : '';
  const fragment = document.createDocumentFragment();
  data.rows.forEach((row, index) => {
    const tr = document.createElement('tr'); tr.className = row.highlighted ? 'surge' : '';
    const gain = row.gainPercent == null ? '—' : (row.gainPercent >= 0 ? '+' : '') + number(row.gainPercent, 2) + '%';
    const values = [index + 1, row.symbol, price(row.price), gain, number(row.sessionVolume), number(row.currentVolume), number(row.tradeCount), row.volumeMultiplier == null ? '—' : number(row.volumeMultiplier, 2) + '×', row.status, row.ageSeconds == null ? 'No events' : row.ageSeconds + 's ago'];
    values.forEach((value, column) => { const td = document.createElement('td'); td.textContent = value; if (column === 3 && row.gainPercent != null) td.className = row.gainPercent >= 0 ? 'positive' : 'negative'; tr.append(td); });
    fragment.append(tr);
  });
  if (!data.rows.length) { const tr = document.createElement('tr'); const td = document.createElement('td'); td.colSpan = 10; td.className = 'empty'; td.textContent = 'Waiting for eligible session gainers. The table fills automatically.'; tr.append(td); fragment.append(tr); }
  el('rows').replaceChildren(fragment);
}
async function refresh() {
  try {
    const response = await fetch('/api/dashboard', {cache: 'no-store', signal: AbortSignal.timeout(4000)});
    if (!response.ok) throw new Error('Dashboard unavailable');
    render(await response.json());
  } catch (_) {
    el('notice').textContent = 'Dashboard update failed. Displayed values may be stale. Retrying…';
    el('connection').textContent = '○ Dashboard unavailable'; el('connection').className = '';
    el('surges').textContent = '—';
    document.querySelectorAll('tr.surge').forEach(row => { row.classList.remove('surge'); row.cells[8].textContent = 'Update unavailable'; });
  } finally { setTimeout(refresh, 1000); }
}
refresh();
