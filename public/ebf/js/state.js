// Mutable app state — single source of truth
export const S = {
  mode: 'idle', // idle | calibrate_1 | calibrate_2 | calibrate_confirm | draw | measure | angle | drag_vertex | drag_label | drag_poly | drag_meas | drag_annotation | text
  scale: null,         // meters per world-pixel (uniform, kept for backward compat)
  scaleX: null,        // meters per pixel along scaleDirX
  scaleY: null,        // meters per pixel along scaleDirY
  scaleDirX: null,     // {x, y} normalized direction vector of horizontal calibration
  scaleDirY: null,     // {x, y} normalized direction vector of vertical calibration
  calibDirection: 'uniform', // 'uniform' | 'x' | 'y'
  polygons: [],        // [{id, label, points, color, pixelArea, area}] label is the external sync identifier
  measurements: [],    // [{id, pt1, pt2}]
  angles: [],          // [{id, vertex, pt1, pt2}]  vertex=Scheitelpunkt, pt1/pt2=Arme
  annotations: [],     // [{id, x, y, text}]
  current: [],         // in-progress polygon world-coords
  calibPt1: null, calibPt2: null,
  measPt1: null,
  anglePt1: null,      // Scheitelpunkt
  anglePt2: null,      // Ende Arm 1
  image: null, imageW: 0, imageH: 0,
  imageDataUrl: null,  // data URL of the current plan image (for plan persistence)
  zoom: 1, panX: 0, panY: 0,
  panning: false, lastMouse: null,
  mouse: null,         // {sx, sy, wx, wy}
  nextId: 1, nextMeasId: 1, nextAngleId: 1, nextAnnotationId: 1,
  shadingPolyId:  null, // polygon ID being measured for shading (stable, not an index)
  shadingType:    null, // 'overhang' | 'side'
  shadingPt1:     null, // world-coord center point of shading measure
  wbCurrentPts:        [],  // in-progress polyline points for wb_measure mode
  wbSessionPoints:     [],  // temporary points during active wb_point session
  wbLines:             [],  // permanent WB polylines: [{id, points:[{x,y}], totalLength}]
  wbPersistentPoints:  [],  // permanent WB point markers: [{id, x, y}]
  nextWbLineId:        1,
  nextWbPointId:       1,
  drawMeasureArea: false, // true while drawing a "Fläche messen" (measure-only) polygon
  dragVertex: null,    // {polyIdx, vtxIdx}
  dragPoly:       null,    // {polyIdx, startWX, startWY, origPoints, origLabelOffset}
  dragMeas:       null,    // {measIdx, startWX, startWY, origPt1, origPt2}
  dragAnnotation: null,    // {annIdx, startWX, startWY, origX, origY}
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
  canvas.style.cursor = (m === 'draw' || m === 'measure' || m === 'angle' || m === 'text' || m === 'shading_measure' || m === 'wb_measure' || m === 'wb_point' || m.startsWith('calibrate'))
    ? 'crosshair' : 'grab';
}

const WALL_ROOF_AREA_TYPES = new Set([
  'Wand gegen Aussenluft', 'Wand gegen Erdreich', 'Wand gegen unbeheizt',
  'Dach gegen Aussenluft', 'Decke gegen unbeheizt',
]);

function _centroidOf(pts) {
  let x = 0, y = 0;
  for (const p of pts) { x += p.x; y += p.y; }
  return { x: x / pts.length, y: y / pts.length };
}

function _pointInPoly(px, py, pts) {
  let inside = false;
  for (let i = 0, j = pts.length - 1; i < pts.length; j = i++) {
    const xi = pts[i].x, yi = pts[i].y, xj = pts[j].x, yj = pts[j].y;
    if ((yi > py) !== (yj > py) && px < (xj - xi) * (py - yi) / (yj - yi) + xi)
      inside = !inside;
  }
  return inside;
}

export function emitPolygonSyncEvent() {
  if (typeof window === 'undefined' || typeof window.dispatchEvent !== 'function') return;

  // Aggregate polygons from all plans.
  // Use the live S.polygons for the active plan so unsaved changes are included.
  const allRaw = S.plans.flatMap(plan => {
    const planPolys = (plan.id === S.activePlanId ? S.polygons : plan.polygons)
      .filter(poly => (poly.areaType || '') !== '__measure__');

    return planPolys.map(poly => {
      const inc = poly.inclination || 0;
      const raw = Number.isFinite(poly.area) ? poly.area : 0;
      const area = (inc > 0) ? raw / Math.cos(inc * Math.PI / 180) : raw;
      const areaType = (poly.areaType || '').trim();

      // For Fenster/Türe: detect which wall/roof polygon geometrically contains this polygon's centroid
      let installedIn = null;
      if ((areaType === 'Fenster' || areaType === 'Tür') && poly.points.length >= 3) {
        const c = _centroidOf(poly.points);
        for (const wp of planPolys) {
          if (wp !== poly && WALL_ROOF_AREA_TYPES.has(wp.areaType) && wp.points.length >= 3) {
            if (_pointInPoly(c.x, c.y, wp.points)) {
              installedIn = (wp.label || '').trim() || null;
              break;
            }
          }
        }
      }

      return {
        label:       (poly.label || '').trim(),
        areaType,
        area,
        overhangDist: Number.isFinite(poly.overhangDist) ? poly.overhangDist : null,
        sideDist:     Number.isFinite(poly.sideDist)     ? poly.sideDist     : null,
        installedIn,
      };
    }).filter(poly => poly.label);
  });

  // Send labels exactly as they are in the canvas — no renumbering.
  const polygons = allRaw;

  console.log('[EBF] emitPolygonSyncEvent:', polygons.length, 'polygons:', polygons.map(p => `${p.label}(${p.areaType})`));
  window.dispatchEvent(new CustomEvent(EBF_POLYGONS_SYNC_EVENT, { detail: polygons }));
}

/** Emit full plans list (without image data URLs) so the Scala layer can persist it.
 *  Uses live S.polygons/S.measurements for the active plan so renames and last-drawn
 *  polygons are always captured even before saveCurrentPlanState() is called. */
export function emitPlansSyncEvent() {
  if (typeof window === 'undefined' || typeof window.dispatchEvent !== 'function') return;
  const payload = {
    plans: S.plans.map(plan => {
      const { imageDataUrl, ...rest } = plan;
      return {
        ...rest,
        polygons:     plan.id === S.activePlanId ? S.polygons     : (plan.polygons     ?? []),
        measurements: plan.id === S.activePlanId ? S.measurements : (plan.measurements ?? []),
      };
    }),
    activePlanId: S.activePlanId,
  };
  window.dispatchEvent(new CustomEvent(EBF_PLANS_SYNC_EVENT, { detail: payload }));
}

