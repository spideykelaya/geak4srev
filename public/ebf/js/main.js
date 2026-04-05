'use strict';

import { S, initDom, canvas, s2w, w2s, setMode, px2m2, $, emitPolygonSyncEvent, emitPlansSyncEvent, EBF_LOAD_PLANS_EVENT } from './state.js';
import { PDFJS_WORKER, COLORS, SNAP_RADIUS }             from './config.js';
import { shoelace, findNearVertex, findNearEdge, dist }   from './geo.js';
import { render }                                         from './render.js';
import { updateSidebar, nextPolygonLabel, setSwitchPlanHandler, setCurrentAreaTypeLabel } from './sidebar.js';
import { loadPDF, loadImg, exportData, exportExcel, exportXML, importData, printView, loadImageFromDataUrl } from './io.js';

pdfjsLib.GlobalWorkerOptions.workerSrc = PDFJS_WORKER;

let currentUnmount = null;

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

  // Area type selection from Scala sidebar
  window.addEventListener('geak:ebf-area-type-selected', e => {
    setCurrentAreaTypeLabel(e.detail);
  });

  $('calibrate-btn').addEventListener('click', startCalibration);
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
  $('calib-intro-start').addEventListener('click', () => { $('calib-intro-modal').style.display = 'none'; startCalibration(); });
  $('calib-intro-skip').addEventListener('click', () => { $('calib-intro-modal').style.display = 'none'; setMode('idle'); });
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
  try {
    if (file.type === 'application/pdf') await loadPDF(file);
    else await loadImg(file);
  } catch (err) { alert('Laden fehlgeschlagen: ' + err.message); return; }

  // Persist the outgoing plan before switching
  saveCurrentPlanState();

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
    scale: null,
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
  S.scale = null; S.nextId = 1; S.nextMeasId = 1;

  fitToCanvas();
  show('scale-section'); show('area-type-section'); show('draw-section');
  updateSidebar(); render();
  emitPolygonSyncEvent();
  emitPlansSyncEvent();

  // Upload PDF to Google Drive
  if (file.type === 'application/pdf') {
    file.arrayBuffer().then(buffer => {
      window.dispatchEvent(new CustomEvent('geak:ebf-plan-upload', {
        detail: { planId, planLabel, fileName: file.name, mimeType: file.type, buffer },
      }));
    });
  }

  $('calib-intro-modal').style.display = 'flex';
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
  plan.nextId       = S.nextId;
  plan.nextMeasId   = S.nextMeasId;
  plan.imageW       = S.imageW;
  plan.imageH       = S.imageH;
  if (S.imageDataUrl) plan.imageDataUrl = S.imageDataUrl;
}

/** Switch to a different imported plan.  Saves the current plan first. */
async function switchToPlan(planId) {
  if (planId === S.activePlanId) return;

  saveCurrentPlanState();

  const plan = S.plans.find(p => p.id === planId);
  if (!plan) return;

  // Clear current state
  S.image = null;
  S.imageDataUrl = null;

  // Restore image: try in-memory first, then localStorage
  const imageDataUrl = plan.imageDataUrl || localStorage.getItem('ebf_plan_image_' + planId);

  if (imageDataUrl) {
    try {
      await loadImageFromDataUrl(imageDataUrl);
      plan.imageDataUrl = S.imageDataUrl; // keep in sync
    } catch (err) {
      dom.console.warn('Failed to load image:', err);
      S.image = null;
      S.imageW = plan.imageW;
      S.imageH = plan.imageH;
    }
  } else {
    S.image = null;
    S.imageW = plan.imageW;
    S.imageH = plan.imageH;
  }

  // Restore plan data
  S.activePlanId  = planId;
  S.polygons      = plan.polygons.map(p => ({ ...p }));
  S.measurements  = plan.measurements.map(m => ({ ...m }));
  S.scale         = plan.scale ?? null;
  S.nextId        = plan.nextId ?? 1;
  S.nextMeasId    = plan.nextMeasId ?? 1;
  S.current       = [];

  $('file-name').textContent = plan.label;

  // Fit image to canvas if available
  if (S.image) {
    fitToCanvas();
    applyScaleStatus();
  }

  show('scale-section');
  show('area-type-section');
  show('draw-section');
  updateSidebar();
  render();
  emitPolygonSyncEvent();
  emitPlansSyncEvent();
}

/** Restore plans received from the Scala layer (project load). */
async function onLoadPlans(event) {
  const data = event.detail;
  if (!data || !Array.isArray(data.plans) || data.plans.length === 0) return;

  // Restore image data URLs from localStorage
  S.plans = data.plans.map(plan => ({
    ...plan,
    imageDataUrl: localStorage.getItem('ebf_plan_image_' + plan.id) || null,
  }));

  const activeId = data.activePlanId || S.plans[0]?.id;
  updateSidebar();

  if (activeId) {
    await switchToPlan(activeId);
  }
}

/** Delete a plan after confirmation. */
async function onDeletePlan(event) {
  const { planId, planLabel } = event.detail;

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
function startCalibration() {
  S.calibPt1 = null; S.calibPt2 = null;
  setMode('calibrate_1');
  setInstructions('Klicken Sie auf den ersten Referenzpunkt');
  render();
}

function confirmScale() {
  const val  = parseFloat($('real-length').value);
  const unit = parseFloat($('length-unit').value);
  if (!val || val <= 0) { $('real-length').focus(); return; }
  S.scale = (val * unit) / dist(S.calibPt1, S.calibPt2);
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
  if (!S.scale) return;
  $('scale-status').textContent = `1 m = ${(1 / S.scale).toFixed(1)} px`;
  $('scale-status').className   = 'scale-status calibrated';
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
  S.polygons.push({ id, label, points: pts, color: COLORS[(id - 1) % COLORS.length], pixelArea, area: px2m2(pixelArea) });
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

  // Idle: drag vertex or pan
  const nv = findNearVertex(sx, sy);
  if (nv) { S.dragVertex = nv; S.mode = 'drag_vertex'; canvas.style.cursor = 'move'; return; }
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

  if (S.panning) {
    S.panX += e.clientX - S.lastMouse.x;
    S.panY += e.clientY - S.lastMouse.y;
    S.lastMouse = { x: e.clientX, y: e.clientY };
    updateZoomIndicator();
  }

  if (S.mode === 'idle' && !S.panning) {
    canvas.style.cursor = findNearVertex(sx, sy) ? 'move' : 'grab';
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

// ── Misc helpers ──────────────────────────────────────────────────────────────
function show(id)              { $(id).style.display = ''; }
function setInstructions(t)    { $('instructions').textContent = t; }
function updateZoomIndicator() { $('zoom-indicator').textContent = Math.round(S.zoom * 100) + '%'; }
