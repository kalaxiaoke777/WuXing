import json
import os
import re
import urllib.error
import urllib.request
from pathlib import Path

BASE_URL = "https://api.minimax.cn/anthropic/v1/messages"
MODELS = ("MiniMax-M3", "MiniMax-M3.1-Flash-Preview")
ALLOWED_TYPES = {"image/jpeg", "image/png", "image/webp", "image/gif"}

MEAL_PROMPT = (
    "你在看一餐饭的照片。只输出一个 JSON 对象，不要其它文字。"
    "字段：title 六个字以内的菜名；foods 看得见的食物，最多六样；"
    "kcal 这一餐大约多少千卡，整数；"
    "produce、variety、fresh、balance 都是 0 到 100 的整数，"
    "分别表示蔬果占比、种类、看着新鲜、荤素主食是否均衡；"
    "commentary 一句不超过二十二字的中文，说这一餐怎么样。"
    "看不清就少猜。"
)


class MiniMaxError(Exception):
    def __init__(self, status, message):
        super().__init__(message)
        self.status = status


def load_local_env():
    path = Path(__file__).resolve().parents[1] / ".env"
    if not path.is_file():
        return
    for line in path.read_text(encoding="utf-8").splitlines():
        text = line.strip()
        if not text or text.startswith("#") or "=" not in text:
            continue
        key, value = text.split("=", 1)
        key = key.strip()
        value = value.strip().strip('"').strip("'")
        if key and value:
            os.environ[key] = value


def api_key():
    load_local_env()
    return os.environ.get("MINIMAX_API_KEY", "").strip()


def configured():
    return bool(api_key())


def model_name():
    chosen = os.environ.get("MINIMAX_MODEL", "").strip()
    return chosen or MODELS[0]


def understand_meal(image_base64="", media_type="image/jpeg", image_url="", note=""):
    key = api_key()
    if not key:
        raise MiniMaxError(0, "还没有 MiniMax 密钥。在 server/.env 写入 MINIMAX_API_KEY 后重启。")
    source = _source(image_base64, media_type, image_url)
    prompt = MEAL_PROMPT
    extra = (note or "").strip()
    if extra:
        prompt += "补充：" + extra[:80]
    text, used = _complete(key, source, prompt)
    parsed = _read_meal(text)
    parsed["model"] = used
    parsed["ready"] = True
    return parsed


def write_summary(span, brief):
    label = {"week": "本周", "month": "本月"}.get(span, "今日")
    prompt = (
        "你在给五行养生写%s总结。只输出一个 JSON 对象，不要其它文字。"
        "字段：title 六个字以内；verdict 不超过二十八个字；"
        "keep 该留住的一件，十四个字以内；leave 该放下的一件，十四个字以内。"
        "不要说教，不要列清单。材料如下：\n%s"
    ) % (label, (brief or "")[:1800])
    text, used = _complete_text(api_key(), prompt)
    data = _read_summary(text)
    data["model"] = used
    data["ready"] = True
    data["span"] = label
    return data


def _complete_text(key, prompt):
    if not key:
        raise MiniMaxError(0, "还没有 MiniMax 密钥。在 server/.env 写入 MINIMAX_API_KEY 后重启。")
    last = MiniMaxError(502, "模型没有回应")
    models = _models()
    for index, model in enumerate(models):
        try:
            return _call_text(key, model, prompt), model
        except MiniMaxError as error:
            last = error
            if error.status == 401 or index == len(models) - 1 or not _can_retry(error):
                raise
    raise last


def _source(image_base64, media_type, image_url):
    url = (image_url or "").strip()
    if url:
        if not url.startswith("https://"):
            raise MiniMaxError(400, "图片地址需要是 https")
        return {"type": "url", "url": url, "detail": "default"}
    data = (image_base64 or "").strip()
    if data.startswith("data:") and "," in data:
        header, data = data.split(",", 1)
        marker = header[5:].split(";")[0].strip()
        if marker:
            media_type = marker
    data = re.sub(r"\s+", "", data)
    if not data:
        raise MiniMaxError(400, "需要一张餐食照片")
    if len(data) * 3 // 4 > 8 * 1024 * 1024:
        raise MiniMaxError(413, "图片太大")
    kind = media_type if media_type in ALLOWED_TYPES else "image/jpeg"
    return {"type": "base64", "media_type": kind, "data": data, "detail": "default"}


def _models():
    chosen = os.environ.get("MINIMAX_MODEL", "").strip()
    if chosen:
        return (chosen,)
    return MODELS


def _complete(key, source, prompt):
    last = MiniMaxError(502, "模型没有回应")
    models = _models()
    for index, model in enumerate(models):
        try:
            return _call(key, model, source, prompt), model
        except MiniMaxError as error:
            last = error
            if error.status == 401 or index == len(models) - 1 or not _can_retry(error):
                raise
    raise last


def _can_retry(error):
    if error.status == 402:
        return True
    text = str(error)
    needles = ("model", "模型", "不支持", "无权限", "permission", "not found", "not_found")
    return error.status in (400, 403, 404) and any(item in text.lower() for item in needles)


def _call_text(key, model, prompt):
    body = {
        "model": model,
        "max_tokens": 800,
        "messages": [{"role": "user", "content": prompt}],
    }
    if "M3.1" in model:
        body["thinking"] = {"type": "adaptive"}
        body["output_config"] = {"effort": "low"}
    else:
        body["thinking"] = {"type": "disabled"}
    return _post(key, body)


def _call(key, model, source, prompt):
    body = {
        "model": model,
        "max_tokens": 2048 if "M3.1" in model else 800,
        "messages": [
            {
                "role": "user",
                "content": [
                    {"type": "image", "source": source},
                    {"type": "text", "text": prompt},
                ],
            }
        ],
    }
    if "M3.1" in model:
        body["thinking"] = {"type": "adaptive"}
        body["output_config"] = {"effort": "low"}
    else:
        body["thinking"] = {"type": "disabled"}
    return _post(key, body)


def _post(key, body):
    raw = json.dumps(body).encode("utf-8")
    request = urllib.request.Request(
        os.environ.get("MINIMAX_BASE_URL", BASE_URL),
        data=raw,
        method="POST",
        headers={
            "Authorization": "Bearer " + key,
            "Content-Type": "application/json",
        },
    )
    try:
        with urllib.request.urlopen(request, timeout=90) as response:
            payload = json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as error:
        detail = error.read().decode("utf-8", errors="replace")
        raise MiniMaxError(error.code, _error_text(detail, error.code))
    except urllib.error.URLError:
        raise MiniMaxError(502, "连不上 MiniMax")
    text = _text_of(payload)
    if not text:
        raise MiniMaxError(502, "模型没有返回文字")
    return text


def _error_text(detail, status):
    try:
        payload = json.loads(detail)
    except json.JSONDecodeError:
        payload = {}
    error = payload.get("error") if isinstance(payload, dict) else None
    message = ""
    if isinstance(error, dict):
        message = str(error.get("message") or "")
    if status == 402 or "1008" in message or "insufficient balance" in message.lower():
        return "这把是按量接口密钥，没有余额。套餐额度要用订阅管理里的订阅 Key。"
    if not message:
        message = "MiniMax 拒绝了这次请求（%s）" % status
    return message[:240]


def _text_of(payload):
    content = payload.get("content") if isinstance(payload, dict) else None
    if isinstance(content, str):
        return content.strip()
    if not isinstance(content, list):
        return ""
    parts = []
    for block in content:
        if isinstance(block, dict) and block.get("type") == "text":
            parts.append(str(block.get("text") or ""))
    return "\n".join(part for part in parts if part).strip()


def _read_meal(text):
    try:
        data = _extract_json(text)
    except (json.JSONDecodeError, ValueError):
        return {
            "title": "这一餐",
            "kcal": 0,
            "foods": [],
            "commentary": text.replace("\n", " ")[:40],
            "produce": 0,
            "variety": 0,
            "fresh": 0,
            "balance": 0,
            "score": 0,
        }
    produce = _score(data.get("produce"))
    variety = _score(data.get("variety"))
    fresh = _score(data.get("fresh"))
    balance = _score(data.get("balance"))
    score = int(round(produce * 0.34 + variety * 0.18 + fresh * 0.28 + balance * 0.20))
    foods = data.get("foods") if isinstance(data.get("foods"), list) else []
    names = []
    for item in foods:
        name = str(item).strip()
        if name and name not in names:
            names.append(name[:12])
        if len(names) >= 6:
            break
    return {
        "title": str(data.get("title") or "这一餐").strip()[:12] or "这一餐",
        "kcal": _kcal(data.get("kcal")),
        "foods": names,
        "commentary": str(data.get("commentary") or "").strip()[:40],
        "produce": produce,
        "variety": variety,
        "fresh": fresh,
        "balance": balance,
        "score": max(0, min(100, score)),
    }


def _extract_json(text):
    cleaned = text.strip()
    if cleaned.startswith("```"):
        cleaned = re.sub(r"^```(?:json)?", "", cleaned).strip()
        cleaned = re.sub(r"```$", "", cleaned).strip()
    start = cleaned.find("{")
    end = cleaned.rfind("}")
    if start < 0 or end <= start:
        raise ValueError("no json")
    return json.loads(cleaned[start : end + 1])


def _score(value):
    try:
        number = int(float(value))
    except (TypeError, ValueError):
        return 0
    return max(0, min(100, number))


def _kcal(value):
    try:
        number = int(float(value))
    except (TypeError, ValueError):
        return 0
    return max(0, min(4000, number))


def _read_summary(text):
    try:
        data = _extract_json(text)
    except (json.JSONDecodeError, ValueError):
        return {
            "title": "这一程",
            "verdict": text.replace("\n", " ")[:28],
            "keep": "",
            "leave": "",
        }
    return {
        "title": str(data.get("title") or "这一程").strip()[:12] or "这一程",
        "verdict": str(data.get("verdict") or "").strip()[:40],
        "keep": str(data.get("keep") or "").strip()[:20],
        "leave": str(data.get("leave") or "").strip()[:20],
    }
