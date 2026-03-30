import { S, canvas, ctx, w2s }                          from './state.js';
import { COLORS, CLOSE_VERTEX_RADIUS, SNAP_RADIUS, MEAS_COLOR } from './config.js';
import { dist, labelPoint, clamp, fmtArea, fmtLength }   from './geo.js';

// ── Entry point ───────────────────────────────────────────────────────────────
export function render() {
  ctx.clearRect(0, 0, canvas.width, canvas.height);
  ctx.fillStyle = '#111122';
  ctx.fillRect(0, 0, canvas.width, canvas.height);

  if (!S.image) {
    ctx.fillStyle = '#2d2d4a';
    ctx.font = '16px system-ui, sans-serif';
    ctx.textAlign = 'center'; ctx.textBaseline = 'middle';
    ctx.fillText('Importieren Sie einen Plan, um zu beginnen', canvas.width / 2, canvas.height / 2);
    return;
  }

  ctx.save();
  ctx.setTransform(S.zoom, 0, 0, S.zoom, S.panX, S.panY);
  ctx.drawImage(S.image, 0, 0);
  S.polygons.forEach(drawPolygon);
  S.measurements.forEach(drawMeasurement);
  drawCalibLine();
  drawCurrentPolygon();
  drawMeasureLine();
  ctx.restore();

  drawEdgeLengthTooltip(); // screen-space overlay
}

// ── Polygon ───────────────────────────────────────────────────────────────────
function drawPolygon({ points, color, area }) {
  if (points.length < 2) return;

  ctx.beginPath();
  ctx.moveTo(points[0].x, points[0].y);
  points.slice(1).forEach(p => ctx.lineTo(p.x, p.y));
  ctx.closePath();

  ctx.fillStyle = color + '50';
  ctx.fill();

  // Double-stroke: white outline for contrast on any background
  ctx.setLineDash([]);
  ctx.strokeStyle = 'rgba(255,255,255,0.7)'; ctx.lineWidth = 4 / S.zoom; ctx.stroke();
  ctx.strokeStyle = color;                   ctx.lineWidth = 2 / S.zoom; ctx.stroke();

  points.forEach(p => dot(p, color, CLOSE_VERTEX_RADIUS / S.zoom));

  const c = labelPoint(points), lbl = fmtArea(area), fsz = clamp(14 / S.zoom, 10, 36);
  ctx.font = `bold ${fsz}px system-ui, sans-serif`;
  ctx.textAlign = 'center'; ctx.textBaseline = 'middle';
  const tw = ctx.measureText(lbl).width, th = fsz * 1.5;
  ctx.fillStyle = 'rgba(0,0,0,0.65)';
  rrect(c.x - tw / 2 - 6 / S.zoom, c.y - th / 2, tw + 12 / S.zoom, th, 4 / S.zoom);
  ctx.fill();
  ctx.fillStyle = '#fff';
  ctx.fillText(lbl, c.x, c.y);
}

// ── Measurements ──────────────────────────────────────────────────────────────
function drawMeasurement({ pt1, pt2 }) {
  const lbl = S.scale ? fmtLength(dist(pt1, pt2) * S.scale) : dist(pt1, pt2).toFixed(1) + ' px';
  measLine(pt1, pt2, lbl);
}

function drawMeasureLine() {
  if (S.mode !== 'measure' || !S.measPt1 || !S.mouse) return;
  const pt2 = { x: S.mouse.wx, y: S.mouse.wy };
  const lbl = S.scale ? fmtLength(dist(S.measPt1, pt2) * S.scale) : dist(S.measPt1, pt2).toFixed(1) + ' px';
  measLine(S.measPt1, pt2, lbl);
}

function measLine(pt1, pt2, lbl) {
  ctx.strokeStyle = MEAS_COLOR; ctx.lineWidth = 1.5 / S.zoom;
  ctx.setLineDash([6 / S.zoom, 4 / S.zoom]);
  ctx.beginPath(); ctx.moveTo(pt1.x, pt1.y); ctx.lineTo(pt2.x, pt2.y);
  ctx.stroke(); ctx.setLineDash([]);
  dot(pt1, MEAS_COLOR, 4 / S.zoom); dot(pt2, MEAS_COLOR, 4 / S.zoom);

  const mx = (pt1.x + pt2.x) / 2, my = (pt1.y + pt2.y) / 2;
  const fsz = clamp(12 / S.zoom, 9, 28);
  ctx.font = `bold ${fsz}px system-ui, sans-serif`;
  ctx.textAlign = 'center'; ctx.textBaseline = 'middle';
  const tw = ctx.measureText(lbl).width, th = fsz * 1.5;
  ctx.fillStyle = 'rgba(0,0,0,0.7)';
  rrect(mx - tw / 2 - 5 / S.zoom, my - th / 2, tw + 10 / S.zoom, th, 3 / S.zoom);
  ctx.fill();
  ctx.fillStyle = MEAS_COLOR; ctx.fillText(lbl, mx, my);
}

// ── Edge length tooltip (screen-space) ────────────────────────────────────────
function drawEdgeLengthTooltip() {
  if (!S.hoverEdge) return;
  const { wmx, wmy, len } = S.hoverEdge;
  const sm  = w2s(wmx, wmy);
  const lbl = S.scale ? fmtLength(len * S.scale) : len.toFixed(1) + ' px';
  const fsz = 12, pad = 7, th = fsz * 1.6;
  ctx.save();
  ctx.font = `bold ${fsz}px system-ui, sans-serif`;
  ctx.textAlign = 'center'; ctx.textBaseline = 'middle';
  const tw = ctx.measureText(lbl).width;
  const rx = sm.x - tw / 2 - pad, ry = sm.y - th - 12;
  ctx.fillStyle = 'rgba(10,10,30,0.9)'; ctx.strokeStyle = MEAS_COLOR; ctx.lineWidth = 1;
  rrect(rx, ry, tw + pad * 2, th, 4); ctx.fill(); ctx.stroke();
  ctx.fillStyle = MEAS_COLOR; ctx.fillText(lbl, sm.x, ry + th / 2);
  ctx.restore();
}

// ── Calibration line ──────────────────────────────────────────────────────────
function drawCalibLine() {
  if (!S.calibPt1) return;
  const end = S.calibPt2 || (S.mouse ? { x: S.mouse.wx, y: S.mouse.wy } : null);
  if (!end) return;
  ctx.strokeStyle = MEAS_COLOR; ctx.lineWidth = 2 / S.zoom;
  ctx.setLineDash([8 / S.zoom, 5 / S.zoom]);
  ctx.beginPath(); ctx.moveTo(S.calibPt1.x, S.calibPt1.y); ctx.lineTo(end.x, end.y);
  ctx.stroke(); ctx.setLineDash([]);
  dot(S.calibPt1, MEAS_COLOR, 5 / S.zoom);
  if (S.calibPt2) dot(S.calibPt2, MEAS_COLOR, 5 / S.zoom);
}

// ── In-progress polygon ───────────────────────────────────────────────────────
function drawCurrentPolygon() {
  if (!S.current.length) return;
  const color = COLORS[(S.nextId - 1) % COLORS.length];
  const r     = CLOSE_VERTEX_RADIUS / S.zoom;
  const snap  = S.current.length >= 3 && S.mouse && (() => {
    const fs = w2s(S.current[0].x, S.current[0].y);
    return Math.hypot(fs.x - S.mouse.sx, fs.y - S.mouse.sy) <= SNAP_RADIUS;
  })();

  if (S.current.length >= 3 && S.mouse) {
    ctx.beginPath(); ctx.moveTo(S.current[0].x, S.current[0].y);
    S.current.slice(1).forEach(p => ctx.lineTo(p.x, p.y));
    if (!snap) ctx.lineTo(S.mouse.wx, S.mouse.wy);
    ctx.closePath(); ctx.fillStyle = color + '1a'; ctx.fill();
  }

  ctx.strokeStyle = color; ctx.lineWidth = 1.5 / S.zoom; ctx.setLineDash([]);
  ctx.beginPath(); ctx.moveTo(S.current[0].x, S.current[0].y);
  S.current.slice(1).forEach(p => ctx.lineTo(p.x, p.y));
  if (S.mouse && !snap) ctx.lineTo(S.mouse.wx, S.mouse.wy);
  ctx.stroke();

  if (snap) {
    ctx.strokeStyle = '#fff'; ctx.lineWidth = 2 / S.zoom;
    ctx.beginPath(); ctx.arc(S.current[0].x, S.current[0].y, SNAP_RADIUS / S.zoom, 0, Math.PI * 2); ctx.stroke();
    ctx.strokeStyle = color; ctx.lineWidth = 1.5 / S.zoom; ctx.setLineDash([4 / S.zoom, 4 / S.zoom]);
    ctx.beginPath();
    ctx.moveTo(S.current[S.current.length - 1].x, S.current[S.current.length - 1].y);
    ctx.lineTo(S.current[0].x, S.current[0].y);
    ctx.stroke(); ctx.setLineDash([]);
  }

  S.current.forEach((p, i) => dot(p, i === 0 ? '#fff' : color, r));

  // Live length label on the in-progress segment
  if (S.mouse && !snap && S.current.length >= 1) {
    const last = S.current[S.current.length - 1];
    const cur  = { x: S.mouse.wx, y: S.mouse.wy };
    const d    = dist(last, cur);
    if (d > 2 / S.zoom) {
      const lbl = S.scale ? fmtLength(d * S.scale) : d.toFixed(0) + ' px';
      const mx  = (last.x + cur.x) / 2, my = (last.y + cur.y) / 2;
      const fsz = clamp(11 / S.zoom, 8, 22);
      ctx.font = `600 ${fsz}px system-ui, sans-serif`;
      ctx.textAlign = 'center'; ctx.textBaseline = 'middle';
      const tw = ctx.measureText(lbl).width, th = fsz * 1.5;
      ctx.fillStyle = 'rgba(0,0,0,0.7)';
      rrect(mx - tw / 2 - 5 / S.zoom, my - th / 2, tw + 10 / S.zoom, th, 3 / S.zoom);
      ctx.fill();
      ctx.fillStyle = color; ctx.fillText(lbl, mx, my);
    }
  }
}

// ── Canvas primitives ─────────────────────────────────────────────────────────
function dot(p, color, r) {
  ctx.beginPath(); ctx.arc(p.x, p.y, r, 0, Math.PI * 2);
  ctx.fillStyle = color; ctx.fill();
}

function rrect(x, y, w, h, radius) {
  ctx.beginPath();
  ctx.moveTo(x + radius, y); ctx.lineTo(x + w - radius, y);
  ctx.quadraticCurveTo(x + w, y, x + w, y + radius);
  ctx.lineTo(x + w, y + h - radius);
  ctx.quadraticCurveTo(x + w, y + h, x + w - radius, y + h);
  ctx.lineTo(x + radius, y + h);
  ctx.quadraticCurveTo(x, y + h, x, y + h - radius);
  ctx.lineTo(x, y + radius);
  ctx.quadraticCurveTo(x, y, x + radius, y);
  ctx.closePath();
}
