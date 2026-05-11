import { updateThemeIcons } from './theme';
import { startM3Loader } from './m3-loader';

const rippleSelector = [
  '.philosophy-item',
  '.avium-btn-download',
  '.avium-btn-outline',
  '.kt-btn-download',
  '.kt-btn-outline',
  '.btn',
  '.preset-chip',
  '.theme-chip',
  '.segmented-button',
  '.filter-chip',
  '.input-chip',
  '.suggestion-chip',
  '.assist-chip',
  '.compose-settings-item',
].join(', ');

export function closeDrawer() {
  const drawer = document.getElementById('mobile-drawer');
  const scrim = document.getElementById('drawer-scrim');
  if (drawer) drawer.classList.remove('show');
  if (scrim) scrim.classList.remove('show');
}

export function toggleDrawer() {
  document.getElementById('mobile-drawer')?.classList.toggle('show');
  document.getElementById('drawer-scrim')?.classList.toggle('show');
}

function initReveal(root: ParentNode = document) {
  const revealItems = root.querySelectorAll('.showcase-row, .reveal');
  if (!revealItems.length || !('IntersectionObserver' in window)) {
    revealItems.forEach((item) => item.classList.add('visible'));
    return;
  }

  const observer = new IntersectionObserver((entries) => {
    entries.forEach((entry) => {
      if (!entry.isIntersecting) return;
      entry.target.classList.add('visible');
      observer.unobserve(entry.target);
    });
  }, { threshold: 0.15 });

  setTimeout(() => {
    revealItems.forEach((item) => {
      const element = item as HTMLElement;
      if (!element.dataset.revealBound) {
        element.dataset.revealBound = 'true';
        observer.observe(element);
      }
    });
  }, 300);
}

function initRipples(root: ParentNode = document) {
  root.querySelectorAll<HTMLElement>(rippleSelector).forEach((element) => {
    if (element.dataset.rippleBound) return;
    element.dataset.rippleBound = 'true';
    element.addEventListener('pointerdown', function (this: HTMLElement, event: PointerEvent) {
      const rect = this.getBoundingClientRect();
      const size = Math.max(rect.width, rect.height) * 2;
      const ripple = document.createElement('span');
      ripple.classList.add('md3-ripple');
      ripple.style.width = `${size}px`;
      ripple.style.height = `${size}px`;
      ripple.style.left = `${event.clientX - rect.left - size / 2}px`;
      ripple.style.top = `${event.clientY - rect.top - size / 2}px`;
      this.appendChild(ripple);
      ripple.addEventListener('animationend', () => ripple.remove());
    });
  });
}

let cachedApkUrl: string | null = null;
function updateLatestDownloadLinks() {
  if (cachedApkUrl) {
    document.querySelectorAll<HTMLAnchorElement>('.latest-download-link').forEach((link) => {
      link.href = cachedApkUrl;
    });
    return;
  }
  fetch('https://api.github.com/repos/alan7383/kittytune/releases/latest')
    .then((response) => {
      if (!response.ok) throw new Error('API Rate limit ou erreur réseau');
      return response.json();
    })
    .then((data) => {
      interface GitHubAsset {
        name: string;
        browser_download_url: string;
      }
      const apk = data.assets && data.assets.find((asset: GitHubAsset) => asset.name.endsWith('.apk'));
      if (!apk) return;
      cachedApkUrl = apk.browser_download_url;
      document.querySelectorAll<HTMLAnchorElement>('.latest-download-link').forEach((link) => {
        link.href = cachedApkUrl;
      });
    })
    .catch((err) => {
      console.warn('KittyTune: Impossible de récupérer la dernière release sur GitHub.', err);
    });
}

function showLoader() {
  const loader = document.getElementById('loading-screen');
  const wrapper = document.getElementById('app-wrapper');
  if (!loader || !wrapper) return;
  loader.style.display = 'flex';
  void loader.offsetWidth;
  loader.classList.remove('hidden');
  wrapper.classList.remove('loaded');
  wrapper.classList.add('unloading');
  
  // FIX: On lance la rotation ICI, avant le fetch !
  startM3Loader(); 
}

function hideLoader() {
  const loader = document.getElementById('loading-screen');
  const wrapper = document.getElementById('app-wrapper');
  if (!loader || !wrapper) return;
  wrapper.classList.remove('unloading');
  wrapper.classList.add('loaded');
  loader.classList.add('hidden');
  setTimeout(() => {
    loader.style.display = 'none';
  }, 800);
}

document.addEventListener('astro:before-preparation', (ev: Event) => {
  const customEv = ev as unknown as { loader: () => Promise<void> };
  const originalLoader = customEv.loader;
  customEv.loader = async function() {
    showLoader();
    // On laisse le temps au loader d'apparaître fluidement avant de bloquer le thread principal
    await new Promise(resolve => setTimeout(resolve, 300));
    await originalLoader();
    await new Promise(resolve => setTimeout(resolve, 300));
  };
});

document.addEventListener('astro:after-swap', () => {
  const wrapper = document.getElementById('app-wrapper');
  if (wrapper) {
    wrapper.classList.remove('loaded');
    wrapper.classList.remove('unloading');
  }
});

let isInitialLoad = true;
document.addEventListener('astro:page-load', () => {
  if (isInitialLoad) {
    isInitialLoad = false;
    setTimeout(() => hideLoader(), 1500);
  } else {
    setTimeout(() => hideLoader(), 30);
  }
  initReveal();
  initRipples();
  updateLatestDownloadLinks();
  updateThemeIcons();

  document.querySelectorAll('.m3-menu-btn:not(.js-search-btn), .js-drawer-btn').forEach(btn => {
      btn.addEventListener('click', toggleDrawer);
  });
  
  document.getElementById('drawer-scrim')?.addEventListener('click', closeDrawer);
  document.querySelectorAll('.m3-drawer-item').forEach(item => item.addEventListener('click', closeDrawer));

  document.querySelectorAll('.js-search-btn').forEach(btn => {
      btn.addEventListener('click', () => {
          alert('Search not available yet! ( > . < )');
      });
  });
  
  document.querySelectorAll('.js-placeholder-btn').forEach(btn => {
      btn.addEventListener('click', (e) => {
          e.preventDefault();
          const msg = (btn as HTMLElement).dataset.message || 'Feature coming soon! ( > . < )';
          alert(msg);
      });
  });

  // Synchronisation instantanée de la navigation (Effet M3)
  const currentPath = window.location.pathname;
  document.querySelectorAll('.nav-rail-item, .m3-drawer-item').forEach(item => {
      // 1. Gère les retours en arrière (Back/Forward) du navigateur
      const href = item.getAttribute('href');
      if (href === currentPath || href === currentPath + '/') {
          item.classList.add('active');
      } else {
          item.classList.remove('active');
      }

      // 2. Déclenche l'animation instantanément au clic (avant que la page ne charge)
      item.addEventListener('click', function(this: HTMLElement, e: Event) {
          // Si le bouton cliqué pointe vers la page actuelle, on annule complètement le clic
          const targetPath = (this as HTMLAnchorElement).pathname;
          if (targetPath === window.location.pathname || targetPath === window.location.pathname + '/') {
              e.preventDefault();
              return; // On arrête tout, pas de rechargement !
          }

          const parent = this.closest('aside');
          if (parent && !this.classList.contains('active')) {
              parent.querySelectorAll('.nav-rail-item, .m3-drawer-item').forEach(n => n.classList.remove('active'));
              this.classList.add('active');
          }
      });
  });
});
