from typing import Optional

from fastapi import APIRouter, Header
from pydantic import BaseModel

from ..minimax import MiniMaxError, configured, model_name, understand_meal, write_summary
from ..security import bearer_user

router = APIRouter(prefix="/ai", tags=["ai"])


class MealIn(BaseModel):
    note: str = ""
    image_base64: str = ""
    media_type: str = "image/jpeg"
    image_url: str = ""


class PlanIn(BaseModel):
    season: str = "keep"
    basal: int = 0
    intake: int = 0
    burn: int = 0


@router.get("/status")
def status() -> dict:
    ready = configured()
    return {
        "ready": ready,
        "model": model_name() if ready else "",
        "message": "可以看餐食照片" if ready else "还没有配置 MINIMAX_API_KEY",
    }


@router.post("/meal")
def meal(body: MealIn, authorization: Optional[str] = Header(default=None)) -> dict:
    bearer_user(authorization or "")
    try:
        return understand_meal(
            image_base64=body.image_base64,
            media_type=body.media_type,
            image_url=body.image_url,
            note=body.note,
        )
    except MiniMaxError as error:
        return {
            "ready": False,
            "title": "",
            "kcal": 0,
            "foods": [],
            "message": str(error),
        }


class SummaryIn(BaseModel):
    span: str = "day"
    brief: str = ""


@router.post("/summary")
def summary(body: SummaryIn, authorization: Optional[str] = Header(default=None)) -> dict:
    bearer_user(authorization or "")
    span = body.span if body.span in ("day", "week", "month") else "day"
    try:
        return write_summary(span, body.brief)
    except MiniMaxError as error:
        return {"ready": False, "message": str(error), "span": span}


@router.post("/plan")
def plan(body: PlanIn, authorization: Optional[str] = Header(default=None)) -> dict:
    bearer_user(authorization or "")
    return {
        "ready": False,
        "season": body.season,
        "suggestion": "",
        "message": "配餐建议还没接上。看图已经接上。",
    }
