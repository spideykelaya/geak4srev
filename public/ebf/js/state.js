// Mutable app state — single source of truth
export const S = {
  mode: 'idle', // idle | calibrate_1 | calibrate_2 | calibrate_confirm | draw | measure | drag_vertex
  scale: null,         // meters per world-pixel
  polygons: [],        // [{id, label, points, color, pixelArea, area}]
  measurements: [],    // [{id, pt1, pt2}]
  current: [],         // in-progress polygon world-coords
  calibPt1: null, calibPt2: null,
  measPt1: null,
  image: null, imageW: 0, imageH: 0,
  zoom: 1, panX: 0, panY: 0,
  panning: false, lastMouse: null,
  mouse: null,         // {sx, sy, wx, wy}
  nextId: 1, nextMeasId: 1,
  dragVertex: null,    // {polyIdx, vtxIdx}
  hoverEdge: null,     // {wmx, wmy, len}
};

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
