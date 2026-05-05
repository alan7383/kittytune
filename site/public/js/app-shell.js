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

  function updateLatestDownloadLinks() {
    fetch('https://api.github.com/repos/alan7383/kittytune/releases/latest')
      .then((response) => response.json())
      .then((data) => {
        const apk = data.assets && data.assets.find((asset) => asset.name.endsWith('.apk'));
        if (!apk) return;
        document.querySelectorAll('.latest-download-link').forEach((link) => {
          link.href = apk.browser_download_url;
        });
      })
      .catch(() => {});
  }

  function initCurrentPage(root = document) {
    initReveal(root);
    initRipples(root);
    if (window.updateThemeIcons) window.updateThemeIcons();
  }

  function runInlinePageScripts(doc) {
    document.querySelectorAll('.pjax-script').forEach((script) => script.remove());
    doc.querySelectorAll('script').forEach((oldScript) => {
      if (oldScript.src) return;
      if (oldScript.type && oldScript.type !== 'text/javascript' && oldScript.type !== 'module') return;
      if (oldScript.textContent.includes('navigateSmoothly')) return;
      const newScript = document.createElement('script');
      newScript.textContent = oldScript.textContent;
      newScript.classList.add('pjax-script');
      document.body.appendChild(newScript);
    });
  }

  function showLoader(loader, wrapper) {
    loader.style.display = 'flex';
    void loader.offsetWidth;
    loader.classList.remove('hidden');
    if (window.startM3Loader) window.startM3Loader();
    wrapper.classList.remove('loaded');
    wrapper.classList.add('unloading');
  }

  function hideLoader(loader, wrapper) {
    wrapper.classList.remove('unloading');
    wrapper.classList.add('loaded');
    setTimeout(() => {
      loader.classList.add('hidden');
      setTimeout(() => {
        loader.style.display = 'none';
      }, 800);
    }, 800);
  }

  window.switchTab = function switchTab(event, element) {
    event.preventDefault();
    document.querySelectorAll('.nav-rail-item').forEach((item) => item.classList.remove('active'));
    element.classList.add('active');
  };

  window.navigateSmoothly = async function navigateSmoothly(event, url) {
    event.preventDefault();
    const target = url.split('/').pop().replace('.html', '') || 'index';
    const current = window.location.pathname.split('/').pop().replace('.html', '') || 'index';

    if (target === current) {
      closeDrawer();
      return;
    }

    if (typeof window.stopTypoAutoPlay === 'function') window.stopTypoAutoPlay();

    const loader = document.getElementById('loading-screen');
    const wrapper = document.getElementById('app-wrapper');
    const navRail = document.querySelector('.m3-nav-rail');
    if (!loader || !wrapper || !navRail) {
      window.location.href = url;
      return;
    }

    closeDrawer();
    showLoader(loader, wrapper);

    try {
      const response = await fetch(url);
      if (!response.ok) throw new Error(`Navigation failed: ${response.status}`);
      const htmlText = await response.text();
      const doc = new DOMParser().parseFromString(htmlText, 'text/html');
      const nextWrapper = doc.getElementById('app-wrapper');
      const nextNavRail = doc.querySelector('.m3-nav-rail');
      const nextDrawer = doc.getElementById('mobile-drawer');

      if (!nextWrapper || !nextNavRail) throw new Error('Missing page shell');

      setTimeout(() => {
        document.title = doc.title;
        wrapper.innerHTML = nextWrapper.innerHTML;
        navRail.innerHTML = nextNavRail.innerHTML;
        window.scrollTo(0, 0);

        const mobileDrawer = document.getElementById('mobile-drawer');
        if (mobileDrawer && nextDrawer) mobileDrawer.innerHTML = nextDrawer.innerHTML;
        if (window.syncPageStyles) window.syncPageStyles(doc);

        let cleanUrl = url.replace(/\/index\.html$/, '/').replace(/\.html$/, '');
        if (cleanUrl.endsWith('/index')) {
          cleanUrl = cleanUrl.slice(0, -6);
        } else if (cleanUrl === 'index') {
          cleanUrl = './';
        }
        window.history.pushState({}, '', cleanUrl);

        runInlinePageScripts(doc);
        initCurrentPage(wrapper);
        hideLoader(loader, wrapper);
      }, 550);
    } catch (error) {
      window.location.href = url;
    }
  };

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
})();
