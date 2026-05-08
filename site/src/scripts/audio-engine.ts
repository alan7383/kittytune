const codeSnippets: Record<string, string> = {
  '8d': `
<span style="color: #7f848e;">
<span style="color: #c678dd;">val</span> rotationSpeed = (<span style="color: #d19a66;">0.000002</span> + <span class="code-highlight" id="code-speed-val" style="background: rgba(208, 188, 255, 0.15); color: #d0bcff;">0.50f</span> * <span style="color: #d19a66;">0.000038</span>)
<span style="color: #7f848e;">
time += rotationSpeed
<span style="color: #c678dd;">val</span> pan = sin(time) 
<span style="color: #7f848e;">
<span style="color: #c678dd;">val</span> leftVol = (<span style="color: #d19a66;">1.0</span> - pan) / <span style="color: #d19a66;">2.0</span>
<span style="color: #c678dd;">val</span> rightVol = (<span style="color: #d19a66;">1.0</span> + pan) / <span style="color: #d19a66;">2.0</span>
<span style="color: #c678dd;">val</span> newLeft = (originalLeft * leftVol).toInt().toShort()
<span style="color: #c678dd;">val</span> newRight = (originalRight * rightVol).toInt().toShort()`,
  'biquad': `
<span style="color: #7f848e;">
<span style="color: #c678dd;">val</span> A = Math.pow(<span style="color: #d19a66;">10.0</span>, gain / <span style="color: #d19a66;">40.0</span>).toFloat()
<span style="color: #c678dd;">val</span> w0 = (<span style="color: #d19a66;">2.0</span> * PI * <span style="color: #d19a66;">100f</span> / sampleRate).toFloat()
<span style="color: #c678dd;">val</span> alpha = sin(w0) / <span style="color: #d19a66;">2f</span> * Math.sqrt(((A + <span style="color: #d19a66;">1f</span>/A)*(<span style="color: #d19a66;">1f</span>/S - <span style="color: #d19a66;">1f</span>)+<span style="color: #d19a66;">2f</span>).toDouble()).toFloat()
<span style="color: #c678dd;">val</span> a0 = (A + <span style="color: #d19a66;">1f</span>) + (A - <span style="color: #d19a66;">1f</span>) * cos(w0) + beta
<span style="color: #c678dd;">val</span> b0 = (A * ((A + <span style="color: #d19a66;">1f</span>) - (A - <span style="color: #d19a66;">1f</span>) * cos(w0) + beta)) / a0
<span style="color: #7f848e;">
<span style="color: #7f848e;">
<span style="color: #c678dd;">val</span> y = b0*x + b1*x1 + b2*x2 - a1*y1 - a2*y2
<span style="color: #7f848e;">
x2 = x1; x1 = x
y2 = y1; y1 = y
<span style="color: #7f848e;">
output = y.coerceIn(Short.MIN_VALUE.toFloat(), Short.MAX_VALUE.toFloat()).toInt().toShort()`,
  'reverb': `
<span style="color: #7f848e;">
<span style="color: #c678dd;">val</span> delayedSample = buffer[cursor]
<span style="color: #7f848e;">
<span style="color: #c678dd;">val</span> outputSample = (inputSample + delayedSample * decay)
    .toInt()
    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
    .toShort()
<span style="color: #7f848e;">
buffer[cursor] = outputSample
<span style="color: #7f848e;">
cursor++
<span style="color: #c678dd;">if</span> (cursor >= buffer.size) cursor = <span style="color: #d19a66;">0</span>`,
  'muffled': `
<span style="color: #7f848e;">
<span style="color: #c678dd;">val</span> w0 = (<span style="color: #d19a66;">2.0</span> * PI * <span style="color: #d19a66;">300f</span> / sampleRate).toFloat()
<span style="color: #c678dd;">val</span> cosW0 = cos(w0)
<span style="color: #c678dd;">val</span> alpha = sin(w0) / (<span style="color: #d19a66;">2f</span> * <span style="color: #d19a66;">0.5f</span>) <span style="color: #7f848e;">
<span style="color: #7f848e;">
<span style="color: #c678dd;">val</span> a0 = <span style="color: #d19a66;">1f</span> + alpha
<span style="color: #c678dd;">val</span> b0 = ((<span style="color: #d19a66;">1f</span> - cosW0) / <span style="color: #d19a66;">2f</span>) / a0
<span style="color: #c678dd;">val</span> b1 = (<span style="color: #d19a66;">1f</span> - cosW0) / a0
<span style="color: #7f848e;">
<span style="color: #7f848e;">
<span style="color: #c678dd;">val</span> y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
<span style="color: #7f848e;">
x2 = x1; x1 = x
y2 = y1; y1 = y
output = y.toInt().toShort()`
};

let radarFaceTimer: any = null;

export function updateM3Slider() {
  const sliderInput = document.getElementById('speed-slider') as HTMLInputElement;
  if (!sliderInput) return;
  // FIX: On cible précisément le composant avec la variable CSS
  const sliderEl = sliderInput.closest('[data-slider-root]') as HTMLElement; 
  if (!sliderEl) return;
  
  const min = parseFloat(sliderInput.min) || 0;
  const max = parseFloat(sliderInput.max) || 1;
  const val = parseFloat(sliderInput.value);
  const percent = (val - min) / (max - min);
  
  // FIX: On met à jour l'élément exact !
  sliderEl.style.setProperty('--val', percent.toString());
  
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
    updateM3Slider();
  }
}

export function initAudioFX() {
  if (document.getElementById('code-display')) {
    updateM3Slider();
    switchCode('8d');
    
    const sliderInput = document.getElementById('speed-slider') as HTMLInputElement;
    if (sliderInput) {
      // FIX: On attache les events au bon élément
      const sliderEl = sliderInput.closest('[data-slider-root]') as HTMLElement;
      if (sliderEl) {
        sliderInput.addEventListener('pointerdown', () => {
          sliderEl.classList.add('is-dragging');
          sliderEl.classList.add('no-transition');
          sliderInput.dataset.inputCount = "0";
        });
        window.addEventListener('pointerup', () => {
          sliderEl.classList.remove('is-dragging');
          sliderEl.classList.remove('no-transition');
        });
        sliderInput.addEventListener('input', (e) => {
          const count = parseInt(sliderInput.dataset.inputCount || "0") + 1;
          sliderInput.dataset.inputCount = count.toString();
          if (count > 1) {
            sliderEl.classList.add('no-transition');
          }
          updateM3Slider();
        });
      }
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
