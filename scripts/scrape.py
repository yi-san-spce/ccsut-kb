#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""长沙工业学院教务系统 · 全班级课表抓取脚本

用法:
  python3 scripts/scrape.py                 # 全量抓取 + 构建数据集
  python3 scripts/scrape.py --limit 3       # 只抓前 3 个班(冒烟测试)
  python3 scripts/scrape.py --build-only    # 只用本地缓存重建数据集
  python3 scripts/scrape.py --force         # 忽略缓存重新抓取
  python3 scripts/scrape.py --xnxq 2026-2027-1

前置条件:
  把浏览器里复制的 Cookie 整行存入 .session/cookie.txt (失效后重新复制即可,
  已抓取的原始缓存仍然有效, 无需重抓)。

接口说明见 docs/api.md
"""
from __future__ import annotations

import argparse
import concurrent.futures
import hashlib
import http.client
import json
import pathlib
import socket
import sys
import threading
import time
import urllib.parse
from datetime import datetime

ROOT = pathlib.Path(__file__).resolve().parents[1]
COOKIE_FILE = ROOT / ".session" / "cookie.txt"
CACHE_DIR = ROOT / ".cache" / "raw"
DATA_DIR = ROOT / "data"
VERSION_FILE = DATA_DIR / "version.json"

HOST = "tls.ccsut.cn"
# 与抓到会话的浏览器保持相同 UA, 降低被网关指纹拦截的风险
UA = ("Mozilla/5.0 (Linux; Android 16; Pixel 10) AppleWebKit/537.36 "
      "(KHTML, like Gecko) Chrome/154.0.0.0 Mobile Safari/537.36")


class SessionExpired(Exception):
    pass


class Client:
    """线程级 keep-alive 的最小 JSON 客户端"""

    def __init__(self, cookie: str):
        self.cookie = cookie.strip()
        self._local = threading.local()

    def _conn(self) -> http.client.HTTPSConnection:
        c = getattr(self._local, "conn", None)
        if c is None:
            c = http.client.HTTPSConnection(HOST, timeout=20)
            self._local.conn = c
        return c

    def _drop(self):
        c = getattr(self._local, "conn", None)
        if c is not None:
            try:
                c.close()
            except Exception:
                pass
            self._local.conn = None

    def request(self, method: str, path: str, form: dict | None = None,
                query: dict | None = None) -> bytes:
        if query:
            path = path + "?" + urllib.parse.urlencode(query)
        body = urllib.parse.urlencode(form).encode() if form else None
        headers = {
            "User-Agent": UA,
            "Cookie": self.cookie,
            "X-Requested-With": "XMLHttpRequest",
            "Accept": "application/json, text/javascript, */*; q=0.01",
            "Referer": f"https://{HOST}/admin/jwxtgld/kbcx/bjkblist",
        }
        if body:
            headers["Content-Type"] = "application/x-www-form-urlencoded; charset=UTF-8"
            headers["Origin"] = f"https://{HOST}"

        last_err = None
        for attempt in range(3):
            try:
                c = self._conn()
                c.request(method, path, body=body, headers=headers)
                resp = c.getresponse()
                data = resp.read()
                if resp.status in (301, 302, 401, 403):
                    raise SessionExpired(f"HTTP {resp.status} @ {path}")
                head = data[:64].lstrip().lower()
                if head.startswith(b"<!doctype") or head.startswith(b"<html"):
                    raise SessionExpired(f"返回了 HTML(登录页) @ {path}")
                return data
            except SessionExpired:
                raise
            except (http.client.HTTPException, socket.timeout, OSError) as e:
                last_err = e
                self._drop()
                time.sleep(0.8 * (attempt + 1))
        raise RuntimeError(f"请求失败 {path}: {last_err}")


def load_client() -> Client:
    if not COOKIE_FILE.exists():
        sys.exit("缺少 .session/cookie.txt —— 请把浏览器里的 Cookie 整行复制进去")
    return Client(COOKIE_FILE.read_text(encoding="utf-8"))


# ---------------------------------------------------------------- 接口封装

def fetch_meta(cli: Client, xnxq: str) -> dict:
    """学期元数据: 周次列表(含日期范围)、作息时间、当前周次"""
    data = cli.request("POST", "/admin/api/getZclistByXnxq",
                       form={"xnxq": xnxq, "role": "xs", "userId": ""})
    d = json.loads(data)
    if d.get("ret") != 0:
        raise RuntimeError(f"getZclistByXnxq 返回异常: {d}")
    return d["data"]


def fetch_tree(cli: Client, sznj: str = "") -> list:
    """班级树: 学院 -> 专业 -> 班级(encodeId/bjmc)"""
    data = cli.request("POST", "/admin/jwxtgld/xscx/getXssjYxxx",
                       form={"sznj": sznj, "xm": ""})
    d = json.loads(data)
    if not isinstance(d, list):
        raise RuntimeError(f"getXssjYxxx 返回异常: {str(d)[:200]}")
    return d


def fetch_bjkb(cli: Client, xnxq: str, bjid: str, week: int) -> list:
    data = cli.request("GET", "/admin/jwxtgld/kbcx/getBjkb",
                       query={"xnxq": xnxq, "bjid": bjid, "xqid": "",
                              "week": week, "sfsykb": ""})
    d = json.loads(data)
    return d if isinstance(d, list) else []


# ---------------------------------------------------------------- 抓取与合并

def dedupe_key(e: dict):
    return (e.get("type"), e.get("kcmc"), e.get("xingqi"), e.get("djc"),
            e.get("zc"), e.get("croommc"), e.get("tmc"),
            e.get("jxbid", ""), e.get("fxlxmc", ""))


def crawl_class(cli: Client, xnxq: str, bjid: str, weeks: list,
                force: bool = False) -> list:
    """一个班级的所有周次 -> 去重后的课程条目列表(带磁盘缓存)"""
    cls_dir = CACHE_DIR / xnxq
    cls_dir.mkdir(parents=True, exist_ok=True)
    cache_file = cls_dir / f"{bjid}.json"
    if cache_file.exists() and not force:
        return json.loads(cache_file.read_text(encoding="utf-8"))

    merged, seen = [], set()
    for w in weeks:
        week_cache = cls_dir / "_weeks" / f"{bjid}_w{w}.json"
        if week_cache.exists():
            entries = json.loads(week_cache.read_text(encoding="utf-8"))
        else:
            entries = fetch_bjkb(cli, xnxq, bjid, w)
            week_cache.parent.mkdir(parents=True, exist_ok=True)
            week_cache.write_text(json.dumps(entries, ensure_ascii=False),
                                  encoding="utf-8")
            time.sleep(0.04)  # 轻微限速
        for e in entries:
            k = dedupe_key(e)
            if k not in seen:
                seen.add(k)
                merged.append(e)

    cache_file.write_text(json.dumps(merged, ensure_ascii=False), encoding="utf-8")
    return merged


# ---------------------------------------------------------------- 数据集构建

def parse_zc(zc: str) -> list:
    """'1-4,6-12' -> [[1,4],[6,12]]"""
    out = []
    for part in (zc or "").replace("，", ",").split(","):
        part = part.strip()
        if not part:
            continue
        if "-" in part:
            a, b = part.split("-", 1)
            if a.isdigit() and b.isdigit():
                out.append([int(a), int(b)])
        elif part.isdigit():
            out.append([int(part), int(part)])
    return out


def build_dataset(xnxq: str) -> pathlib.Path:
    meta = json.loads((DATA_DIR / xnxq / "meta.json").read_text(encoding="utf-8"))
    tree = json.loads((DATA_DIR / xnxq / "tree.json").read_text(encoding="utf-8"))
    sznj_file = DATA_DIR / xnxq / "sznj.json"
    sznj_map = json.loads(sznj_file.read_text(encoding="utf-8")) if sznj_file.exists() else {}
    cls_dir = CACHE_DIR / xnxq  # crawl_class 的班级级缓存目录

    zclist = meta.get("zclist") or []
    periods = [{"jc": int(p["jc"]), "start": p["kssj"], "end": p["jssj"],
                "djs": int(p.get("djs", 1))}
               for p in (meta.get("jcsjszList") or [])]

    classes, colleges = {}, []
    for yx in tree:
        majors = []
        for zy in yx.get("zyxxList") or []:
            bj_ids = []
            for bj in zy.get("bjxxList") or []:
                enc = bj["encodeId"]
                bj_ids.append(enc)
                courses = []
                f = cls_dir / f"{enc}.json"
                if f.exists():
                    for e in json.loads(f.read_text(encoding="utf-8")):
                        courses.append({
                            "kc": e.get("kcmc"),
                            "kcId": e.get("kcid", ""),
                            "jxb": e.get("jxbmc", ""),
                            "teacher": e.get("tmc", ""),
                            "room": e.get("croommc", ""),
                            "day": e.get("xingqi"),
                            "jc": e.get("djc"),
                            "djs": e.get("djs", 1),
                            "zc": e.get("zc", ""),
                            "zcRanges": parse_zc(e.get("zc", "")),
                            "type": e.get("type"),
                            "fx": e.get("fxlxmc", ""),
                            "jxbzc": e.get("jxbzc", ""),
                        })
                classes[enc] = {"bjmc": bj.get("bjmc", ""), "yxmc": yx.get("yxmc", ""),
                                "zymc": zy.get("zymc", ""), "sznj": sznj_map.get(enc, ""),
                                "courses": courses}
            majors.append({"name": zy.get("zymc", ""), "classes": bj_ids})
        colleges.append({"name": yx.get("yxmc", ""), "majors": majors})

    version_file = DATA_DIR / "version.json"
    ver = 1
    if version_file.exists():
        try:
            ver = json.loads(version_file.read_text())["version"] + 1
        except Exception:
            ver = 1
    version_file.write_text(json.dumps({"version": ver}), encoding="utf-8")

    weeks = len(zclist)
    dataset = {
        "schema": 1,
        "version": ver,
        "generatedAt": datetime.now().astimezone().isoformat(timespec="seconds"),
        "xnxq": xnxq,
        "term": {
            "weeks": weeks,
            "startDate": zclist[0]["minrq"][:10] if zclist else None,
            "weekRanges": [{"zc": int(z["zc"]), "range": z["rqfw"]} for z in zclist],
            "currentWeekAtCrawl": int(meta.get("dqzc") or 0),
        },
        "periods": periods,
        "colleges": colleges,
        "classes": classes,
    }

    out = DATA_DIR / f"dataset_{xnxq}_v{ver}.json"
    out.write_text(json.dumps(dataset, ensure_ascii=False, separators=(",", ":")),
                   encoding="utf-8")
    (DATA_DIR / f"dataset_{xnxq}_latest.json").write_text(
        json.dumps(dataset, ensure_ascii=False, separators=(",", ":")),
        encoding="utf-8")

    digest = hashlib.sha256(out.read_bytes()).hexdigest()
    (DATA_DIR / "latest.json").write_text(json.dumps({
        "xnxq": xnxq, "version": ver, "file": out.name,
        "sha256": digest, "bytes": out.stat().st_size,
        "generatedAt": dataset["generatedAt"],
    }, ensure_ascii=False, indent=2), encoding="utf-8")
    return out


# ---------------------------------------------------------------- 主流程

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--xnxq", default="2026-2027-1")
    ap.add_argument("--workers", type=int, default=4)
    ap.add_argument("--limit", type=int, default=0, help="只抓前 N 个班(测试)")
    ap.add_argument("--force", action="store_true", help="忽略缓存重新抓取")
    ap.add_argument("--build-only", action="store_true", help="仅重建数据集")
    args = ap.parse_args()

    xnxq = args.xnxq
    (DATA_DIR / xnxq).mkdir(parents=True, exist_ok=True)

    if not args.build_only:
        cli = load_client()
        try:
            meta = fetch_meta(cli, xnxq)
            print(f"[meta] 周数={len(meta.get('zclist') or [])} "
                  f"当前周={meta.get('dqzc')} 作息={len(meta.get('jcsjszList') or [])} 节")
            (DATA_DIR / xnxq / "meta.json").write_text(
                json.dumps(meta, ensure_ascii=False, indent=1), encoding="utf-8")

            # 分年级抓树以获得 sznj 标签; '' 一次拿全量做兜底
            tree_all = fetch_tree(cli, "")
            sznj_map = {}
            for sznj in ("2024", "2025", "2026"):
                try:
                    for yx in fetch_tree(cli, sznj):
                        for zy in yx.get("zyxxList") or []:
                            for bj in zy.get("bjxxList") or []:
                                sznj_map[bj["encodeId"]] = sznj
                except Exception as e:
                    print(f"[tree] 年级 {sznj} 抓取失败(忽略): {e}")
            print(f"[tree] 学院 {len(tree_all)} 个, 班级 {sum(len(z.get('bjxxList') or []) for y in tree_all for z in y.get('zyxxList') or [])} 个")
            (DATA_DIR / xnxq / "tree.json").write_text(
                json.dumps(tree_all, ensure_ascii=False, indent=1), encoding="utf-8")
            (DATA_DIR / xnxq / "sznj.json").write_text(
                json.dumps(sznj_map, ensure_ascii=False, indent=1), encoding="utf-8")

            classes = [bj for yx in tree_all for zy in yx.get("zyxxList") or []
                       for bj in zy.get("bjxxList") or []]
            if args.limit:
                classes = classes[:args.limit]
            weeks = list(range(1, len(meta.get("zclist") or []) + 1))
            print(f"[crawl] 开始: {len(classes)} 个班 x {len(weeks)} 周, "
                  f"{args.workers} 线程")

            done = failed = 0
            t0 = time.time()
            stop = threading.Event()

            def work(bj):
                if stop.is_set():
                    return bj["encodeId"], False, "已跳过"
                try:
                    crawl_class(cli, xnxq, bj["encodeId"], weeks, force=args.force)
                    return bj["encodeId"], True, ""
                except SessionExpired:
                    stop.set()
                    return bj["encodeId"], False, "会话失效"
                except Exception as e:
                    return bj["encodeId"], False, str(e)[:120]

            with concurrent.futures.ThreadPoolExecutor(args.workers) as ex:
                for enc, ok, msg in ex.map(work, classes):
                    done += 1
                    if not ok:
                        failed += 1
                        print(f"  [失败] {enc[:12]}… {msg}")
                    if stop.is_set():
                        break
                    if done % 20 == 0 or done == len(classes):
                        rate = done / max(time.time() - t0, 1)
                        print(f"  进度 {done}/{len(classes)} "
                              f"({rate:.1f} 班/秒, 预计剩 {int((len(classes)-done)/max(rate,0.01))}s)")
            if stop.is_set():
                sys.exit("会话已失效: 请重新复制 Cookie 到 .session/cookie.txt 后重跑,"
                         " 已抓取的缓存不会丢失。")
            print(f"[crawl] 完成, 失败 {failed}")
        except SessionExpired as e:
            sys.exit(f"会话已失效({e}): 请重新复制 Cookie 到 .session/cookie.txt 后重跑,"
                     f" 已抓取的缓存不会丢失。")

    out = build_dataset(xnxq)
    info = json.loads((DATA_DIR / "latest.json").read_text(encoding="utf-8"))
    d = json.loads(out.read_text(encoding="utf-8"))
    n_empty = sum(1 for c in d["classes"].values() if not c["courses"])
    print(f"[done] {out.name}")
    print(f"       版本 v{info['version']} | 班级 {len(d['classes'])} (其中无课 "
          f"{n_empty}) | 大小 {info['bytes']/1024:.0f} KB | sha256 {info['sha256'][:16]}…")


if __name__ == "__main__":
    main()
