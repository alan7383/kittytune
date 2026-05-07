import { audioStore } from './audio-store';

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
  const baseDrops = audioStore.rainState === 'heavy' ? 80 : 25;
  const count = Math.floor(baseDrops * (canvasW / 1200)); 
  for (let i = 0; i < count; i++) {
    rainDrops.push({
      x: Math.random() * canvasW,
      y: Math.random() * canvasH,
      l: Math.random() * 20 + (audioStore.rainState === 'heavy' ? 30 : 15),
      xs: -2 + Math.random() * 1.5,
      ys: Math.random() * 15 + (audioStore.rainState === 'heavy' ? 20 : 10)
    });
  }
}

function drawRain() {
  if (!ctx) return;
  ctx.clearRect(0, 0, canvasW, canvasH);
  const isLight = document.documentElement.classList.contains('light-mode');
  if (audioStore.rainState === 'heavy') {
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
    const isRainActive = audioStore.rainState !== 'none' && !audioStore.isGloballyMuted;
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
    const isRainPlaying = audioStore.rainState !== 'none';
    const isMusicPlaying = audioStore.isPlaying;
    if (isRainPlaying || isMusicPlaying) {
      mp.classList.add('playing');
      mp.classList.remove('muted'); 
      const icon = mp.querySelector('.material-icons-round');
      if (icon) {
        if (isMusicPlaying && isRainPlaying && !audioStore.isGloballyMuted) {
          icon.textContent = 'graphic_eq'; 
        } else if (isMusicPlaying) {
          icon.textContent = 'music_note'; 
        } else {
          icon.textContent = audioStore.isGloballyMuted ? 'volume_off' : 'cloud_sync';
          if (audioStore.isGloballyMuted && !isMusicPlaying) mp.classList.add('muted');
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
  if (audioStore.isGloballyMuted && target > 0) target = 0;
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
  audioStore.rainState = state;
  if (state !== 'none' && !audioStore.isGloballyMuted) {
    canvas.style.opacity = '1';
    initRaindrops();
    if (!rainAnimationId) drawRain();
  } else {
    canvas.style.opacity = '0';
    setTimeout(() => {
      if (audioStore.rainState === 'none' || audioStore.isGloballyMuted) {
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
  const isRainActive = audioStore.rainState !== 'none' && !audioStore.isGloballyMuted;
  if (isRainActive) {
    setRainState('none');
  } else {
    if(audioStore.isGloballyMuted) {
      audioStore.isGloballyMuted = false;
      localStorage.removeItem('kitty_rain_volume_enabled');
    }
    setRainState('heavy');
  }
}

export function toggleRainMute() {
  const isRainPlaying = audioStore.rainState !== 'none';
  if (audioStore.isPlaying && isRainPlaying) {
    // If both are playing, we can't easily mute just one with this simplified logic
    // but the original logic tried to pause music. 
    // Let's keep it simple: toggle rain mute first.
    audioStore.isGloballyMuted = !audioStore.isGloballyMuted;
    localStorage.setItem('kitty_rain_volume_enabled', audioStore.isGloballyMuted ? 'muted' : 'unmuted');
    setRainState(audioStore.rainState);
  } else if (isRainPlaying) {
    audioStore.isGloballyMuted = !audioStore.isGloballyMuted;
    localStorage.setItem('kitty_rain_volume_enabled', audioStore.isGloballyMuted ? 'muted' : 'unmuted');
    setRainState(audioStore.rainState);
  }
}

function initRain() {
  if (!audio) {
    audio = document.createElement('audio');
    audio.id = 'global-rain-audio';
    audio.src = 'assets/audio/rain.mp3';
    audio.loop = true;
    audio.volume = 0;
    document.body.appendChild(audio);
  }
  if (!canvas) {
    canvas = document.createElement('canvas');
    canvas.id = 'global-rain-canvas';
    Object.assign(canvas.style, {
      position: 'fixed',
      top: '0', left: '0',
      width: '100vw', height: '100vh',
      pointerEvents: 'none',
      zIndex: '9998', 
      opacity: '0',
      transition: 'opacity 1.5s cubic-bezier(0.2, 0, 0, 1)'
    });
    document.body.appendChild(canvas);
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

document.addEventListener('audio-state-changed', updateMiniplayerUI);
