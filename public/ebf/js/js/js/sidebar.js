import { S }                       from './state.js';
import { MEAS_COLOR }              from './config.js';
import { dist, fmtArea, fmtLength } from './geo.js';
import { render }                  from './render.js';

export function updateSidebar() {
  updatePolygonList();
  updateMeasurementList();
}

// ── Polygons ──────────────────────────────────────────────────────────────────
function updatePolygonList() {
  const section = document.getElementById('polygons-section');
  const listEl  = document.getElementById('polygon-list');
  const totalEl = document.getElementById('total-surface');

  if (!S.polygons.length) { section.style.display = 'none'; return; }
  section.style.display = 'block';
  listEl.innerHTML = '';

  let total = 0, hasScale = false;

  S.polygons.forEach(poly => {
    const li = document.createElement('li');
    li.className = 'polygon-item';

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
      poly.label = lbl.textContent.trim() || poly.label;
      lbl.textContent = poly.label;
      render();
    });

    const areaEl = document.createElement('span');
    areaEl.className = 'polygon-area';
    areaEl.textContent = fmtArea(poly.area);

    const del = document.createElement('button');
    del.className = 'btn-delete'; del.textContent = '\u00d7'; del.title = 'Supprimer';
    del.onclick = () => {
      S.polygons = S.polygons.filter(p => p.id !== poly.id);
      updateSidebar(); render();
    };

    li.append(cdot, lbl, areaEl, del);
    listEl.appendChild(li);
    if (poly.area !== null) { total += poly.area; hasScale = true; }
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
  const section = document.getElementById('measurements-section');
  const listEl  = document.getElementById('measurement-list');

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

    const len = dist(meas.pt1, meas.pt2);
    const val = document.createElement('span');
    val.className = 'polygon-area';
    val.textContent = S.scale ? fmtLength(len * S.scale) : len.toFixed(1) + ' px';

    const del = document.createElement('button');
    del.className = 'btn-delete'; del.textContent = '\u00d7';
    del.onclick = () => {
      S.measurements = S.measurements.filter(m => m.id !== meas.id);
      updateSidebar(); render();
    };

    li.append(icon, lbl, val, del);
    listEl.appendChild(li);
  });
}
