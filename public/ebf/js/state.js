// Mutable app state — single source of truth
export const S = {
  mode: 'idle', // idle | calibrate_1 | calibrate_2 | calibrate_confirm | draw | measure | drag_vertex
  scale: null,         // meters per world-pixel
  polygons: [],        // [{id, label, points, color, pixelArea, area}] label is the external sync identifier
  measurements: [],    // [{id, pt1, pt2}]
  current: [],         // in-progress polygon world-coords
  calibPt1: null, calibPt2: null,
  measPt1: null,
  image: null, imageW: 0, imageH: 0,
  imageDataUrl: null,  // data URL of the current plan image (for plan persistence)
  zoom: 1, panX: 0, panY: 0,
  panning: false, lastMouse: null,
  mouse: null,         // {sx, sy, wx, wy}
  nextId: 1, nextMeasId: 1,
  dragVertex: null,    // {polyIdx, vtxIdx}
  hoverEdge: null,     // {wmx, wmy, len}
  // ── Plans ────────────────────────────────────────────────────────────────
  plans: [],           // [{id, label, driveFileId, imageDataUrl, imageW, imageH, scale, nextId, nextMeasId, polygons, measurements}]
  activePlanId: null,  // id of the currently displayed plan
};

export const EBF_POLYGONS_SYNC_EVENT = 'geak:ebf-polygons-sync';
export const EBF_PLANS_SYNC_EVENT    = 'geak:ebf-plans-sync';
export const EBF_PLAN_UPLOAD_EVENT   = 'geak:ebf-plan-upload';
export const EBF_LOAD_PLANS_EVENT    = 'geak:ebf-load-plans';

// Canvas references — undefined until initDom() is called
export let canvas, ctx;
let domRoot = document;

export function setDomRoot(root) {
  domRoot = root || document;
}

export function $(id) {
  if (typeof domRoot.getElementById === 'function') return domRoot.getElementById(id);
  return domRoot.querySelector(`#${id}`);
}

export function initDom(root = document) {
  setDomRoot(root);
  canvas = $('main-canvas');
  ctx    = canvas.getContext('2d');
}

// Coordinate transforms
export function s2w(sx, sy) { return { x: (sx - S.panX) / S.zoom, y: (sy - S.panY) / S.zoom }; }
export function w2s(wx, wy) { return { x: wx * S.zoom + S.panX,   y: wy * S.zoom + S.panY   }; }

export function px2m2(px) { return S.scale ? px * S.scale * S.scale : null; }

export function setMode(m) {
  S.mode = m;
  canvas.style.cursor = (m === 'draw' || m === 'measure' || m.startsWith('calibrate'))
    ? 'crosshair' : 'grab';
}

export function emitPolygonSyncEvent() {
  if (typeof window === 'undefined' || typeof window.dispatchEvent !== 'function') return;

  // Aggregate polygons from all plans.
  // Use the live S.polygons for the active plan so unsaved changes are included.
  const allRaw = S.plans.flatMap(plan =>
    (plan.id === S.activePlanId ? S.polygons : plan.polygons)
      .map(poly => ({
        label:    (poly.label    || '').trim(),
        areaType: (poly.areaType || '').trim(),
        area: Number.isFinite(poly.area) ? poly.area : 0,
      }))
      .filter(poly => poly.label)
  );

  // Re-number labels sequentially per prefix across all plans so there are no
  // duplicates (e.g. two plans each having "EBF1" become "EBF1" and "EBF2").
  const prefixCounters = {};
  const polygons = allRaw.map(poly => {
    const prefix = poly.label.replace(/\d+$/, '') || poly.label;
    prefixCounters[prefix] = (prefixCounters[prefix] || 0) + 1;
    return { ...poly, label: prefix + prefixCounters[prefix] };
  });

  window.dispatchEvent(new CustomEvent(EBF_POLYGONS_SYNC_EVENT, { detail: polygons }));
}

/** Emit full plans list (without image data URLs) so the Scala layer can persist it. */
export function emitPlansSyncEvent() {
  if (typeof window === 'undefined' || typeof window.dispatchEvent !== 'function') return;
  const payload = {
    plans: S.plans.map(({ imageDataUrl, ...rest }) => rest), // strip large image data
    activePlanId: S.activePlanId,
  };
  window.dispatchEvent(new CustomEvent(EBF_PLANS_SYNC_EVENT, { detail: payload }));
}

