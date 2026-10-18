from fastapi import FastAPI

from . import db
from .minimax import load_local_env
from .routes import ai, auth, rank, sync

load_local_env()

app = FastAPI(title="五行", version="0.1.0")
app.include_router(auth.router)
app.include_router(sync.router)
app.include_router(rank.router)
app.include_router(ai.router)


@app.on_event("startup")
def startup() -> None:
    db.init()


@app.get("/health")
def health() -> dict:
    return {"ok": True}
