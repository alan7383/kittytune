(function() {
    if (window.fxPlayerInited) return;
    window.fxPlayerInited = true;
    window.kittyFX = {
        isPlaying: false,
        offsetTime: 0,
        pause: null,
        play: null,
        activeEffects: {
            '8d': false,
            'nightcore': false,
            'reverb': false,
            'muffled': false
        }
    };
    let audioContext;
    let audioBuffer = null;
    let sourceNode;
    let panNode;
    let nightcoreFilter;
    let convolverNode;
    let muffledFilter;
    let isPlaying = false;
    let startTime = 0;
    let lfoId = null;
    let progressDrag = false;
    let lastRenderId = null;
    const updateUITotalTime = () => {
        const timeTotal = document.getElementById('fx-time-total');
        if (timeTotal && audioBuffer) {
            let minutes = Math.floor(audioBuffer.duration / 60);
            let seconds = Math.floor(audioBuffer.duration % 60).toString().padStart(2, '0');
            timeTotal.textContent = `${minutes}:${seconds}`;
        }
    };
    const loadAudio = async () => {
        try {
            audioContext = new (window.AudioContext || window.webkitAudioContext)();
            const response = await fetch('assets/audio/music.mp3');
            const arrayBuffer = await response.arrayBuffer();
            audioBuffer = await audioContext.decodeAudioData(arrayBuffer);
            updateUITotalTime();
        } catch(e) {
            console.error("Failed to load assets/audio/music.mp3", e);
        }
    };
    const createDSPGraph = () => {
        if (sourceNode) sourceNode.disconnect();
        sourceNode = audioContext.createBufferSource();
        sourceNode.buffer = audioBuffer;
        sourceNode.loop = true;
        panNode = audioContext.createStereoPanner();
        nightcoreFilter = audioContext.createBiquadFilter();
        nightcoreFilter.type = 'highshelf';
        nightcoreFilter.frequency.value = 4000;
        nightcoreFilter.gain.value = window.kittyFX.activeEffects['nightcore'] ? 6 : 0;
        convolverNode = audioContext.createConvolver();
        const length = audioContext.sampleRate * 2.0; 
        const impulse = audioContext.createBuffer(2, length, audioContext.sampleRate);
        for (let i = 0; i < 2; i++) {
            const channel = impulse.getChannelData(i);
            for (let j = 0; j < length; j++) {
                channel[j] = (Math.random() * 2 - 1) * Math.pow(1 - j / length, 4);
            }
        }
        convolverNode.buffer = impulse;
        const dryGain = audioContext.createGain();
        const wetGain = audioContext.createGain();
        dryGain.gain.value = window.kittyFX.activeEffects['reverb'] ? 0.6 : 1.0;
        wetGain.gain.value = window.kittyFX.activeEffects['reverb'] ? 0.5 : 0.0;
        muffledFilter = audioContext.createBiquadFilter();
        muffledFilter.type = 'lowpass';
        muffledFilter.Q.value = 0.5;
        muffledFilter.frequency.value = window.kittyFX.activeEffects['muffled'] ? 300 : 20000;
        let playbackRate = 1.0;
        if (window.kittyFX.activeEffects['nightcore']) playbackRate *= 1.35;
        if (window.kittyFX.activeEffects['reverb']) playbackRate *= 0.8;
        sourceNode.playbackRate.value = playbackRate;
        sourceNode.connect(panNode);
        panNode.connect(nightcoreFilter);
        nightcoreFilter.connect(dryGain);
        nightcoreFilter.connect(convolverNode);
        convolverNode.connect(wetGain);
        dryGain.connect(muffledFilter);
        wetGain.connect(muffledFilter);
        let masterGain = audioContext.createGain();
        masterGain.gain.value = 0.75;
        muffledFilter.connect(masterGain);
        masterGain.connect(audioContext.destination);
    };
    const start8D = () => {
        if (lfoId) cancelAnimationFrame(lfoId);
        const lfoLoop = () => {
            if (panNode) panNode.pan.value = Math.sin(Date.now() / 800) * 0.9;
            lfoId = requestAnimationFrame(lfoLoop);
        };
        lfoLoop();
    };
    const startPlayback = () => {
        if (!audioBuffer) return;
        createDSPGraph();
        sourceNode.start(0, window.kittyFX.offsetTime);
        startTime = audioContext.currentTime;
        isPlaying = true;
        window.kittyFX.isPlaying = true;
        window.syncFXPlayerUI();
        if (window.kittyFX.activeEffects['8d']) start8D();
        if (window.updateMiniplayerUI) window.updateMiniplayerUI();
        updateProgress();
    };
    const pauseAudio = () => {
        if (sourceNode) {
            try { sourceNode.stop(); } catch(e){}
            window.kittyFX.offsetTime += (audioContext.currentTime - startTime) * sourceNode.playbackRate.value;
            window.kittyFX.offsetTime = window.kittyFX.offsetTime % audioBuffer.duration;
            sourceNode.disconnect();
        }
        isPlaying = false;
        window.kittyFX.isPlaying = false;
        window.syncFXPlayerUI();
        if (lfoId) cancelAnimationFrame(lfoId);
        if (lastRenderId) cancelAnimationFrame(lastRenderId);
        if (window.updateMiniplayerUI) window.updateMiniplayerUI();
    };
    window.kittyFX.pause = pauseAudio;
    window.kittyFX.play = async () => {
        if (!audioContext) {
            const playIcon = document.getElementById('live-fx-play-icon');
            if (playIcon) playIcon.textContent = 'hourglass_empty';
            await loadAudio();
        }
        if (audioContext && audioContext.state === 'suspended') await audioContext.resume();
        if (isPlaying) pauseAudio();
        else startPlayback();
    };
    window.syncFXPlayerUI = function() {
        const playIcon = document.getElementById('live-fx-play-icon');
        if (playIcon) playIcon.textContent = isPlaying ? 'pause' : 'play_arrow';
        if (audioBuffer) updateUITotalTime();
        if (document.getElementById('toggle-8d')) {
            document.getElementById('toggle-8d').checked = window.kittyFX.activeEffects['8d'];
            document.getElementById('toggle-nightcore').checked = window.kittyFX.activeEffects['nightcore'];
            document.getElementById('toggle-reverb').checked = window.kittyFX.activeEffects['reverb'];
            document.getElementById('toggle-muffled').checked = window.kittyFX.activeEffects['muffled'];
        }
    };
    const updateProgress = () => {
        if (!isPlaying) return;
        if (!progressDrag && audioBuffer) {
            let current = (window.kittyFX.offsetTime + (audioContext.currentTime - startTime) * sourceNode.playbackRate.value) % audioBuffer.duration;
            const percent = current / audioBuffer.duration;
            const pSlider = document.getElementById('fx-progress-slider');
            const pCont = document.getElementById('fx-progress-container');
            const tCurr = document.getElementById('fx-time-current');
            if (pSlider && pCont && tCurr) {
                pSlider.value = percent;
                pCont.style.setProperty('--val', percent);
                let minutes = Math.floor(current / 60);
                let seconds = Math.floor(current % 60).toString().padStart(2, '0');
                tCurr.textContent = `${minutes}:${seconds}`;
            }
        }
        lastRenderId = requestAnimationFrame(updateProgress);
    };
    document.addEventListener('click', async e => {
        const playBtn = e.target.closest('#live-fx-play-btn');
        if (playBtn) await window.kittyFX.play();
    });
    document.addEventListener('change', e => {
        if (e.target.id === 'toggle-8d') window.kittyFX.activeEffects['8d'] = e.target.checked;
        if (e.target.id === 'toggle-nightcore') window.kittyFX.activeEffects['nightcore'] = e.target.checked;
        if (e.target.id === 'toggle-reverb') window.kittyFX.activeEffects['reverb'] = e.target.checked;
        if (e.target.id === 'toggle-muffled') window.kittyFX.activeEffects['muffled'] = e.target.checked;
        if (['toggle-nightcore', 'toggle-reverb', 'toggle-muffled'].includes(e.target.id)) {
            if (isPlaying) { pauseAudio(); startPlayback(); }
        }
        if (e.target.id === 'toggle-8d') {
            if (isPlaying) {
                if (e.target.checked) start8D();
                else {
                    if (lfoId) cancelAnimationFrame(lfoId);
                    if (panNode) panNode.pan.value = 0;
                }
            }
        }
    });
    const addDrag = () => {
        progressDrag = true;
        const pCont = document.getElementById('fx-progress-container');
        if (pCont) pCont.classList.add('is-dragging');
    };
    const removeDrag = () => {
        progressDrag = false;
        const pCont = document.getElementById('fx-progress-container');
        if (pCont) pCont.classList.remove('is-dragging');
    };
    document.addEventListener('mousedown', e => { if (e.target.id === 'fx-progress-slider') addDrag(); });
    document.addEventListener('touchstart', e => { if (e.target.id === 'fx-progress-slider') addDrag(); }, {passive: true});
    window.addEventListener('mouseup', removeDrag);
    window.addEventListener('touchend', removeDrag);
    document.addEventListener('input', e => {
        if (e.target.id === 'fx-progress-slider') {
            let val = parseFloat(e.target.value);
            const pCont = document.getElementById('fx-progress-container');
            const tCurr = document.getElementById('fx-time-current');
            if (pCont) pCont.style.setProperty('--val', val);
            if (audioBuffer && tCurr) {
                let current = val * audioBuffer.duration;
                let minutes = Math.floor(current / 60);
                let seconds = Math.floor(current % 60).toString().padStart(2, '0');
                tCurr.textContent = `${minutes}:${seconds}`;
            }
        }
    });
    document.addEventListener('change', e => {
        if (e.target.id === 'fx-progress-slider') {
            progressDrag = false;
            if (audioBuffer) {
                if (isPlaying) {
                    pauseAudio();
                    window.kittyFX.offsetTime = parseFloat(e.target.value) * audioBuffer.duration;
                    startPlayback();
                } else {
                    window.kittyFX.offsetTime = parseFloat(e.target.value) * audioBuffer.duration;
                }
            }
        }
    });
    window.syncFXPlayerUI();
    if (!audioBuffer) {
        loadAudio().catch(e => console.log(e));
    }
})();