export const SITE = {
  name: 'KittyTune',
  url: "https://alan7383.github.io/kittytune",
  github: 'https://github.com/alan7383/kittytune',
  releases: 'https://github.com/alan7383/kittytune/releases',
  issues: 'https://github.com/alan7383/kittytune/issues',
} as const;

export const NAV_ITEMS = [
  { key: 'home', label: 'Home', href: 'index.html', icon: 'home', logo: true },
  { key: 'custom', label: 'Custom', href: 'custom.html', icon: 'palette' },
  { key: 'audio-fx', label: 'Audio FX', href: 'audio-fx.html', icon: 'graphic_eq' },
] as const;

export const PLACEHOLDER_NAV_ITEMS = [
  { label: 'Badges', icon: 'emoji_events', message: 'Badges are not available yet! ( > . < )' },
  { label: 'Updates', icon: 'update', message: 'Updates are not available yet! ( > . < )' },
] as const;

export const PAGES = {
  "home": {
    "key": "home",
    "title": "KittyTune ✧ Open Source Music Player",
    "description": "An Android music player built with Jetpack Compose. SoundCloud, YouTube, local files — with real-time effects like Nightcore and 8D. No accounts, no ads.",
    "keywords": "KittyTune, FOSS, Android Music Player, Material You, SoundCloud Client, Nightcore, Audio FX, Jetpack Compose, Ad-free, Indie App",
    "canonical": "https://alan7383.github.io/kittytune/",
    "og": [
      {
        "property": "og:title",
        "content": "KittyTune ( ◡‿◡ *)"
      },
      {
        "property": "og:description",
        "content": "SoundCloud, YouTube, local files, and audio effects in one app. Material You, no login required."
      },
      {
        "property": "og:image",
        "content": "https://alan7383.github.io/kittytune/preview-image.jpg"
      },
      {
        "property": "og:url",
        "content": "https://alan7383.github.io/kittytune/"
      },
      {
        "property": "og:type",
        "content": "website"
      },
      {
        "property": "og:site_name",
        "content": "KittyTune"
      }
    ],
    "twitter": [
      {
        "name": "twitter:card",
        "content": "summary_large_image"
      },
      {
        "name": "twitter:title",
        "content": "KittyTune ✧ Audio & Vibes"
      },
      {
        "name": "twitter:description",
        "content": "Open source Android music player. SoundCloud + YouTube + local files, no accounts needed."
      }
    ],
    "schema": "{\r\n              \"@context\": \"https://schema.org\",\r\n              \"@type\": \"SoftwareApplication\",\r\n              \"name\": \"KittyTune\",\r\n              \"operatingSystem\": \"Android\",\r\n              \"applicationCategory\": \"MultimediaApplication\",\r\n              \"offers\": {\r\n                \"@type\": \"Offer\",\r\n                \"price\": \"0\",\r\n                \"priceCurrency\": \"USD\",\r\n                \"availability\": \"https://schema.org/InStock\"\r\n              },\r\n              \"description\": \"An open-source hybrid music player featuring SoundCloud, YouTube playback, and real-time audio effects.\",\r\n                \"downloadUrl\": \"https://github.com/alan7383/kittytune/releases\",\r\n                \"author\": {\r\n                    \"@type\": \"Person\",\r\n                    \"name\": \"alan7383\"\r\n                },\r\n                \"license\": \"GPL-3.0 License\" \r\n                }",
    "styles": [
      "css/shared/home-audio.css",
      "css/pages/home.css"
    ]
  },
  "custom": {
    "key": "custom",
    "title": "KittyTune ✧ Open Source Music Player",
    "description": "KittyTune customization studio: variable typography, Material You color palettes, player styles, and Android widgets.",
    "canonical": "https://alan7383.github.io/kittytune/custom.html",
    "og": [],
    "twitter": [],
    "styles": [
      "css/pages/custom.css"
    ]
  },
  "audio-fx": {
    "key": "audio-fx",
    "title": "KittyTune ✧ Open Source Music Player",
    "description": "An Android music player built with Jetpack Compose. SoundCloud, YouTube, local files — with real-time effects like Nightcore and 8D. No accounts, no ads.",
    "keywords": "KittyTune, FOSS, Android Music Player, Material You, SoundCloud Client, Nightcore, Audio FX, Jetpack Compose, Ad-free, Indie App",
    "canonical": "https://alan7383.github.io/kittytune/audio-fx.html",
    "og": [
      {
        "property": "og:title",
        "content": "KittyTune ( ◡‿◡ *)"
      },
      {
        "property": "og:description",
        "content": "SoundCloud, YouTube, local files, and audio effects in one app. Material You, no login required."
      },
      {
        "property": "og:image",
        "content": "https://alan7383.github.io/kittytune/preview-image.jpg"
      },
      {
        "property": "og:url",
        "content": "https://alan7383.github.io/kittytune/audio-fx.html"
      },
      {
        "property": "og:type",
        "content": "website"
      },
      {
        "property": "og:site_name",
        "content": "KittyTune"
      }
    ],
    "twitter": [
      {
        "name": "twitter:card",
        "content": "summary_large_image"
      },
      {
        "name": "twitter:title",
        "content": "KittyTune ✧ Audio & Vibes"
      },
      {
        "name": "twitter:description",
        "content": "Open source Android music player. SoundCloud + YouTube + local files, no accounts needed."
      }
    ],
    "schema": "{\r\n              \"@context\": \"https://schema.org\",\r\n              \"@type\": \"SoftwareApplication\",\r\n              \"name\": \"KittyTune\",\r\n              \"operatingSystem\": \"Android\",\r\n              \"applicationCategory\": \"MultimediaApplication\",\r\n              \"offers\": {\r\n                \"@type\": \"Offer\",\r\n                \"price\": \"0\",\r\n                \"priceCurrency\": \"USD\",\r\n                \"availability\": \"https://schema.org/InStock\"\r\n              },\r\n              \"description\": \"An open-source hybrid music player featuring SoundCloud, YouTube playback, and real-time audio effects.\",\r\n                \"downloadUrl\": \"https://github.com/alan7383/kittytune/releases\",\r\n                \"author\": {\r\n                    \"@type\": \"Person\",\r\n                    \"name\": \"alan7383\"\r\n                },\r\n                \"license\": \"GPL-3.0 License\" \r\n                }",
    "styles": [
      "css/shared/home-audio.css",
      "css/pages/audio-fx.css"
    ]
  }
} as const;

export type PageKey = keyof typeof PAGES;
export type PageConfig = typeof PAGES[PageKey];
