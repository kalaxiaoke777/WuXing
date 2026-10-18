from __future__ import annotations

import base64
import hashlib
import hmac
import json
import os
import time

SECRET = os.environ.get("WUXING_SECRET", "wuxing-dev-secret").encode()


def hash_password(password: str, salt: bytes = b"") -> str:
    salt = salt or os.urandom(16)
    digest = hashlib.pbkdf2_hmac("sha256", password.encode(), salt, 120_000)
    return base64.b64encode(salt).decode() + "$" + base64.b64encode(digest).decode()


def verify_password(password: str, stored: str) -> bool:
    salt_b64, digest_b64 = stored.split("$", 1)
    salt = base64.b64decode(salt_b64)
    expect = base64.b64decode(digest_b64)
    digest = hashlib.pbkdf2_hmac("sha256", password.encode(), salt, 120_000)
    return hmac.compare_digest(digest, expect)


def issue_token(user_id: int) -> str:
    payload = base64.urlsafe_b64encode(
        json.dumps({"sub": user_id, "exp": int(time.time()) + 60 * 60 * 24 * 30}).encode()
    ).decode().rstrip("=")
    sig = hmac.new(SECRET, payload.encode(), hashlib.sha256).hexdigest()
    return f"{payload}.{sig}"


def bearer_user(authorization: str) -> int:
    from fastapi import HTTPException

    if not authorization or not authorization.startswith("Bearer "):
        raise HTTPException(status_code=401, detail="未登录")
    found = read_token(authorization.removeprefix("Bearer ").strip())
    if found is None:
        raise HTTPException(status_code=401, detail="登录已失效")
    return found


def read_token(token: str):
    try:
        payload, sig = token.split(".", 1)
    except ValueError:
        return None
    expect = hmac.new(SECRET, payload.encode(), hashlib.sha256).hexdigest()
    if not hmac.compare_digest(expect, sig):
        return None
    padded = payload + "=" * (-len(payload) % 4)
    data = json.loads(base64.urlsafe_b64decode(padded))
    if int(data["exp"]) < time.time():
        return None
    return int(data["sub"])
