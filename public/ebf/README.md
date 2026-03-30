# plan-surface

> Measure areas and distances on architectural floor plans. Upload a PDF or image, calibrate the scale, draw polygons, and get instant surface calculations.

A lightweight, privacy-first tool that runs entirely in the browser — no server, no data collection, no account required.

---

## Features

- **Import plans** — PDF (first page) or any image format (PNG, JPG, WEBP…)
- **Scale calibration** — click two known points, enter the real-world distance
- **Draw polygons** — click to place vertices, snap to close, supports any shape including concave ones (L, U, T…)
- **Move vertices** — drag any vertex of a completed polygon to adjust it; area recalculates instantly
- **Measure distances** — drop two points to measure a segment, displayed on canvas and in sidebar
- **Edge length on hover** — hover near any polygon side to see its length
- **Rename polygons** — click the name in the sidebar to edit it inline
- **Export / Import** — save your work as JSON (polygons, measurements, scale) and reload it later
- **Print / PDF** — generates a clean report with the annotated plan, a summary table, and legal notices
- **Zoom & pan** — mouse wheel to zoom, click-drag to pan, Alt+drag also works

---

## Getting Started

No build step. Just open `index.html` in a browser, or serve the folder with any static server:

```bash
# Python
python3 -m http.server 8080

# Node (npx)
npx serve .
```

Then open [http://localhost:8080](http://localhost:8080).

---

## Usage

### 1. Import a plan
Click **Importer un plan** and select a PDF or image file.

### 2. Calibrate the scale
Click **Calibrer l'echelle**, then click two points on the plan whose real-world distance you know (e.g. a wall marked "5 m"). Enter the value and confirm.

### 3. Draw a surface
Click **+ Nouveau polygone**, then click to place each vertex. Close the polygon by double-clicking or clicking back on the first vertex. The area is displayed immediately.

### 4. Measure a distance
Click **Mesurer une distance**, then click two points. The segment and its length appear on the plan.

### 5. Edit
- **Move a vertex** — hover over it (cursor changes to a cross) then drag
- **Rename a polygon** — click its name in the sidebar
- **Delete** — click the × button next to any polygon or measurement

### 6. Export / Import
- **Exporter** — downloads a `.json` file with all polygons, measurements, and the scale
- **Importer** — restores a previously exported session (re-import the plan image separately)

### 7. Print
Click **Imprimer / PDF** to open a print-ready page with the annotated plan and a summary table. Use the browser's print dialog to save as PDF.

---

## Keyboard Shortcuts

| Key | Action |
|-----|--------|
| `Scroll` | Zoom in / out |
| `Click + drag` | Pan |
| `Alt + drag` | Pan (alternative) |
| `Double-click` | Close polygon |
| `Right-click` / `Esc` | Cancel current action |

---

## Privacy

All processing happens locally in your browser.
No image, measurement, or personal data is ever sent to a server or stored anywhere outside your device.

---

## Stack

- Vanilla JS (ES2020, no framework, no bundler)
- HTML5 Canvas API
- [PDF.js](https://mozilla.github.io/pdf.js/) for PDF rendering

---

## Legal

This tool is provided for informational purposes only. Measurements may be inaccurate and should not be used for legal, administrative, or technical purposes without verification by a qualified professional. The author accepts no liability for measurement errors or misuse of the results.
