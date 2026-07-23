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

const LINE_COUNT = 220; // 리본 두께를 이루는 곡선 수. 많을수록 덩어리가 매끈해진다
const STEP = 6; // x 샘플 간격(px). 작을수록 매끄럽지만 비싸다

function drawFrame(ctx: CanvasRenderingContext2D, w: number, h: number, time: number) {
  ctx.clearRect(0, 0, w, h);
  ctx.globalCompositeOperation = 'lighter';

  const baseY = h * 0.42;
  const t = time * 0.00018;

  for (let i = 0; i < LINE_COUNT; i += 1) {
    const p = i / (LINE_COUNT - 1); // 0..1 리본 두께 방향
    const centered = 1 - Math.abs(p - 0.5) * 2; // 가운데가 1, 가장자리가 0

    ctx.beginPath();
    for (let x = 0; x <= w + STEP; x += STEP) {
      const nx = x / w;

      // 리본이 좌우로 가면서 굵어졌다 얇아지도록 폭을 변조한다.
      // 최소값을 0으로 두지 않아야 끝부분이 실처럼 끊기지 않는다.
      const swell = 0.45 + 0.55 * Math.sin(nx * Math.PI * 0.9 + t * 0.6);
      const thickness = h * 0.3 * swell;

      const y =
        baseY +
        Math.sin(nx * 2.6 + t * 1.15 + p * 0.9) * h * 0.11 +
        Math.sin(nx * 5.7 - t * 0.8 + p * 1.6) * h * 0.035 +
        Math.sin(nx * 1.3 + t * 0.45) * h * 0.05 +
        (p - 0.5) * thickness;

      if (x === 0) ctx.moveTo(x, y);
      else ctx.lineTo(x, y);
    }

    // 가운데 곡선일수록 밝게 — 이게 덩어리의 입체감을 만든다.
    // 가장자리는 거의 0에 수렴시켜 경계선이 보이지 않게 한다.
    // 지수를 높이면 가운데에 밝은 심지가 서고 바깥은 빠르게 사라진다 —
    // 이게 없으면 리본이 아니라 뿌연 얼룩이 된다.
    const falloff = Math.pow(centered, 3.2);
    const alpha = 0.003 + falloff * 0.1;
    ctx.strokeStyle = `rgba(255, 255, 255, ${alpha})`;
    ctx.lineWidth = 1.4;
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
