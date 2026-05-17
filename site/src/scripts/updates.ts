/**
 * Updates page — client-side logic.
 * Fetches GitHub releases, renders them as a timeline,
 * and wires up the segmented tab navigation.
 */

const REPO_API  = 'https://api.github.com/repos/alan7383/kittytune/releases';
const PER_PAGE  = 5;

/* ===== Types ===== */
interface GitHubAsset {
  name: string;
  size: number;
  download_count: number;
  browser_download_url: string;
}

interface GitHubRelease {
  tag_name: string;
  name: string;
  html_url: string;
  published_at: string;
  body: string;
  assets: GitHubAsset[];
  prerelease: boolean;
}

/* ===== Helpers ===== */
function formatDate(iso: string): string {
  return new Date(iso).toLocaleDateString('en-US', {
    year: 'numeric', month: 'short', day: 'numeric',
  });
}

function relativeTime(iso: string): string {
  const diff = Date.now() - new Date(iso).getTime();
  const mins  = Math.floor(diff / 60000);
  const hours = Math.floor(diff / 3600000);
  const days  = Math.floor(diff / 86400000);

  if (mins  < 1)  return 'just now';
  if (mins  < 60) return `${mins}m ago`;
  if (hours < 24) return `${hours}h ago`;
  if (days  < 30) return `${days}d ago`;
  return formatDate(iso);
}

function formatSize(bytes: number): string {
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

function escapeHtml(str: string): string {
  const div = document.createElement('div');
  div.textContent = str;
  return div.innerHTML;
}

/* ===== Icons per category ===== */
const categoryMeta: Record<string, { icon: string; cls: string }> = {
  'Features':         { icon: 'auto_awesome', cls: 'feat' },
  'Bug Fixes':        { icon: 'bug_report',   cls: 'fix' },
  'Performance':      { icon: 'speed',        cls: 'perf' },
  'Breaking Changes': { icon: 'warning',      cls: 'breaking' },
  'Refactoring':      { icon: 'build',        cls: 'refactor' },
  'Documentation':    { icon: 'description',  cls: 'docs' },
};

/* ===== Parse markdown body from git-cliff ===== */
interface ChangeGroup {
  title: string;
  items: string[];
}

function parseReleaseBody(body: string): ChangeGroup[] {
  const groups: ChangeGroup[] = [];
  let current: ChangeGroup | null = null;

  for (const line of body.split('\n')) {
    const trimmed = line.trim();
    // Match "### Features", "### Bug Fixes", etc.
    const headingMatch = trimmed.match(/^###\s+(.+)/);
    if (headingMatch) {
      current = { title: headingMatch[1], items: [] };
      groups.push(current);
      continue;
    }
    // Match "* Some message ([commit](url))"
    const itemMatch = trimmed.match(/^\*\s+(.+)/);
    if (itemMatch && current) {
      // Convert markdown links to HTML
      let text = escapeHtml(itemMatch[1]);
      // Handle the pattern: text ([hash](url))
      text = itemMatch[1].replace(
        /\[([^\]]+)\]\(([^)]+)\)/g,
        '<a href="$2" target="_blank" rel="noopener">$1</a>'
      );
      current.items.push(text);
    }
  }

  return groups;
}

/* ===== Render a single release card ===== */
function renderReleaseCard(release: GitHubRelease, index: number): string {
  const isLatest = index === 0;
  const groups   = parseReleaseBody(release.body || '');
  const apk      = release.assets.find(a => a.name.endsWith('.apk'));
  const version  = release.tag_name;

  let groupsHtml = '';
  for (const group of groups) {
    const meta = categoryMeta[group.title] || { icon: 'label', cls: 'feat' };
    groupsHtml += `
      <h3 class="${meta.cls}">
        <span class="material-icons-round">${meta.icon}</span>
        ${escapeHtml(group.title)}
      </h3>
      <ul>
        ${group.items.map(item => `<li>${item}</li>`).join('')}
      </ul>
    `;
  }

  let actionsHtml = '';
  if (apk) {
    actionsHtml += `
      <a href="${apk.browser_download_url}" class="release-action-btn primary" id="download-${version}">
        <span class="material-icons-round">download</span>
        Download APK
      </a>
    `;
  }
  actionsHtml += `
    <a href="${release.html_url}" target="_blank" rel="noopener" class="release-action-btn outline" id="view-${version}">
      <span class="material-icons-round">open_in_new</span>
      View on GitHub
    </a>
  `;

  let metaHtml = '';
  if (apk) {
    metaHtml += `
      <span class="apk-size">
        <span class="material-icons-round">sd_card</span>
        ${formatSize(apk.size)}
      </span>
    `;
    if (apk.download_count > 0) {
      metaHtml += `
        <span class="download-count">
          <span class="material-icons-round">download</span>
          ${apk.download_count.toLocaleString()} downloads
        </span>
      `;
    }
  }

  return `
    <div class="release-card reveal" data-index="${index}">
      <div class="release-dot">
        <span class="material-icons-round">new_releases</span>
      </div>
      <div class="release-card-inner">
        <div class="release-header">
          <span class="release-version">${escapeHtml(release.name || version)}</span>
          <span class="release-tag ${isLatest ? 'latest' : 'stable'}">
            ${isLatest ? '✦ Latest' : 'Stable'}
          </span>
          <span class="release-date" title="${formatDate(release.published_at)}">
            ${relativeTime(release.published_at)}
          </span>
        </div>
        <div class="release-body">
          ${groupsHtml}
        </div>
        ${metaHtml ? `<div style="display: flex; align-items: center; gap: 12px; flex-wrap: wrap;">${metaHtml}</div>` : ''}
        <div class="release-actions">
          ${actionsHtml}
        </div>
      </div>
    </div>
  `;
}

/* ===== State ===== */
let currentPage = 1;
let allLoaded = false;
let isLoading = false;

/* ===== Fetch and render releases ===== */
async function fetchReleases(page: number): Promise<GitHubRelease[]> {
  const resp = await fetch(`${REPO_API}?per_page=${PER_PAGE}&page=${page}`);
  if (!resp.ok) throw new Error(`GitHub API error: ${resp.status}`);
  return resp.json();
}

function showSkeleton() {
  const container = document.getElementById('releases-timeline');
  if (!container) return;
  container.innerHTML = `
    <div class="timeline-skeleton">
      ${Array.from({ length: 3 }, () => `
        <div class="skeleton-card">
          <div class="skeleton-line title w40"></div>
          <div class="skeleton-line w80"></div>
          <div class="skeleton-line w60"></div>
          <div class="skeleton-line w100"></div>
        </div>
      `).join('')}
    </div>
  `;
}

function showError() {
  const container = document.getElementById('releases-timeline');
  if (!container) return;
  container.innerHTML = `
    <div class="timeline-error">
      <span class="material-icons-round">cloud_off</span>
      <h3>Could not load releases</h3>
      <p>GitHub API might be rate-limited. Try again in a moment. ( > . < )</p>
      <button id="retry-releases-btn">
        <span class="material-icons-round">refresh</span>
        Retry
      </button>
    </div>
  `;
  document.getElementById('retry-releases-btn')?.addEventListener('click', () => {
    loadInitialReleases();
  });
}

function observeCards(container: Element) {
  const observer = new IntersectionObserver((entries) => {
    entries.forEach(entry => {
      if (entry.isIntersecting) {
        entry.target.classList.add('visible');
        observer.unobserve(entry.target);
      }
    });
  }, { threshold: 0.1 });

  container.querySelectorAll('.release-card:not(.visible), .announcement-card:not(.visible)').forEach(card => {
    observer.observe(card);
  });
}

async function loadInitialReleases() {
  const container = document.getElementById('releases-timeline');
  if (!container) return;

  currentPage = 1;
  allLoaded = false;
  showSkeleton();

  try {
    const releases = await fetchReleases(1);
    if (releases.length < PER_PAGE) allLoaded = true;

    container.innerHTML = `
      <div class="timeline" id="timeline-list">
        ${releases.map((r, i) => renderReleaseCard(r, i)).join('')}
      </div>
      ${!allLoaded ? `
        <div class="load-more-wrapper">
          <button class="load-more-btn" id="load-more-btn">
            <span class="material-icons-round">expand_more</span>
            Load older releases
          </button>
        </div>
      ` : ''}
    `;

    observeCards(container);
    wireLoadMore();
  } catch {
    showError();
  }
}

function wireLoadMore() {
  const btn = document.getElementById('load-more-btn');
  if (!btn) return;

  btn.addEventListener('click', async () => {
    if (isLoading || allLoaded) return;
    isLoading = true;
    btn.classList.add('loading');
    btn.innerHTML = `
      <span class="material-icons-round" style="animation: spin 1s linear infinite;">sync</span>
      Loading...
    `;

    try {
      currentPage++;
      const releases = await fetchReleases(currentPage);
      if (releases.length < PER_PAGE) allLoaded = true;

      const timeline = document.getElementById('timeline-list');
      if (timeline) {
        const existingCount = timeline.querySelectorAll('.release-card').length;
        const fragment = document.createRange().createContextualFragment(
          releases.map((r, i) => renderReleaseCard(r, existingCount + i)).join('')
        );
        timeline.appendChild(fragment);
        observeCards(timeline);
      }

      if (allLoaded) {
        btn.remove();
      } else {
        btn.classList.remove('loading');
        btn.innerHTML = `
          <span class="material-icons-round">expand_more</span>
          Load older releases
        `;
      }
    } catch {
      btn.classList.remove('loading');
      btn.innerHTML = `
        <span class="material-icons-round">error_outline</span>
        Retry
      `;
    }
    isLoading = false;
  });
}

/* ===== Tab switching ===== */
function initTabs() {
  const tabs   = document.querySelectorAll<HTMLButtonElement>('.updates-tab');
  const panels = document.querySelectorAll<HTMLElement>('.updates-panel');

  tabs.forEach(tab => {
    tab.addEventListener('click', () => {
      const target = tab.dataset.tab;
      if (!target) return;

      tabs.forEach(t => t.classList.remove('active'));
      tab.classList.add('active');

      panels.forEach(panel => {
        if (panel.id === `panel-${target}`) {
          panel.classList.add('active');
          // Re-observe cards for animations
          observeCards(panel);
        } else {
          panel.classList.remove('active');
        }
      });
    });
  });
}

/* ===== Announcements reveal ===== */
function initAnnouncementReveal() {
  const panel = document.getElementById('panel-announcements');
  if (panel) observeCards(panel);
}

/* ===== Spin keyframe (injected once) ===== */
function injectSpinKeyframe() {
  if (document.getElementById('spin-keyframe')) return;
  const style = document.createElement('style');
  style.id = 'spin-keyframe';
  style.textContent = `@keyframes spin { from { transform: rotate(0deg); } to { transform: rotate(360deg); } }`;
  document.head.appendChild(style);
}

/* ===== Init ===== */
function initUpdatesPage() {
  // Only run on the updates page
  if (!document.getElementById('releases-timeline')) return;

  injectSpinKeyframe();
  initTabs();
  loadInitialReleases();
  initAnnouncementReveal();
}

document.addEventListener('astro:page-load', initUpdatesPage);
