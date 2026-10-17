"""언론사 로고를 선명한 것으로 모은다 (주인 요청 2026-10-17: 홈 화면에서 커진 로고가 깨짐).

앱은 사이트의 apple-touch-icon이나 구글 파비콘(128px)을 받아 왔는데, 79곳 중 6곳만 180px 이상이었다.
여기서는 언론사 누리집의 아이콘 링크·웹앱 매니페스트·구글 256px 파비콘 가운데 가장 큰 그림을 골라
_site/logos/<누리집>.png 로 올린다 (최대 512px). 앱은 이것을 먼저 받는다 (LogoWall).
한 번 찾은 로고는 캐시에 두고 7일 동안 다시 쓴다. 실패해도 배포는 계속된다 (앱이 예전 방식으로 찾는다).

python3 tools/logos.py <_site> <캐시 폴더>
"""
import concurrent.futures as cf
import io
import json
import os
import re
import shutil
import sys
import time
import urllib.parse
import urllib.request

from PIL import Image

UA = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/126 Mobile Safari/537.36"
MAX_PX, MIN_PX, KEEP_DAYS = 512, 96, 7


def get(url, limit=3_000_000):
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    with urllib.request.urlopen(req, timeout=8) as r:
        return r.read(limit), r.geturl()


def picture(url):
    """그림을 받아 가장 큰 프레임을 RGBA로 (ICO는 여러 크기 중 가장 큰 것). 못 읽으면 None"""
    try:
        data, _ = get(url)
        im = Image.open(io.BytesIO(data))
        if im.format == "ICO":
            im.size = max(im.ico.sizes())
        im.load()
        return im.convert("RGBA")
    except Exception:
        return None


def candidates(site):
    """누리집 첫 화면의 아이콘 링크와 웹앱 매니페스트 아이콘, apple-touch-icon, 구글 256px 파비콘 (SVG는 앱이 못 읽어 뺀다)"""
    out = []
    for base in (f"https://{site}/", f"https://www.{site}/"):
        try:
            html, final = get(base, 600_000)
        except Exception:
            continue
        html = html.decode("utf-8", "ignore")
        for tag in re.findall(r"<link[^>]+>", html, re.I):
            rel = re.search(r'rel=["\']([^"\']+)', tag, re.I)
            href = re.search(r'href=["\']([^"\']+)', tag, re.I)
            if rel and href and "icon" in rel.group(1).lower():
                out.append(urllib.parse.urljoin(final, href.group(1)))
            if rel and href and rel.group(1).lower() == "manifest":
                try:
                    man = json.loads(get(urllib.parse.urljoin(final, href.group(1)))[0])
                    out += [urllib.parse.urljoin(final, ic["src"]) for ic in man.get("icons", []) if "src" in ic]
                except Exception:
                    pass
        break
    out += [f"https://{site}/apple-touch-icon.png",
            f"https://t1.gstatic.com/faviconV2?client=SOCIAL&type=FAVICON&fallback_opts=TYPE,SIZE,URL&url=https://{site}&size=256"]
    return [u for u in dict.fromkeys(out) if not u.lower().split("?")[0].endswith(".svg")]


def best(site):
    """가장 큰 그림(짧은 변 기준). MIN_PX보다 작으면 None"""
    top = None
    for u in candidates(site):
        im = picture(u)
        if im and (top is None or min(im.size) > min(top.size)):
            top = im
    if top is None or min(top.size) < MIN_PX:
        return None
    if max(top.size) > MAX_PX:
        top.thumbnail((MAX_PX, MAX_PX), Image.LANCZOS)
    return top


def name(site):
    return re.sub(r"[^a-zA-Z0-9.-]", "_", site) + ".png"  # 앱 LogoWall의 파일 이름과 같은 규칙


def one(site, cache):
    path = os.path.join(cache, name(site))
    if os.path.exists(path) and time.time() - os.path.getmtime(path) < KEEP_DAYS * 86400:
        return site, path, "캐시"
    im = best(site)
    if im is None:
        return site, None, "없음"
    im.save(path, "PNG", optimize=True)
    return site, path, f"{im.size[0]}x{im.size[1]}"


def main(site_dir, cache):
    os.makedirs(cache, exist_ok=True)
    out = os.path.join(site_dir, "logos")
    os.makedirs(out, exist_ok=True)
    b = json.load(open(os.path.join(site_dir, "briefing.json"), encoding="utf-8"))
    sites = sorted({s["site"] for sec in b["sections"] for s in sec["sources"] if s.get("site")})
    index = {}
    with cf.ThreadPoolExecutor(12) as ex:
        for site, path, note in ex.map(lambda s: one(s, cache), sites):
            if path:
                shutil.copy(path, os.path.join(out, name(site)))
                index[site] = min(Image.open(path).size)
            print(f"로고 {site}: {note}")
    json.dump(index, open(os.path.join(out, "index.json"), "w", encoding="utf-8"))
    big = sum(1 for px in index.values() if px >= 180)
    print(f"로고 {len(index)}/{len(sites)}곳 올림 (180px 이상 {big}곳)")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
