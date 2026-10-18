from __future__ import annotations

from datetime import datetime, timezone

from typing import Optional

from fastapi import APIRouter, Header
from pydantic import BaseModel, Field

from ..db import connect
from ..security import bearer_user

router = APIRouter(tags=["sync"])


class SyncIn(BaseModel):
    ritual: str = Field(min_length=2)
    week_score: int = Field(ge=0, le=100)
    month_score: int = Field(ge=0, le=100)


@router.put("/sync")
def push_sync(body: SyncIn, authorization: Optional[str] = Header(default=None)) -> dict:
    uid = bearer_user(authorization)
    now = datetime.now(timezone.utc).isoformat()
    with connect() as conn:
        conn.execute(
            """
            insert into rituals(user_id, body, week_score, month_score, updated_at)
            values (?, ?, ?, ?, ?)
            on conflict(user_id) do update set
                body = excluded.body,
                week_score = excluded.week_score,
                month_score = excluded.month_score,
                updated_at = excluded.updated_at
            """,
            (uid, body.ritual, body.week_score, body.month_score, now),
        )
    return {"saved": True, "updated_at": now}


@router.get("/sync")
def pull_sync(authorization: Optional[str] = Header(default=None)) -> dict:
    uid = bearer_user(authorization)
    with connect() as conn:
        row = conn.execute("select body, updated_at from rituals where user_id = ?", (uid,)).fetchone()
    if row is None:
        return {"ritual": None, "updated_at": ""}
    return {"ritual": row["body"], "updated_at": row["updated_at"]}
