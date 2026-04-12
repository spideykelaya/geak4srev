'use strict';

import { S, initDom, canvas, s2w, w2s, setMode, px2m2, $, emitPolygonSyncEvent, emitPlansSyncEvent, EBF_LOAD_PLANS_EVENT } from './state.js';
import { PDFJS_WORKER, SNAP_RADIUS }                      from './config.js';
import { shoelace, findNearVertex, findNearEdge, dist, labelPoint } from './geo.js';
import { render }                                         from './render.js';
import { updateSidebar, nextPolygonLabel, setSwitchPlanHandler, setCurrentAreaTypeLabel, colorForCurrentAreaType, getCurrentAreaTypeLabel } from './sidebar.js';
import { loadPDF, loadImg, exportData, exportExcel, exportXML, importData, printView, loadImageFromDataUrl } from './io.js';

pdfjsLib.GlobalWorkerOptions.workerSrc = PDFJS_WORKER;

let currentUnmount = null;
// Set to true by onLoadPlans so that the first geak:ebf-request-sync fired by AreaView
// on mount is suppressed.  Without this, AreaView's mount-time sync overwrites area
// calculations that were just restored from the project JSON.
let suppressNextRequestSync = false;

export function mountEbf(root = document) {
  currentUnmount?.();

  initDom(root);
  resizeCanvas();
  bindUI(root.ownerDocument || document);
  render();

  const onResize = () => { resizeCanvas(); render(); };
  window.addEventListener('resize', onResize);

  currentUnmount = () => {
    window.removeEventListener('resize', onResize);
  };

  return currentUnmount;
}

if (typeof window !== 'undefined') {
  window.addEventListener('DOMContentLoaded', () => {
    if (document.getElementById('main-canvas')) {
      mountEbf(document);
    }
  });
}

// ── Canvas sizing ─────────────────────────────────────────────────────────────
function resizeCanvas() {
  const r = $('canvas-container').getBoundingClientRect();
  canvas.width = r.width; canvas.height = r.height;
}

function fitToCanvas() {
  const pad = 30;
  S.zoom = Math.min((canvas.width  - pad * 2) / S.imageW, (canvas.height - pad * 2) / S.imageH);
  S.panX = (canvas.width  - S.imageW * S.zoom) / 2;
  S.panY = (canvas.height - S.imageH * S.zoom) / 2;
}

// ── UI bindings ───────────────────────────────────────────────────────────────
function bindUI(ownerDocument) {
  setSwitchPlanHandler(switchToPlan);

  $('file-input').addEventListener('change', onFileChange);
  $('import-input').addEventListener('change', async e => {
    const f = e.target.files[0]; if (!f) return;
    const ok = await importData(f); e.target.value = '';
    if (!ok) return;
    applyScaleStatus();
    show('scale-section'); show('area-type-section'); show('draw-section');
    updateSidebar(); render();
    emitPolygonSyncEvent();
    saveCurrentPlanState();
    emitPlansSyncEvent();
  });

  // Restore plans dispatched by Scala after project load
  window.addEventListener(EBF_LOAD_PLANS_EVENT, onLoadPlans);

  // Handle plan deletion
  window.addEventListener('geak:ebf-delete-plan', onDeletePlan);

  // Re-emit polygon sync on request (e.g. when AreaView mounts).
  // Suppressed once after a project load so that area calculations restored from
  // the project JSON are not immediately overwritten by the mount-time sync.
  window.addEventListener('geak:ebf-request-sync', () => {
    if (suppressNextRequestSync) { suppressNextRequestSync = false; return; }
    emitPolygonSyncEvent();
  });

  // Handle polygon rename from area calculation table
  window.addEventListener('geak:ebf-rename-polygon', e => {
    const { oldLabel, newLabel } = e.detail;
    let changed = false;
    S.plans.forEach(plan => {
      const polys = plan.id === S.activePlanId ? S.polygons : plan.polygons;
      polys.forEach(p => {
        if (p.label === oldLabel) { p.label = newLabel; changed = true; }
      });
    });
    if (changed) {
      updateSidebar(); render();
      emitPolygonSyncEvent();
      saveCurrentPlanState();
      emitPlansSyncEvent();
    }
  });

  // Area type selection from Scala sidebar
  window.addEventListener('geak:ebf-area-type-selected', e => {
    setCurrentAreaTypeLabel(e.detail);
  });

  $('calibrate-btn').addEventListener('click', () => startCalibration('uniform'));
  $('calibrate-x-btn')?.addEventListener('click', () => startCalibration('x'));
  $('calibrate-y-btn')?.addEventListener('click', () => startCalibration('y'));
  $('confirm-scale').addEventListener('click', confirmScale);
  $('cancel-scale').addEventListener('click', cancelCalibration);
  $('draw-btn').addEventListener('click', startDrawing);
  $('measure-btn').addEventListener('click', startMeasuring);
  $('clear-btn').addEventListener('click', clearAll);
  $('export-btn').addEventListener('click', exportData);
  $('export-excel-btn').addEventListener('click', exportExcel);
  $('export-xml-btn').addEventListener('click', exportXML);
  $('import-btn').addEventListener('click', () => $('import-input').click());
  $('print-btn').addEventListener('click', () => printView());
  $('clear-confirm-yes').addEventListener('click', clearAllConfirmed);
  $('clear-confirm-no').addEventListener('click', () => { $('clear-confirm-modal').style.display = 'none'; });
$('real-length').addEventListener('keydown', e => { if (e.key === 'Enter') confirmScale(); });

  canvas.addEventListener('mousedown',   onMouseDown);
  canvas.addEventListener('mousemove',   onMouseMove);
  canvas.addEventListener('mouseup',     onMouseUp);
  canvas.addEventListener('dblclick',    onDblClick);
  canvas.addEventListener('wheel',       onWheel, { passive: false });
  canvas.addEventListener('contextmenu', e => e.preventDefault());
  ownerDocument.addEventListener('keydown',   e => { if (e.key === 'Escape') cancelCurrent(); });
}

// ── File loading ──────────────────────────────────────────────────────────────
async function onFileChange(e) {
  const file = e.target.files[0]; if (!file) return;

  // Persist the outgoing plan BEFORE loading the new image, so the old plan
  // keeps its own imageDataUrl, scale, and polygons intact.
  saveCurrentPlanState();

  try {
    if (file.type === 'application/pdf') await loadPDF(file);
    else await loadImg(file);
  } catch (err) { alert('Laden fehlgeschlagen: ' + err.message); return; }

  // Build a new plan entry
  const planId    = 'plan_' + Date.now();
  const planLabel = file.name.replace(/\.[^/.]+$/, ''); // strip extension
  const newPlan = {
    id: planId,
    label: planLabel,
    driveFileId: null,
    imageDataUrl: S.imageDataUrl,   // stored from loadPDF / loadImg
    imageW: S.imageW,
    imageH: S.imageH,
    scale: null, scaleX: null, scaleY: null,
    nextId: 1,
    nextMeasId: 1,
    polygons: [],
    measurements: [],
  };

  // Cache image in localStorage (best-effort; might fail for very large images)
  try { localStorage.setItem('ebf_plan_image_' + planId, S.imageDataUrl); } catch (_) {}

  S.plans.push(newPlan);
  S.activePlanId = planId;

  $('file-name').textContent = planLabel;
  S.polygons = []; S.measurements = []; S.current = [];
  S.scale = null; S.scaleX = null; S.scaleY = null; S.scaleDirX = null; S.scaleDirY = null;
  S.nextId = 1; S.nextMeasId = 1;

  resizeCanvas();
  fitToCanvas();
  show('scale-section'); show('area-type-section'); show('draw-section');
  updateSidebar(); render();
  emitPolygonSyncEvent();
  emitPlansSyncEvent();

  // Reset input so the same file can be selected again
  e.target.value = '';

  // Upload PDF to Google Drive
  if (file.type === 'application/pdf') {
    file.arrayBuffer().then(buffer => {
      window.dispatchEvent(new CustomEvent('geak:ebf-plan-upload', {
        detail: { planId, planLabel, fileName: file.name, mimeType: file.type, buffer },
      }));
    });
  }

}

// ── Plan management ───────────────────────────────────────────────────────────

/** Snapshot the current working state back into the active plan entry. */
function saveCurrentPlanState() {
  if (!S.activePlanId) return;
  const plan = S.plans.find(p => p.id === S.activePlanId);
  if (!plan) return;
  plan.polygons     = S.polygons.map(p => ({ ...p }));
  plan.measurements = S.measurements.map(m => ({ ...m }));
  plan.scale        = S.scale;
  plan.scaleX       = S.scaleX;
  plan.scaleY       = S.scaleY;
  plan.scaleDirX    = S.scaleDirX;
  plan.scaleDirY    = S.scaleDirY;
  plan.nextId       = S.nextId;
  plan.nextMeasId   = S.nextMeasId;
  plan.imageW       = S.imageW;
  plan.imageH       = S.imageH;
  if (S.imageDataUrl) plan.imageDataUrl = S.imageDataUrl;
}

/** Switch to a different imported plan.  Saves the current plan first.
 *  @param {boolean} suppressPolygonSync - when true, skip emitPolygonSyncEvent() at the end.
 *    Used during initial project load (onLoadPlans) so that area calculations already
 *    restored from the project JSON are not immediately overwritten by a stale polygon sync. */
async function switchToPlan(planId, { suppressPolygonSync = false } = {}) {
  if (planId === S.activePlanId) return;

  saveCurrentPlanState();

  const plan = S.plans.find(p => p.id === planId);
  if (!plan) return;

  // Clear current state
  S.image = null;
  S.imageDataUrl = null;

  // Restore image: try in-memory first, then localStorage
  const imageDataUrl = plan.imageDataUrl || localStorage.getItem('ebf_plan_image_' + planId);
  console.log(`[EBF] switchToPlan ${planId}: imageDataUrl=${imageDataUrl ? imageDataUrl.substring(0,40)+'...' : 'null'}`);

  if (imageDataUrl) {
    try {
      await loadImageFromDataUrl(imageDataUrl);
      plan.imageDataUrl = S.imageDataUrl; // keep in sync
      console.log(`[EBF] switchToPlan image loaded OK, S.image=${!!S.image} W=${S.imageW} H=${S.imageH}`);
    } catch (err) {
      console.warn('[EBF] Failed to load image:', err);
      S.image = null;
      S.imageW = plan.imageW;
      S.imageH = plan.imageH;
    }
  } else {
    console.warn(`[EBF] switchToPlan: no imageDataUrl for plan ${planId} — canvas will be blank`);
    S.image = null;
    S.imageW = plan.imageW;
    S.imageH = plan.imageH;
  }

  // Restore plan data
  S.activePlanId  = planId;
  S.polygons      = plan.polygons.map(p => ({ ...p }));
  S.measurements  = plan.measurements.map(m => ({ ...m }));
  S.scale         = plan.scale     ?? null;
  S.scaleX        = plan.scaleX    ?? plan.scale ?? null;
  S.scaleY        = plan.scaleY    ?? plan.scale ?? null;
  S.scaleDirX     = plan.scaleDirX ?? null;
  S.scaleDirY     = plan.scaleDirY ?? null;
  S.nextId        = plan.nextId ?? 1;
  S.nextMeasId    = plan.nextMeasId ?? 1;
  S.current       = [];

  $('file-name').textContent = plan.label;

  // Fit image to canvas if available.
  // Always re-measure the container first so canvas.width/height reflect the
  // current DOM layout.  This matters when switchToPlan is called on initial
  // project load (from JSON upload), where mountEbf may have run before the
  // browser had finished laying out the canvas container, leaving the canvas
  // with zero or incorrect dimensions and causing fitToCanvas() to produce a
  // negative (or near-zero) zoom that makes the plan appear completely warped.
  if (S.image) {
    resizeCanvas();
    fitToCanvas();
    applyScaleStatus();
  }

  show('scale-section');
  show('area-type-section');
  show('draw-section');
  updateSidebar();
  render();
  if (!suppressPolygonSync) emitPolygonSyncEvent();
  emitPlansSyncEvent();
}

/** Restore plans received from the Scala layer (project load). */
async function onLoadPlans(event) {
  const data = event.detail;
  if (!data || !Array.isArray(data.plans)) return;

  // Empty plans = new/cleared project: reset all JS canvas state
  if (data.plans.length === 0) {
    S.plans = [];
    S.activePlanId = null;
    S.image = null;
    S.imageDataUrl = null;
    S.imageW = 0;
    S.imageH = 0;
    S.polygons = [];
    S.measurements = [];
    S.scale = null;
    S.scaleX = null;
    S.scaleY = null;
    S.nextId = 1;
    S.nextMeasId = 1;
    S.current = [];
    $('file-name').textContent = '';
    updateSidebar();
    render();
    return;
  }

  // Restore image data URLs: prefer localStorage (fast), fall back to embedded imageDataUrl
  // (present when loaded from a self-contained local JSON export).
  // Also write back to localStorage so future loads can skip the inline data.
  S.plans = data.plans.map(plan => {
    const fromStorage = localStorage.getItem('ebf_plan_image_' + plan.id);
    const imageDataUrl = fromStorage || plan.imageDataUrl || null;
    console.log(`[EBF] onLoadPlans plan=${plan.id} fromStorage=${!!fromStorage} fromPlan=${!!plan.imageDataUrl} result=${!!imageDataUrl}`);
    if (!fromStorage && imageDataUrl) {
      try { localStorage.setItem('ebf_plan_image_' + plan.id, imageDataUrl); } catch (_) {}
    }
    return { ...plan, imageDataUrl };
  });

  const activeId = data.activePlanId || S.plans[0]?.id;
  // Reset activePlanId so switchToPlan always runs fully and reloads the image
  // into the freshly mounted canvas (the component may have remounted with a new DOM).
  S.activePlanId = null;
  updateSidebar();

  if (activeId) {
    console.log(`[EBF] onLoadPlans switching to ${activeId}`);
    // suppressPolygonSync: area calculations were already restored from the project JSON;
    // emitting a polygon sync here would overwrite them with potentially incomplete data.
    await switchToPlan(activeId, { suppressPolygonSync: true });
  }
  // Suppress the next geak:ebf-request-sync so that AreaView's mount-time sync
  // does not overwrite the area calculations just restored from the project JSON.
  suppressNextRequestSync = true;
}

/** Delete a plan after confirmation. */
async function onDeletePlan(event) {
  const { planId } = event.detail;

  // Remove the plan from S.plans
  S.plans = S.plans.filter(p => p.id !== planId);

  // If this was the active plan, switch to the first remaining plan (if any)
  if (S.activePlanId === planId) {
    if (S.plans.length > 0) {
      // Switch to first plan
      await switchToPlan(S.plans[0].id);
    } else {
      // No plans left
      S.activePlanId = null;
      S.image = null;
      S.imageDataUrl = null;
      S.polygons = [];
      S.measurements = [];
      S.scale = null;
      S.nextId = 1;
      S.nextMeasId = 1;
      $('file-name').textContent = '';
      updateSidebar();
      render();
      emitPolygonSyncEvent();
      emitPlansSyncEvent();
    }
  } else {
    // Just update the list and sync
    updateSidebar();
    emitPlansSyncEvent();
  }

  // Clear localStorage cache for deleted plan
  try {
    localStorage.removeItem('ebf_plan_image_' + planId);
  } catch (_) {}
}


// ── Calibration ───────────────────────────────────────────────────────────────
function startCalibration(direction = 'uniform') {
  S.calibDirection = direction;
  S.calibPt1 = null; S.calibPt2 = null;
  setMode('calibrate_1');
  const hint = direction === 'x'
    ? 'Horizontale Kalibrierung: Klicken Sie auf den ersten Punkt einer bekannten horizontalen Strecke'
    : direction === 'y'
    ? 'Vertikale Kalibrierung: Klicken Sie auf den ersten Punkt einer bekannten vertikalen Strecke'
    : 'Klicken Sie auf den ersten Referenzpunkt';
  setInstructions(hint);
  render();
}

function confirmScale() {
  const val  = parseFloat($('real-length').value);
  const unit = parseFloat($('length-unit').value);
  if (!val || val <= 0) { $('real-length').focus(); return; }

  const direction = S.calibDirection || 'uniform';
  const pixelDist = dist(S.calibPt1, S.calibPt2);
  if (!pixelDist) { alert('Ungültige Linie – bitte eine längere Strecke einzeichnen.'); return; }

  const newScale = (val * unit) / pixelDist;
  const dx = S.calibPt2.x - S.calibPt1.x;
  const dy = S.calibPt2.y - S.calibPt1.y;

  if (direction === 'x') {
    S.scaleX    = newScale;
    S.scaleDirX = { x: dx / pixelDist, y: dy / pixelDist };
    if (!S.scaleY) {
      S.scaleY    = newScale;
      S.scaleDirY = { x: -S.scaleDirX.y, y: S.scaleDirX.x }; // senkrecht zu dh
    }
  } else if (direction === 'y') {
    S.scaleY    = newScale;
    S.scaleDirY = { x: dx / pixelDist, y: dy / pixelDist };
    if (!S.scaleX) {
      S.scaleX    = newScale;
      S.scaleDirX = { x: -S.scaleDirY.y, y: S.scaleDirY.x }; // senkrecht zu dv
    }
  } else {
    S.scaleX    = newScale;
    S.scaleY    = newScale;
    S.scale     = newScale;
    S.scaleDirX = null;
    S.scaleDirY = null;
  }

  $('scale-dialog').style.display = 'none';
  applyScaleStatus();
  S.polygons.forEach(p => { p.area = px2m2(p.pixelArea); });
  S.calibPt1 = null; S.calibPt2 = null;
  setMode('idle'); setInstructions('');
  updateSidebar(); render();
  emitPolygonSyncEvent();
  saveCurrentPlanState();
  emitPlansSyncEvent();
}

function cancelCalibration() {
  $('scale-dialog').style.display = 'none';
  S.calibPt1 = null; S.calibPt2 = null;
  setMode('idle'); setInstructions(''); render();
}

function applyScaleStatus() {
  const sx = S.scaleX ?? S.scale;
  const sy = S.scaleY ?? S.scale;
  if (!sx && !sy) return;
  if (sx === sy) {
    $('scale-status').textContent = `1 m = ${(1 / sx).toFixed(1)} px`;
  } else {
    $('scale-status').textContent =
      `H: 1 m = ${(1 / sx).toFixed(1)} px  |  V: 1 m = ${(1 / sy).toFixed(1)} px`;
  }
  $('scale-status').className = 'scale-status calibrated';
  $('draw-btn').classList.add('btn-highlight');
}

// ── Drawing actions ───────────────────────────────────────────────────────────
function startDrawing() {
  if (!S.image) return;
  $('draw-btn').classList.remove('btn-highlight');
  S.current = []; setMode('draw');
  setInstructions('Klicken Sie, um Punkte hinzuzufügen – Doppelklick oder Klicken auf den ersten Punkt zum Beenden');
}

function finishPolygon() {
  const pts = [...S.current];
  if (pts.length < 3) { S.current = []; setMode('idle'); setInstructions(''); render(); return; }
  const pixelArea = shoelace(pts);
  const id = S.nextId++;
  const label = nextPolygonLabel();
  S.polygons.push({ id, label, areaType: getCurrentAreaTypeLabel(), points: pts, color: colorForCurrentAreaType(), pixelArea, area: px2m2(pixelArea) });
  S.current = []; setMode('idle'); setInstructions('');
  updateSidebar(); render();
  emitPolygonSyncEvent();
  saveCurrentPlanState();
  emitPlansSyncEvent();
}

function startMeasuring() {
  if (!S.image) return;
  S.measPt1 = null; setMode('measure');
  setInstructions('Klicken Sie auf den ersten Messpunkt');
}

function clearAll() {
  if (!S.polygons.length && !S.current.length && !S.measurements.length) return;
  $('clear-confirm-modal').style.display = 'flex';
}

function clearAllConfirmed() {
  $('clear-confirm-modal').style.display = 'none';
  S.polygons = []; S.measurements = []; S.current = [];
  setMode('idle'); setInstructions('');
  updateSidebar(); render();
  emitPolygonSyncEvent();
  saveCurrentPlanState();
  emitPlansSyncEvent();
}

function cancelCurrent() {
  if (S.mode === 'draw')                   { S.current = []; setMode('idle'); setInstructions(''); render(); }
  else if (S.mode === 'measure')           { S.measPt1 = null; setMode('idle'); setInstructions(''); render(); }
  else if (S.mode.startsWith('calibrate')) cancelCalibration();
}

// ── Mouse events ──────────────────────────────────────────────────────────────
function onMouseDown(e) {
  if (e.button === 1 || (e.button === 0 && e.altKey)) {
    S.panning = true; S.lastMouse = { x: e.clientX, y: e.clientY };
    canvas.style.cursor = 'grabbing'; return;
  }
  if (e.button === 2) { cancelCurrent(); return; }
  if (e.button !== 0) return;

  const r = canvas.getBoundingClientRect();
  const sx = e.clientX - r.left, sy = e.clientY - r.top;
  const wp = s2w(sx, sy);

  if (S.mode === 'calibrate_1') {
    S.calibPt1 = wp; setMode('calibrate_2');
    setInstructions('Klicken Sie auf den zweiten Referenzpunkt');
    render(); return;
  }
  if (S.mode === 'calibrate_2') {
    S.calibPt2 = wp; setMode('calibrate_confirm');
    $('real-length').value = '';
    const titles = { uniform: 'Wahre Entfernung', x: 'Horizontale Entfernung', y: 'Vertikale Entfernung' };
    const titleEl = $('scale-dialog-title');
    if (titleEl) titleEl.textContent = titles[S.calibDirection] || titles.uniform;
    $('scale-dialog').style.display = 'flex';
    setTimeout(() => $('real-length').focus(), 50);
    render(); return;
  }
  if (S.mode === 'draw') {
    if (S.current.length >= 3) {
      const fs = w2s(S.current[0].x, S.current[0].y);
      if (Math.hypot(fs.x - sx, fs.y - sy) <= SNAP_RADIUS) { finishPolygon(); return; }
    }
    S.current.push(wp); render(); return;
  }
  if (S.mode === 'measure') {
    if (!S.measPt1) {
      S.measPt1 = wp; setInstructions('Klicken Sie auf den zweiten Punkt');
    } else {
      S.measurements.push({ id: S.nextMeasId++, pt1: S.measPt1, pt2: wp });
      S.measPt1 = null; setMode('idle'); setInstructions(''); updateSidebar();
    }
    render(); return;
  }

  // Idle: drag vertex, label, or pan
  const nv = findNearVertex(sx, sy);
  if (nv) { S.dragVertex = nv; S.mode = 'drag_vertex'; canvas.style.cursor = 'move'; return; }
  const li = findNearLabel(sx, sy);
  if (li !== null) {
    const poly = S.polygons[li];
    S.dragLabel = { polyIdx: li, startSX: sx, startSY: sy, origDX: poly.labelOffset?.dx || 0, origDY: poly.labelOffset?.dy || 0 };
    S.mode = 'drag_label'; canvas.style.cursor = 'move'; return;
  }
  S.panning = true; S.lastMouse = { x: e.clientX, y: e.clientY };
  canvas.style.cursor = 'grabbing';
}

function onMouseMove(e) {
  const r  = canvas.getBoundingClientRect();
  const sx = e.clientX - r.left, sy = e.clientY - r.top;
  const wp = s2w(sx, sy);
  S.mouse  = { sx, sy, wx: wp.x, wy: wp.y };

  if (S.mode === 'drag_vertex' && S.dragVertex) {
    const poly = S.polygons[S.dragVertex.polyIdx];
    poly.points[S.dragVertex.vtxIdx] = { x: wp.x, y: wp.y };
    poly.pixelArea = shoelace(poly.points); poly.area = px2m2(poly.pixelArea);
    updateSidebar(); render(); return;
  }
  if (S.mode === 'drag_label' && S.dragLabel !== null) {
    const dl = S.dragLabel;
    const poly = S.polygons[dl.polyIdx];
    poly.labelOffset = { dx: dl.origDX + (sx - dl.startSX) / S.zoom, dy: dl.origDY + (sy - dl.startSY) / S.zoom };
    render(); return;
  }

  if (S.panning) {
    S.panX += e.clientX - S.lastMouse.x;
    S.panY += e.clientY - S.lastMouse.y;
    S.lastMouse = { x: e.clientX, y: e.clientY };
    updateZoomIndicator();
  }

  if (S.mode === 'idle' && !S.panning) {
    canvas.style.cursor = (findNearVertex(sx, sy) || findNearLabel(sx, sy) !== null) ? 'move' : 'grab';
    S.hoverEdge = findNearEdge(sx, sy);
  } else {
    S.hoverEdge = null;
  }

  if (S.image) render();
}

function onMouseUp() {
  if (S.mode === 'drag_vertex') {
    S.dragVertex = null; setMode('idle'); canvas.style.cursor = 'grab';
    emitPolygonSyncEvent();
    saveCurrentPlanState();
    emitPlansSyncEvent();
    return;
  }
  if (S.mode === 'drag_label') {
    S.dragLabel = null; setMode('idle'); canvas.style.cursor = 'grab';
    saveCurrentPlanState();
    emitPlansSyncEvent();
    return;
  }
  if (S.panning) { S.panning = false; canvas.style.cursor = S.mode === 'idle' ? 'grab' : 'crosshair'; }
}

function onDblClick() {
  if (S.mode === 'draw' && S.current.length >= 3) { S.current.pop(); finishPolygon(); }
}

function onWheel(e) {
  e.preventDefault();
  const r      = canvas.getBoundingClientRect();
  const sx     = e.clientX - r.left, sy = e.clientY - r.top;
  const factor = e.deltaY < 0 ? 1.12 : 1 / 1.12;
  const nz     = Math.max(0.02, Math.min(80, S.zoom * factor));
  S.panX = sx - (sx - S.panX) * (nz / S.zoom);
  S.panY = sy - (sy - S.panY) * (nz / S.zoom);
  S.zoom = nz;
  updateZoomIndicator(); render();
}

// ── Label hit detection ───────────────────────────────────────────────────────
function findNearLabel(sx, sy) {
  const HIT_RADIUS = 40; // screen pixels
  for (let i = S.polygons.length - 1; i >= 0; i--) {
    const poly = S.polygons[i];
    if (poly.points.length < 3) continue;
    const c = labelPoint(poly.points);
    const lx = c.x + (poly.labelOffset?.dx || 0);
    const ly = c.y + (poly.labelOffset?.dy || 0);
    const ls = { x: lx * S.zoom + S.panX, y: ly * S.zoom + S.panY };
    if (Math.hypot(ls.x - sx, ls.y - sy) <= HIT_RADIUS) return i;
  }
  return null;
}

// ── Image export helper (called from Scala for JSON download) ────────────────
/**
 * Returns a map of planId → imageDataUrl for all plans that have an image.
 * Ensures the currently active plan's imageDataUrl is up to date first.
 * Exposed on window so Scala's downloadProjectJson can call it synchronously.
 */
function getEbfPlanImages() {
  saveCurrentPlanState();
  const result = {};
  S.plans.forEach(plan => {
    if (plan.imageDataUrl) result[plan.id] = plan.imageDataUrl;
  });
  return result;
}
window.getEbfPlanImages = getEbfPlanImages;

// ── Misc helpers ──────────────────────────────────────────────────────────────
function show(id) {
  const el = $(id);
  if (!el) return;
  el.style.display = '';
  if (id === 'area-type-section') {
    const sel = $('area-type-select');
    if (sel) setCurrentAreaTypeLabel(sel.value);
  }
}
function setInstructions(t)    { $('instructions').textContent = t; }
function updateZoomIndicator() { $('zoom-indicator').textContent = Math.round(S.zoom * 100) + '%'; }
