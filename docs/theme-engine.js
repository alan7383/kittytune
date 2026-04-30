// theme-engine.js
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

    // Fonction globale pour appliquer et sauvegarder le thème
    window.applyMonetTheme = function(hex, styleName) {
        const root = document.documentElement;
        const base = hexToHsl(hex);
        const tuning = {
            TonalSpot: { sat: 0.72, lift: 0, shift: 42 },
            Vibrant: { sat: 1.18, lift: 2, shift: 68 },
            Expressive: { sat: 0.94, lift: 4, shift: 118 },
            Monochrome: { sat: 0.1, lift: -2, shift: 0 }
        }[styleName] || { sat: 0.72, lift: 0, shift: 42 };

        const s = clamp(base.s * tuning.sat, styleName === "Monochrome" ? 3 : 38, 96);
        const h = base.h;

        // Application des variables CSS sur le :root
        root.style.setProperty("--seed-h", h);
        root.style.setProperty("--seed-s", `${Math.round(s)}%`);
        root.style.setProperty("--seed-l", `${base.l}%`);
        root.style.setProperty("--md-sys-color-primary", hsl(h, s, 78 + tuning.lift));
        root.style.setProperty("--md-sys-color-on-primary", hsl(h, 46, 18));
        root.style.setProperty("--md-sys-color-primary-container", hsl(h, s * 0.72, 32));
        root.style.setProperty("--md-sys-color-on-primary-container", hsl(h, 82, 90));
        root.style.setProperty("--md-sys-color-secondary", hsl(h + 24, Math.max(22, s * 0.38), 76));
        root.style.setProperty("--md-sys-color-secondary-container", hsl(h + 24, Math.max(20, s * 0.32), 30));
        root.style.setProperty("--md-sys-color-on-secondary-container", hsl(h + 24, 62, 90));
        root.style.setProperty("--md-sys-color-tertiary", hsl(h + tuning.shift, Math.max(34, s * 0.62), 76));
        root.style.setProperty("--md-sys-color-tertiary-container", hsl(h + tuning.shift, Math.max(30, s * 0.5), 30));
        root.style.setProperty("--md-sys-color-on-tertiary-container", hsl(h + tuning.shift, 72, 90));

        // Sauvegarde dans le navigateur
        localStorage.setItem('kittytune_theme_color', hex);
        localStorage.setItem('kittytune_theme_style', styleName);
    };

    // Chargement automatique dès que la balise <head> est lue (empêche le clignotement)
    const savedColor = localStorage.getItem('kittytune_theme_color') || "#d0bcff";
    const savedStyle = localStorage.getItem('kittytune_theme_style') || "TonalSpot";
    window.applyMonetTheme(savedColor, savedStyle);
})();
