const TAG_NAME = 'ebf-calculator';
const PDF_JS_SRC = 'https://cdnjs.cloudflare.com/ajax/libs/pdf.js/3.11.174/pdf.min.js';

let pdfJsLoadPromise = null;

function ensurePdfJsLoaded() {
  if (window.pdfjsLib) return Promise.resolve();
  if (pdfJsLoadPromise) return pdfJsLoadPromise;

  pdfJsLoadPromise = new Promise((resolve, reject) => {
    const script = document.createElement('script');
    script.src = PDF_JS_SRC;
    script.async = true;
    script.onload = () => resolve();
    script.onerror = () => reject(new Error('Failed to load pdf.js'));
    document.head.appendChild(script);
  });

  return pdfJsLoadPromise;
}

const TEMPLATE = `
  <style>
    :host {
      --bg: #09090f;
      --panel: #0f0f17;
      --surface: #141420;
      --raised: #1a1a28;
      --border: rgba(255,255,255,0.07);
      --border-hi: rgba(255,255,255,0.12);
      --text-1: #f0f0f8;
      --text-2: #8888aa;
      --text-3: #44445a;
      --accent: #6d5acd;
      --accent-hi: #9b8af4;
      --green: #34d399;
      --amber: #fbbf24;
      --red: #f87171;
      --r: 7px;

      display: block;
      width: 100%;
      height: 100%;
      min-height: 800px;
      font-family: Inter, system-ui, sans-serif;
      background: var(--bg);
      color: var(--text-1);
      border: 1px solid #ccc;
      border-radius: 8px;
      box-shadow: 0 2px 16px #0002;
      overflow: hidden;
    }

    .app { height: 100% !important; }
  </style>
  <link rel="stylesheet" href="/ebf/styles.css?v=8">

  <div class="app">
    <aside class="sidebar">
      <div class="sidebar-header">
        <h1>EBF-Rechner</h1>
      </div>

      <div class="sidebar-body">
        <div class="section">
          <div class="section-label">Plan</div>
          <label class="btn btn-primary" for="file-input">
            Plan importieren
            <input type="file" id="file-input" accept=".pdf,image/*" hidden>
          </label>
          <div id="file-name" class="file-name"></div>
        </div>

        <div class="section" id="scale-section" style="display:none">
          <div class="section-label">Massstab</div>
          <div id="scale-status" class="scale-status uncalibrated">Nicht kalibriert</div>
          <button class="btn" id="calibrate-btn">Massstab neu kalibrieren</button>
        </div>

        <div class="section" id="draw-section" style="display:none">
          <div class="section-label">Zeichnen</div>
          <button class="btn btn-success btn-draw-main" id="draw-btn">Neues Polygon</button>
          <div class="tools-divider"></div>
          <div class="section-label">Werkzeuge</div>
          <button class="btn btn-measure" id="measure-btn">Distanz messen</button>
          <button class="btn btn-danger" id="clear-btn">Alles loeschen</button>
          <div class="tools-divider"></div>
          <div class="section-label">Export</div>
          <div class="btn-row">
            <button class="btn" id="export-excel-btn">Excel</button>
            <button class="btn" id="export-xml-btn">XML</button>
            <button class="btn" id="print-btn">PDF</button>
          </div>
          <div class="btn-row">
            <button class="btn" id="export-btn">Exportieren</button>
            <button class="btn" id="import-btn">Importieren</button>
            <input type="file" id="import-input" accept=".json" hidden>
          </div>
        </div>

        <div class="section" id="polygons-section" style="display:none">
          <div class="section-label">Polygone</div>
          <ul id="polygon-list"></ul>
          <div class="total" id="total-surface"></div>
        </div>

        <div class="section" id="measurements-section" style="display:none">
          <div class="section-label">Messungen</div>
          <ul id="measurement-list"></ul>
        </div>
      </div>

      <div class="sidebar-footer">
        <div class="shortcuts">
          <div class="shortcut-title">Tastaturkuerzel</div>
          <div class="shortcut-row"><span>Zoom</span><span class="shortcut-key">Mausrad</span></div>
          <div class="shortcut-row"><span>Ansicht verschieben</span><span class="shortcut-key">Klick + Ziehen</span></div>
          <div class="shortcut-row"><span>Abbrechen</span><span class="shortcut-key">Esc</span></div>
          <div class="shortcut-row"><span>Polygon schliessen</span><span class="shortcut-key">Doppelklick</span></div>
        </div>
      </div>
    </aside>

    <main class="canvas-container" id="canvas-container">
      <canvas id="main-canvas"></canvas>
      <div id="instructions"></div>
      <div id="zoom-indicator"></div>
    </main>
  </div>

  <div id="calib-intro-modal" class="dialog-overlay" style="display:none">
    <div class="dialog">
      <h3>Massstabs-Kalibrierung</h3>
      <p>Um Flaechen zu berechnen, muss die App den Massstab des Plans kennen.</p>
      <ol class="calib-steps">
        <li><span>Klicken Sie auf <strong>2 Punkte</strong>, deren echte Entfernung Sie kennen (z. B. eine Wand)</span></li>
        <li><span>Geben Sie die <strong>reale Entfernung</strong> zwischen diesen 2 Punkten ein</span></li>
        <li><span>Der Massstab wird berechnet und die Flaechen werden automatisch angezeigt</span></li>
      </ol>
      <div class="dialog-buttons">
        <button class="btn btn-primary" id="calib-intro-start">Starten</button>
        <button class="btn" id="calib-intro-skip">Ueberspringen</button>
      </div>
    </div>
  </div>

  <div id="clear-confirm-modal" class="dialog-overlay" style="display:none">
    <div class="dialog">
      <h3>Alles loeschen?</h3>
      <p>Alle Polygone und Messungen werden geloescht. Diese Aktion ist nicht umkehrbar.</p>
      <div class="dialog-buttons">
        <button class="btn btn-danger-solid" id="clear-confirm-yes">Loeschen</button>
        <button class="btn" id="clear-confirm-no">Abbrechen</button>
      </div>
    </div>
  </div>

  <div id="scale-dialog" class="dialog-overlay" style="display:none">
    <div class="dialog">
      <h3>Wahre Entfernung</h3>
      <p>Geben Sie die reale Laenge der Linie ein, die Sie gerade gezeichnet haben:</p>
      <div class="input-group">
        <input type="number" id="real-length" min="0.001" step="any" placeholder="z. B. 5">
        <select id="length-unit">
          <option value="1">m</option>
          <option value="0.01">cm</option>
          <option value="0.001">mm</option>
        </select>
      </div>
      <div class="dialog-buttons">
        <button class="btn btn-primary" id="confirm-scale">Bestaetigen</button>
        <button class="btn" id="cancel-scale">Abbrechen</button>
      </div>
    </div>
  </div>
`;

class EbfCalculator extends HTMLElement {
  constructor() {
    super();
    this.attachShadow({ mode: 'open' });
    this.unmount = null;
  }

  async connectedCallback() {
    this.shadowRoot.innerHTML = TEMPLATE;

    try {
      await ensurePdfJsLoaded();
      const module = await import('/ebf/js/main.js');
      this.unmount = module.mountEbf(this.shadowRoot);
    } catch (error) {
      console.error('EBF component failed to initialize', error);
      this.shadowRoot.innerHTML = '<div style="padding:16px;color:#b91c1c">EBF-Rechner konnte nicht geladen werden.</div>';
    }
  }

  disconnectedCallback() {
    if (this.unmount) this.unmount();
  }
}

if (!customElements.get(TAG_NAME)) {
  customElements.define(TAG_NAME, EbfCalculator);
}


