"""카탈로그 덤프. MySQL의 상품·태그·성분을 CSV 세 개로 내려 저장소에 넣는다.

사람이 한 번만 돌린다. 그 뒤로는 파이프라인의 어느 단계도 DB에 붙지 않는다
(구현 계획 §3.1). 덕분에 pytest가 살아 있는 MySQL 없이 돌고 T1 테스트가 T2와 격리된다.

    python tools/recsys/export_catalog.py --host 127.0.0.1 --port 13306 \\
        --user beautyboy --password <비밀번호> --database beautyboy \\
        --out tools/recsys/data/catalog

비밀번호는 인자로만 받는다. 코드·커밋에 넣지 않는다(CLAUDE.md 시크릿 규칙).

열 순서와 이름이 계약이다. build_table.py와 seed_generator.py가 이 이름을 읽는다.
정렬은 전부 첫 열 오름차순이다. 같은 DB면 같은 파일이 나와야 하므로 DB가 주는 순서를
믿지 않고 SQL에서 ORDER BY로 못 박는다.
"""

from __future__ import annotations

import argparse
import csv
import sys
from pathlib import Path

# (파일 이름, 헤더, SQL). 세 쌍이 이 스크립트의 전부다.
EXPORTS: list[tuple[str, list[str], str]] = [
    (
        "goods.csv",
        ["goods_id", "category_code", "brand_id", "sale_price", "status", "view_count"],
        """
        SELECT id, category_code, brand_id, sale_price, status, view_count
          FROM goods
         WHERE status <> 'HIDDEN'
         ORDER BY id
        """,
    ),
    (
        "goods_tag.csv",
        ["goods_id", "tag_slug", "sort_order"],
        """
        SELECT gt.goods_id, t.slug, t.sort_order
          FROM goods_tag gt
          JOIN tag t ON t.id = gt.tag_id
          JOIN goods g ON g.id = gt.goods_id AND g.status <> 'HIDDEN'
         ORDER BY gt.goods_id, t.sort_order
        """,
    ),
    (
        "goods_ingredient.csv",
        ["goods_id", "ingredient_id"],
        """
        SELECT gi.goods_id, gi.ingredient_id
          FROM goods_ingredient gi
          JOIN goods g ON g.id = gi.goods_id AND g.status <> 'HIDDEN'
         ORDER BY gi.goods_id, gi.ingredient_id
        """,
    ),
]


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="카탈로그 3파일을 CSV로 덤프한다.")
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=13306)
    parser.add_argument("--user", required=True)
    parser.add_argument("--password", required=True, help="인자로만 받는다. 파일에 적지 않는다.")
    parser.add_argument("--database", default="beautyboy")
    parser.add_argument("--out", required=True, type=Path, help="CSV 세 개를 쓸 디렉터리")
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv)

    # 파이프라인 본체와 테스트는 이 드라이버를 import하지 않는다(구현 계획 §4.0).
    # 그래서 함수 안에서 늦게 불러온다. PyMySQL이 없어도 --help는 뜬다.
    import pymysql

    args.out.mkdir(parents=True, exist_ok=True)

    connection = pymysql.connect(
        host=args.host,
        port=args.port,
        user=args.user,
        password=args.password,
        database=args.database,
        charset="utf8mb4",
    )
    try:
        with connection.cursor() as cursor:
            for name, header, sql in EXPORTS:
                cursor.execute(sql)
                rows = cursor.fetchall()
                path = args.out / name
                with path.open("w", newline="", encoding="utf-8") as fp:
                    writer = csv.writer(fp)
                    writer.writerow(header)
                    writer.writerows(rows)
                print(f"{path}: {len(rows)}행", file=sys.stderr)
    finally:
        connection.close()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
