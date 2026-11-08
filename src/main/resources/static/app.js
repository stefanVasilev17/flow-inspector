'use strict';

const state = { traces: [], selectedTraceId: null, selectedNodeId: null, snapshot: null };
const elements = {
  demo: document.getElementById('demo-button'),
  list: document.getElementById('trace-list'),
  search: document.getElementById('search'),
  count: document.getElementById('trace-count'),
  title: document.getElementById('trace-title'),
  summary: document.getElementById('trace-summary'),
  graph: document.getElementById('graph-viewport'),
  banner: document.getElementById('failure-banner'),
  timeline: document.getElementById('timeline'),
  observations: document.getElementById('observation-count'),
  details: document.getElementById('details'),
  live: document.getElementById('live-status')
};

function el(tag, cls, text) {
  const result = document.createElement(tag);
  if (cls) result.className = cls;
  if (text !== undefined && text !== null) result.textContent = String(text);
  return result;
}
function isoTime(date) {
  return new Date(date).toLocaleTimeString(undefined, { hour12: false, hour: '2-digit',
    minute: '2-digit', second: '2-digit', fractionalSecondDigits: 3 });
}
function statusClass(status) { return String(status).toLowerCase(); }
function badge(status) { return el('span', `status status-${statusClass(status)}`, status); }
function shortId(traceId) { return traceId.length > 22 ? `${traceId.slice(0, 19)}...` : traceId; }
async function getJson(path, options) {
  const response = await fetch(path, options);
  if (!response.ok) throw new Error(`HTTP ${response.status} — ${await response.text()}`);
  return response.json();
}
function showError(message) {
  elements.graph.replaceChildren(el('div', 'graph-placeholder', `Request failed: ${message}`));
}
function renderTraceList() {
  const q = elements.search.value.trim().toLowerCase();
  const matching = state.traces.filter(t => t.traceId.toLowerCase().includes(q)
    || t.services.some(service => service.toLowerCase().includes(q)));
  elements.count.textContent = String(state.traces.length);
  elements.list.replaceChildren();
  if (matching.length === 0) {
    elements.list.append(el('p', 'empty-state', q ? 'No matching traces.' : 'No traces recorded yet.'));
    return;
  }
  for (const trace of matching) {
    const item = el('button', `trace-item ${trace.traceId === state.selectedTraceId ? 'selected' : ''}`);
    item.type = 'button';
    const top = el('div', 'trace-item-top');
    top.append(el('span', 'trace-id', shortId(trace.traceId)), badge(trace.status));
    item.append(top, el('div', 'trace-services', trace.services.join(' · ')),
      el('div', 'trace-time', `${isoTime(trace.updatedAt)} · ${trace.observationCount} state changes`));
    item.addEventListener('click', () => selectTrace(trace.traceId));
    elements.list.append(item);
  }
}
function getLayout(snapshot) {
  const nodeById = new Map(snapshot.nodes.map(node => [node.id, node]));
  const depths = new Map();
  function depth(nodeId, visiting = new Set()) {
    if (depths.has(nodeId)) return depths.get(nodeId);
    if (visiting.has(nodeId)) return 0; // Bad/cyclic input must not crash the renderer.
    visiting.add(nodeId);
    const parentId = nodeById.get(nodeId)?.parentId;
    const value = parentId && nodeById.has(parentId) ? Math.min(7, depth(parentId, visiting) + 1) : 0;
    visiting.delete(nodeId);
    depths.set(nodeId, value);
    return value;
  }
  for (const node of snapshot.nodes) depth(node.id);
  const buckets = new Map();
  for (const node of snapshot.nodes) {
    const d = depths.get(node.id);
    if (!buckets.has(d)) buckets.set(d, []);
    buckets.get(d).push(node);
  }
  const positions = new Map();
  let maxRows = 1;
  for (const [d, nodes] of buckets) {
    maxRows = Math.max(maxRows, nodes.length);
    nodes.forEach((node, i) => positions.set(node.id, { x: 35 + d * 288, y: 37 + i * 154 }));
  }
  return {
    positions,
    width: Math.max(680, 35 + (Math.max(...depths.values(), 0) + 1) * 288 + 25),
    height: Math.max(295, 37 + maxRows * 154 + 25)
  };
}
function renderGraph(snapshot) {
  elements.graph.replaceChildren();
  if (!snapshot.nodes.length) return;
  const layout = getLayout(snapshot);
  const canvas = el('div', 'graph-canvas');
  canvas.style.width = `${layout.width}px`;
  canvas.style.height = `${layout.height}px`;
  const ns = 'http://www.w3.org/2000/svg';
  const svg = document.createElementNS(ns, 'svg');
  svg.setAttribute('class', 'graph-links');
  svg.setAttribute('width', String(layout.width));
  svg.setAttribute('height', String(layout.height));
  const defs = document.createElementNS(ns, 'defs');
  const marker = document.createElementNS(ns, 'marker');
  Object.entries({ id: 'arrow', viewBox: '0 0 10 10', refX: '9', refY: '5',
    markerWidth: '5', markerHeight: '5', orient: 'auto-start-reverse' })
    .forEach(([key, value]) => marker.setAttribute(key, value));
  const arrow = document.createElementNS(ns, 'path');
  arrow.setAttribute('d', 'M 0 0 L 10 5 L 0 10 z');
  marker.append(arrow);
  defs.append(marker);
  svg.append(defs);
  for (const edge of snapshot.edges) {
    const parent = layout.positions.get(edge.source);
    const child = layout.positions.get(edge.target);
    if (!parent || !child) continue;
    const startX = parent.x + 230;
    const startY = parent.y + 59;
    const endX = child.x;
    const endY = child.y + 59;
    const middle = (endX - startX) / 2;
    const path = document.createElementNS(ns, 'path');
    path.setAttribute('d', `M ${startX} ${startY} C ${startX + middle} ${startY}, ${endX - middle} ${endY}, ${endX - 8} ${endY}`);
    path.setAttribute('marker-end', 'url(#arrow)');
    svg.append(path);
  }
  canvas.append(svg);
  for (const node of snapshot.nodes) {
    const position = layout.positions.get(node.id);
    const box = el('button', `flow-node ${statusClass(node.status)} ${node.id === state.selectedNodeId ? 'selected' : ''}`);
    box.type = 'button';
    box.style.left = `${position.x}px`;
    box.style.top = `${position.y}px`;
    box.append(el('div', 'node-service', node.service), el('div', 'node-operation', node.operation));
    const bottom = el('div', 'node-bottom');
    const duration = Math.max(0, new Date(node.lastSeen) - new Date(node.firstSeen));
    bottom.append(badge(node.status), el('span', 'node-duration', `${duration}ms`));
    box.append(bottom);
    box.title = node.id;
    box.addEventListener('click', () => selectNode(node.id));
    canvas.append(box);
  }
  elements.graph.append(canvas);
}
function renderBanner(snapshot) {
  elements.banner.replaceChildren();
  if (!snapshot.firstObservedFailure) {
    elements.banner.classList.add('hidden');
    return;
  }
  const failure = snapshot.firstObservedFailure;
  elements.banner.classList.remove('hidden');
  elements.banner.append(el('strong', '', 'FIRST OBSERVED FAILURE · '),
    document.createTextNode(`${failure.service} / ${failure.operation}`));
  if (failure.exceptionType) elements.banner.append(document.createTextNode(` · ${failure.exceptionType}`));
}
function renderTimeline(snapshot) {
  elements.observations.textContent = `${snapshot.timeline.length} observations`;
  elements.timeline.replaceChildren();
  for (const observation of snapshot.timeline) {
    const row = el('button', `timeline-row ${observation.eventId === state.selectedNodeId ? 'selected' : ''}`);
    row.type = 'button';
    row.append(el('span', 'timeline-time', isoTime(observation.occurredAt)),
      el('span', `timeline-dot ${statusClass(observation.status)}`),
      el('span', 'timeline-event', `${observation.operation} · ${observation.status}`),
      el('span', 'timeline-service', observation.service));
    row.addEventListener('click', () => selectNode(observation.eventId));
    elements.timeline.append(row);
  }
}
function addDetailSection(label, value, source = false) {
  elements.details.append(el('div', 'detail-label', label), el('div', source ? 'detail-value source-link' : 'detail-value', value ?? 'Not recorded'));
}
function renderDetails(snapshot) {
  elements.details.replaceChildren();
  const node = snapshot.nodes.find(item => item.id === state.selectedNodeId);
  if (!node) {
    const placeholder = el('div', 'details-empty');
    placeholder.append(el('strong', '', 'Select a node'),
      el('p', '', 'Inspect the state changes and source hints of an operation.'));
    elements.details.append(placeholder);
    return;
  }
  elements.details.append(el('div', 'service-label', node.service), el('h3', '', node.operation), badge(node.status));
  addDetailSection('EVENT ID', node.id, true);
  addDetailSection('PARENT EVENT', node.parentId ?? 'Root operation');
  const failing = node.transitions.find(item => item.status === 'FAILED');
  const source = failing ?? node.transitions.at(-1);
  if (source.exceptionType) addDetailSection('EXCEPTION', source.exceptionType);
  if (source.message) addDetailSection('LATEST / FAILURE MESSAGE', source.message);
  addDetailSection('JAVA SOURCE HINT', source.sourceClass ? `${source.sourceClass}${source.sourceLine ? `:${source.sourceLine}` : ''}` : 'Not instrumented', true);
  elements.details.append(el('div', 'detail-label', 'STATE HISTORY'));
  for (const transition of node.transitions) {
    const row = el('div', `detail-event ${statusClass(transition.status)}`);
    row.append(el('strong', '', transition.status), el('div', 'when', isoTime(transition.occurredAt)));
    if (transition.message) row.append(el('div', 'message', transition.message));
    elements.details.append(row);
  }
}
function renderSelectedTrace() {
  const snapshot = state.snapshot;
  if (!snapshot) return;
  elements.title.textContent = snapshot.traceId;
  elements.summary.textContent = `${snapshot.nodes.length} operations · ${snapshot.status}`;
  renderBanner(snapshot);
  renderGraph(snapshot);
  renderTimeline(snapshot);
  renderDetails(snapshot);
}
function selectNode(eventId) {
  state.selectedNodeId = eventId;
  renderGraph(state.snapshot);
  renderTimeline(state.snapshot);
  renderDetails(state.snapshot);
}
async function selectTrace(traceId) {
  state.selectedTraceId = traceId;
  try {
    const snapshot = await getJson(`/api/v1/traces/${encodeURIComponent(traceId)}`);
    if (state.selectedTraceId !== traceId) return;
    state.snapshot = snapshot;
    if (!snapshot.nodes.some(node => node.id === state.selectedNodeId)) {
      state.selectedNodeId = snapshot.firstObservedFailure?.eventId ?? snapshot.nodes[0]?.id ?? null;
    }
    renderTraceList();
    renderSelectedTrace();
  } catch (error) {
    showError(error.message);
  }
}
async function refresh() {
  try {
    state.traces = await getJson('/api/v1/traces');
    if (!state.selectedTraceId && state.traces.length) state.selectedTraceId = state.traces[0].traceId;
    if (state.selectedTraceId && !state.traces.some(t => t.traceId === state.selectedTraceId)) {
      state.selectedTraceId = state.traces[0]?.traceId ?? null;
      state.selectedNodeId = null;
    }
    renderTraceList();
    if (state.selectedTraceId) await selectTrace(state.selectedTraceId);
  } catch (error) {
    showError(error.message);
  }
}
let refreshTimer;
function scheduleRefresh() {
  clearTimeout(refreshTimer);
  refreshTimer = setTimeout(refresh, 180);
}
function connectStream() {
  const events = new EventSource('/api/v1/stream');
  events.addEventListener('connected', () => {
    elements.live.classList.add('online');
    elements.live.textContent = 'Live updates';
  });
  events.addEventListener('observation', scheduleRefresh);
  events.onerror = () => {
    elements.live.classList.remove('online');
    elements.live.textContent = 'Reconnecting…';
  };
}
elements.demo.addEventListener('click', async () => {
  elements.demo.disabled = true;
  try {
    const result = await getJson('/api/v1/demo', { method: 'POST' });
    state.selectedTraceId = result.traceId;
    state.selectedNodeId = null;
    await refresh();
  } catch (error) {
    showError(error.message);
  } finally {
    elements.demo.disabled = false;
  }
});
elements.search.addEventListener('input', renderTraceList);
connectStream();
refresh();
