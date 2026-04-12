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
export async function loadPDF(file) {
  const url = URL.createObjectURL(file);
  try {
    const pdf      = await pdfjsLib.getDocument({ url, workerSrc: PDFJS_WORKER }).promise;
    const page     = await pdf.getPage(1);
    const viewport = page.getViewport({ scale: 2 });
    const off      = document.createElement('canvas');
    off.width = viewport.width; off.height = viewport.height;
    await page.render({ canvasContext: off.getContext('2d'), viewport }).promise;
    // Use the actual canvas pixel dimensions (integers) rather than the raw
    // viewport floats (e.g. 1190.56).  The offscreen canvas truncates the float
    // to an integer anyway, so using viewport.width would produce a fractional
    // S.imageW that (a) mismatches the real image and (b) fails Scala's Int
    // decoder, causing the plans-sync to silently drop the plan data.
    S.image = off; S.imageW = off.width; S.imageH = off.height;
    S.imageDataUrl = off.toDataURL('image/jpeg', 0.85); // JPEG for smaller size
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

function renderPrintCanvas() {
  const MAX = 1800;
  const sc  = Math.min(MAX / S.imageW, MAX / S.imageH, 1);
  const w   = Math.round(S.imageW * sc);
  const h   = Math.round(S.imageH * sc);

  const off = document.createElement('canvas');
  off.width = w; off.height = h;
  const c = off.getContext('2d');

  c.drawImage(S.image, 0, 0, w, h);

  const px = p => [p.x * sc, p.y * sc];

  const pill = (c, x, y, tw, fsz) => {
    const pad = 6, rh = fsz * 1.5;
    c.beginPath();
    const rx = x - tw/2 - pad, ry = y - rh/2, rw = tw + pad*2, r = 4;
    c.moveTo(rx+r, ry); c.lineTo(rx+rw-r, ry);
    c.quadraticCurveTo(rx+rw, ry, rx+rw, ry+r);
    c.lineTo(rx+rw, ry+rh-r); c.quadraticCurveTo(rx+rw, ry+rh, rx+rw-r, ry+rh);
    c.lineTo(rx+r, ry+rh); c.quadraticCurveTo(rx, ry+rh, rx, ry+rh-r);
    c.lineTo(rx, ry+r); c.quadraticCurveTo(rx, ry, rx+r, ry);
    c.closePath();
  };

  S.polygons.forEach(({ points, color, area, label }) => {
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

  S.measurements.forEach(({ pt1, pt2, id }) => {
    const [ax, ay] = px(pt1), [bx, by] = px(pt2);
    c.strokeStyle = '#fbbf24'; c.lineWidth = 1.5; c.setLineDash([6, 4]);
    c.beginPath(); c.moveTo(ax, ay); c.lineTo(bx, by); c.stroke(); c.setLineDash([]);
    [[ax,ay],[bx,by]].forEach(([x,y]) => { c.beginPath(); c.arc(x,y,3,0,Math.PI*2); c.fillStyle='#fbbf24'; c.fill(); });
    const mx = (ax+bx)/2, my = (ay+by)/2;
    const len = dist(pt1, pt2);
    const lbl = S.scale ? fmtLength(len * S.scale) : len.toFixed(1) + ' px', fsz = 12;
    c.font = `bold ${fsz}px system-ui`; c.textAlign = 'center'; c.textBaseline = 'middle';
    const tw = c.measureText(lbl).width;
    c.fillStyle = 'rgba(0,0,0,0.7)'; pill(c, mx, my, tw, fsz); c.fill();
    c.fillStyle = '#fbbf24'; c.fillText(lbl, mx, my);
  });

  return off.toDataURL('image/png');
}

export function printView() {
  const dataUrl = renderPrintCanvas();

  const polyRows = S.polygons.map(p =>
    `<tr>
      <td><span style="color:${p.color};font-size:18px">&#9632;</span></td>
      <td>${esc(p.label)}</td>
      <td>${fmtArea(p.area)}</td>
    </tr>`
  ).join('');

  const measRows = S.measurements.map(m => {
    const len = dist(m.pt1, m.pt2);
    return `<tr>
      <td><span style="color:#fbbf24;font-size:14px">\u2014</span></td>
      <td>Mesure ${m.id}</td>
      <td>${S.scale ? fmtLength(len * S.scale) : len.toFixed(1) + ' px'}</td>
    </tr>`;
  }).join('');

  const totalArea = S.polygons.reduce((sum, p) => p.area !== null ? sum + p.area : sum, 0);
  const hasScale  = S.polygons.some(p => p.area !== null);
  const totalRow  = hasScale && S.polygons.length > 1
    ? `<tr style="font-weight:700;background:#f0f0ff">
        <td></td><td>Total</td><td>${fmtArea(totalArea)}</td>
      </tr>` : '';

  const win = window.open('', '_blank');
  win.document.write(`<!DOCTYPE html><html lang="fr"><head>
  <meta charset="UTF-8"><title>Flächenbericht</title>
  <style>
    body  { font-family:system-ui,sans-serif; padding:28px; color:#111; max-width:960px; margin:auto; }
    h1   { font-size:22px; font-weight:700; margin-bottom:18px; }
    img  { max-width:100%; border:1px solid #ccc; border-radius:6px; display:block; }
    table{ width:100%; border-collapse:collapse; margin-top:22px; font-size:13px; }
    th   { background:#f4f4f8; padding:9px 12px; text-align:left; border:1px solid #ddd; font-weight:600; }
    td   { padding:8px 12px; border:1px solid #ddd; vertical-align:middle; }
    tr:nth-child(even) td { background:#fafafa; }
    .footer { margin-top:28px; font-size:11px; color:#666; border-top:1px solid #ddd; padding-top:14px; line-height:1.7; }
    .footer a { color:#4f46e5; }
    .print-btn { margin-top:20px; padding:10px 24px; cursor:pointer; font-size:14px; font-weight:600; background:#4f46e5; color:#fff; border:none; border-radius:8px; }
    @media print { .print-btn { display:none; } }
  </style>
</head><body>
  <h1>Flächenbericht</h1>
  <img src="${dataUrl}" alt="Annotierter Plan">
  <table>
    <thead><tr><th></th><th>Name</th><th>Fläche / Entfernung</th></tr></thead>
    <tbody>${polyRows}${totalRow}${measRows}</tbody>
  </table>
  <button class="print-btn" onclick="window.print()">Drucken / Als PDF speichern</button>
</body></html>`);
  win.document.close();
}
