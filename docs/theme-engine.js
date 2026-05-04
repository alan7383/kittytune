(function() {
    function clamp(value, min, max) { return Math.max(min, Math.min(max, value)); }
    function hexToHsl(hex) {
        const clean = hex.replace("#", "");
        const r = parseInt(clean.substring(0, 2), 16) / 255;
        const g = parseInt(clean.substring(2, 4), 16) / 255;
        const b = parseInt(clean.substring(4, 6), 16) / 255;
        const max = Math.max(r, g, b), min = Math.min(r, g, b);
        let h = 0, s = 0, l = (max + min) / 2;
        if (max !== min) {
            const d = max - min;
            s = l > 0.5 ? d / (2 - max - min) : d / (max + min);
            switch (max) {
                case r: h = (g - b) / d + (g < b ? 6 : 0); break;
                case g: h = (b - r) / d + 2; break;
                default: h = (r - g) / d + 4;
            }
            h *= 60;
        }
        return { h: Math.round(h), s: Math.round(s * 100), l: Math.round(l * 100) };
    }
    function hsl(h, s, l) {
        return `hsl(${Math.round((h + 360) % 360)} ${clamp(s, 0, 100)}% ${clamp(l, 0, 100)}%)`;
    }
    const systemPrefersLight = window.matchMedia && window.matchMedia('(prefers-color-scheme: light)').matches;
    const savedMode = localStorage.getItem('kittytune_theme_mode');
    if (savedMode === 'light' || (!savedMode && systemPrefersLight)) {
        document.documentElement.classList.add('light-mode');
    }
    if (window.matchMedia) {
        window.matchMedia('(prefers-color-scheme: light)').addEventListener('change', e => {
            if (localStorage.getItem('kittytune_theme_mode')) return;
            const updateSystemTheme = () => {
                if (e.matches) {
                    document.documentElement.classList.add('light-mode');
                } else {
                    document.documentElement.classList.remove('light-mode');
                }
                if (window.updateThemeIcons) window.updateThemeIcons();
                const color = localStorage.getItem('kittytune_theme_color') || "#d0bcff";
                const style = localStorage.getItem('kittytune_theme_style') || "TonalSpot";
                if (window.applyMonetTheme) window.applyMonetTheme(color, style);
            };
            if (document.startViewTransition) {
                document.startViewTransition(updateSystemTheme);
            } else {
                updateSystemTheme();
            }
        });
    }
    window.applyMonetTheme = function(hex, styleName) {
        const root = document.documentElement;
        const base = hexToHsl(hex);
        const isLight = root.classList.contains('light-mode');
        const tuning = {
            TonalSpot: { sat: 0.72, shift: 42 },
            Vibrant:   { sat: 1.18, shift: 68 },
            Expressive:{ sat: 0.94, shift: 118 },
            Monochrome:{ sat: 0.1,  shift: 0 }
        }[styleName] || { sat: 0.72, shift: 42 };
        const s = clamp(base.s * tuning.sat, styleName === "Monochrome" ? 3 : 38, 96);
        const h = base.h;
        const pL  = isLight ? 40 : 80;
        const opL = isLight ? 100 : 20;
        const pcL = isLight ? 90 : 30;
        const opcL= isLight ? 10 : 90;
        root.style.setProperty("--seed-h", h);
        root.style.setProperty("--seed-s", `${Math.round(s)}%`);
        root.style.setProperty("--seed-l", `${base.l}%`);
        root.style.setProperty("--md-sys-color-primary", hsl(h, s, pL));
        root.style.setProperty("--md-sys-color-on-primary", hsl(h, s, opL));
        root.style.setProperty("--md-sys-color-primary-container", hsl(h, s * 0.72, pcL));
        root.style.setProperty("--md-sys-color-on-primary-container", hsl(h, s, opcL));
        root.style.setProperty("--md-sys-color-secondary", hsl(h + 24, Math.max(22, s * 0.38), pL - 5));
        root.style.setProperty("--md-sys-color-secondary-container", hsl(h + 24, Math.max(20, s * 0.32), pcL));
        root.style.setProperty("--md-sys-color-on-secondary-container", hsl(h + 24, 62, opcL));
        root.style.setProperty("--md-sys-color-tertiary", hsl(h + tuning.shift, Math.max(34, s * 0.62), pL));
        root.style.setProperty("--md-sys-color-tertiary-container", hsl(h + tuning.shift, Math.max(30, s * 0.5), pcL));
        root.style.setProperty("--md-sys-color-on-tertiary-container", hsl(h + tuning.shift, 72, opcL));
        localStorage.setItem('kittytune_theme_color', hex);
        localStorage.setItem('kittytune_theme_style', styleName);
    };
    window.toggleThemeMode = function() {
        const updateTheme = () => {
            const root = document.documentElement;
            const isLight = root.classList.toggle('light-mode');
            localStorage.setItem('kittytune_theme_mode', isLight ? 'light' : 'dark');
            const savedColor = localStorage.getItem('kittytune_theme_color') || "#d0bcff";
            const savedStyle = localStorage.getItem('kittytune_theme_style') || "TonalSpot";
            window.applyMonetTheme(savedColor, savedStyle);
            window.updateThemeIcons();
        };
        if (document.startViewTransition) {
            document.startViewTransition(updateTheme);
        } else {
            updateTheme();
        }
    };
    window.updateThemeIcons = function() {
        const isLight = document.documentElement.classList.contains('light-mode');
        document.querySelectorAll('.theme-toggle-icon').forEach(icon => {
            icon.textContent = isLight ? 'dark_mode' : 'light_mode';
        });
    };
    const savedColor = localStorage.getItem('kittytune_theme_color') || "#d0bcff";
    const savedStyle = localStorage.getItem('kittytune_theme_style') || "TonalSpot";
    window.applyMonetTheme(savedColor, savedStyle);
    document.addEventListener('DOMContentLoaded', window.updateThemeIcons);
})();