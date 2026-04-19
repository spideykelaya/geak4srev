import { S, $, emitPolygonSyncEvent, emitPlansSyncEvent, pxVecToM, px2m2 } from './state.js';
import { MEAS_COLOR, ANGLE_COLOR }  from './config.js';
import { fmtArea, fmtLength, shoelace } from './geo.js';
import { render }                  from './render.js';

const DEFAULT_POLYGON_LABEL = 'Flaeche';

let currentAreaTypeLabel = DEFAULT_POLYGON_LABEL;

// ── Clipboard (persists across plan switches) ─────────────────────────────────
let clipboard = null; // { type: 'polygon' | 'measurement', data: {...} }

function setClipboard(type, data) {
  clipboard = { type, data: JSON.parse(JSON.stringify(data)) };
  updatePasteBtn();
}

function updatePasteBtn() {
  const btn = $('paste-btn');
  if (!btn) return;
  btn.disabled = !clipboard;
  btn.title = clipboard
    ? (clipboard.type === 'polygon' ? 'Fläche einfügen' : 'Distanz einfügen')
    : 'Zwischenablage leer';
}

export function pasteFromClipboard() {
  if (!clipboard) return;
  const OFFSET = 30; // world-pixel nudge so paste doesn't land exactly on original
  if (clipboard.type === 'polygon') {
    const src = clipboard.data;
    const pts = src.points.map(p => ({ x: p.x + OFFSET, y: p.y + OFFSET }));
    const pixelArea = shoelace(pts);
    S.polygons.push({
      ...src,
      id: S.nextId++,
      label: createUniquePolygonLabel(src.label),
      points: pts,
      pixelArea,
      area: px2m2(pixelArea),
      labelOffset: null,
    });
    updateSidebar(); render();
    emitPolygonSyncEvent();
    emitPlansSyncEvent();
  } else if (clipboard.type === 'measurement') {
    const src = clipboard.data;
    S.measurements.push({
      id: S.nextMeasId++,
      pt1: { x: src.pt1.x + OFFSET, y: src.pt1.y + OFFSET },
      pt2: { x: src.pt2.x + OFFSET, y: src.pt2.y + OFFSET },
    });
    updateSidebar(); render();
  }
}

const POLYGON_PREFIXES = {
  'EBF':                   'EBF',
  'Dach gegen Aussenluft': 'DA',
  'Decke gegen unbeheizt': 'DU',
  'Wand gegen Aussenluft': 'WA',
  'Wand gegen Erdreich':   'WE',
  'Wand gegen unbeheizt':  'WU',
  'Fenster':               'FE',
  'Tür':                   'FE',
  'Boden gegen Erdreich':  'BE',
  'Boden gegen unbeheizt': 'BU',
  'Boden gegen aussen':    'BA',
};

const AREA_TYPE_COLORS = {
  'EBF':                   '#fb923c', // orange
  'Dach gegen Aussenluft': '#a78bfa', // violet
  'Decke gegen unbeheizt': '#a78bfa', // violet
  'Wand gegen Aussenluft': '#fbbf24', // gelb
  'Wand gegen Erdreich':   '#34d399', // grün
  'Wand gegen unbeheizt':  '#60a5fa', // blau
  'Fenster':               '#f472b6', // pink
  'Tür':                   '#f472b6', // pink
  'Boden gegen Erdreich':  '#34d399', // grün
  'Boden gegen unbeheizt': '#60a5fa', // blau
  'Boden gegen aussen':    '#fbbf24', // gelb
};

export function setCurrentAreaTypeLabel(label) {
  currentAreaTypeLabel = (label && label.trim()) || DEFAULT_POLYGON_LABEL;
}

export function colorForCurrentAreaType() {
  return AREA_TYPE_COLORS[currentAreaTypeLabel] ?? '#8888aa';
}

export function getCurrentAreaTypeLabel() {
  return currentAreaTypeLabel;
}

// Callback registered by main.js so the sidebar can trigger plan switches
let _switchPlanHandler = null;
export function setSwitchPlanHandler(fn) { _switchPlanHandler = fn; }

export function createUniquePolygonLabel(rawLabel = DEFAULT_POLYGON_LABEL, currentPoly = null) {
  const base = (rawLabel || '').trim() || DEFAULT_POLYGON_LABEL;
  const used = new Set(
    S.polygons
      .filter(poly => poly !== currentPoly)
      .map(poly => (poly.label || '').trim())
      .filter(Boolean)
  );

  if (!used.has(base)) return base;

  let idx = 2;
  while (used.has(`${base} ${idx}`)) idx += 1;
  return `${base} ${idx}`;
}

export function nextPolygonLabel() {
  const prefix = POLYGON_PREFIXES[currentAreaTypeLabel] ?? currentAreaTypeLabel;
  const allPolygons = S.plans.flatMap(plan =>
    plan.id === S.activePlanId ? S.polygons : plan.polygons
  );
  const used = new Set(allPolygons.map(p => (p.label || '').trim()));
  // EBF: no number (just "EBF"), then "EBF2", "EBF3" if taken
  if (prefix === 'EBF') {
    if (!used.has('EBF')) return 'EBF';
    let idx = 2;
    while (used.has(`EBF${idx}`)) idx++;
    return `EBF${idx}`;
  }
  let idx = 1;
  while (used.has(`${prefix}${idx}`)) idx++;
  return `${prefix}${idx}`;
}

export function updateSidebar() {
  updatePlanList();
  updatePolygonList();
  updateMeasurementList();
  updateAngleList();
  updatePasteBtn();
}

// ── Plans ─────────────────────────────────────────────────────────────────────
function updatePlanList() {
  const section = $('plans-section');
  const listEl  = $('plan-list');
  if (!section || !listEl) return;

  if (!S.plans.length) { section.style.display = 'none'; return; }
  section.style.display = 'block';
  listEl.innerHTML = '';

  S.plans.forEach(plan => {
    const li = document.createElement('li');
    li.className = 'polygon-item plan-item';

    const icon = document.createElement('span');
    icon.className = 'plan-icon';
    icon.textContent = '📄';

    if (plan.id === S.activePlanId) {
      // ── Active plan: editable label + "aktiv" badge + delete btn ──
      li.classList.add('plan-item--active');

      const lbl = document.createElement('span');
      lbl.className = 'polygon-label plan-label-editable';
      lbl.contentEditable = 'true';
      lbl.spellcheck = false;
      lbl.title = 'Klicken zum Umbenennen';
      lbl.textContent = plan.label;
      lbl.addEventListener('keydown', e => {
        if (e.key === 'Enter') { e.preventDefault(); lbl.blur(); }
        e.stopPropagation();
      });
      lbl.addEventListener('blur', () => {
        const trimmed = lbl.textContent.trim();
        if (trimmed) plan.label = trimmed;
        lbl.textContent = plan.label;
        emitPlansSyncEvent();
      });

      const badge = document.createElement('span');
      badge.className = 'plan-active-badge';
      badge.textContent = 'aktiv';

      const delBtn = document.createElement('button');
      delBtn.className = 'btn-delete plan-delete';
      delBtn.textContent = '×';
      delBtn.title = 'Plan löschen';
      delBtn.onclick = (e) => {
        e.stopPropagation();
        showPlanDeleteConfirm(plan.id, plan.label);
      };

      li.append(icon, lbl, badge, delBtn);
    } else {
      // ── Inactive plan: label + load button + delete button ──
      const lbl = document.createElement('span');
      lbl.className = 'polygon-label';
      lbl.textContent = plan.label;

      const loadBtn = document.createElement('button');
      loadBtn.className = 'btn-load-plan';
      loadBtn.title = 'Plan öffnen';
      loadBtn.innerHTML = '▶';
      loadBtn.onclick = () => { if (_switchPlanHandler) _switchPlanHandler(plan.id); };

      const delBtn = document.createElement('button');
      delBtn.className = 'btn-delete plan-delete';
      delBtn.textContent = '×';
      delBtn.title = 'Plan löschen';
      delBtn.onclick = (e) => {
        e.stopPropagation();
        showPlanDeleteConfirm(plan.id, plan.label);
      };

      li.append(icon, lbl, loadBtn, delBtn);
    }

    listEl.appendChild(li);
  });
}

// ── Slope correction ──────────────────────────────────────────────────────────
const NEIGUNG_TYPES = new Set(['Dach gegen Aussenluft', 'Wand gegen Aussenluft']);

function getDisplayArea(poly) {
  if (poly.area === null || poly.area === undefined) return poly.area ?? null;
  const inc = poly.inclination || 0;
  if (inc <= 0) return poly.area;
  return poly.area / Math.cos(inc * Math.PI / 180);
}

function createInclinationInput(poly) {
  const wrap = document.createElement('span');
  wrap.style.cssText = 'display:inline-flex;align-items:center;gap:1px;font-size:0.8em;color:var(--text-2);white-space:nowrap';
  wrap.title = 'Neigung (0°=flach, 45°=steiles Dach)';

  const sym = document.createElement('span');
  sym.textContent = '∠';

  const inp = document.createElement('input');
  inp.type = 'text';
  inp.value = poly.inclination || 0;
  inp.style.cssText = 'width:3em;background:transparent;border:none;border-bottom:1px solid var(--border-hi);color:var(--text-1);font-size:inherit;text-align:right;padding:0 2px;';

  const unit = document.createElement('span');
  unit.textContent = '°';

  inp.addEventListener('keydown', e => e.stopPropagation());
  inp.addEventListener('change', () => {
    const angle = Math.min(89, Math.max(0, parseFloat(inp.value) || 0));
    poly.inclination = angle;
    inp.value = angle;
    updateSidebar();
    emitPolygonSyncEvent();
    emitPlansSyncEvent();
  });

  wrap.append(sym, inp, unit);
  return wrap;
}

// ── Polygons ──────────────────────────────────────────────────────────────────
function updatePolygonList() {
  const section = $('polygons-section');
  const listEl  = $('polygon-list');
  const totalEl = $('total-surface');

  if (!S.polygons.length) { section.style.display = 'none'; return; }
  section.style.display = 'block';
  listEl.innerHTML = '';

  let total = 0, hasScale = false;

  S.polygons.forEach(poly => {
    const li = document.createElement('li');
    li.className = 'polygon-item';
    li.dataset.polygonLabel = poly.label;

    const cdot = document.createElement('span');
    cdot.className = 'color-dot';
    cdot.style.background = poly.color;

    // Inline-editable label
    const lbl = document.createElement('span');
    lbl.className = 'polygon-label';
    lbl.contentEditable = 'true';
    lbl.spellcheck = false;
    lbl.title = 'Klicken zum Umbenennen';
    lbl.textContent = poly.label;
    lbl.addEventListener('keydown', e => {
      if (e.key === 'Enter') { e.preventDefault(); lbl.blur(); }
      e.stopPropagation(); // prevent canvas shortcuts while typing
    });
    lbl.addEventListener('blur', () => {
      const oldLabel = poly.label;
      poly.label = createUniquePolygonLabel(lbl.textContent, poly);
      li.dataset.polygonLabel = poly.label;
      lbl.textContent = poly.label;
      if (oldLabel !== poly.label) {
        window.dispatchEvent(new CustomEvent('geak:ebf-polygon-renamed', {
          detail: { oldLabel, newLabel: poly.label }
        }));
      }
      render();
      emitPolygonSyncEvent();
      emitPlansSyncEvent();
    });

    const displayArea = getDisplayArea(poly);
    const areaEl = document.createElement('span');
    areaEl.className = 'polygon-area';
    areaEl.textContent = fmtArea(displayArea);

    const copy = document.createElement('button');
    copy.className = 'btn-copy'; copy.textContent = '\u29c9'; copy.title = 'Kopieren';
    copy.onclick = () => setClipboard('polygon', poly);

    const del = document.createElement('button');
    del.className = 'btn-delete'; del.textContent = '\u00d7'; del.title = 'Löschen';
    del.onclick = () => {
      S.polygons = S.polygons.filter(p => p.label !== poly.label);
      updateSidebar(); render();
      emitPolygonSyncEvent();
      emitPlansSyncEvent();
    };

    if (NEIGUNG_TYPES.has(poly.areaType)) {
      li.append(cdot, lbl, createInclinationInput(poly), areaEl, copy, del);
    } else {
      li.append(cdot, lbl, areaEl, copy, del);
    }
    listEl.appendChild(li);
    if (displayArea !== null) { total += displayArea; hasScale = true; }
  });

  if (hasScale) {
    totalEl.textContent = 'Total : ' + fmtArea(total);
    totalEl.style.color = '#a78bfa'; totalEl.style.fontSize = '';
  } else {
    totalEl.textContent = "Kalibrieren Sie den Maßstab, um die Flächen zu sehen";
    totalEl.style.color = '#94a3b8'; totalEl.style.fontSize = '12px';
  }
}

// ── Measurements ──────────────────────────────────────────────────────────────
function updateMeasurementList() {
  const section = $('measurements-section');
  const listEl  = $('measurement-list');

  if (!S.measurements.length) { section.style.display = 'none'; return; }
  section.style.display = 'block';
  listEl.innerHTML = '';

  S.measurements.forEach(meas => {
    const li = document.createElement('li');
    li.className = 'polygon-item';

    const icon = document.createElement('span');
    icon.className = 'color-dot'; icon.style.background = MEAS_COLOR;

    const lbl = document.createElement('span');
    lbl.className = 'polygon-label'; lbl.textContent = 'Mesure ' + meas.id;

    const dx = meas.pt2.x - meas.pt1.x, dy = meas.pt2.y - meas.pt1.y;
    const realLen = pxVecToM(dx, dy);
    const val = document.createElement('span');
    val.className = 'polygon-area';
    val.textContent = realLen !== null ? fmtLength(realLen) : Math.hypot(dx, dy).toFixed(1) + ' px';

    const copy = document.createElement('button');
    copy.className = 'btn-copy'; copy.textContent = '\u29c9'; copy.title = 'Kopieren';
    copy.onclick = () => setClipboard('measurement', meas);

    const del = document.createElement('button');
    del.className = 'btn-delete'; del.textContent = '\u00d7';
    del.onclick = () => {
      S.measurements = S.measurements.filter(m => m.id !== meas.id);
      updateSidebar(); render();
    };

    li.append(icon, lbl, val, copy, del);
    listEl.appendChild(li);
  });
}

// ── Angles ────────────────────────────────────────────────────────────────────
function updateAngleList() {
  const section = $('angles-section');
  const listEl  = $('angle-list');
  if (!section || !listEl) return;

  if (!S.angles.length) { section.style.display = 'none'; return; }
  section.style.display = 'block';
  listEl.innerHTML = '';

  S.angles.forEach(ang => {
    const li = document.createElement('li');
    li.className = 'polygon-item';

    const icon = document.createElement('span');
    icon.className = 'color-dot'; icon.style.background = ANGLE_COLOR;

    const lbl = document.createElement('span');
    lbl.className = 'polygon-label'; lbl.textContent = 'Winkel ' + ang.id;

    const ax = ang.pt1.x - ang.vertex.x, ay = ang.pt1.y - ang.vertex.y;
    const bx = ang.pt2.x - ang.vertex.x, by = ang.pt2.y - ang.vertex.y;
    const deg = Math.atan2(Math.abs(ax * by - ay * bx), ax * bx + ay * by) * 180 / Math.PI;
    const val = document.createElement('span');
    val.className = 'polygon-area';
    val.textContent = deg.toFixed(1) + '°';

    const del = document.createElement('button');
    del.className = 'btn-delete'; del.textContent = '\u00d7';
    del.onclick = () => {
      S.angles = S.angles.filter(a => a.id !== ang.id);
      updateSidebar(); render();
    };

    li.append(icon, lbl, val, del);
    listEl.appendChild(li);
  });
}

// ── Plan deletion ──────────────────────────────────────────────────────────
function showPlanDeleteConfirm(planId, planLabel) {
  const message = `Plan "${planLabel}" wirklich löschen? Diese Aktion ist nicht umkehrbar.`;

  if (!confirm(message)) return;

  // Dispatch event to main.js for handling
  window.dispatchEvent(new CustomEvent('geak:ebf-delete-plan', {
    detail: { planId, planLabel }
  }));
}

