import { S, canvas, ctx, w2s, pxVecToM }                          from './state.js';
import { CLOSE_VERTEX_RADIUS, SNAP_RADIUS, MEAS_COLOR, ANGLE_COLOR } from './config.js';
import { labelPoint, clamp, fmtArea, fmtLength }          from './geo.js';
import { colorForCurrentAreaType }                        from './sidebar.js';

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
  S.angles.forEach(drawAngle);
  S.annotations.forEach(drawAnnotation);
  drawCalibLine();
  drawCurrentPolygon();
  drawMeasureLine();
  drawAngleLine();
  ctx.restore();

  drawEdgeLengthTooltip(); // screen-space overlay
}

// ── Polygon ───────────────────────────────────────────────────────────────────
function drawPolygon(poly) {
  const { points, color, area, label, labelOffset } = poly;
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

  const c = labelPoint(points);
  const lx = c.x + (labelOffset?.dx || 0);
  const ly = c.y + (labelOffset?.dy || 0);
  const areaLbl = fmtArea(area);
  const titleLbl = (label || '').trim();
  const fsz = clamp(14 / S.zoom, 10, 36);
  ctx.font = `bold ${fsz}px system-ui, sans-serif`;
  ctx.textAlign = 'center'; ctx.textBaseline = 'middle';
  const titleW = titleLbl ? ctx.measureText(titleLbl).width : 0;
  const areaW = ctx.measureText(areaLbl).width;
  const tw = Math.max(titleW, areaW);
  const lines = titleLbl ? 2 : 1;
  const lineH = fsz * 1.25;
  const th = lineH * lines + (6 / S.zoom);
  ctx.fillStyle = 'rgba(0,0,0,0.65)';
  rrect(lx - tw / 2 - 6 / S.zoom, ly - th / 2, tw + 12 / S.zoom, th, 4 / S.zoom);
  ctx.fill();
  ctx.fillStyle = '#fff';
  if (titleLbl) {
    ctx.fillText(titleLbl, lx, ly - lineH / 2);
    ctx.fillText(areaLbl, lx, ly + lineH / 2);
  } else {
    ctx.fillText(areaLbl, lx, ly);
  }
}

// ── Measurements ──────────────────────────────────────────────────────────────
function drawMeasurement({ pt1, pt2 }) {
  const dx = pt2.x - pt1.x, dy = pt2.y - pt1.y;
  const realLen = pxVecToM(dx, dy);
  const lbl = realLen !== null ? fmtLength(realLen) : Math.hypot(dx, dy).toFixed(1) + ' px';
  measLine(pt1, pt2, lbl);
}

function drawMeasureLine() {
  if (S.mode !== 'measure' || !S.measPt1 || !S.mouse) return;
  const pt2 = { x: S.mouse.wx, y: S.mouse.wy };
  const dx = pt2.x - S.measPt1.x, dy = pt2.y - S.measPt1.y;
  const realLen = pxVecToM(dx, dy);
  const lbl = realLen !== null ? fmtLength(realLen) : Math.hypot(dx, dy).toFixed(1) + ' px';
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

// ── Angle measurements ────────────────────────────────────────────────────────
/** Angle in degrees between two arms meeting at vertex. */
function angleBetween(vertex, pt1, pt2) {
  const ax = pt1.x - vertex.x, ay = pt1.y - vertex.y;
  const bx = pt2.x - vertex.x, by = pt2.y - vertex.y;
  const dot   = ax * bx + ay * by;
  const cross = ax * by - ay * bx;
  return Math.atan2(Math.abs(cross), dot) * 180 / Math.PI;
}

function fmtAngle(deg) { return deg.toFixed(1) + '°'; }

function drawAngle({ vertex, pt1, pt2 }) {
  angleShape(vertex, pt1, pt2);
}

function drawAngleLine() {
  if (S.mode !== 'angle' || !S.mouse) return;
  const mouse = { x: S.mouse.wx, y: S.mouse.wy };

  if (!S.anglePt1) return; // waiting for vertex — nothing to draw yet

  if (!S.anglePt2) {
    // Vertex set, dragging arm 1 — draw one dashed arm
    angleArm(S.anglePt1, mouse);
    dot(S.anglePt1, ANGLE_COLOR, 4 / S.zoom);
  } else {
    // Both vertex and arm 1 set — draw full preview with live arm 2
    angleShape(S.anglePt1, S.anglePt2, mouse);
  }
}

/** Draw complete angle: two arms + arc + label. */
function angleShape(vertex, pt1, pt2) {
  const deg = angleBetween(vertex, pt1, pt2);

  angleArm(vertex, pt1);
  angleArm(vertex, pt2);
  dot(vertex, ANGLE_COLOR, 4 / S.zoom);
  dot(pt1, ANGLE_COLOR, 3 / S.zoom);
  dot(pt2, ANGLE_COLOR, 3 / S.zoom);

  // Arc at vertex
  const ax = pt1.x - vertex.x, ay = pt1.y - vertex.y;
  const bx = pt2.x - vertex.x, by = pt2.y - vertex.y;
  const cross = ax * by - ay * bx;
  const r = Math.min(28 / S.zoom, Math.hypot(ax, ay) * 0.35, Math.hypot(bx, by) * 0.35);
  if (r > 1 / S.zoom) {
    ctx.beginPath();
    ctx.arc(vertex.x, vertex.y, r, Math.atan2(ay, ax), Math.atan2(by, bx), cross < 0);
    ctx.strokeStyle = ANGLE_COLOR; ctx.lineWidth = 1.5 / S.zoom; ctx.setLineDash([]); ctx.stroke();
  }

  // Label near arc midpoint
  const midA = (Math.atan2(ay, ax) + Math.atan2(by, bx)) / 2 + (cross < 0 ? Math.PI : 0);
  const lr = r + 14 / S.zoom;
  const lx = vertex.x + Math.cos(midA) * lr;
  const ly = vertex.y + Math.sin(midA) * lr;
  const lbl = fmtAngle(deg);
  const fsz = clamp(12 / S.zoom, 9, 28);
  ctx.font = `bold ${fsz}px system-ui, sans-serif`;
  ctx.textAlign = 'center'; ctx.textBaseline = 'middle';
  const tw = ctx.measureText(lbl).width, th = fsz * 1.5;
  ctx.fillStyle = 'rgba(0,0,0,0.75)';
  rrect(lx - tw / 2 - 5 / S.zoom, ly - th / 2, tw + 10 / S.zoom, th, 3 / S.zoom);
  ctx.fill();
  ctx.fillStyle = ANGLE_COLOR; ctx.fillText(lbl, lx, ly);
}

function angleArm(from, to) {
  ctx.strokeStyle = ANGLE_COLOR; ctx.lineWidth = 1.5 / S.zoom;
  ctx.setLineDash([6 / S.zoom, 4 / S.zoom]);
  ctx.beginPath(); ctx.moveTo(from.x, from.y); ctx.lineTo(to.x, to.y);
  ctx.stroke(); ctx.setLineDash([]);
}

// ── Edge length tooltip (screen-space) ────────────────────────────────────────
function drawEdgeLengthTooltip() {
  if (!S.hoverEdge) return;
  const { wmx, wmy, dx, dy, len } = S.hoverEdge;
  const sm  = w2s(wmx, wmy);
  const realLen = pxVecToM(dx, dy);
  const lbl = realLen !== null ? fmtLength(realLen) : len.toFixed(1) + ' px';
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
  const color = colorForCurrentAreaType();
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
    const dx = cur.x - last.x, dy = cur.y - last.y;
    const d  = Math.hypot(dx, dy);
    if (d > 2 / S.zoom) {
      const realLen = pxVecToM(dx, dy);
      const lbl = realLen !== null ? fmtLength(realLen) : d.toFixed(0) + ' px';
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

// ── Annotations ───────────────────────────────────────────────────────────────
function drawAnnotation(ann) {
  const text = ann.text || '';
  if (!text.trim()) return;
  const lines  = text.split('\n');
  const fsz    = ann.fontSize || 16;
  const lineH  = fsz * 1.45;
  const pad    = 8 / S.zoom;
  ctx.font = `${fsz}px system-ui, sans-serif`;
  ctx.textAlign = 'left'; ctx.textBaseline = 'top';
  const maxW = Math.max(...lines.map(l => ctx.measureText(l).width));
  const boxW = maxW + pad * 2;
  const boxH = lineH * lines.length + pad * 1.5;
  // White background
  ctx.fillStyle = '#fff';
  rrect(ann.x, ann.y, boxW, boxH, 5 / S.zoom);
  ctx.fill();
  // Black border
  ctx.strokeStyle = '#000'; ctx.lineWidth = 1.5 / S.zoom; ctx.setLineDash([]);
  ctx.stroke();
  // Dark text
  ctx.fillStyle = '#111';
  lines.forEach((line, i) => ctx.fillText(line, ann.x + pad, ann.y + pad * 0.75 + i * lineH));
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
