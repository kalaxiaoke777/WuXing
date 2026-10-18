from __future__ import annotations

from typing import Optional

from fastapi import APIRouter, Header, Query

from ..db import connect
from ..security import read_token

router = APIRouter(tags=["rank"])


@router.get("/rank")
def rank(
    span: str = Query(default="week"),
    authorization: Optional[str] = Header(default=None),
) -> dict:
    column = "week_score" if span != "month" else "month_score"
    mine = None
    if authorization and authorization.startswith("Bearer "):
        mine = read_token(authorization.removeprefix("Bearer ").strip())
    with connect() as conn:
        rows = conn.execute(
            f"""
            select users.id, users.name, users.email, users.phone, rituals.{column} as score
            from rituals
            join users on users.id = rituals.user_id
            order by rituals.{column} desc, users.id asc
            limit 50
            """
        ).fetchall()
    board = []
    for index, row in enumerate(rows, start=1):
        name = row["name"] or _mask(row["email"], row["phone"])
        board.append(
            {
                "place": index,
                "name": name,
                "score": row["score"],
                "mine": row["id"] == mine,
            }
        )
    return {"span": "month" if span == "month" else "week", "board": board}


def _mask(email, phone) -> str:
    if email:
        head = email.split("@", 1)[0]
        return head[:2] + "…"
    if phone and len(phone) >= 7:
        return phone[:3] + "****" + phone[-4:]
    return "未署名"
