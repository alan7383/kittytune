import { codeSnippets } from '../data/snippets';

let radarFaceTimer: any = null;

export function updateRadarUI(val: number) {
  const min = 0;
  const max = 1;
  const percent = (val - min) / (max - min);
  const formattedVal = val.toFixed(2);
  const orbitSeconds = 4.0 - (val * 3.5);

  const uiSpeed = document.getElementById('ui-speed-val');
  const codeSpeed = document.getElementById('code-speed-val');
  const codeSpeedChip = document.getElementById('code-speed-chip');
  const orbitDuration = document.getElementById('orbit-duration-val');
  const speedAmount = document.getElementById('speed-amount-val');
  const radarOrbit = document.getElementById('radar-orbit');
  const radarContainer = document.getElementById('radar-container');
  const radarHead = document.getElementById('radar-head');
  
  if (uiSpeed) uiSpeed.textContent = formattedVal;
  if (codeSpeed) codeSpeed.textContent = `${formattedVal}f`;
  if (codeSpeedChip) codeSpeedChip.textContent = `${formattedVal}f`;
  if (orbitDuration) orbitDuration.textContent = `${orbitSeconds.toFixed(2)}s`;
  if (speedAmount) speedAmount.textContent = `${Math.round(percent * 100)}%`;
  if (radarOrbit) radarOrbit.style.animationDuration = `${orbitSeconds}s`;
  if (radarContainer) {
    radarContainer.style.setProperty('--dot-scale', (0.85 + percent * 0.45).toFixed(2));
    radarContainer.style.setProperty('--dot-glow', `${20 + percent * 22}px`);
  }
  if (radarHead) {
    radarHead.innerHTML = `(&gt;_&lt;)`;
    radarHead.classList.add('is-listening');
    if (radarFaceTimer) clearTimeout(radarFaceTimer);
    radarFaceTimer = setTimeout(() => {
      const h = document.getElementById('radar-head');
      if (h) {
        h.innerHTML = `( o_o )`;
        h.classList.remove('is-listening');
      }
    }, 260);
  }
}

export function switchCode(tabId: string, element?: HTMLElement) {
  document.querySelectorAll('.ide-tab').forEach(btn => {
    btn.classList.remove('active');
  });
  if (element) {
    element.classList.add('active');
  } else {
    const targetTab = document.querySelector(`.ide-tab[data-tab="${tabId}"]`) as HTMLElement;
    if (targetTab) targetTab.classList.add('active');
  }
  const display = document.getElementById('code-display');
  if (display && codeSnippets[tabId]) {
    display.innerHTML = codeSnippets[tabId].replace(/\n/g, '<br>').replace(/    /g, '&nbsp;&nbsp;&nbsp;&nbsp;');
  }
  if(tabId === '8d') {
    const speedSlider = document.querySelector('m3-slider[data-axis="speed"]');
    if (speedSlider) {
      const input = speedSlider.querySelector('input') as HTMLInputElement;
      if (input) updateRadarUI(parseFloat(input.value));
    }
  }
}

export function initAudioFX() {
  if (document.getElementById('code-display')) {
    switchCode('8d');
    
    // Valeur par défaut si le slider est présent
    const speedSlider = document.querySelector('m3-slider[data-axis="speed"]');
    if (speedSlider) {
      const input = speedSlider.querySelector('input') as HTMLInputElement;
      if (input) updateRadarUI(parseFloat(input.value));
      
      speedSlider.addEventListener('m3-change', (e: any) => {
        updateRadarUI(e.detail.value);
      });
    }

    document.querySelectorAll('.ide-tab').forEach(tab => {
        tab.addEventListener('click', () => {
            const tabId = (tab as HTMLElement).dataset.tab;
            if (tabId) switchCode(tabId, tab as HTMLElement);
        });
    });
  }
}

document.addEventListener('astro:page-load', initAudioFX);
