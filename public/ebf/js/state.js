// Mutable app state — single source of truth
export const S = {
  mode: 'idle', // idle | calibrate_1 | calibrate_2 | calibrate_confirm | draw | measure | drag_vertex
  scale: null,         // meters per world-pixel (uniform, kept for backward compat)
  scaleX: null,        // meters per pixel along scaleDirX
  scaleY: null,        // meters per pixel along scaleDirY
  scaleDirX: null,     // {x, y} normalized direction vector of horizontal calibration
  scaleDirY: null,     // {x, y} normalized direction vector of vertical calibration
  calibDirection: 'uniform', // 'uniform' | 'x' | 'y'
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
  plans: [],           // [{id, label, driveFileId, imageDataUrl, imageW, imageH, scale, scaleX, scaleY, nextId, nextMeasId, polygons, measurements}]
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

/** Convert a pixel-space vector (dx, dy) to real-world meters.
 *  Uses calibration direction vectors when available, otherwise uniform scale. */
export function pxVecToM(dx, dy) {
  const sx = S.scaleX ?? S.scale;
  const sy = S.scaleY ?? S.scale;
  if (!sx || !sy) return null;
  if (S.scaleDirX && S.scaleDirY) {
    const projX = dx * S.scaleDirX.x + dy * S.scaleDirX.y;
    const projY = dx * S.scaleDirY.x + dy * S.scaleDirY.y;
    return Math.sqrt((projX * sx) ** 2 + (projY * sy) ** 2);
  }
  return Math.hypot(dx, dy) * sx;
}

export function px2m2(px) {
  const sx = S.scaleX ?? S.scale;
  const sy = S.scaleY ?? S.scale;
  if (!sx || !sy) return null;
  if (S.scaleDirX && S.scaleDirY) {
    const crossMag = Math.abs(S.scaleDirX.x * S.scaleDirY.y - S.scaleDirX.y * S.scaleDirY.x);
    if (crossMag > 0.01) return px * sx * sy * crossMag;
  }
  return px * sx * sy;
}

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
      .map(poly => {
        const inc = poly.inclination || 0;
        const raw = Number.isFinite(poly.area) ? poly.area : 0;
        const area = (inc > 0) ? raw / Math.cos(inc * Math.PI / 180) : raw;
        return {
          label:    (poly.label    || '').trim(),
          areaType: (poly.areaType || '').trim(),
          area,
        };
      })
      .filter(poly => poly.label)
  );

  // Send labels exactly as they are in the canvas — no renumbering.
  const polygons = allRaw;

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

