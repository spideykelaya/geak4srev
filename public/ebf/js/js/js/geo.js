import { S, w2s }                   from './state.js';
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
        best = { wmx: (a.x + b.x) / 2, wmy: (a.y + b.y) / 2, len: dist(a, b) };
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
