import sqlite3
from pathlib import Path

DB = Path(__file__).resolve().parent.parent / "data" / "wuxing.db"


def connect() -> sqlite3.Connection:
    DB.parent.mkdir(parents=True, exist_ok=True)
    conn = sqlite3.connect(DB)
    conn.row_factory = sqlite3.Row
    return conn


def init() -> None:
    with connect() as conn:
        conn.execute(
            """
            create table if not exists users (
                id integer primary key autoincrement,
                email text unique,
                phone text unique,
                name text not null default '',
                password_hash text not null,
                created_at text not null
            )
            """
        )
        conn.execute(
            """
            create table if not exists rituals (
                user_id integer primary key,
                body text not null,
                week_score integer not null default 0,
                month_score integer not null default 0,
                updated_at text not null
            )
            """
        )
