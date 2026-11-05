#!/bin/sh
# 피부분석 E2E 용 가짜 카메라 영상. 출처: MediaPipe 테스트 자산 portrait.jpg (README 09 절 출처 표기 참조)
set -eu
dir="$(cd "$(dirname "$0")" && pwd)"
cd "$dir"
[ -f portrait.jpg ] || curl -sSLo portrait.jpg https://storage.googleapis.com/mediapipe-assets/portrait.jpg
ffmpeg -y -loglevel error -loop 1 -i portrait.jpg -t 1 -vf "crop=420:420:200:0,scale=720:720" -pix_fmt yuv420p portrait.y4m
ffmpeg -y -loglevel error -f lavfi -i color=black:s=720x720 -t 1 -pix_fmt yuv420p black.y4m
echo "made: $dir/portrait.y4m $dir/black.y4m"
