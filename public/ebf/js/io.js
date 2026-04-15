import { S }                         from './state.js';
import { PDFJS_WORKER }              from './config.js';
import { dist, fmtArea, fmtLength, esc } from './geo.js';

function normalizePolygonLabels(polygons) {
  const used = new Set();
  return polygons.map(poly => {
    const base = (poly.label || '').trim() || 'Flaeche';
    let label = base;
    let idx = 2;
    while (used.has(label)) {
      label = `${base} ${idx}`;
      idx += 1;
    }
    used.add(label);
    return { ...poly, label };
  });
}

// ── File loading ──────────────────────────────────────────────────────────────

/** Render a single PDF page into S (sets S.image / S.imageW / S.imageH / S.imageDataUrl). */
export async function loadPDF(file) {
  const pages = await loadPDFPages(file);
  if (pages.length === 0) throw new Error('Leeres PDF');
  const p = pages[0];
  S.imageW = p.imageW; S.imageH = p.imageH;
  S.imageDataUrl = p.imageDataUrl;
  S.image = p.offscreen;
}

/**
 * Render ALL pages of a PDF.
 * Returns an array of { offscreen, imageDataUrl, imageW, imageH } – one entry per page.
 * Use the actual canvas pixel dimensions (integers) rather than raw viewport floats
 * (e.g. 1190.56) so S.imageW is always an integer and passes Scala's Int decoder.
 */
export async function loadPDFPages(file) {
  const url = URL.createObjectURL(file);
  try {
    const pdf    = await pdfjsLib.getDocument({ url, workerSrc: PDFJS_WORKER }).promise;
    const result = [];
    for (let i = 1; i <= pdf.numPages; i++) {
      const page     = await pdf.getPage(i);
      const viewport = page.getViewport({ scale: 2 });
      const off      = document.createElement('canvas');
      off.width  = viewport.width;
      off.height = viewport.height;
      await page.render({ canvasContext: off.getContext('2d'), viewport }).promise;
      result.push({
        offscreen:    off,
        imageDataUrl: off.toDataURL('image/jpeg', 0.85),
        imageW:       off.width,
        imageH:       off.height,
      });
    }
    return result;
  } finally { URL.revokeObjectURL(url); }
}

export async function loadImg(file) {
  const url = URL.createObjectURL(file);
  await new Promise((resolve, reject) => {
    const img = new Image();
    img.onload  = () => {
      S.image = img; S.imageW = img.naturalWidth; S.imageH = img.naturalHeight;
      // Capture as data URL via offscreen canvas
      const off = document.createElement('canvas');
      off.width = img.naturalWidth; off.height = img.naturalHeight;
      off.getContext('2d').drawImage(img, 0, 0);
      S.imageDataUrl = off.toDataURL('image/jpeg', 0.85);
      URL.revokeObjectURL(url);
      resolve();
    };
    img.onerror = () => { URL.revokeObjectURL(url); reject(new Error('Image invalide')); };
    img.src = url;
  });
}

/** Restore a plan image from a previously captured data URL. */
export async function loadImageFromDataUrl(dataUrl) {
  if (!dataUrl) return;
  await new Promise((resolve, reject) => {
    const img = new Image();
    img.onload = () => {
      const off = document.createElement('canvas');
      off.width = img.naturalWidth; off.height = img.naturalHeight;
      off.getContext('2d').drawImage(img, 0, 0);
      S.image = off; S.imageW = img.naturalWidth; S.imageH = img.naturalHeight;
      S.imageDataUrl = dataUrl;
      resolve();
    };
    img.onerror = () => reject(new Error('Could not restore plan image'));
    img.src = dataUrl;
  });
}

// ── Export / Import ───────────────────────────────────────────────────────────
export function exportData() {
  const payload = { version: 1, scale: S.scale, nextId: S.nextId, nextMeasId: S.nextMeasId, polygons: S.polygons, measurements: S.measurements };
  const url = URL.createObjectURL(new Blob([JSON.stringify(payload, null, 2)], { type: 'application/json' }));
  Object.assign(document.createElement('a'), { href: url, download: 'flaechen-berechnung.json' }).click();
  URL.revokeObjectURL(url);
}

export function exportExcel() {
  if (!S.scale || S.polygons.length === 0) {
    alert('Keine kalibrierten Polygone zum Exportieren vorhanden.');
    return;
  }
  
  const totalArea = S.polygons.reduce((sum, poly) => sum + poly.area, 0);
  const csv = `Gesamtfläche\n${totalArea}`;
  
  const url = URL.createObjectURL(new Blob([csv], { type: 'text/csv;charset=utf-8;' }));
  Object.assign(document.createElement('a'), { href: url, download: 'gesamtflaeche.csv' }).click();
  URL.revokeObjectURL(url);
}

export function exportXML() {
  if (!S.scale || S.polygons.length === 0) {
    alert('Keine kalibrierten Polygone zum Exportieren vorhanden.');
    return;
  }
  
  const totalArea = S.polygons.reduce((sum, poly) => sum + poly.area, 0);
  let xml = '<?xml version="1.0" encoding="UTF-8"?>\n';
  xml += '<gesamtflaeche>\n';
  xml += `  <wert>${totalArea}</wert>\n`;
  xml += `  <einheit>m²</einheit>\n`;
  xml += `  <formatiert>${fmtArea(totalArea)}</formatiert>\n`;
  xml += '</gesamtflaeche>';
  
  const url = URL.createObjectURL(new Blob([xml], { type: 'application/xml' }));
  Object.assign(document.createElement('a'), { href: url, download: 'gesamtflaeche.xml' }).click();
  URL.revokeObjectURL(url);
}

// Returns true on success, false on failure
export async function importData(file) {
  try {
    const data = JSON.parse(await file.text());
    if (data.version !== 1) { alert('Format de fichier incompatible.'); return false; }
    S.scale        = data.scale        ?? null;
    S.polygons     = normalizePolygonLabels(data.polygons ?? []);
    S.measurements = data.measurements ?? [];
    S.nextId       = data.nextId       ?? S.nextId;
    S.nextMeasId   = data.nextMeasId   ?? S.nextMeasId;
    return true;
  } catch (err) {
    alert('Import fehlgeschlagen: ' + err.message);
    return false;
  }
}

// ── Print ─────────────────────────────────────────────────────────────────────

/** Draw one plan's image + polygons + measurements onto an offscreen canvas and return dataURL. */
function renderPlanCanvas(imageEl, imageW, imageH, polygons, measurements, scale) {
  const MAX = 1800;
  const sc  = Math.min(MAX / imageW, MAX / imageH, 1);
  const w   = Math.round(imageW * sc);
  const h   = Math.round(imageH * sc);

  const off = document.createElement('canvas');
  off.width = w; off.height = h;
  const c = off.getContext('2d');

  c.drawImage(imageEl, 0, 0, w, h);

  const px = p => [p.x * sc, p.y * sc];

  const pill = (ctx, x, y, tw, fsz) => {
    const pad = 6, rh = fsz * 1.5;
    ctx.beginPath();
    const rx = x - tw/2 - pad, ry = y - rh/2, rw = tw + pad*2, r = 4;
    ctx.moveTo(rx+r, ry); ctx.lineTo(rx+rw-r, ry);
    ctx.quadraticCurveTo(rx+rw, ry, rx+rw, ry+r);
    ctx.lineTo(rx+rw, ry+rh-r); ctx.quadraticCurveTo(rx+rw, ry+rh, rx+rw-r, ry+rh);
    ctx.lineTo(rx+r, ry+rh); ctx.quadraticCurveTo(rx, ry+rh, rx, ry+rh-r);
    ctx.lineTo(rx, ry+r); ctx.quadraticCurveTo(rx, ry, rx+r, ry);
    ctx.closePath();
  };

  polygons.forEach(({ points, color, area, label }) => {
    if (points.length < 2) return;
    const spts = points.map(p => px(p));
    c.beginPath();
    c.moveTo(...spts[0]); spts.slice(1).forEach(p => c.lineTo(...p)); c.closePath();
    c.fillStyle = color + '50'; c.fill();
    c.strokeStyle = 'rgba(255,255,255,0.7)'; c.lineWidth = 3; c.stroke();
    c.strokeStyle = color; c.lineWidth = 2; c.stroke();
    spts.forEach(([x, y]) => { c.beginPath(); c.arc(x, y, 4, 0, Math.PI*2); c.fillStyle = color; c.fill(); });

    let cx = 0, cy = 0;
    points.forEach(p => { cx += p.x; cy += p.y; });
    cx = cx / points.length * sc; cy = cy / points.length * sc;
    const titleLbl = (label || '').trim();
    const areaLbl = fmtArea(area);
    const fsz = 14;
    c.font = `bold ${fsz}px system-ui`; c.textAlign = 'center'; c.textBaseline = 'middle';
    const titleW = titleLbl ? c.measureText(titleLbl).width : 0;
    const areaW = c.measureText(areaLbl).width;
    const tw = Math.max(titleW, areaW);
    const y = titleLbl ? cy + (fsz * 0.25) : cy;
    c.fillStyle = 'rgba(0,0,0,0.65)'; pill(c, cx, y, tw, fsz * (titleLbl ? 2 : 1)); c.fill();
    c.fillStyle = '#fff';
    if (titleLbl) {
      c.fillText(titleLbl, cx, cy - (fsz * 0.45));
      c.fillText(areaLbl, cx, cy + (fsz * 0.7));
    } else {
      c.fillText(areaLbl, cx, cy);
    }
  });

  measurements.forEach(({ pt1, pt2 }) => {
    const [ax, ay] = px(pt1), [bx, by] = px(pt2);
    c.strokeStyle = '#fbbf24'; c.lineWidth = 1.5; c.setLineDash([6, 4]);
    c.beginPath(); c.moveTo(ax, ay); c.lineTo(bx, by); c.stroke(); c.setLineDash([]);
    [[ax,ay],[bx,by]].forEach(([x,y]) => { c.beginPath(); c.arc(x,y,3,0,Math.PI*2); c.fillStyle='#fbbf24'; c.fill(); });
    const mx = (ax+bx)/2, my = (ay+by)/2;
    const len = dist(pt1, pt2);
    const lbl = scale ? fmtLength(len * scale) : len.toFixed(1) + ' px', fsz = 12;
    c.font = `bold ${fsz}px system-ui`; c.textAlign = 'center'; c.textBaseline = 'middle';
    const tw = c.measureText(lbl).width;
    c.fillStyle = 'rgba(0,0,0,0.7)'; pill(c, mx, my, tw, fsz); c.fill();
    c.fillStyle = '#fbbf24'; c.fillText(lbl, mx, my);
  });

  return off.toDataURL('image/png');
}

/** Load an image element from a dataURL, resolve with the element. */
function loadImageEl(dataUrl) {
  return new Promise((resolve, reject) => {
    const img = new Image();
    img.onload = () => resolve(img);
    img.onerror = () => reject(new Error('Bild konnte nicht geladen werden'));
    img.src = dataUrl;
  });
}

/** Generate the multi-plan HTML report and open it in a new tab. */
async function generateMultiPlanPDF(selectedIds) {
  // Build list of plan descriptors to include
  const planEntries = [];
  for (const plan of S.plans) {
    if (!selectedIds.has(plan.id)) continue;
    const isActive = plan.id === S.activePlanId;
    const polygons = isActive ? S.polygons : (plan.polygons || []);
    const measurements = isActive ? S.measurements : (plan.measurements || []);
    const scale = isActive ? S.scale : (plan.scale ?? null);
    const imageDataUrl = isActive ? S.imageDataUrl : plan.imageDataUrl;
    const imageW = isActive ? S.imageW : plan.imageW;
    const imageH = isActive ? S.imageH : plan.imageH;
    let imageEl;
    try {
      imageEl = isActive ? S.image : await loadImageEl(imageDataUrl);
    } catch (_) {
      imageEl = null;
    }
    planEntries.push({ plan, polygons, measurements, scale, imageDataUrl, imageW, imageH, imageEl });
  }

  if (planEntries.length === 0) return;

  // Render annotated images
  const planSections = [];
  for (const entry of planEntries) {
    let imgTag = '';
    if (entry.imageEl && entry.imageW && entry.imageH) {
      try {
        const dataUrl = renderPlanCanvas(entry.imageEl, entry.imageW, entry.imageH, entry.polygons, entry.measurements, entry.scale);
        imgTag = `<img src="${dataUrl}" alt="${esc(entry.plan.label)}">`;
      } catch (_) {
        imgTag = `<p style="color:#c00">Bild konnte nicht gerendert werden.</p>`;
      }
    }
    planSections.push(`
      <h2 class="plan-title">${esc(entry.plan.label)}</h2>
      ${imgTag}
    `);
  }

  // Collect all polygons grouped by areaType, carrying plan label
  // areaType order for display
  const TYPE_ORDER = ['EBF', 'Dach gegen Aussenluft', 'Dach gegen unbeheizt', 'Wand gegen Aussenluft', 'Wand gegen unbeheizt', 'Fenster', 'Türe', 'Boden gegen Aussenluft', 'Boden gegen Erdreich', 'Boden gegen unbeheizt'];
  const byType = new Map(); // areaType -> [{poly, planLabel}]

  for (const entry of planEntries) {
    for (const poly of entry.polygons) {
      if (poly.area === null || poly.area === undefined) continue;
      const type = (poly.areaType || 'Sonstige').trim();
      if (!byType.has(type)) byType.set(type, []);
      byType.get(type).push({ poly, planLabel: entry.plan.label });
    }
  }

  // Sort types: known order first, then alphabetical for unknowns
  const sortedTypes = [...byType.keys()].sort((a, b) => {
    const ia = TYPE_ORDER.indexOf(a), ib = TYPE_ORDER.indexOf(b);
    if (ia !== -1 && ib !== -1) return ia - ib;
    if (ia !== -1) return -1;
    if (ib !== -1) return 1;
    return a.localeCompare(b);
  });

  // Build table rows grouped by type
  let tableRows = '';
  let grandTotal = 0;
  let hasAnyArea = false;

  for (const type of sortedTypes) {
    const entries = byType.get(type);
    const subtotal = entries.reduce((s, { poly }) => s + (poly.area || 0), 0);
    grandTotal += subtotal;
    hasAnyArea = true;

    // Group header
    tableRows += `<tr class="type-header">
      <td colspan="4"><strong>${esc(type)}</strong></td>
    </tr>`;

    // Individual rows
    for (const { poly, planLabel } of entries) {
      const inc = poly.inclination || 0;
      const displayArea = inc > 0 ? poly.area / Math.cos(inc * Math.PI / 180) : poly.area;
      tableRows += `<tr>
        <td><span style="color:${poly.color};font-size:18px">&#9632;</span></td>
        <td>${esc(poly.label)}</td>
        <td style="color:#666;font-size:12px">${esc(planLabel)}</td>
        <td>${fmtArea(displayArea)}</td>
      </tr>`;
    }

    // Subtotal row
    tableRows += `<tr class="subtotal-row">
      <td></td>
      <td colspan="2"><em>Summe ${esc(type)}</em></td>
      <td><strong>${fmtArea(subtotal)}</strong></td>
    </tr>`;
  }

  // Grand total
  const grandTotalRow = hasAnyArea && sortedTypes.length > 1
    ? `<tr class="grand-total-row">
        <td></td>
        <td colspan="2"><strong>Gesamttotal</strong></td>
        <td><strong>${fmtArea(grandTotal)}</strong></td>
      </tr>`
    : '';

  // Measurement rows (all plans combined, at end)
  let measRows = '';
  for (const entry of planEntries) {
    for (const m of entry.measurements) {
      const len = dist(m.pt1, m.pt2);
      measRows += `<tr>
        <td><span style="color:#fbbf24;font-size:14px">&#8212;</span></td>
        <td>Messung ${m.id}</td>
        <td style="color:#666;font-size:12px">${esc(entry.plan.label)}</td>
        <td>${entry.scale ? fmtLength(len * entry.scale) : len.toFixed(1) + ' px'}</td>
      </tr>`;
    }
  }

  const win = window.open('', '_blank');
  if (!win) { alert('Popup wurde blockiert. Bitte Popup-Blocker deaktivieren.'); return; }
  win.document.write(`<!DOCTYPE html><html lang="de"><head>
  <meta charset="UTF-8"><title>Flächenbericht</title>
  <style>
    body  { font-family:system-ui,sans-serif; padding:28px; color:#111; max-width:1000px; margin:auto; }
    h1   { font-size:22px; font-weight:700; margin-bottom:18px; }
    h2.plan-title { font-size:16px; font-weight:600; margin:32px 0 10px; color:#4f46e5; border-bottom:2px solid #e0e0f0; padding-bottom:6px; }
    img  { max-width:100%; border:1px solid #ccc; border-radius:6px; display:block; margin-bottom:8px; }
    table{ width:100%; border-collapse:collapse; margin-top:22px; font-size:13px; }
    th   { background:#f4f4f8; padding:9px 12px; text-align:left; border:1px solid #ddd; font-weight:600; }
    td   { padding:7px 12px; border:1px solid #ddd; vertical-align:middle; }
    tr.type-header td { background:#eef2ff; font-size:13px; padding:8px 12px; border-top:2px solid #c7d2fe; }
    tr.subtotal-row td { background:#f8f9ff; font-size:12px; }
    tr.grand-total-row td { background:#e0e7ff; font-weight:700; font-size:14px; border-top:2px solid #4f46e5; }
    tr:nth-child(even) td { background:#fafafa; }
    tr.type-header td, tr.subtotal-row td, tr.grand-total-row td { background-color: inherit !important; }
    .print-btn { margin-top:20px; padding:10px 24px; cursor:pointer; font-size:14px; font-weight:600; background:#4f46e5; color:#fff; border:none; border-radius:8px; }
    @media print { .print-btn { display:none; } h2.plan-title { page-break-before: auto; } }
  </style>
</head><body>
  <h1>Flächenbericht</h1>
  ${planSections.join('\n')}
  <h2 style="margin-top:40px;font-size:17px;border-bottom:2px solid #ccc;padding-bottom:6px;">Flächenübersicht</h2>
  <table>
    <thead><tr><th></th><th>Name</th><th>Plan</th><th>Fläche / Entfernung</th></tr></thead>
    <tbody>${tableRows}${grandTotalRow}${measRows}</tbody>
  </table>
  <button class="print-btn" onclick="window.print()">Drucken / Als PDF speichern</button>
</body></html>`);
  win.document.close();
}

export function printView() {
  // If only one plan, skip the dialog
  if (!S.plans || S.plans.length <= 1) {
    const activePlan = S.plans && S.plans.length === 1 ? S.plans[0] : null;
    const ids = new Set(activePlan ? [activePlan.id] : (S.activePlanId ? [S.activePlanId] : []));
    if (ids.size === 0) {
      // Fallback: no plans array, just use current state directly
      const win = window.open('', '_blank');
      if (!win) { alert('Popup wurde blockiert.'); return; }
      win.document.write(`<!DOCTYPE html><html lang="de"><head><meta charset="UTF-8"><title>Flächenbericht</title></head><body><h1>Kein Plan geladen</h1></body></html>`);
      win.document.close();
      return;
    }
    generateMultiPlanPDF(ids);
    return;
  }

  // Show plan selection dialog
  const overlay = document.createElement('div');
  overlay.style.cssText = 'position:fixed;inset:0;background:rgba(0,0,0,0.5);z-index:9999;display:flex;align-items:center;justify-content:center;';

  const dialog = document.createElement('div');
  dialog.style.cssText = 'background:#fff;border-radius:12px;padding:28px;min-width:340px;max-width:520px;box-shadow:0 8px 32px rgba(0,0,0,0.25);font-family:system-ui,sans-serif;';

  const title = document.createElement('h2');
  title.textContent = 'Pläne auswählen';
  title.style.cssText = 'margin:0 0 16px;font-size:18px;font-weight:700;color:#111;';
  dialog.appendChild(title);

  const subtitle = document.createElement('p');
  subtitle.textContent = 'Wählen Sie die Pläne aus, die im PDF enthalten sein sollen:';
  subtitle.style.cssText = 'margin:0 0 16px;font-size:13px;color:#555;';
  dialog.appendChild(subtitle);

  // Select all toggle
  const allRow = document.createElement('div');
  allRow.style.cssText = 'display:flex;align-items:center;gap:10px;padding:8px 0;border-bottom:1px solid #e5e7eb;margin-bottom:8px;';
  const allCb = document.createElement('input'); allCb.type = 'checkbox'; allCb.id = '_pdf_all'; allCb.checked = true;
  allCb.style.cssText = 'width:16px;height:16px;cursor:pointer;';
  const allLbl = document.createElement('label'); allLbl.htmlFor = '_pdf_all'; allLbl.textContent = 'Alle auswählen';
  allLbl.style.cssText = 'font-size:13px;font-weight:600;cursor:pointer;';
  allRow.appendChild(allCb); allRow.appendChild(allLbl);
  dialog.appendChild(allRow);

  const checkboxes = [];
  const planList = document.createElement('div');
  planList.style.cssText = 'max-height:280px;overflow-y:auto;';

  for (const plan of S.plans) {
    const row = document.createElement('div');
    row.style.cssText = 'display:flex;align-items:center;gap:10px;padding:7px 0;border-bottom:1px solid #f3f4f6;';
    const cb = document.createElement('input'); cb.type = 'checkbox'; cb.id = `_pdf_${plan.id}`;
    cb.checked = true; cb.dataset.planId = plan.id;
    cb.style.cssText = 'width:16px;height:16px;cursor:pointer;';
    const lbl = document.createElement('label'); lbl.htmlFor = `_pdf_${plan.id}`; lbl.textContent = plan.label || plan.id;
    lbl.style.cssText = 'font-size:13px;cursor:pointer;flex:1;';
    row.appendChild(cb); row.appendChild(lbl);
    planList.appendChild(row);
    checkboxes.push(cb);
  }

  // Sync "select all" state
  allCb.addEventListener('change', () => {
    checkboxes.forEach(cb => { cb.checked = allCb.checked; });
  });
  checkboxes.forEach(cb => {
    cb.addEventListener('change', () => {
      allCb.checked = checkboxes.every(c => c.checked);
    });
  });

  dialog.appendChild(planList);

  const btnRow = document.createElement('div');
  btnRow.style.cssText = 'display:flex;gap:12px;justify-content:flex-end;margin-top:20px;';

  const cancelBtn = document.createElement('button');
  cancelBtn.textContent = 'Abbrechen';
  cancelBtn.style.cssText = 'padding:9px 20px;font-size:13px;font-weight:600;border:1px solid #d1d5db;background:#fff;border-radius:8px;cursor:pointer;';
  cancelBtn.addEventListener('click', () => document.body.removeChild(overlay));

  const confirmBtn = document.createElement('button');
  confirmBtn.textContent = 'PDF erstellen';
  confirmBtn.style.cssText = 'padding:9px 20px;font-size:13px;font-weight:600;background:#4f46e5;color:#fff;border:none;border-radius:8px;cursor:pointer;';
  confirmBtn.addEventListener('click', () => {
    document.body.removeChild(overlay);
    const selectedIds = new Set(checkboxes.filter(cb => cb.checked).map(cb => cb.dataset.planId));
    if (selectedIds.size === 0) { alert('Bitte mindestens einen Plan auswählen.'); return; }
    generateMultiPlanPDF(selectedIds);
  });

  btnRow.appendChild(cancelBtn); btnRow.appendChild(confirmBtn);
  dialog.appendChild(btnRow);
  overlay.appendChild(dialog);

  // Close on backdrop click
  overlay.addEventListener('click', e => { if (e.target === overlay) document.body.removeChild(overlay); });

  document.body.appendChild(overlay);
}
