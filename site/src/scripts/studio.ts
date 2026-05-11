import { applyMonetTheme } from './theme';

const presets = {
  default: { wght: 400, wdth: 100, slnt: 0, rond: 0, grad: 0, opsz: 14 },
  rounded: { wght: 650, wdth: 102, slnt: 0, rond: 100, grad: 0, opsz: 24 },
  elegant: { wght: 260, wdth: 108, slnt: 0, rond: 0, grad: -45, opsz: 44 },
  chunky: { wght: 930, wdth: 112, slnt: 0, rond: 58, grad: 40, opsz: 18 },
  compact: { wght: 470, wdth: 70, slnt: 0, rond: 12, grad: 0, opsz: 12 },
  slanted: { wght: 560, wdth: 100, slnt: -10, rond: 24, grad: 0, opsz: 28 },
  playful: { wght: 760, wdth: 118, slnt: -4, rond: 92, grad: 20, opsz: 36 },
  wide: { wght: 520, wdth: 145, slnt: 0, rond: 36, grad: 0, opsz: 26 }
};

const pageState = {
  currentColor: localStorage.getItem('kittytune_theme_color') || "#d0bcff",
  paletteStyle: localStorage.getItem('kittytune_theme_style') || "TonalSpot",
  isTypoPlaying: true,
  typoInterval: null as ReturnType<typeof setInterval> | null,
  currentPresetIdx: 0
};

const presetNames = ["default", "rounded", "elegant", "chunky", "compact", "slanted", "playful", "wide"];

function updateSliderVisual(slider: HTMLElement) {
  const input = slider.querySelector('[data-slider-input]') as HTMLInputElement;
  if (!input) return;
  const min = parseFloat(input.min);
  const max = parseFloat(input.max);
  const value = parseFloat(input.value);
  const percent = (value - min) / (max - min);
  slider.style.setProperty("--val", percent.toFixed(4));
}

function applyAxes(values: Record<string, number>) {
  Object.entries(values).forEach(([axis, value]) => {
    const input = document.getElementById(`axis-${axis}`) as HTMLInputElement;
    const output = document.getElementById(`out-${axis}`);
    if (input) input.value = value.toString();
    if (output) output.textContent = Math.round(value).toString();
    document.documentElement.style.setProperty(`--lab-${axis}`, value.toString());
  });
  document.querySelectorAll<HTMLElement>(".lab-slider").forEach((s) => updateSliderVisual(s));
}

export function stopTypoAutoPlay() {
  if (pageState.typoInterval) clearInterval(pageState.typoInterval);
  pageState.isTypoPlaying = false;
  const icon = document.getElementById('typo-autoplay-icon');
  if (icon) icon.textContent = 'play_arrow';
}

export function startTypoAutoPlay() {
  if (pageState.typoInterval) clearInterval(pageState.typoInterval);
  pageState.isTypoPlaying = true;
  const icon = document.getElementById('typo-autoplay-icon');
  if (icon) icon.textContent = 'pause';
  pageState.typoInterval = setInterval(() => {
    pageState.currentPresetIdx = (pageState.currentPresetIdx + 1) % presetNames.length;
    const nextPresetBtn = document.querySelector(`.preset-chip[data-preset="${presetNames[pageState.currentPresetIdx]}"]`) as HTMLElement;
    if (nextPresetBtn) nextPresetBtn.click();
  }, 2500);
}

function updateDynamicSwatch(hex: string) {
  const swatchContainer = document.getElementById("color-grid");
  let dynamicSwatch = document.getElementById("dynamic-swatch") as HTMLElement;
  if (!dynamicSwatch) {
    dynamicSwatch = document.createElement("button");
    dynamicSwatch.id = "dynamic-swatch";
    dynamicSwatch.className = "color-swatch";
    dynamicSwatch.title = "Custom Seed";
    const innerDiv = document.createElement("div");
    innerDiv.className = "color-swatch-inner";
    dynamicSwatch.appendChild(innerDiv);
    dynamicSwatch.addEventListener("click", () => {
      document.querySelectorAll(".color-swatch").forEach(item => item.classList.remove("active"));
      dynamicSwatch.classList.add("active");
      pageState.currentColor = dynamicSwatch.dataset.color || "#d0bcff";
      applyMonetTheme(pageState.currentColor, pageState.paletteStyle);
    });
    if (swatchContainer) {
      swatchContainer.insertBefore(dynamicSwatch, swatchContainer.firstChild);
    }
  }
  document.querySelectorAll(".color-swatch").forEach(s => s.classList.remove("active"));
  dynamicSwatch.dataset.color = hex;
  dynamicSwatch.classList.add("active");
  const rootStyle = document.documentElement.style;
  const primary = rootStyle.getPropertyValue('--md-sys-color-primary');
  const tertiary = rootStyle.getPropertyValue('--md-sys-color-tertiary');
  const container = rootStyle.getPropertyValue('--md-sys-color-primary-container');
  const innerDiv = dynamicSwatch.querySelector('.color-swatch-inner') as HTMLElement;
  if (innerDiv) {
    innerDiv.style.background = `conic-gradient(from 270deg, ${primary} 0 180deg, ${tertiary} 180deg 270deg, ${container} 270deg 360deg)`;
  }
}

export function generateAndAddRandomPalette() {
  const randomHex = '#' + Math.floor(Math.random()*16777215).toString(16).padStart(6, '0');
  const styles = ["TonalSpot", "Vibrant", "Expressive", "Monochrome"];
  const randomStyle = styles[Math.floor(Math.random() * styles.length)];
  pageState.currentColor = randomHex;
  pageState.paletteStyle = randomStyle;
  applyMonetTheme(randomHex, randomStyle);
  document.querySelectorAll(".monet-chip").forEach(c => {
    (c as HTMLElement).classList.toggle("active", (c as HTMLElement).dataset.style === randomStyle);
  });
  updateDynamicSwatch(randomHex);
  const swatchContainer = document.getElementById("color-grid");
  if (swatchContainer) {
    swatchContainer.scrollTo({ left: 0, behavior: 'smooth' });
  }
}

export function randomizeAll() {
  generateAndAddRandomPalette();
  const randomAxes = {
    wght: Math.floor(Math.random() * (1000 - 100 + 1)) + 100,
    wdth: Math.floor(Math.random() * (151 - 25 + 1)) + 25,
    slnt: Math.floor(Math.random() * (0 - (-10) + 1)) + (-10),
    rond: Math.floor(Math.random() * 101),
    grad: Math.floor(Math.random() * (150 - (-200) + 1)) + (-200),
    opsz: Math.floor(Math.random() * (144 - 8 + 1)) + 8
  };
  applyAxes(randomAxes);
  stopTypoAutoPlay();
  const fontText = document.getElementById("dynamic-font-text");
  if (fontText) fontText.textContent = "Surprise!";
}

function initDiscordRpc() {
  const buttons = document.querySelectorAll("#discord-rpc-switcher .preset-chip");
  const titleEl = document.getElementById("rpc-title");
  const detailsEl = document.getElementById("rpc-details");
  const stateEl = document.getElementById("rpc-state");
  const toggle = document.getElementById("rpc-toggle") as HTMLInputElement;
  const wrap = document.getElementById("discord-activity-wrap");
  if (!buttons.length) return;
  const trackName = "METAMORPHOSIS";
  const artistName = "INTERWORLD";
  if (toggle && wrap) {
    toggle.addEventListener("change", () => {
      if (toggle.checked) {
        wrap.style.opacity = "1";
        wrap.style.transform = "scale(1)";
        wrap.style.pointerEvents = "auto";
        wrap.style.marginTop = "0";
      } else {
        wrap.style.opacity = "0";
        wrap.style.transform = "scale(0.95)";
        wrap.style.pointerEvents = "none";
        wrap.style.marginTop = "-40px"; 
      }
    });
  }
  buttons.forEach(btn => {
    btn.addEventListener("click", () => {
      buttons.forEach(b => b.classList.remove("active"));
      btn.classList.add("active");
      const mode = (btn as HTMLElement).dataset.mode;
      if (!titleEl || !detailsEl || !stateEl) return;
      if (mode === "ACTIVITY") {
        titleEl.textContent = "KittyTune";
        detailsEl.textContent = trackName;
        stateEl.textContent = "by " + artistName;
      } else if (mode === "SONG") {
        titleEl.textContent = trackName;
        detailsEl.textContent = "by " + artistName;
        stateEl.textContent = "on KittyTune";
      } else if (mode === "ARTIST") {
        titleEl.textContent = artistName;
        detailsEl.textContent = trackName;
        stateEl.textContent = "on KittyTune";
      }
    });
  });
}

function initSliders() {
  // 1. On écoute l'événement natif qu'on a créé dans le Web Component !
  document.querySelectorAll("m3-slider").forEach((slider) => {
    slider.addEventListener("m3-change", (e: Event) => {
      const { axis, value } = (e as CustomEvent).detail;
      const output = document.getElementById(`out-${axis}`);
      
      // Met à jour la variable CSS pour la police
      document.documentElement.style.setProperty(`--lab-${axis}`, value.toString());
      if (output) output.textContent = Math.round(value).toString();
      
      // Désélectionne les presets
      document.querySelectorAll("#preset-row .preset-chip").forEach(l => l.classList.remove("active"));
    });
  });

  // 2. Garde la logique des boutons de presets intacte
  document.querySelectorAll<HTMLElement>("#preset-row .preset-chip").forEach((chip) => {
    chip.addEventListener("click", () => {
      const preset = presets[chip.dataset.preset as keyof typeof presets];
      if (!preset) return;
      document.querySelectorAll("#preset-row .preset-chip").forEach((item) => item.classList.remove("active"));
      chip.classList.add("active");
      applyAxes(preset); // (Tu peux garder applyAxes pour les presets)
      const fontText = document.getElementById("dynamic-font-text");
      if (fontText) fontText.textContent = chip.textContent;
    });
  });
}

function initPlayerSwitcher() {
  const player = document.getElementById("style-player") as HTMLElement;
  const buttons = document.querySelectorAll<HTMLElement>("#style-switcher .preset-chip");
  const pureBlackToggle = document.getElementById("pure-black-toggle") as HTMLInputElement;
  const play = document.getElementById("style-play");
  if (!player) return;
  buttons.forEach((button) => {
    button.addEventListener("click", () => {
      buttons.forEach((item) => item.classList.remove("active"));
      button.classList.add("active");
      player.dataset.style = button.dataset.style;
    });
  });
  if (pureBlackToggle) {
    pureBlackToggle.addEventListener("change", () => {
      player.dataset.pureBlack = pureBlackToggle.checked.toString();
    });
  }
  if (play) {
    play.addEventListener("click", () => {
      const icon = play.querySelector(".material-icons-round");
      if (!icon) return;
      icon.textContent = icon.textContent?.trim() === "pause" ? "play_arrow" : "pause";
    });
  }
  const inlineToggle = document.getElementById('toggle-inline-lyrics') as HTMLInputElement;
  const lyricsContainer = document.getElementById('pp-lyrics-inner');
  const lyricsData = [
    "I'm a pimp hoes fallin' for this pimpin' rap game",
    "Slowly hit the tempo, just enough to keep these bitches tame",
    "Makin' fame off a nigga dick, bitches suckin' dick",
    "Make it quick, off my dick, yeah, I took a foreign chick"
  ];
  if (lyricsContainer) {
    lyricsContainer.innerHTML = lyricsData.map((line, i) => 
      `<p class="pp-lyric-line ${i === 0 ? 'active' : ''}">${line}</p>`
    ).join('');
  }
  let activeLyricIndex = 0;
  function updateLyricsScroll() {
    const lines = document.querySelectorAll('.pp-lyric-line');
    if(lines.length === 0 || !lyricsContainer) return;
    lines.forEach(l => l.classList.remove('active'));
    const activeLine = lines[activeLyricIndex] as HTMLElement;
    activeLine.classList.add('active');
    const parentHeight = lyricsContainer.parentElement!.clientHeight;
    const lineOffset = activeLine.offsetTop;
    const lineHeight = activeLine.clientHeight;
    const scrollY = (parentHeight / 2) - lineOffset - (lineHeight / 2);
    lyricsContainer.style.transform = `translateY(${scrollY}px)`;
  }
  if (inlineToggle && player) {
    inlineToggle.addEventListener('change', (e: Event) => {
      const target = e.target as HTMLInputElement;
      if (target.checked) {
        player.classList.add('show-lyrics');
        setTimeout(updateLyricsScroll, 50); 
      } else {
        player.classList.remove('show-lyrics');
      }
    });
  }
  setInterval(() => {
    if (!player || !player.classList.contains('show-lyrics')) return;
    const playBtnIcon = document.querySelector('#style-play .material-icons-round');
    if (playBtnIcon && playBtnIcon.textContent?.trim() === 'pause') {
      activeLyricIndex = (activeLyricIndex + 1) % lyricsData.length;
      updateLyricsScroll();
    }
  }, 2800);
}

function initMonet() {
  const colorButtons = document.querySelectorAll<HTMLElement>(".color-swatch");
  const styleButtons = document.querySelectorAll<HTMLElement>(".monet-chip");
  const localSurpriseBtn = document.getElementById("monet-surprise-btn");
  styleButtons.forEach((c) => {
    c.classList.toggle("active", c.dataset.style === pageState.paletteStyle);
  });
  let matchedBaseColor = false;
  colorButtons.forEach((button) => {
    if (button.dataset.color === pageState.currentColor) {
      button.classList.add("active");
      matchedBaseColor = true;
    } else {
      button.classList.remove("active");
    }
    button.addEventListener("click", () => {
      document.querySelectorAll(".color-swatch").forEach((item) => item.classList.remove("active"));
      button.classList.add("active");
      pageState.currentColor = button.dataset.color;
      applyMonetTheme(pageState.currentColor, pageState.paletteStyle);
    });
  });
  if (!matchedBaseColor) {
    updateDynamicSwatch(pageState.currentColor);
  }
  styleButtons.forEach((button) => {
    button.addEventListener("click", () => {
      styleButtons.forEach((item) => item.classList.remove("active"));
      button.classList.add("active");
      pageState.paletteStyle = button.dataset.style || "TonalSpot";
      applyMonetTheme(pageState.currentColor, pageState.paletteStyle);
      if (document.getElementById("dynamic-swatch")?.classList.contains("active")) {
        updateDynamicSwatch(pageState.currentColor);
      }
    });
  });
  if (localSurpriseBtn) {
    localSurpriseBtn.addEventListener("click", generateAndAddRandomPalette);
  }
}

function initReveal() {
  const revealItems = document.querySelectorAll(".reveal");
  if (!revealItems.length) return;
  const observer = new IntersectionObserver((entries) => {
    entries.forEach((entry) => {
      if (!entry.isIntersecting) return;
      entry.target.classList.add("visible");
      observer.unobserve(entry.target);
    });
  }, { threshold: 0.14 });
  revealItems.forEach((item) => observer.observe(item));
}

function initTypoAutoPlay() {
  const playBtn = document.getElementById('typo-autoplay-btn');
  const labPanel = document.getElementById('typography-lab');
  if (playBtn) {
    playBtn.addEventListener('click', () => {
      if (pageState.isTypoPlaying) stopTypoAutoPlay();
      else startTypoAutoPlay();
    });
  }
  if (labPanel) {
    labPanel.addEventListener('pointerdown', (e: PointerEvent) => {
      const target = e.target as HTMLElement;
      const isControl = target.closest('[data-slider-root], .preset-chip');
      if (isControl && pageState.isTypoPlaying) {
        stopTypoAutoPlay();
      }
    });
  }
  startTypoAutoPlay();
}

export function initCustomStudio() {
  if (!document.getElementById('typography-lab')) return;
  initReveal();
  initSliders();
  initDiscordRpc();
  initPlayerSwitcher();
  initMonet();
  initTypoAutoPlay();
  
  const randomizeBtn = document.getElementById('randomize-all-btn');
  if (randomizeBtn) {
    randomizeBtn.addEventListener('click', randomizeAll);
  }
}

document.addEventListener('astro:page-load', initCustomStudio);

document.addEventListener('astro:before-swap', () => {
  stopTypoAutoPlay();
});
