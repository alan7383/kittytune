/**
 * Announcements data — curated messages from the project owner.
 * Add new entries at the TOP of the array (newest first).
 *
 * type: 'info' | 'warning' | 'celebration' | 'milestone'
 */

export interface Announcement {
  id: string;
  date: string;           // ISO 8601 date string  (YYYY-MM-DD)
  type: 'info' | 'warning' | 'celebration' | 'milestone';
  title: string;
  body: string;           // Supports basic HTML like <br>, <a>, <strong>
  pinned?: boolean;
  link?: { label: string; url: string };
}

export const ANNOUNCEMENTS: Announcement[] = [
  {
    id: 'sc-login-fixed',
    date: '2026-05-17',
    type: 'celebration',
    title: 'SoundCloud Login is Back! 🎉',
    body: 'We reverse-engineered the entire SoundCloud login flow. OAuth sessions now persist across reboots, and liking tracks is <strong>finally</strong> synced natively.',
    pinned: true,
    link: { label: 'Get v2.39.5', url: 'https://github.com/alan7383/kittytune/releases/tag/v2.39.5' },
  },
  {
    id: 'website-live',
    date: '2026-05-16',
    type: 'milestone',
    title: 'KittyTune Website Is Live',
    body: 'The official KittyTune website is now available. Explore features, try audio FX demos, and grab the latest APK — all in one place.',
  },
  {
    id: 'sleep-timer-upgrade',
    date: '2026-04-21',
    type: 'info',
    title: 'Sleep Timer Got an Upgrade',
    body: 'v2.39.3 brings a fade-out effect to the sleep timer, plus a precise seconds countdown. Sweet dreams. 🌙',
    link: { label: 'Release Notes', url: 'https://github.com/alan7383/kittytune/releases/tag/v2.39.3' },
  },
  {
    id: 'offline-mode',
    date: '2026-04-21',
    type: 'info',
    title: 'Offline Mode UI',
    body: 'KittyTune now shows an expressive offline banner instead of crashing silently. Because even error states deserve love.',
  },
];
