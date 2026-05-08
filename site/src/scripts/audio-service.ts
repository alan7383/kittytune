import { isPlaying, offsetTime, activeEffects } from './audio-store';

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
let isGraphInitialized = false;

export const loadAudio = async () => {
  try {
    audioContext = new (window.AudioContext || (window as any).webkitAudioContext)();
    const response = await fetch('assets/audio/music.mp3');
    const arrayBuffer = await response.arrayBuffer();
    audioBuffer = await audioContext.decodeAudioData(arrayBuffer);
    return audioBuffer;
  } catch(e) {
    console.error("Failed to load assets/audio/music.mp3", e);
    return null;
  }
};

export const initDSPGraph = () => {
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

export const updateEffectValues = () => {
  if (!isGraphInitialized || !sourceNode) return;

  const effects = activeEffects.get();
  nightcoreFilter!.gain.value = effects['nightcore'] ? 6 : 0;
  dryGainNode!.gain.value = effects['reverb'] ? 0.6 : 1.0;
  wetGainNode!.gain.value = effects['reverb'] ? 0.5 : 0.0;
  muffledFilter!.frequency.value = effects['muffled'] ? 300 : 20000;

  let playbackRate = 1.0;
  if (effects['nightcore']) playbackRate *= 1.35;
  if (effects['reverb']) playbackRate *= 0.8;
  sourceNode.playbackRate.value = playbackRate;
};

export const start8D = () => {
  if (lfoId) cancelAnimationFrame(lfoId);
  const lfoLoop = () => {
    if (panNode) panNode.pan.value = Math.sin(Date.now() / 800) * 0.9;
    lfoId = requestAnimationFrame(lfoLoop);
  };
  lfoLoop();
};

export const stop8D = () => {
  if (lfoId) cancelAnimationFrame(lfoId);
  if (panNode) panNode.pan.value = 0;
};

export const pauseAudio = () => {
  if (sourceNode && audioContext && audioBuffer) {
    try { sourceNode.stop(); } catch(e){}
    let newOffset = offsetTime.get() + (audioContext.currentTime - startTime) * sourceNode.playbackRate.value;
    offsetTime.set(newOffset % audioBuffer.duration);
    sourceNode.disconnect();
  }
  isPlaying.set(false);
  if (lfoId) cancelAnimationFrame(lfoId);
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
  
  sourceNode.start(0, offsetTime.get());
  startTime = audioContext.currentTime;
  isPlaying.set(true);
  if (activeEffects.get()['8d']) start8D();
};

export const togglePlayback = async () => {
  if (!audioContext) {
    await loadAudio();
  }
  if (audioContext && audioContext.state === 'suspended') await audioContext.resume();
  if (isPlaying.get()) pauseAudio();
  else startPlayback();
};

export const getCurrentTime = () => {
  if (!isPlaying.get() || !sourceNode || !audioContext || !audioBuffer) {
    return offsetTime.get();
  }
  return (offsetTime.get() + (audioContext.currentTime - startTime) * sourceNode.playbackRate.value) % audioBuffer.duration;
};

export const getDuration = () => {
  return audioBuffer ? audioBuffer.duration : 0;
};

export const setOffset = (val: number) => {
  offsetTime.set(val);
};
