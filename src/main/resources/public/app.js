// what it does: [NetWatchAPI, StatsRenderer, ChartRenderer, TableRenderer, EndpointsRenderer, OriginsRenderer, StatusIndicator, App, escapeHtml, methodBadge, statusBadge, formatTimestamp, formatHourLabel]

'use strict';

const CONFIG = {
    apiBase:         '/api',
    pollIntervalMs:  5_000,
    logLimit:        25,
    originsLimit:    12,
    chartHoursBack:  24,
    countUpDuration: 800,
};

const NetWatchAPI = {
    async fetchAnalytics() {
        const res = await fetch(`${CONFIG.apiBase}/analytics`, {
            method:  'GET',
            headers: { 'Accept': 'application/json' },
            signal:  AbortSignal.timeout(8_000),
        });
        if (!res.ok) throw new Error(`HTTP ${res.status}: ${res.statusText}`);
        return res.json();
    },

    async sendBeacon(payload = {}) {
        try {
            await fetch(`${CONFIG.apiBase}/track`, {
                method:  'POST',
                headers: { 'Content-Type': 'application/json' },
                body:    JSON.stringify(payload),
                keepalive: true,
            });
        } catch (e) {}
    },
};

const StatsRenderer = {
    _counters: {},

    animateTo(elId, newValue, suffix = '', decimals = 0) {
        const el = document.getElementById(elId);
        if (!el) return;

        const start     = this._counters[elId] ?? 0;
        const startTime = performance.now();
        const duration  = CONFIG.countUpDuration;

        const tick = (now) => {
            const elapsed  = now - startTime;
            const progress = Math.min(elapsed / duration, 1);
            const eased    = 1 - Math.pow(1 - progress, 3);
            const current  = start + (newValue - start) * eased;

            el.textContent = current.toFixed(decimals) + suffix;

            if (progress < 1) {
                requestAnimationFrame(tick);
            } else {
                el.textContent = newValue.toFixed(decimals) + suffix;
                this._counters[elId] = newValue;
            }
        };

        requestAnimationFrame(tick);
    },

    render(summary) {
        const total    = summary.totalRequests   ?? 0;
        const unique   = summary.uniqueIps        ?? 0;
        const rate     = summary.reqPerMinute     ?? 0;
        const blocked  = summary.blockedIps       ?? 0;
        const last24h  = summary.requestsLast24h  ?? 0;
        const lastHour = summary.requestsLastHour ?? 0;

        this.animateTo('stat-total',   total,   '', 0);
        this.animateTo('stat-unique',  unique,  '', 0);
        this.animateTo('stat-rate',    rate,    '', 1);
        this.animateTo('stat-blocked', blocked, '', 0);

        setText('stat-total-sub',   `${last24h.toLocaleString()} in last 24h`);
        setText('stat-unique-sub',  `${lastHour} active in last hour`);
        setText('stat-rate-sub',    'averaged over last 60 min');
        setText('stat-blocked-sub', blocked > 0 ? 'Review flagged IPs' : 'No threats detected');
    },
};

const ChartRenderer = {
    _chart: null,

    init() {
        const ctx = document.getElementById('traffic-chart');
        if (!ctx) return;

        this._chart = new Chart(ctx, {
            type: 'line',
            data: {
                labels:   [],
                datasets: [{
                    label:           'Requests',
                    data:            [],
                    borderColor:     '#3b82f6',
                    backgroundColor: 'rgba(59, 130, 246, 0.08)',
                    borderWidth:     2,
                    pointRadius:     3,
                    pointHoverRadius: 6,
                    pointBackgroundColor: '#3b82f6',
                    tension:         0.4,
                    fill:            true,
                }],
            },
            options: {
                responsive:          true,
                maintainAspectRatio: false,
                interaction: { mode: 'index', intersect: false },
                plugins: {
                    legend: { display: false },
                    tooltip: {
                        backgroundColor: '#161f30',
                        borderColor:     'rgba(99, 130, 191, 0.25)',
                        borderWidth:     1,
                        titleColor:      '#e8edf5',
                        bodyColor:       '#8fa8cc',
                        padding:         10,
                        callbacks: {
                            title: (items) => formatHourLabel(items[0]?.label ?? ''),
                            label: (item) => ` ${item.raw.toLocaleString()} requests`,
                        },
                    },
                },
                scales: {
                    x: {
                        grid:  { color: 'rgba(99, 130, 191, 0.07)', drawBorder: false },
                        ticks: {
                            color: '#4d6080',
                            maxTicksLimit: 8,
                            callback: (_, i, items) => formatHourLabel(items[i]?.label ?? '', true),
                        },
                    },
                    y: {
                        grid:  { color: 'rgba(99, 130, 191, 0.07)', drawBorder: false },
                        ticks: { color: '#4d6080', callback: (v) => v.toLocaleString(), precision: 0 },
                        beginAtZero: true,
                    },
                },
            },
        });
    },

    update(hourlyTraffic) {
        if (!this._chart) return;
        const labels = hourlyTraffic.map(row => row[0]);
        const data   = hourlyTraffic.map(row => Number(row[1]));
        const total  = data.reduce((a, b) => a + b, 0);

        this._chart.data.labels = labels;
        this._chart.data.datasets[0].data = data;
        this._chart.update('active');

        setText('chart-total-badge', `${total.toLocaleString()} total`);
    },
};

const TableRenderer = {
    _lastIds: new Set(),

    render(logs) {
        const tbody = document.getElementById('logs-tbody');
        if (!tbody) return;

        if (!logs || logs.length === 0) {
            tbody.innerHTML = `<tr><td colspan="7" class="table-empty">No traffic logged yet.</td></tr>`;
            return;
        }

        const newIds = new Set(logs.map(l => l.id));

        tbody.innerHTML = logs.map((log, index) => {
            const isNew = !this._lastIds.has(log.id);
            return `
                <tr class="${isNew ? 'row-new' : ''}">
                    <td>${index + 1}</td>
                    <td class="td-ip">${escapeHtml(log.ipAddress ?? '—')}</td>
                    <td>${methodBadge(log.requestMethod)}</td>
                    <td class="td-endpoint" title="${escapeHtml(log.endpoint ?? '')}">${escapeHtml(log.endpoint ?? '/')}</td>
                    <td>${statusBadge(log.statusCode)}</td>
                    <td class="td-ua" title="${escapeHtml(log.userAgent ?? '')}">${escapeHtml(log.userAgent ?? '—')}</td>
                    <td class="td-time">${formatTimestamp(log.accessedAt)}</td>
                </tr>`;
        }).join('');

        this._lastIds = newIds;
    },
};

const EndpointsRenderer = {
    render(topEndpoints) {
        const container = document.getElementById('endpoints-list');
        if (!container) return;

        if (!topEndpoints || topEndpoints.length === 0) {
            container.innerHTML = `<p style="color: var(--color-text-muted); font-size: 0.8rem; padding: 0.5rem 0;">No endpoint data yet.</p>`;
            return;
        }

        const max = Number(topEndpoints[0][1]) || 1;

        container.innerHTML = topEndpoints.map(([endpoint, count]) => {
            const pct = Math.round((Number(count) / max) * 100);
            return `
                <div class="endpoint-row">
                    <div class="endpoint-row__meta">
                        <span class="endpoint-row__path" title="${escapeHtml(endpoint)}">${escapeHtml(endpoint)}</span>
                        <span class="endpoint-row__count">${Number(count).toLocaleString()}</span>
                    </div>
                    <div class="endpoint-row__bar-track">
                        <div class="endpoint-row__bar-fill" data-pct="${pct}"></div>
                    </div>
                </div>`;
        }).join('');

        requestAnimationFrame(() => {
            container.querySelectorAll('.endpoint-row__bar-fill').forEach(bar => {
                bar.style.width = bar.dataset.pct + '%';
            });
        });
    },
};

const OriginsRenderer = {
    render(topOrigins) {
        const grid  = document.getElementById('origins-grid');
        const badge = document.getElementById('origins-count-badge');
        if (!grid) return;

        if (!topOrigins || topOrigins.length === 0) {
            grid.innerHTML = `<p style="color: var(--color-text-muted); grid-column: 1/-1;">No origin data yet.</p>`;
            return;
        }

        const max = topOrigins[0]?.requestCount || 1;
        if (badge) badge.textContent = `${topOrigins.length} IPs`;

        grid.innerHTML = topOrigins.map((stat, i) => {
            const pct      = Math.round((stat.requestCount / max) * 100);
            const lastSeen = formatTimestamp(stat.lastSeen);
            const firstSeen = formatTimestamp(stat.firstSeen);
            const endpoint = stat.mostVisitedEndpoint ?? '—';

            return `
                <div class="origin-card" role="listitem">
                    <div class="origin-card__top">
                        <span class="origin-card__rank">#${i + 1}</span>
                        ${stat.blocked ? '<span class="origin-card__blocked">BLOCKED</span>' : ''}
                    </div>
                    <div class="origin-card__ip">${escapeHtml(stat.ipAddress ?? '—')}</div>
                    <div class="origin-card__meta">
                        <div class="meta-item">
                            <span class="meta-item__label">Requests</span>
                            <span class="meta-item__value">${Number(stat.requestCount).toLocaleString()}</span>
                        </div>
                        <div class="meta-item">
                            <span class="meta-item__label">Last Seen</span>
                            <span class="meta-item__value">${lastSeen}</span>
                        </div>
                        <div class="meta-item">
                            <span class="meta-item__label">First Seen</span>
                            <span class="meta-item__value">${firstSeen}</span>
                        </div>
                        <div class="meta-item">
                            <span class="meta-item__label">Top Endpoint</span>
                            <span class="meta-item__value" title="${escapeHtml(endpoint)}">${truncate(endpoint, 22)}</span>
                        </div>
                    </div>
                    <div class="origin-card__bar-track">
                        <div class="origin-card__bar-fill" data-pct="${pct}"></div>
                    </div>
                </div>`;
        }).join('');

        requestAnimationFrame(() => {
            grid.querySelectorAll('.origin-card__bar-fill').forEach(bar => {
                bar.style.width = bar.dataset.pct + '%';
            });
        });
    },
};

const StatusIndicator = {
    setOnline()  { setClass('status-dot', ['online'], ['offline']); setText('status-text', 'Connected'); },
    setOffline() { setClass('status-dot', ['offline'], ['online']); setText('status-text', 'Disconnected'); },
    updateTimestamp() { setText('last-refresh', `Updated ${new Date().toLocaleTimeString()}`); },
};

const App = {
    _pollTimer: null,

    async init() {
        ChartRenderer.init();

        NetWatchAPI.sendBeacon({
            endpoint:  window.location.pathname,
            method:    'GET',
            userAgent: navigator.userAgent,
            sessionId: getOrCreateSessionId(),
        });

        await this.refresh();
        this._pollTimer = setInterval(() => this.refresh(), CONFIG.pollIntervalMs);
    },

    async refresh() {
        try {
            const data = await NetWatchAPI.fetchAnalytics();

            StatsRenderer.render(data.summary       ?? {});
            ChartRenderer.update(data.hourlyTraffic ?? []);
            TableRenderer.render(data.recentLogs    ?? []);
            EndpointsRenderer.render(data.topEndpoints ?? []);
            OriginsRenderer.render(data.topOrigins  ?? []);

            StatusIndicator.setOnline();
            StatusIndicator.updateTimestamp();
        } catch (err) {
            StatusIndicator.setOffline();
        }
    },
};

function setText(id, text) {
    const el = document.getElementById(id);
    if (el) el.textContent = text;
}

function setClass(id, add = [], remove = []) {
    const el = document.getElementById(id);
    if (!el) return;
    el.classList.add(...add);
    el.classList.remove(...remove);
}

function escapeHtml(str) {
    if (str == null) return '';
    return String(str)
        .replace(/&/g,  '&amp;')
        .replace(/</g,  '&lt;')
        .replace(/>/g,  '&gt;')
        .replace(/"/g,  '&quot;')
        .replace(/'/g,  '&#39;');
}

function truncate(str, maxLen) {
    if (!str) return '—';
    return str.length > maxLen ? str.slice(0, maxLen) + '…' : str;
}

function formatTimestamp(raw) {
    if (!raw) return '—';
    try {
        if (Array.isArray(raw)) {
            const [y, mo, d, h, mi, s] = raw;
            return new Date(y, mo - 1, d, h, mi, s ?? 0).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' });
        }
        return new Date(raw).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' });
    } catch { return String(raw); }
}

function formatHourLabel(raw, short = false) {
    if (!raw) return '';
    try {
        const dt = new Date(raw.replace(' ', 'T') + 'Z');
        if (short) return dt.toLocaleTimeString([], { hour: '2-digit', hour12: false });
        return dt.toLocaleString([], { month: 'short', day: 'numeric', hour: '2-digit', hour12: false });
    } catch { return raw; }
}

function methodBadge(method) {
    const m = (method ?? 'GET').toUpperCase();
    const cls = ['GET','POST','PUT','DELETE','PATCH'].includes(m) ? `method-${m}` : 'method-OTHER';
    return `<span class="method-badge ${cls}">${escapeHtml(m)}</span>`;
}

function statusBadge(code) {
    const c = Number(code) || 0;
    let cls = 'status-ok';
    if (c >= 200 && c < 300) cls = 'status-2xx';
    else if (c >= 300 && c < 400) cls = 'status-3xx';
    else if (c >= 400 && c < 500) cls = 'status-4xx';
    else if (c >= 500)            cls = 'status-5xx';
    return `<span class="status-badge ${cls}">${c || '—'}</span>`;
}

function getOrCreateSessionId() {
    let sid = sessionStorage.getItem('nw_sid');
    if (!sid) { sid = crypto.randomUUID(); sessionStorage.setItem('nw_sid', sid); }
    return sid;
}

document.addEventListener('DOMContentLoaded', () => App.init());
