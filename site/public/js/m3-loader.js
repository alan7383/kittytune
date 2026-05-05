(function() {
    const N = 192;
    let MS_PER_SHAPE = 650;
    const CONST_ROT = 50, EXTRA_ROT = 90;

    function springValue(t) {
        const o0 = 14.142135623730951, z = 0.6, od = 11.313708498984761, ratio = z * o0 / od;
        const rt = t * 0.65, dec = Math.exp(-z * o0 * rt);
        return 1 - (dec * (Math.cos(od * rt) + ratio * Math.sin(od * rt)));
    }

    const tempSvg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
    tempSvg.style.cssText = 'position:fixed;visibility:hidden;width:0;height:0;top:-9999px';
    document.body.appendChild(tempSvg);

    function samplePath(d, n) {
        const pe = document.createElementNS('http://www.w3.org/2000/svg', 'path');
        pe.setAttribute('d', d); tempSvg.appendChild(pe);
        const L = pe.getTotalLength(), pts = [];
        for (let i = 0; i < n; i++) { const p = pe.getPointAtLength(L * i / n); pts.push({ x: p.x, y: p.y }); }
        tempSvg.removeChild(pe); return pts;
    }

    function radialNorm(poly, cx = 0, cy = 0) {
        let mx = 0;
        for (const p of poly) mx = Math.max(mx, Math.hypot(p.x - cx, p.y - cy));
        const s = mx > 0 ? 1 / mx : 1;
        return poly.map(p => ({ x: (p.x - cx) * s, y: (p.y - cy) * s }));
    }

    function buildRoundedSVG(verts) {
        const M = verts.length; if (M < 3) return '';
        const dir = [], el = [];
        for (let i = 0; i < M; i++) {
            const a = verts[i], b = verts[(i + 1) % M], dx = b.x - a.x, dy = b.y - a.y, L = Math.hypot(dx, dy);
            dir.push(L > 0 ? { x: dx / L, y: dy / L } : { x: 1, y: 0 }); el.push(L);
        }
        const beta = [], des = [];
        for (let i = 0; i < M; i++) {
            const vi = dir[(i + M - 1) % M], vo = dir[i];
            const cb = Math.max(-1, Math.min(1, -(vi.x * vo.x + vi.y * vo.y)));
            const b = Math.acos(cb); beta.push(b);
            des.push((b > 1e-9 && verts[i].r > 0) ? verts[i].r / Math.tan(b * 0.5) : 0);
        }
        const es = new Array(M).fill(1);
        for (let i = 0; i < M; i++) { const s = des[i] + des[(i + 1) % M]; if (s > el[i] && s > 0) es[i] = el[i] / s; }
        const cut = [];
        for (let i = 0; i < M; i++) cut.push(des[i] * Math.min(es[(i + M - 1) % M], es[i]));
        let d = '';
        for (let i = 0; i < M; i++) {
            const vi = dir[(i + M - 1) % M], vo = dir[i], p1 = verts[i], c = cut[i];
            const sx = p1.x - vi.x * c, sy = p1.y - vi.y * c, ex = p1.x + vo.x * c, ey = p1.y + vo.y * c;
            d += i === 0 ? `M${sx} ${sy}` : `L${sx} ${sy}`;
            if (c > 0 && beta[i] < Math.PI - 1e-6) {
                const a2 = Math.PI - beta[i], t4 = Math.tan(a2 * 0.25), ctrl = (2 / 3) * (1 - t4 * t4) * c;
                d += ` C${sx + vi.x * ctrl} ${sy + vi.y * ctrl} ${ex - vo.x * ctrl} ${ey - vo.y * ctrl} ${ex} ${ey}`;
            }
        }
        return d + 'Z';
    }

    function repeatAround(tmpl, repeat, cx, cy, mirror) {
        const pol = tmpl.map(v => ({ a: Math.atan2(v.y - cy, v.x - cx), d: Math.hypot(v.x - cx, v.y - cy), r: v.r }));
        let span = 2 * Math.PI / repeat;
        const out = [];
        if (mirror) {
            span /= 2;
            const mc = repeat * 2;
            for (let i = 0; i < mc; i++) {
                const rev = (i & 1) !== 0;
                for (let j = 0; j < pol.length; j++) {
                    const idx = rev ? pol.length - 1 - j : j;
                    if (idx === 0 && rev) continue;
                    const tp = pol[idx];
                    const angle = rev ? span * i + span - tp.a + 2 * pol[0].a : span * i + tp.a;
                    out.push({ a: angle, d: tp.d, r: tp.r });
                }
            }
        } else {
            for (let i = 0; i < repeat; i++) for (const tp of pol) out.push({ a: span * i + tp.a, d: tp.d, r: tp.r });
        }
        return out.map(v => ({ x: v.d * Math.cos(v.a) + cx, y: v.d * Math.sin(v.a) + cy, r: v.r }));
    }

    function customPoly(tmpl, repeat, cx, cy, mirror) {
        const verts = repeatAround(tmpl, repeat, cx, cy, mirror);
        return radialNorm(samplePath(buildRoundedSVG(verts), N), cx, cy);
    }

    function starPoly(spokes, outerR, innerR, crn, rotRad) {
        const verts = []; const total = spokes * 2;
        for (let i = 0; i < total; i++) {
            const r = (i % 2 === 0) ? outerR : innerR, a = rotRad + Math.PI * i / spokes;
            verts.push({ x: r * Math.cos(a), y: r * Math.sin(a), r: crn });
        }
        return radialNorm(samplePath(buildRoundedSVG(verts), N));
    }

    function regPoly(vertices, radius, crn, rotRad) {
        const verts = [];
        for (let i = 0; i < vertices; i++) {
            const a = rotRad + 2 * Math.PI * i / vertices;
            verts.push({ x: radius * Math.cos(a), y: radius * Math.sin(a), r: crn });
        }
        return radialNorm(samplePath(buildRoundedSVG(verts), N));
    }

    function ovalShape() {
        const k = 0.5522847498, rx = 1.0, ry = 0.64;
        const d = `M${rx},0 C${rx},${ry * k} ${rx * k},${ry} 0,${ry} C${-rx * k},${ry} ${-rx},${ry * k} ${-rx},0 C${-rx},${-ry * k} ${-rx * k},${-ry} 0,${-ry} C${rx * k},${-ry} ${rx},${-ry * k} ${rx},0Z`;
        const pts = samplePath(d, N);
        const ca = Math.cos(-Math.PI / 4), sa = Math.sin(-Math.PI / 4);
        return radialNorm(pts.map(p => ({ x: p.x * ca - p.y * sa, y: p.x * sa + p.y * ca })));
    }

    function alignTo(ref, tgt) {
        const n = Math.min(ref.length, tgt.length); if (!n) return tgt;
        let bk = 0, bc = Infinity;
        outer: for (let k = 0; k < n; k++) {
            let cost = 0;
            for (let i = 0; i < n; i++) {
                const dx = ref[i].x - tgt[(i + k) % n].x, dy = ref[i].y - tgt[(i + k) % n].y;
                cost += dx * dx + dy * dy; if (cost >= bc) continue outer;
            }
            bc = cost; bk = k;
        }
        if (!bk) return tgt;
        const out = [];
        for (let i = 0; i < n; i++) out.push(tgt[(i + bk) % n]);
        return out;
    }

    const loaderScreen = document.getElementById('loading-screen');
    const svgEl = document.querySelector('.loader-svg-container svg');
    const polyEl = svgEl ? svgEl.querySelector('polygon') : null;

    let shapes = [];
    let aligned = [];
    let animationId = null;
    let t0 = null;

    setTimeout(() => {
        if (!polyEl) return;
        
        shapes = [
            customPoly([{ x: .193, y: .277, r: .053 }, { x: .176, y: .055, r: .053 }], 10, .5, .5, false),
            starPoly(9, 1, .8, .5, -Math.PI / 2),
            regPoly(5, 1, .172, -Math.PI / 2),
            customPoly([{ x: .961, y: .039, r: .426 }, { x: 1.001, y: .428, r: 0 }, { x: 1, y: .609, r: 1 }], 2, .5, .5, true),
            starPoly(8, 1, .8, .15, 0),
            customPoly([{ x: 1.237, y: 1.236, r: .258 }, { x: .5, y: .918, r: .233 }], 4, .5, .5, false),
            ovalShape()
        ];
        aligned = shapes.map((s, i) => alignTo(s, shapes[(i + 1) % shapes.length]));
        
        document.body.removeChild(tempSvg);

        window.startM3Loader();
    }, 0);

    function render(ts) {
        const isHidden = !loaderScreen || loaderScreen.style.display === "none";
        if (isHidden) {
            animationId = null;
            return;
        }

        if (!t0) t0 = ts;
        const progress = (ts - t0) / MS_PER_SHAPE;
        const base = Math.floor(progress), rawF = progress - base;
        const sf = springValue(rawF);
        const idx = base % shapes.length;
        const s1 = shapes[idx], s2 = aligned[idx];
        const inv = 1 - sf;

        let pts = '';
        for (let i = 0; i < N; i++) {
            pts += `${(s1[i].x * inv + s2[i].x * sf).toFixed(4)},${(s1[i].y * inv + s2[i].y * sf).toFixed(4)} `;
        }
        polyEl.setAttribute('points', pts.trimEnd());

        const rot = (CONST_ROT * (base + rawF) + EXTRA_ROT * sf) % 360;
        svgEl.style.transform = `rotate(${rot}deg)`;

        animationId = requestAnimationFrame(render);
    }

    window.startM3Loader = function() {
        if (!animationId && shapes.length > 0 && polyEl) {
            t0 = null; 
            animationId = requestAnimationFrame(render);
        }
    };

})();
