from __future__ import annotations

import re
from datetime import datetime, timezone

from fastapi import APIRouter, Header, HTTPException
from typing import Optional

from pydantic import BaseModel, Field

from ..db import connect
from ..security import bearer_user, hash_password, issue_token, verify_password

router = APIRouter(prefix="/auth", tags=["auth"])

EMAIL = re.compile(r"^[^@\s]+@[^@\s]+\.[^@\s]+$")
PHONE = re.compile(r"^1\d{10}$")


class RegisterIn(BaseModel):
    account: str
    password: str = Field(min_length=6, max_length=64)
    name: str = ""


class LoginIn(BaseModel):
    account: str
    password: str


class ProfileIn(BaseModel):
    name: Optional[str] = None
    email: Optional[str] = None
    phone: Optional[str] = None
    password: Optional[str] = None


def public_user(row) -> dict:
    return {
        "id": row["id"],
        "email": row["email"] or "",
        "phone": row["phone"] or "",
        "name": row["name"] or "",
    }


def token_body(row) -> dict:
    return {"token": issue_token(row["id"]), "user": public_user(row)}


@router.post("/email/register")
def register_email(body: RegisterIn) -> dict:
    email = body.account.strip().lower()
    if not EMAIL.match(email):
        raise HTTPException(status_code=400, detail="邮箱格式不对")
    return _register(email=email, phone=None, password=body.password, name=body.name)


@router.post("/phone/register")
def register_phone(body: RegisterIn) -> dict:
    phone = body.account.strip()
    if not PHONE.match(phone):
        raise HTTPException(status_code=400, detail="手机号是11位，以1开头")
    return _register(email=None, phone=phone, password=body.password, name=body.name)


def _register(email, phone, password: str, name: str) -> dict:
    now = datetime.now(timezone.utc).isoformat()
    with connect() as conn:
        if email:
            exists = conn.execute("select id from users where email = ?", (email,)).fetchone()
        if phone:
            exists = conn.execute("select id from users where phone = ?", (phone,)).fetchone()
        if exists:
            raise HTTPException(status_code=409, detail="这个账号已经注册过")
        cur = conn.execute(
            "insert into users(email, phone, name, password_hash, created_at) values (?, ?, ?, ?, ?)",
            (email, phone, name.strip()[:12], hash_password(password), now),
        )
        row = conn.execute("select * from users where id = ?", (cur.lastrowid,)).fetchone()
    return token_body(row)


@router.post("/login")
def login(body: LoginIn) -> dict:
    account = body.account.strip()
    key = account.lower() if "@" in account else account
    column = "email" if "@" in account else "phone"
    with connect() as conn:
        row = conn.execute(f"select * from users where {column} = ?", (key,)).fetchone()
    if row is None or not verify_password(body.password, row["password_hash"]):
        raise HTTPException(status_code=401, detail="账号或密码不对")
    return token_body(row)


@router.get("/me")
def me(authorization: Optional[str] = Header(default=None)) -> dict:
    uid = bearer_user(authorization)
    with connect() as conn:
        row = conn.execute("select * from users where id = ?", (uid,)).fetchone()
    if row is None:
        raise HTTPException(status_code=401, detail="用户不存在")
    return public_user(row)


@router.patch("/me")
def update_me(body: ProfileIn, authorization: Optional[str] = Header(default=None)) -> dict:
    uid = bearer_user(authorization)
    email = body.email.strip().lower() if body.email is not None else None
    phone = body.phone.strip() if body.phone is not None else None
    if email is not None and email != "" and not EMAIL.match(email):
        raise HTTPException(status_code=400, detail="邮箱格式不对")
    if phone is not None and phone != "" and not PHONE.match(phone):
        raise HTTPException(status_code=400, detail="手机号是11位，以1开头")
    if body.password is not None and len(body.password) < 6:
        raise HTTPException(status_code=400, detail="密码至少6位")
    with connect() as conn:
        row = conn.execute("select * from users where id = ?", (uid,)).fetchone()
        if row is None:
            raise HTTPException(status_code=401, detail="用户不存在")
        name = row["name"] if body.name is None else body.name.strip()[:12]
        next_email = row["email"] if email is None else (email or None)
        next_phone = row["phone"] if phone is None else (phone or None)
        if next_email:
            clash = conn.execute(
                "select id from users where email = ? and id != ?",
                (next_email, uid),
            ).fetchone()
            if clash:
                raise HTTPException(status_code=409, detail="这个邮箱已被占用")
        if next_phone:
            clash = conn.execute(
                "select id from users where phone = ? and id != ?",
                (next_phone, uid),
            ).fetchone()
            if clash:
                raise HTTPException(status_code=409, detail="这个手机号已被占用")
        password_hash = row["password_hash"] if not body.password else hash_password(body.password)
        conn.execute(
            "update users set name = ?, email = ?, phone = ?, password_hash = ? where id = ?",
            (name, next_email, next_phone, password_hash, uid),
        )
        row = conn.execute("select * from users where id = ?", (uid,)).fetchone()
    return public_user(row)
