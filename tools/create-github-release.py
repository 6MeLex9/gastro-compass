#!/usr/bin/env python3
"""Создание релиза на GitHub и загрузка APK как ассета.

Зачем отдельный скрипт: в изолированных средах Windows-клиенты (curl, PowerShell/.NET)
могут не работать по HTTPS из-за Schannel (SEC_E_NO_CREDENTIALS), тогда как Python
использует собственный OpenSSL — это надёжный путь к API GitHub.

Использование:

    python tools/create-github-release.py \
        --repo 6MeLex9/gastro-compass \
        --tag v1.0.0 \
        --name "ГастроКомпас 1.0.0" \
        --notes docs/release-notes-1.0.0.md \
        --apk dist/GastroCompass-debug.apk

Токен берётся из --token или из переменной окружения GITHUB_TOKEN.
Нужен fine-grained токен с правом Contents: Read and write (релизы — часть contents).
"""

import argparse
import hashlib
import json
import os
import mimetypes
import sys
import urllib.error
import urllib.request
from pathlib import Path

API = "https://api.github.com"
UPLOADS = "https://uploads.github.com"
PROJECT_ROOT = Path(__file__).resolve().parent.parent


def request(url, token, method="GET", data=None, content_type="application/json", raw=False):
    headers = {
        "Authorization": f"Bearer {token}",
        "Accept": "application/vnd.github+json",
        "User-Agent": "gastro-compass-release",
        "X-GitHub-Api-Version": "2022-11-28",
    }
    body = None
    if data is not None:
        if raw:
            body = data
        else:
            body = json.dumps(data).encode("utf-8")
        headers["Content-Type"] = content_type

    req = urllib.request.Request(url, data=body, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=120) as resp:
            payload = resp.read()
            return resp.status, (json.loads(payload) if payload else None)
    except urllib.error.HTTPError as e:
        detail = e.read().decode("utf-8", errors="replace")
        try:
            detail = json.loads(detail).get("message", detail)
        except json.JSONDecodeError:
            pass
        return e.code, {"error": detail}


def human(size):
    for unit in ("Б", "КБ", "МБ", "ГБ"):
        if size < 1024:
            return f"{size:.1f} {unit}"
        size /= 1024
    return f"{size:.1f} ТБ"


def main():
    parser = argparse.ArgumentParser(description="Создать релиз GitHub и приложить APK")
    parser.add_argument("--repo", required=True, help="владелец/репозиторий")
    parser.add_argument("--tag", required=True, help="существующий тег, например v1.0.0")
    parser.add_argument("--name", default=None, help="заголовок релиза")
    parser.add_argument("--notes", default=None, help="файл с текстом релиза (markdown)")
    parser.add_argument("--apk", default=None, help="файл для загрузки как ассет")
    parser.add_argument("--token", default=os.environ.get("GITHUB_TOKEN"), help="PAT")
    parser.add_argument("--draft", action="store_true", help="создать как черновик")
    parser.add_argument("--prerelease", action="store_true", help="пометить предрелизом")
    args = parser.parse_args()

    if not args.token:
        sys.exit("Не задан токен: передайте --token или переменную окружения GITHUB_TOKEN")

    repo = args.repo.strip().removesuffix(".git")
    name = args.name or args.tag

    body = ""
    if args.notes:
        notes_path = Path(args.notes)
        if not notes_path.is_absolute():
            notes_path = PROJECT_ROOT / notes_path
        body = notes_path.read_text(encoding="utf-8")
        print(f"Текст релиза: {notes_path} ({len(body)} символов)")

    # --- проверка доступа ---------------------------------------------------
    status, user = request(f"{API}/user", args.token)
    if status != 200:
        sys.exit(f"Токен не принят (HTTP {status}): {user}")
    print(f"Токен действителен: {user.get('login')}")

    status, repo_info = request(f"{API}/repos/{repo}", args.token)
    if status != 200:
        sys.exit(f"Репозиторий недоступен (HTTP {status}): {repo_info}")
    print(f"Репозиторий: {repo_info['full_name']} (private={repo_info['private']}, "
          f"push={repo_info.get('permissions', {}).get('push')})")

    # --- релиз ---------------------------------------------------------------
    status, existing = request(f"{API}/repos/{repo}/releases/tags/{args.tag}", args.token)
    if status == 200:
        release = existing
        print(f"Релиз для тега {args.tag} уже существует: {release['html_url']}")
        if body:
            status, release = request(
                f"{API}/repos/{repo}/releases/{release['id']}",
                args.token,
                method="PATCH",
                data={"body": body, "name": name},
            )
            print(f"Текст релиза обновлён (HTTP {status})")
    else:
        status, release = request(
            f"{API}/repos/{repo}/releases",
            args.token,
            method="POST",
            data={
                "tag_name": args.tag,
                "name": name,
                "body": body,
                "draft": args.draft,
                "prerelease": args.prerelease,
            },
        )
        if status not in (200, 201):
            sys.exit(f"Не удалось создать релиз (HTTP {status}): {release}")
        print(f"Релиз создан: {release['html_url']}")

    # --- ассет ---------------------------------------------------------------
    if args.apk:
        apk_path = Path(args.apk)
        if not apk_path.is_absolute():
            apk_path = PROJECT_ROOT / apk_path
        if not apk_path.exists():
            sys.exit(f"Файл не найден: {apk_path}")

        digest = hashlib.sha256(apk_path.read_bytes()).hexdigest().upper()
        size = apk_path.stat().st_size
        print(f"APK: {apk_path.name}, {human(size)}, SHA-256 {digest[:16]}…")

        status, assets = request(f"{API}/repos/{repo}/releases/{release['id']}/assets", args.token)
        names = [a["name"] for a in (assets or [])]
        if apk_path.name in names:
            print(f"Ассет {apk_path.name} уже загружен — пропускаю")
        else:
            content_type = mimetypes.guess_type(apk_path.name)[0] or "application/octet-stream"
            url = (f"{UPLOADS}/repos/{repo}/releases/{release['id']}/assets"
                   f"?name={apk_path.name}")
            status, asset = request(
                url, args.token, method="POST",
                data=apk_path.read_bytes(), content_type=content_type, raw=True
            )
            if status not in (200, 201):
                sys.exit(f"Не удалось загрузить APK (HTTP {status}): {asset}")
            print(f"APK загружен: {asset['browser_download_url']} ({human(asset['size'])})")

    print()
    print("Готово.")
    print(f"  Страница релиза: {release['html_url']}")
    print(f"  Скачать APK:     https://github.com/{repo}/releases/latest/download/"
          f"{Path(args.apk).name if args.apk else 'GastroCompass-debug.apk'}")


if __name__ == "__main__":
    main()
