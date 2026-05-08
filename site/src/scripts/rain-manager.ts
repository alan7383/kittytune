import { rainState, isGloballyMuted, isPlaying } from './audio-store';

let audio: HTMLAudioElement;
let fadeInterval: any = null;
let canvas: HTMLCanvasElement;
let ctx: CanvasRenderingContext2D | null;
let rainDrops: any[] = [];
let rainAnimationId: number | null = null;
let canvasW: number, canvasH: number;

function resizeCanvas() {
  canvasW = window.innerWidth;
  canvasH = window.innerHeight;
  if (canvas) {
    canvas.width = canvasW;
    canvas.height = canvasH;
  }
}

function initRaindrops() {
  rainDrops = [];
  const baseDrops = rainState.get() === 'heavy' ? 80 : 25;
  const count = Math.floor(baseDrops * (canvasW / 1200)); 
  for (let i = 0; i < count; i++) {
    rainDrops.push({
      x: Math.random() * canvasW,
      y: Math.random() * canvasH,
      l: Math.random() * 20 + (rainState.get() === 'heavy' ? 30 : 15),
      xs: -2 + Math.random() * 1.5,
      ys: Math.random() * 15 + (rainState.get() === 'heavy' ? 20 : 10)
    });
  }
}

function drawRain() {
  if (!ctx) return;
  ctx.clearRect(0, 0, canvasW, canvasH);
  const isLight = document.documentElement.classList.contains('light-mode');
  if (rainState.get() === 'heavy') {
    ctx.strokeStyle = isLight ? 'rgba(15, 30, 60, 0.15)' : 'rgba(200, 220, 255, 0.25)';
    ctx.lineWidth = 1.5;
  } else {
    ctx.strokeStyle = isLight ? 'rgba(15, 30, 60, 0.08)' : 'rgba(255, 255, 255, 0.1)';
    ctx.lineWidth = 1;
  }
  ctx.lineCap = 'round';
  ctx.beginPath();
  for (let i = 0; i < rainDrops.length; i++) {
    let p = rainDrops[i];
    ctx.moveTo(p.x, p.y);
    ctx.lineTo(p.x + p.xs, p.y + p.ys);
    p.x += p.xs;
    p.y += p.ys;
    if (p.x > canvasW || p.y > canvasH) {
      p.x = Math.random() * canvasW;
      p.y = -20;
    }
  }
  ctx.stroke();
  rainAnimationId = requestAnimationFrame(drawRain);
}

export function updateBentoUI() {
  const btn = document.getElementById('rain-btn');
  const panel = document.getElementById('rain-panel');
  const face = document.getElementById('rain-face');
  if (btn && panel) {
    const isRainActive = rainState.get() !== 'none' && !isGloballyMuted.get();
    if (isRainActive) {
      panel.classList.add('rain-active');
      btn.textContent = 'Stop Ambience';
      btn.style.background = '#6eb5ff';
      btn.style.color = '#00254d';
      if (face) face.textContent = '( ˘︶˘ )';
    } else {
      panel.classList.remove('rain-active');
      btn.textContent = 'Mix Ambience';
      btn.style.background = 'transparent';
      btn.style.color = 'white';
      if (face) face.textContent = '[ /// ]';
    }
  }
}

export function updateMiniplayerUI() {
  const miniplayers = document.querySelectorAll('.rain-miniplayer');
  miniplayers.forEach(mp => {
    const isRainPlaying = rainState.get() !== 'none';
    const isMusicPlaying = isPlaying.get();
    if (isRainPlaying || isMusicPlaying) {
      mp.classList.add('playing');
      mp.classList.remove('muted'); 
      const icon = mp.querySelector('.material-icons-round');
      if (icon) {
        if (isMusicPlaying && isRainPlaying && !isGloballyMuted.get()) {
          icon.textContent = 'graphic_eq'; 
        } else if (isMusicPlaying) {
          icon.textContent = 'music_note'; 
        } else {
          icon.textContent = isGloballyMuted.get() ? 'volume_off' : 'cloud_sync';
          if (isGloballyMuted.get() && !isMusicPlaying) mp.classList.add('muted');
        }
      }
    } else {
      mp.classList.remove('playing');
      mp.classList.remove('muted');
    }
  });
}

function fadeToVolume(target: number, duration = 1500) {
  if (fadeInterval) clearInterval(fadeInterval);
  if (isGloballyMuted.get() && target > 0) target = 0;
  if (target > 0) {
    audio.play().catch(e => console.log('Audio block:', e));
  }
  const startVol = audio.volume;
  const diff = target - startVol;
  if (diff === 0) return;
  const steps = 30;
  const stepTime = duration / steps;
  let stepCount = 0;
  fadeInterval = setInterval(() => {
    stepCount++;
    let newVol = startVol + (diff * (stepCount / steps));
    newVol = Math.max(0, Math.min(1, newVol));
    try { audio.volume = newVol; } catch(e){}
    if (stepCount >= steps) {
      clearInterval(fadeInterval);
      audio.volume = target;
      if (target === 0) {
        audio.pause();
      }
    }
  }, stepTime);
}

export function setRainState(state: 'none' | 'light' | 'heavy') {
  rainState.set(state);
  if (state !== 'none' && !isGloballyMuted.get()) {
    canvas.style.opacity = '1';
    initRaindrops();
    if (!rainAnimationId) drawRain();
  } else {
    canvas.style.opacity = '0';
    setTimeout(() => {
      if (rainState.get() === 'none' || isGloballyMuted.get()) {
        if (rainAnimationId) cancelAnimationFrame(rainAnimationId);
        rainAnimationId = null;
      }
    }, 1500);
  }
  
  if (state === 'heavy') fadeToVolume(0.15, 1500);
  else if (state === 'light') fadeToVolume(0.05, 2000);
  else fadeToVolume(0, 1500);

  updateBentoUI();
  updateMiniplayerUI();
}

export function toggleRain() {
  const isRainActive = rainState.get() !== 'none' && !isGloballyMuted.get();
  if (isRainActive) {
    setRainState('none');
  } else {
    if(isGloballyMuted.get()) {
      isGloballyMuted.set(false);
      localStorage.removeItem('kitty_rain_volume_enabled');
    }
    setRainState('heavy');
  }
}

export function toggleRainMute() {
  const isRainPlaying = rainState.get() !== 'none';
  if (isPlaying.get() && isRainPlaying) {
    isGloballyMuted.set(!isGloballyMuted.get());
    localStorage.setItem('kitty_rain_volume_enabled', isGloballyMuted.get() ? 'muted' : 'unmuted');
    setRainState(rainState.get());
  } else if (isRainPlaying) {
    isGloballyMuted.set(!isGloballyMuted.get());
    localStorage.setItem('kitty_rain_volume_enabled', isGloballyMuted.get() ? 'muted' : 'unmuted');
    setRainState(rainState.get());
  }
}

function initRain() {
  audio = document.getElementById('global-rain-audio') as HTMLAudioElement;
  canvas = document.getElementById('global-rain-canvas') as HTMLCanvasElement;
  
  if (canvas) {
    ctx = canvas.getContext('2d');
    resizeCanvas();
    window.addEventListener('resize', resizeCanvas);
  }
  
  updateBentoUI();
  updateMiniplayerUI();
}

document.addEventListener('astro:page-load', () => {
  initRain();
  
  const rainBtn = document.getElementById('rain-btn');
  if (rainBtn) rainBtn.addEventListener('click', toggleRain);
  
  const miniplayers = document.querySelectorAll('.rain-miniplayer');
  miniplayers.forEach(mp => {
      mp.addEventListener('click', toggleRainMute);
  });
});

isPlaying.subscribe(updateMiniplayerUI);
rainState.subscribe(() => {
  updateBentoUI();
  updateMiniplayerUI();
});
isGloballyMuted.subscribe(() => {
  updateBentoUI();
  updateMiniplayerUI();
});
