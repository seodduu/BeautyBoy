// MediaPipe Face Landmarker 에셋 준비. predev/prebuild 에서 실행된다.
// 1) node_modules/@mediapipe/tasks-vision/wasm/* → public/models/mediapipe/wasm/
// 2) public/models/mediapipe/face_landmarker.task 가 없거나 크기가 정확히 3,758,596바이트가 아니면 공식 URL 에서 내려받는다(.part 에 쓴 뒤 rename).
// 멱등: 크기가 정상인 파일이 있으면 네트워크를 쓰지 않는다. .task 가 없는데 받지 못하면 종료 코드 1 (조용히 빠진 모델은 런타임에서만 터진다).
import { cpSync, existsSync, mkdirSync, renameSync, rmSync, statSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = dirname(dirname(fileURLToPath(import.meta.url)));           // frontend/
const wasmSrc = join(root, 'node_modules', '@mediapipe', 'tasks-vision', 'wasm');
const outDir = join(root, 'public', 'models', 'mediapipe');
const wasmDst = join(outDir, 'wasm');
const taskDst = join(outDir, 'face_landmarker.task');
const TASK_URL =
  'https://storage.googleapis.com/mediapipe-models/face_landmarker/face_landmarker/float16/1/face_landmarker.task';
// URL 의 버전 경로(/float16/1/)에 고정된 파일 크기. 이 크기가 아니면(0바이트·부분·잘못된 내용) 손상으로 본다(설계 6.4 재검토 N3).
const TASK_BYTES = 3_758_596;

if (!existsSync(wasmSrc)) {
  console.error('[mediapipe-assets] node_modules/@mediapipe/tasks-vision/wasm 이 없다. npm install 먼저.');
  process.exit(1);
}
mkdirSync(wasmDst, { recursive: true });
cpSync(wasmSrc, wasmDst, { recursive: true });

// 이미 있어도 크기가 정확히 맞지 않으면(0바이트·중단된 다운로드·다른 파일) 지우고 다시 받는다(설계 6.4 정정·재검토 N3).
if (existsSync(taskDst)) {
  const size = statSync(taskDst).size;
  if (size !== TASK_BYTES) {
    console.log(`[mediapipe-assets] 기존 .task 가 ${size} 바이트라 손상으로 보고 지운 뒤 다시 받는다`);
    rmSync(taskDst, { force: true });
  }
}

if (!existsSync(taskDst)) {
  const partDst = `${taskDst}.part`;
  let buf;
  try {
    const res = await fetch(TASK_URL);
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    buf = Buffer.from(await res.arrayBuffer());
  } catch (e) {
    console.error(`[mediapipe-assets] .task 다운로드 실패: ${e.message}. 네트워크를 확인하거나 파일을 ${taskDst} 에 직접 둔다.`);
    process.exit(1);
  }
  if (buf.length !== TASK_BYTES) {
    console.error(`[mediapipe-assets] .task 크기 이상 ${buf.length} 바이트(기대 ${TASK_BYTES})`);
    process.exit(1);
  }
  // .part 에 다 쓴 뒤 이름을 바꾼다. 쓰다 끊겨도 정식 경로에 부분 파일이 남지 않는다.
  writeFileSync(partDst, buf);
  renameSync(partDst, taskDst);
  console.log(`[mediapipe-assets] face_landmarker.task 받음 (${buf.length} 바이트)`);
}
