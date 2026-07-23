import { useEffect, useRef } from 'react';
import './WaveCanvas.css';

/**
 * 랜딩 히어로의 물결 리본.
 *
 * 외부 라이브러리(three/gsap)를 쓰지 않고 canvas 2D로만 그린다 — package.json은 공유 계약이라
 * 의존성을 늘리지 않는다. 얇은 곡선 수십 개를 미세하게 어긋난 위상으로 겹쳐 그려서
 * 반투명한 연기/실크 같은 덩어리를 만든다(선 하나하나는 거의 보이지 않는다).
 *
 * prefers-reduced-motion에서는 애니메이션을 멈추고 정지 프레임 1장만 그린다(DESIGN.md UX 계약).
 */

const LINE_COUNT = 260; // 가닥 수. 가닥 하나하나가 보여야 하므로 넉넉히 쓴다
const STEP = 8; // x 샘플 간격(px)

function drawFrame(ctx: CanvasRenderingContext2D, w: number, h: number, time: number) {
  ctx.clearRect(0, 0, w, h);
  ctx.globalCompositeOperation = 'lighter';

  const t = time * 0.00042; // 움직임 속도
  const baseY = h * 0.52;

  // 아치의 봉우리 위치·높이가 천천히 흔들린다 — 덩어리가 살아 움직이는 느낌의 대부분이 여기서 나온다
  const peakX = 0.44 + Math.sin(t * 0.35) * 0.07;
  const peakH = 0.3 + Math.sin(t * 0.5) * 0.06;

  for (let i = 0; i < LINE_COUNT; i += 1) {
    const p = i / (LINE_COUNT - 1); // 0..1 리본 두께 방향
    const centered = 1 - Math.abs(p - 0.5) * 2;

    ctx.beginPath();
    for (let x = -STEP; x <= w + STEP; x += STEP) {
      const nx = x / w;

      // 큰 아치(가우시안 봉우리) — 레퍼런스의 "산" 모양을 만드는 축
      const arch = Math.exp(-Math.pow((nx - peakX) / 0.27, 2));

      // 봉우리에서 가닥이 모이고 양 끝에서 부채처럼 펼쳐진다.
      // 이 대비가 접힌 천처럼 보이게 하는 핵심이다.
      const thickness = h * (0.05 + 0.44 * (1 - arch));

      // 가닥마다 위상을 어긋나게 줘서 서로 교차하며 결이 생긴다
      const strandPhase = p * 3.4;

      const y =
        baseY -
        arch * h * peakH +
        Math.sin(nx * 3.4 + t * 1.6 + strandPhase) * h * 0.05 +
        Math.sin(nx * 7.1 - t * 1.2 + strandPhase * 0.6) * h * 0.02 +
        Math.sin(nx * 1.6 + t * 0.9) * h * 0.035 +
        (p - 0.5) * thickness;

      if (x === -STEP) ctx.moveTo(x, y);
      else ctx.lineTo(x, y);
    }

    // 가닥이 개별로 보이도록 바닥 알파를 남긴다.
    // 가장자리를 0으로 죽이면(이전 버전) 덩어리는 매끈해지지만 결이 사라져 얼룩이 된다.
    const falloff = Math.pow(centered, 1.6);
    const alpha = 0.02 + falloff * 0.075;
    ctx.strokeStyle = `rgba(255, 255, 255, ${alpha})`;
    ctx.lineWidth = 1;
    ctx.stroke();
  }

  ctx.globalCompositeOperation = 'source-over';
}

export function WaveCanvas() {
  const ref = useRef<HTMLCanvasElement>(null);

  useEffect(() => {
    const canvas = ref.current;
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    let raf = 0;
    let w = 0;
    let h = 0;

    const resize = () => {
      const dpr = Math.min(window.devicePixelRatio || 1, 2);
      const rect = canvas.getBoundingClientRect();
      w = rect.width;
      h = rect.height;
      canvas.width = Math.round(w * dpr);
      canvas.height = Math.round(h * dpr);
      ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    };

    const reduced = window.matchMedia('(prefers-reduced-motion: reduce)');

    const start = () => {
      cancelAnimationFrame(raf);
      resize();
      if (reduced.matches) {
        // 정지 프레임 1장. 시간값을 고정해 늘 같은 모양이 나온다.
        drawFrame(ctx, w, h, 12000);
        return;
      }
      const loop = (now: number) => {
        drawFrame(ctx, w, h, now);
        raf = requestAnimationFrame(loop);
      };
      raf = requestAnimationFrame(loop);
    };

    start();
    window.addEventListener('resize', start);
    reduced.addEventListener('change', start);

    return () => {
      cancelAnimationFrame(raf);
      window.removeEventListener('resize', start);
      reduced.removeEventListener('change', start);
    };
  }, []);

  return <canvas ref={ref} className="bb-wave" aria-hidden="true" />;
}
