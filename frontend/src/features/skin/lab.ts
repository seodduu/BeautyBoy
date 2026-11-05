import { DARK_OUTLIER_K, DARK_OUTLIER_MIN_GAP_L, type RoiStats } from './types';

function linearize(c8: number): number {
  const c = c8 / 255;
  return c <= 0.04045 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4;
}
function labF(t: number): number {
  return t > 216 / 24389 ? Math.cbrt(t) : ((24389 / 27) * t + 16) / 116;
}
/** 반환 [L*, a*, b*]. 기준값: 흰색 [100, 0, 0], 검정 [0, 0, 0], 순빨강 [53.24, 80.09, 67.20], 허용 오차 0.05(a·b 는 흰색에서 0.02). */
export function srgbToLab(r: number, g: number, b: number): [number, number, number] {
  const rl = linearize(r), gl = linearize(g), bl = linearize(b);
  const X = (0.4124 * rl + 0.3576 * gl + 0.1805 * bl) / 0.95047;
  const Y =  0.2126 * rl + 0.7152 * gl + 0.0722 * bl;
  const Z = (0.0193 * rl + 0.1192 * gl + 0.9505 * bl) / 1.08883;
  return [116 * labF(Y) - 16, 500 * (labF(X) - labF(Y)), 200 * (labF(Y) - labF(Z))];
}

function median(xs: number[]): number {
  const s = [...xs].sort((a, b) => a - b);
  const mid = s.length >> 1;
  return s.length % 2 ? s[mid] : (s[mid - 1] + s[mid]) / 2;
}

/**
 * RGBA 픽셀 배열(getImageData().data)의 Lab 절사 평균. 알파는 무시한다. 2패스.
 *
 * L* 의 중앙값과 MAD(중앙절대편차)로 컷오프를 잡고, 그 미만인 어두운 픽셀을 평균에서 뺀다. 앞머리·안경테 대책.
 *   cutoff = median(L*) - max(DARK_OUTLIER_MIN_GAP_L, DARK_OUTLIER_K × 1.4826 × MAD)
 * 평균·σ 를 쓰면 머리카락이 14% 만 넘어도 평균이 내려가고 σ 가 커져 컷오프가 머리카락 아래로 떨어지고 절사가 통째로 꺼진다.
 * 중앙값은 오염 50% 미만이면 피부 쪽에 머물러 절사 비율이 실제 가림 비율을 따라간다.
 * 절대 하한 8 은 균질한 피부의 작은 음영(L* 차 3 안팎)이 잘리지 않게 한다.
 */
export function roiStats(rgba: Uint8ClampedArray): RoiStats {
  const n = rgba.length / 4;
  const labs: [number, number, number][] = new Array(n);
  let L = 0, L2 = 0;
  for (let i = 0; i < n; i++) {
    const lab = srgbToLab(rgba[4 * i], rgba[4 * i + 1], rgba[4 * i + 2]);
    labs[i] = lab; L += lab[0]; L2 += lab[0] * lab[0];
  }
  const rawMeanL = L / n;
  const stdL = Math.sqrt(Math.max(0, L2 / n - rawMeanL * rawMeanL));
  const Ls = labs.map((l) => l[0]);
  const medL = median(Ls);
  const mad = median(Ls.map((l) => Math.abs(l - medL)));
  const cutoff = medL - Math.max(DARK_OUTLIER_MIN_GAP_L, DARK_OUTLIER_K * 1.4826 * mad);
  let kept = 0, kL = 0, kL2 = 0, kA = 0, kB = 0;
  for (const [l, a, b] of labs) {
    if (l < cutoff) continue;
    kept++; kL += l; kL2 += l * l; kA += a; kB += b;
  }
  const keptMeanL = kL / kept;
  const keptStdL = Math.sqrt(Math.max(0, kL2 / kept - keptMeanL * keptMeanL));
  return { meanL: keptMeanL, meanA: kA / kept, meanB: kB / kept, stdL, keptStdL, excludedRatio: 1 - kept / n, pixels: n };
}
