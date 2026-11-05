// MediaPipe Face Landmarker 에셋 준비. predev/prebuild 에서 실행된다.
// 1) node_modules/@mediapipe/tasks-vision/wasm/* → public/models/mediapipe/wasm/
// 2) public/models/mediapipe/face_landmarker.task 가 없으면 공식 URL 에서 내려받는다.
// 멱등: 이미 있으면 네트워크를 쓰지 않는다. .task 가 없는데 받지 못하면 종료 코드 1 (조용히 빠진 모델은 런타임에서만 터진다).
import { cpSync, existsSync, mkdirSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = dirname(dirname(fileURLToPath(import.meta.url)));           // frontend/
const wasmSrc = join(root, 'node_modules', '@mediapipe', 'tasks-vision', 'wasm');
const outDir = join(root, 'public', 'models', 'mediapipe');
const wasmDst = join(outDir, 'wasm');
const taskDst = join(outDir, 'face_landmarker.task');
const TASK_URL =
  'https://storage.googleapis.com/mediapipe-models/face_landmarker/face_landmarker/float16/1/face_landmarker.task';
const TASK_MIN_BYTES = 3_000_000;   // 실제 3,758,596 바이트. 이보다 작으면 손상으로 본다

if (!existsSync(wasmSrc)) {
  console.error('[mediapipe-assets] node_modules/@mediapipe/tasks-vision/wasm 이 없다. npm install 먼저.');
  process.exit(1);
}
mkdirSync(wasmDst, { recursive: true });
cpSync(wasmSrc, wasmDst, { recursive: true });

if (!existsSync(taskDst)) {
  let buf;
  try {
    const res = await fetch(TASK_URL);
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    buf = Buffer.from(await res.arrayBuffer());
  } catch (e) {
    console.error(`[mediapipe-assets] .task 다운로드 실패: ${e.message}. 네트워크를 확인하거나 파일을 ${taskDst} 에 직접 둔다.`);
    process.exit(1);
  }
  if (buf.length < TASK_MIN_BYTES) {
    console.error(`[mediapipe-assets] .task 크기 이상 ${buf.length} 바이트`);
    process.exit(1);
  }
  writeFileSync(taskDst, buf);
  console.log(`[mediapipe-assets] face_landmarker.task 받음 (${buf.length} 바이트)`);
}
