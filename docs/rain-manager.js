(function() {
    if (window.rainManagerInited) return;
    window.rainManagerInited = true;

    const audio = document.createElement('audio');
    audio.id = 'global-rain-audio';
    audio.src = 'rain.mp3';
    audio.loop = true;
    audio.volume = 0;
    document.body.appendChild(audio);

    const CACHED_RAIN_VOL = 'kitty_rain_volume_enabled';
    let currentRainState = 'none'; // 'none', 'light', 'heavy'
    let targetVol = 0;
    let fadeInterval = null;
    let isGloballyMuted = localStorage.getItem(CACHED_RAIN_VOL) === 'muted';

    // --- HTML5 CANVAS RAIN SYSTEM ---
    const canvas = document.createElement('canvas');
    canvas.id = 'global-rain-canvas';
    Object.assign(canvas.style, {
        position: 'fixed',
        top: '0', left: '0',
        width: '100vw', height: '100vh',
        pointerEvents: 'none',
        zIndex: '9998', // Just below the loading screen and top modals
        opacity: '0',
        transition: 'opacity 1.5s cubic-bezier(0.2, 0, 0, 1)'
    });
    document.body.appendChild(canvas);
    const ctx = canvas.getContext('2d');
    let rainDrops = [];
    let rainAnimationId;
    let canvasW, canvasH;

    function resizeCanvas() {
        canvasW = window.innerWidth;
        canvasH = window.innerHeight;
        canvas.width = canvasW;
        canvas.height = canvasH;
    }
    window.addEventListener('resize', resizeCanvas);
    resizeCanvas();

    function initRaindrops() {
        rainDrops = [];
        // Scale drop count relative to screen width and rain intensity
        const baseDrops = currentRainState === 'heavy' ? 80 : 25;
        const count = Math.floor(baseDrops * (canvasW / 1200)); 
        for (let i = 0; i < count; i++) {
            rainDrops.push({
                x: Math.random() * canvasW,
                y: Math.random() * canvasH,
                l: Math.random() * 20 + (currentRainState === 'heavy' ? 30 : 15),
                xs: -2 + Math.random() * 1.5,
                ys: Math.random() * 15 + (currentRainState === 'heavy' ? 20 : 10)
            });
        }
    }

    function drawRain() {
        ctx.clearRect(0, 0, canvasW, canvasH);
        
        ctx.strokeStyle = currentRainState === 'heavy' 
            ? 'rgba(200, 220, 255, 0.25)' 
            : 'rgba(255, 255, 255, 0.1)';
        ctx.lineWidth = currentRainState === 'heavy' ? 1.5 : 1;
        ctx.lineCap = 'round';
        
        ctx.beginPath();
        for (let i = 0; i < rainDrops.length; i++) {
            let p = rainDrops[i];
            ctx.moveTo(p.x, p.y);
            ctx.lineTo(p.x + p.xs, p.y + p.ys);
            p.x += p.xs;
            p.y += p.ys;
            if (p.x > canvasW || p.y > canvasH) {
                p.x = Math.random() * canvasW;
                p.y = -20;
            }
        }
        ctx.stroke();
        rainAnimationId = requestAnimationFrame(drawRain);
    }
    // --- END CANVAS RAIN ---

    function updateBentoUI() {
        const btn = document.getElementById('rain-btn');
        const panel = document.getElementById('rain-panel');
        const face = document.getElementById('rain-face');
        if (btn && panel) {
            if (currentRainState === 'heavy') {
                if (!panel.classList.contains('rain-active')) panel.classList.add('rain-active');
                if (btn.textContent !== 'Mute') btn.textContent = 'Mute';
                btn.style.background = '#6eb5ff';
                btn.style.color = '#00254d';
                if (face && face.textContent !== '( ˘︶˘ )') face.textContent = '( ˘︶˘ )';
            } else {
                if (panel.classList.contains('rain-active')) panel.classList.remove('rain-active');
                if (btn.textContent !== 'Mix Ambience') btn.textContent = 'Mix Ambience';
                btn.style.background = 'transparent';
                btn.style.color = 'white';
                if (face && face.textContent !== '[ /// ]') face.textContent = '[ /// ]';
            }
        }
    }

    window.updateMiniplayerUI = function() {
        const miniplayers = document.querySelectorAll('.rain-miniplayer');
        miniplayers.forEach(mp => {
            const isRainPlaying = currentRainState !== 'none';
            const isMusicPlaying = window.kittyFX && window.kittyFX.isPlaying;

            if (isRainPlaying || isMusicPlaying) {
                mp.classList.add('playing');
                mp.classList.remove('muted'); 

                const icon = mp.querySelector('.material-icons-round');
                if (icon) {
                    if (isMusicPlaying && isRainPlaying && !isGloballyMuted) {
                        icon.textContent = 'graphic_eq'; 
                    } else if (isMusicPlaying) {
                        icon.textContent = 'music_note'; 
                    } else {
                        icon.textContent = isGloballyMuted ? 'volume_off' : 'cloud_sync';
                        if (isGloballyMuted && !isMusicPlaying) mp.classList.add('muted');
                    }
                }
            } else {
                mp.classList.remove('playing');
                mp.classList.remove('muted');
            }
        });
    };

    function fadeToVolume(target, duration = 1500) {
        if (fadeInterval) clearInterval(fadeInterval);
        if (isGloballyMuted && target > 0) target = 0;

        if (target > 0) {
            audio.play().catch(e => console.log('Audio block:', e));
        }

        const startVol = audio.volume;
        const diff = target - startVol;
        if (diff === 0) return;

        const steps = 30;
        const stepTime = duration / steps;
        let stepCount = 0;

        fadeInterval = setInterval(() => {
            stepCount++;
            let newVol = startVol + (diff * (stepCount / steps));
            if (newVol < 0) newVol = 0;
            if (newVol > 1) newVol = 1;
            
            try { audio.volume = newVol; } catch(e){}

            if (stepCount >= steps) {
                clearInterval(fadeInterval);
                audio.volume = target;
                if (target === 0) {
                    audio.pause();
                }
            }
        }, stepTime);
    }

    window.setRainState = function(state) {
        currentRainState = state;
        
        // Handle Global Canvas Render
        if (state !== 'none' && !isGloballyMuted) {
            canvas.style.opacity = '1';
            initRaindrops();
            if (!rainAnimationId) drawRain();
        } else {
            canvas.style.opacity = '0';
            setTimeout(() => {
                // Garbage collect logic when faded out
                if (currentRainState === 'none' || isGloballyMuted) {
                    if (rainAnimationId) cancelAnimationFrame(rainAnimationId);
                    rainAnimationId = null;
                }
            }, 1500);
        }

        if (state === 'heavy') {
            fadeToVolume(0.15, 1500);
        } else if (state === 'light') {
            fadeToVolume(0.05, 2000);
        } else {
            fadeToVolume(0, 1500);
        }
        updateBentoUI();
        window.updateMiniplayerUI();
    };

    window.toggleRain = function() {
        if (currentRainState === 'heavy') {
            setRainState('none');
        } else {
            if(isGloballyMuted) {
                isGloballyMuted = false;
                localStorage.removeItem(CACHED_RAIN_VOL);
            }
            setRainState('heavy');
        }
    };

    window.toggleRainMute = function() {
        const isRainPlaying = currentRainState !== 'none';
        const isMusicPlaying = window.kittyFX && window.kittyFX.isPlaying;

        if (isMusicPlaying && isRainPlaying) {
            if (window.kittyFX.pause) window.kittyFX.pause();
            if (!isGloballyMuted) {
                isGloballyMuted = true;
                localStorage.setItem(CACHED_RAIN_VOL, 'muted');
                setRainState(currentRainState); 
            }
        } else if (isMusicPlaying) {
            if (window.kittyFX.pause) window.kittyFX.pause();
        } else if (isRainPlaying) {
            isGloballyMuted = !isGloballyMuted;
            localStorage.setItem(CACHED_RAIN_VOL, isGloballyMuted ? 'muted' : 'unmuted');
            setRainState(currentRainState); 
        }
    };

    document.addEventListener('click', function(e) {
        const isPlayBtn = e.target.closest('.nav-bottom-action') || 
                          e.target.closest('.play-overlay');

        if (isPlayBtn) {
            const loader = document.getElementById('loading-screen');
            if (loader && !loader.classList.contains('hidden')) {
                if (currentRainState === 'none') {
                    setTimeout(() => {
                         if (document.getElementById('loading-screen') && document.getElementById('loading-screen').classList.contains('hidden')) {
                             if (currentRainState === 'none') setRainState('light');
                         }
                    }, 800);
                }
            } else {
                if (currentRainState === 'none') {
                    if(isGloballyMuted) {
                        isGloballyMuted = false;
                        localStorage.removeItem(CACHED_RAIN_VOL);
                    }
                    setRainState('light');
                }
            }
        }
    });

    function init() {
       updateBentoUI();
       window.updateMiniplayerUI();
    }
    
    document.addEventListener('DOMContentLoaded', init);
    const observer = new MutationObserver(() => {
        observer.disconnect();
        updateBentoUI(); 
        if (window.syncFXPlayerUI) window.syncFXPlayerUI();
        observer.observe(document.body, { childList: true, subtree: true });
    });
    observer.observe(document.body, { childList: true, subtree: true });

})();
