import { audioStore } from './audio-store';

let audioContext: AudioContext;
let audioBuffer: AudioBuffer | null = null;
let sourceNode: AudioBufferSourceNode | null = null;
let panNode: StereoPannerNode | null = null;
let nightcoreFilter: BiquadFilterNode | null = null;
let convolverNode: ConvolverNode | null = null;
let muffledFilter: BiquadFilterNode | null = null;
let dryGainNode: GainNode | null = null;
let wetGainNode: GainNode | null = null;
let masterGainNode: GainNode | null = null;
let startTime = 0;
let lfoId: number | null = null;
let progressDrag = false;
let lastRenderId: number | null = null;
let isGraphInitialized = false;

const updateUITotalTime = () => {
  const timeTotal = document.getElementById('fx-time-total');
  if (timeTotal && audioBuffer) {
    let minutes = Math.floor(audioBuffer.duration / 60);
    let seconds = Math.floor(audioBuffer.duration % 60).toString().padStart(2, '0');
    timeTotal.textContent = `${minutes}:${seconds}`;
  }
};

const loadAudio = async () => {
  try {
    audioContext = new (window.AudioContext || (window as any).webkitAudioContext)();
    const response = await fetch('assets/audio/music.mp3');
    const arrayBuffer = await response.arrayBuffer();
    audioBuffer = await audioContext.decodeAudioData(arrayBuffer);
    updateUITotalTime();
  } catch(e) {
    console.error("Failed to load assets/audio/music.mp3", e);
  }
};

const initDSPGraph = () => {
  if (isGraphInitialized || !audioContext || !audioBuffer) return;

  panNode = audioContext.createStereoPanner();
  
  nightcoreFilter = audioContext.createBiquadFilter();
  nightcoreFilter.type = 'highshelf';
  nightcoreFilter.frequency.value = 4000;

  convolverNode = audioContext.createConvolver();
  const length = audioContext.sampleRate * 2.0; 
  const impulse = audioContext.createBuffer(2, length, audioContext.sampleRate);
  for (let i = 0; i < 2; i++) {
    const channel = impulse.getChannelData(i);
    for (let j = 0; j < length; j++) {
      channel[j] = (Math.random() * 2 - 1) * Math.pow(1 - j / length, 4);
    }
  }
  convolverNode.buffer = impulse;

  dryGainNode = audioContext.createGain();
  wetGainNode = audioContext.createGain();

  muffledFilter = audioContext.createBiquadFilter();
  muffledFilter.type = 'lowpass';
  muffledFilter.Q.value = 0.5;

  masterGainNode = audioContext.createGain();
  masterGainNode.gain.value = 0.75;

  // Connections
  panNode.connect(nightcoreFilter);
  nightcoreFilter.connect(dryGainNode);
  nightcoreFilter.connect(convolverNode);
  convolverNode.connect(wetGainNode);
  dryGainNode.connect(muffledFilter);
  wetGainNode.connect(muffledFilter);
  muffledFilter.connect(masterGainNode);
  masterGainNode.connect(audioContext.destination);

  isGraphInitialized = true;
};

const updateEffectValues = () => {
  if (!isGraphInitialized || !sourceNode) return;

  nightcoreFilter!.gain.value = audioStore.activeEffects['nightcore'] ? 6 : 0;
  dryGainNode!.gain.value = audioStore.activeEffects['reverb'] ? 0.6 : 1.0;
  wetGainNode!.gain.value = audioStore.activeEffects['reverb'] ? 0.5 : 0.0;
  muffledFilter!.frequency.value = audioStore.activeEffects['muffled'] ? 300 : 20000;

  let playbackRate = 1.0;
  if (audioStore.activeEffects['nightcore']) playbackRate *= 1.35;
  if (audioStore.activeEffects['reverb']) playbackRate *= 0.8;
  sourceNode.playbackRate.value = playbackRate;
};

const start8D = () => {
  if (lfoId) cancelAnimationFrame(lfoId);
  const lfoLoop = () => {
    if (panNode) panNode.pan.value = Math.sin(Date.now() / 800) * 0.9;
    lfoId = requestAnimationFrame(lfoLoop);
  };
  lfoLoop();
};

const updateProgress = () => {
  if (!audioStore.isPlaying || !sourceNode || !audioContext) return;
  if (!progressDrag && audioBuffer) {
    let current = (audioStore.offsetTime + (audioContext.currentTime - startTime) * sourceNode.playbackRate.value) % audioBuffer.duration;
    const percent = current / audioBuffer.duration;
    const pSlider = document.getElementById('fx-progress-slider') as HTMLInputElement;
    const pCont = document.getElementById('fx-progress-container');
    const tCurr = document.getElementById('fx-time-current');
    if (pSlider && pCont && tCurr) {
      pSlider.value = percent.toString();
      pCont.style.setProperty('--val', percent.toString());
      let minutes = Math.floor(current / 60);
      let seconds = Math.floor(current % 60).toString().padStart(2, '0');
      tCurr.textContent = `${minutes}:${seconds}`;
    }
  }
  lastRenderId = requestAnimationFrame(updateProgress);
};

export const syncFXPlayerUI = () => {
  const playIcon = document.getElementById('live-fx-play-icon');
  if (playIcon) playIcon.textContent = audioStore.isPlaying ? 'pause' : 'play_arrow';
  if (audioBuffer) updateUITotalTime();
  
  const t8d = document.getElementById('toggle-8d') as HTMLInputElement;
  const tnc = document.getElementById('toggle-nightcore') as HTMLInputElement;
  const trv = document.getElementById('toggle-reverb') as HTMLInputElement;
  const tmf = document.getElementById('toggle-muffled') as HTMLInputElement;
  
  if (t8d) {
    t8d.checked = audioStore.activeEffects['8d'];
    tnc.checked = audioStore.activeEffects['nightcore'];
    trv.checked = audioStore.activeEffects['reverb'];
    tmf.checked = audioStore.activeEffects['muffled'];
  }
};

// On écoute le signal du Store
document.addEventListener('kitty:sync-ui', syncFXPlayerUI);

export const pauseAudio = () => {
  if (sourceNode && audioContext && audioBuffer) {
    try { sourceNode.stop(); } catch(e){}
    audioStore.offsetTime += (audioContext.currentTime - startTime) * sourceNode.playbackRate.value;
    audioStore.offsetTime = audioStore.offsetTime % audioBuffer.duration;
    sourceNode.disconnect();
  }
  audioStore.isPlaying = false;
  syncFXPlayerUI();
  if (lfoId) cancelAnimationFrame(lfoId);
  if (lastRenderId) cancelAnimationFrame(lastRenderId);
  
  // Dispatch event for other components
  document.dispatchEvent(new CustomEvent('audio-state-changed'));
};

export const startPlayback = () => {
  if (!audioBuffer || !audioContext) return;
  initDSPGraph();
  
  if (sourceNode) sourceNode.disconnect();
  
  sourceNode = audioContext.createBufferSource();
  sourceNode.buffer = audioBuffer;
  sourceNode.loop = true;
  
  sourceNode.connect(panNode!);
  
  updateEffectValues();
  
  sourceNode.start(0, audioStore.offsetTime);
  startTime = audioContext.currentTime;
  audioStore.isPlaying = true;
  syncFXPlayerUI();
  if (audioStore.activeEffects['8d']) start8D();
  updateProgress();
  
  document.dispatchEvent(new CustomEvent('audio-state-changed'));
};

export const togglePlayback = async () => {
  if (!audioContext) {
    const playIcon = document.getElementById('live-fx-play-icon');
    if (playIcon) playIcon.textContent = 'hourglass_empty';
    await loadAudio();
  }
  if (audioContext && audioContext.state === 'suspended') await audioContext.resume();
  if (audioStore.isPlaying) pauseAudio();
  else startPlayback();
};

const initAudioPlayer = () => {
  syncFXPlayerUI();
  if (!audioBuffer) {
    loadAudio().catch(e => console.log(e));
  }
};

document.addEventListener('astro:page-load', () => {
  initAudioPlayer();
  
  const playBtn = document.getElementById('live-fx-play-btn');
  if (playBtn) {
    playBtn.addEventListener('click', togglePlayback);
  }
  
  const effects = ['8d', 'nightcore', 'reverb', 'muffled'] as const;
  effects.forEach(fx => {
    const el = document.getElementById(`toggle-${fx}`) as HTMLInputElement;
    if (el) {
      el.addEventListener('change', () => {
        audioStore.activeEffects[fx] = el.checked;
        if (fx === '8d') {
          if (audioStore.isPlaying) {
            if (el.checked) start8D();
            else {
              if (lfoId) cancelAnimationFrame(lfoId);
              if (panNode) panNode.pan.value = 0;
            }
          }
        } else {
          updateEffectValues();
        }
      });
    }
  });

  const pSlider = document.getElementById('fx-progress-slider') as HTMLInputElement;
  if (pSlider) {
    pSlider.addEventListener('mousedown', () => {
      progressDrag = true;
      document.getElementById('fx-progress-container')?.classList.add('is-dragging');
    });
    pSlider.addEventListener('touchstart', () => {
      progressDrag = true;
      document.getElementById('fx-progress-container')?.classList.add('is-dragging');
    }, {passive: true});
    
    window.addEventListener('mouseup', () => {
      progressDrag = false;
      document.getElementById('fx-progress-container')?.classList.remove('is-dragging');
    });
    window.addEventListener('touchend', () => {
      progressDrag = false;
      document.getElementById('fx-progress-container')?.classList.remove('is-dragging');
    });
    
    pSlider.addEventListener('input', () => {
      let val = parseFloat(pSlider.value);
      const pCont = document.getElementById('fx-progress-container');
      const tCurr = document.getElementById('fx-time-current');
      if (pCont) pCont.style.setProperty('--val', val.toString());
      if (audioBuffer && tCurr) {
        let current = val * audioBuffer.duration;
        let minutes = Math.floor(current / 60);
        let seconds = Math.floor(current % 60).toString().padStart(2, '0');
        tCurr.textContent = `${minutes}:${seconds}`;
      }
    });
    
    pSlider.addEventListener('change', () => {
      progressDrag = false;
      if (audioBuffer) {
        if (audioStore.isPlaying) {
          pauseAudio();
          audioStore.offsetTime = parseFloat(pSlider.value) * audioBuffer.duration;
          startPlayback();
        } else {
          audioStore.offsetTime = parseFloat(pSlider.value) * audioBuffer.duration;
        }
      }
    });
  }
});
