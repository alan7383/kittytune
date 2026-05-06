(function () {
  const rippleSelector = [
    '.philosophy-item',
    '.avium-btn-download',
    '.avium-btn-outline',
    '.kt-btn-download',
    '.kt-btn-outline',
    '.btn',
    '.nav-rail-item',
    '.preset-chip',
    '.theme-chip',
    '.segmented-button',
    '.filter-chip',
    '.input-chip',
    '.suggestion-chip',
    '.assist-chip',
    '.compose-settings-item',
  ].join(', ');

  function closeDrawer() {
    const drawer = document.getElementById('mobile-drawer');
    const scrim = document.getElementById('drawer-scrim');
    if (drawer) drawer.classList.remove('show');
    if (scrim) scrim.classList.remove('show');
  }

  function initReveal(root) {
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
        if (!item.dataset.revealBound) {
          item.dataset.revealBound = 'true';
          observer.observe(item);
        }
      });
    }, 300);
  }

  function initRipples(root) {
    root.querySelectorAll(rippleSelector).forEach((element) => {
      if (element.dataset.rippleBound) return;
      element.dataset.rippleBound = 'true';
      element.addEventListener('pointerdown', function (event) {
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

  let cachedApkUrl = null;
  function updateLatestDownloadLinks() {
    if (cachedApkUrl) {
      document.querySelectorAll('.latest-download-link').forEach((link) => {
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
        const apk = data.assets && data.assets.find((asset) => asset.name.endsWith('.apk'));
        if (!apk) return;
        cachedApkUrl = apk.browser_download_url;
        document.querySelectorAll('.latest-download-link').forEach((link) => {
          link.href = cachedApkUrl;
        });
      })
      .catch((err) => {
        console.warn('KittyTune: Impossible de récupérer la dernière release sur GitHub.', err);
      });
  }

  function initCurrentPage(root = document) {
    initReveal(root);
    initRipples(root);
    if (window.updateThemeIcons) window.updateThemeIcons();
    updateLatestDownloadLinks();
  }

  window.toggleDrawer = function toggleDrawer() {
    document.getElementById('mobile-drawer')?.classList.toggle('show');
    document.getElementById('drawer-scrim')?.classList.toggle('show');
  };

  window.closeDrawer = closeDrawer;

  document.addEventListener('DOMContentLoaded', () => {
    const loader = document.getElementById('loading-screen');
    const appWrapper = document.getElementById('app-wrapper');
    setTimeout(() => {
      if (loader) loader.classList.add('hidden');
      if (appWrapper) appWrapper.classList.add('loaded');
      setTimeout(() => {
        if (loader) loader.style.display = 'none';
      }, 800);
    }, 2000);

    updateLatestDownloadLinks();
    initCurrentPage(document);
  });

  document.addEventListener('astro:after-swap', () => {
    initCurrentPage(document);

    const loader = document.getElementById('loading-screen');
    const appWrapper = document.getElementById('app-wrapper');
    if (loader) {
      loader.classList.add('hidden');
      loader.style.display = 'none';
    }
    if (appWrapper) appWrapper.classList.add('loaded');
  });

  document.addEventListener('astro:page-load', () => {
    if (window.syncFXPlayerUI) window.syncFXPlayerUI();
    if (window.updateMiniplayerUI) window.updateMiniplayerUI();
  });
})();
