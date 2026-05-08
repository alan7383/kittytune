import { atom, map } from 'nanostores';

export const isPlaying = atom(false);
export const offsetTime = atom(0);
export const activeEffects = map({
  '8d': false,
  'nightcore': false,
  'reverb': false,
  'muffled': false
});
export const rainState = atom<'none' | 'light' | 'heavy'>('none');
export const isGloballyMuted = atom(localStorage.getItem('kitty_rain_volume_enabled') === 'muted');

