import { S, w2s, s2w }              from './state.js';
import { SNAP_RADIUS, EDGE_HIT_RADIUS } from './config.js';

// ── Pure geometry ─────────────────────────────────────────────────────────────
export function dist(a, b) { return Math.hypot(a.x - b.x, a.y - b.y); }
export function clamp(v, lo, hi) { return Math.max(lo, Math.min(hi, v)); }

export function shoelace(pts) {
  let a = 0;
  for (let i = 0; i < pts.length; i++) {
    const j = (i + 1) % pts.length;
    a += pts[i].x * pts[j].y - pts[j].x * pts[i].y;
  }
  return Math.abs(a) / 2;
}

export function distToSegScreen(px, py, ax, ay, bx, by) {
  const dx = bx - ax, dy = by - ay;
  const lenSq = dx * dx + dy * dy;
  if (lenSq === 0) return Math.hypot(px - ax, py - ay);
  const t = Math.max(0, Math.min(1, ((px - ax) * dx + (py - ay) * dy) / lenSq));
  return Math.hypot(px - (ax + t * dx), py - (ay + t * dy));
}

function centroid(pts) {
  let x = 0, y = 0;
  pts.forEach(p => { x += p.x; y += p.y; });
  return { x: x / pts.length, y: y / pts.length };
}

// Returns a point guaranteed to be inside the polygon (scan-line approach)
export function labelPoint(pts) {
  const ys = pts.map(p => p.y).sort((a, b) => a - b);
  let bestX = null, bestY = null, bestW = -1;
  for (let i = 0; i < ys.length - 1; i++) {
    const y = (ys[i] + ys[i + 1]) / 2;
    const xs = [];
    for (let a = 0, b = pts.length - 1; a < pts.length; b = a++) {
      const [x0, y0, x1, y1] = [pts[a].x, pts[a].y, pts[b].x, pts[b].y];
      if ((y0 <= y && y < y1) || (y1 <= y && y < y0))
        xs.push(x0 + (y - y0) * (x1 - x0) / (y1 - y0));
    }
    xs.sort((a, b) => a - b);
    for (let k = 0; k + 1 < xs.length; k += 2) {
      const w = xs[k + 1] - xs[k];
      if (w > bestW) { bestW = w; bestX = (xs[k] + xs[k + 1]) / 2; bestY = y; }
    }
  }
  return bestX !== null ? { x: bestX, y: bestY } : centroid(pts);
}

// ── Hit detection ─────────────────────────────────────────────────────────────

/** Ray-casting point-in-polygon test (world coords). */
export function pointInPolygon(px, py, pts) {
  let inside = false;
  for (let i = 0, j = pts.length - 1; i < pts.length; j = i++) {
    const xi = pts[i].x, yi = pts[i].y, xj = pts[j].x, yj = pts[j].y;
    if ((yi > py) !== (yj > py) && px < (xj - xi) * (py - yi) / (yj - yi) + xi)
      inside = !inside;
  }
  return inside;
}

/** Returns the topmost polygon index whose fill contains screen point (sx,sy), or null. */
export function findPolygonAt(sx, sy) {
  const wp = s2w(sx, sy);
  for (let i = S.polygons.length - 1; i >= 0; i--) {
    if (S.polygons[i].points.length >= 3 && pointInPolygon(wp.x, wp.y, S.polygons[i].points))
      return i;
  }
  return null;
}

/** Returns measurement index whose line is within EDGE_HIT_RADIUS of screen point, or null. */
export function findNearMeasurement(sx, sy) {
  for (let i = S.measurements.length - 1; i >= 0; i--) {
    const m = S.measurements[i];
    const a = { x: m.pt1.x * S.zoom + S.panX, y: m.pt1.y * S.zoom + S.panY };
    const b = { x: m.pt2.x * S.zoom + S.panX, y: m.pt2.y * S.zoom + S.panY };
    if (distToSegScreen(sx, sy, a.x, a.y, b.x, b.y) <= EDGE_HIT_RADIUS) return i;
  }
  return null;
}

export function findNearVertex(sx, sy) {
  for (let pi = 0; pi < S.polygons.length; pi++) {
    const pts = S.polygons[pi].points;
    for (let vi = 0; vi < pts.length; vi++) {
      const s = w2s(pts[vi].x, pts[vi].y);
      if (Math.hypot(s.x - sx, s.y - sy) <= SNAP_RADIUS) return { polyIdx: pi, vtxIdx: vi };
    }
  }
  return null;
}

export function findNearEdge(sx, sy) {
  let best = null, bestD = EDGE_HIT_RADIUS;
  for (let pi = 0; pi < S.polygons.length; pi++) {
    const pts = S.polygons[pi].points;
    for (let ei = 0; ei < pts.length; ei++) {
      const a = pts[ei], b = pts[(ei + 1) % pts.length];
      const as = w2s(a.x, a.y), bs = w2s(b.x, b.y);
      const d = distToSegScreen(sx, sy, as.x, as.y, bs.x, bs.y);
      if (d < bestD) {
        bestD = d;
        best = { wmx: (a.x + b.x) / 2, wmy: (a.y + b.y) / 2, len: dist(a, b), dx: b.x - a.x, dy: b.y - a.y };
      }
    }
  }
  return best;
}

// ── Formatters ────────────────────────────────────────────────────────────────
export function fmtArea(m2) {
  if (m2 === null) return '?';
  if (m2 >= 10000) return (m2 / 10000).toFixed(3) + ' ha';
  if (m2 >= 1)     return m2.toFixed(3) + ' m\u00b2';
  return (m2 * 10000).toFixed(1) + ' cm\u00b2';
}

export function fmtLength(m) {
  if (m >= 1000) return (m / 1000).toFixed(3) + ' km';
  if (m >= 1)    return m.toFixed(3) + ' m';
  return (m * 100).toFixed(1) + ' cm';
}

export function esc(str) {
  return String(str)
    .replace(/&/g, '&amp;').replace(/</g, '&lt;')
    .replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}
