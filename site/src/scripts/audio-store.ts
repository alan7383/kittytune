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

export const updateUI = () => {
  // Global UI sync functions
  if ((window as any).syncFXPlayerUI) (window as any).syncFXPlayerUI();
  if ((window as any).updateMiniplayerUI) (window as any).updateMiniplayerUI();
  if ((window as any).updateBentoUI) (window as any).updateBentoUI();
};
