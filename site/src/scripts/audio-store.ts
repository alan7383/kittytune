export const audioStore = {
  isPlaying: false,
  offsetTime: 0,
  activeEffects: {
    '8d': false,
    'nightcore': false,
    'reverb': false,
    'muffled': false
  },
  rainState: 'none' as 'none' | 'light' | 'heavy',
  isGloballyMuted: localStorage.getItem('kitty_rain_volume_enabled') === 'muted'
};

// Quand une page change, on dit aux composants de se mettre à jour
export const syncUI = () => {
  document.dispatchEvent(new CustomEvent('kitty:sync-ui'));
};
