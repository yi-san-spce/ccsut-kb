#!/usr/bin/env python3
"""校验 data/notice.json (远程通知广播清单)。

语义与客户端 data/Notice.kt 保持一致: 内容错误在推送时红灯拦下,
而不是等 App 端静默降级 (message 缺失 = 整条跳过, 日期写错 = 排期失效,
priority 拼错 = 静默降级为普通通知, 这些用户端都无感知)。

用法: python3 scripts/check_notice.py [path]   # 缺省 data/notice.json
文件不存在时退出码 0 (没有要广播的通知是常态, CI 直接放行)。
仅用标准库, 无第三方依赖。
"""
import json
import re
import sys
from datetime import datetime
from pathlib import Path

DATE_ONLY = re.compile(r"^\d{4}-\d{2}-\d{2}$")
DATETIME = re.compile(r"^\d{4}-\d{2}-\d{2}[ T]\d{2}:\d{2}(:\d{2})?$")
ISO_OFFSET = re.compile(r"^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(:\d{2})?(Z|[+-]\d{2}:\d{2})$")
VERSION = re.compile(r"^\d+(\.\d+)*(?:-(beta|pre|stable|dev)\.?(\d*))?$")

errors = []
warnings = []


def parse_time(s):
    """按客户端 Notice.parseTime 的三种写法校验, 合法返回 True。"""
    s = s.strip()
    try:
        if DATE_ONLY.match(s):
            datetime.strptime(s, "%Y-%m-%d")
            return True
        if DATETIME.match(s):
            t = s[:10] + "T" + s[11:]
            fmt = "%Y-%m-%dT%H:%M:%S" if s.count(":") >= 2 else "%Y-%m-%dT%H:%M"
            datetime.strptime(t, fmt)
            return True
        if ISO_OFFSET.match(s):
            datetime.fromisoformat(s.replace("Z", "+00:00"))
            return True
    except ValueError:
        return False
    return False


def to_naive(s, end):
    """解析成可比较的 naive datetime; 日期型 endAt 按客户端语义折算为当天 23:59:59。"""
    s = s.strip()
    try:
        if DATE_ONLY.match(s):
            d = datetime.strptime(s, "%Y-%m-%d")
            return d.replace(hour=23, minute=59, second=59) if end else d
        if DATETIME.match(s):
            fmt = "%Y-%m-%dT%H:%M:%S" if s.count(":") >= 2 else "%Y-%m-%dT%H:%M"
            return datetime.strptime(s[:10] + "T" + s[11:], fmt)
        if ISO_OFFSET.match(s):
            return datetime.fromisoformat(s.replace("Z", "+00:00")).replace(tzinfo=None)
    except ValueError:
        return None
    return None


def check_notice(n, where, ids):
    if not isinstance(n, dict):
        errors.append(f"{where}: 必须是对象")
        return
    msg = n.get("message")
    if not isinstance(msg, str) or not msg.strip():
        errors.append(f"{where}: message 缺失或空白 (客户端会整条跳过, 用户端无感知)")
    nid = n.get("id")
    if nid is not None:
        if not isinstance(nid, str) or not nid.strip():
            errors.append(f"{where}: id 必须是非空字符串")
        elif nid in ids:
            errors.append(f"{where}: id 「{nid}」与 {ids[nid]} 重复 (重复 id 会让去重互相误伤)")
        else:
            ids[nid] = where
    if "title" in n and not isinstance(n["title"], str):
        errors.append(f"{where}: title 必须是字符串")
    url = n.get("url")
    if url is not None and (not isinstance(url, str) or not url.strip().startswith(("http://", "https://"))):
        errors.append(f"{where}: url 必须以 http(s):// 开头 (否则客户端直接丢弃)")
    if "once" in n and not isinstance(n["once"], bool):
        errors.append(f"{where}: once 必须是 true/false")
    prio = n.get("priority")
    if prio is not None and (not isinstance(prio, str) or prio.strip().lower() not in ("normal", "critical")):
        errors.append(f"{where}: priority 只能是 normal / critical (写错会静默降级为普通通知)")
    for field in ("confirmText", "dismissText"):
        v = n.get(field)
        if v is not None:
            if not isinstance(v, str) or not v.strip():
                errors.append(f"{where}: {field} 必须是非空字符串 (空白会被客户端当作缺省文案)")
            elif len(v.strip()) > 12:
                warnings.append(f"{where}: {field} 「{v}」偏长, 按钮文案建议控制在 6 字左右")
    for field in ("startAt", "endAt"):
        v = n.get(field)
        if v is not None and (not isinstance(v, str) or not parse_time(v)):
            errors.append(f"{where}: {field} 「{v}」无法解析 (会被客户端当作无排期, 通知提前/永不失效)")
    for field in ("minVersion", "maxVersion"):
        v = n.get(field)
        if v is not None:
            if not isinstance(v, str) or not v.strip():
                errors.append(f"{where}: {field} 必须是非空字符串")
            elif not VERSION.match(v.strip()):
                warnings.append(f"{where}: {field} 「{v}」不是常规版本号 (客户端按宽松规则比较, 请确认写法)")
    sa, ea = n.get("startAt"), n.get("endAt")
    if isinstance(sa, str) and isinstance(ea, str) and parse_time(sa) and parse_time(ea):
        s_start, e_end = to_naive(sa, False), to_naive(ea, True)
        if s_start and e_end and s_start > e_end:
            warnings.append(f"{where}: startAt 晚于 endAt, 窗口永不存在 (若为刻意封存可忽略)")


def main():
    path = Path(sys.argv[1] if len(sys.argv) > 1 else "data/notice.json")
    if not path.is_file():
        print(f"OK {path} 不存在 —— 当前无远程通知, 跳过校验")
        return 0
    try:
        raw = path.read_text(encoding="utf-8")
    except UnicodeDecodeError:
        print(f"FAIL {path} 不是 UTF-8 文本")
        return 1
    if len(raw) > 100_000:
        warnings.append(f"文件 {len(raw) // 1024} KB, 建议保持在几 KB 以内")
    try:
        root = json.loads(raw)
    except json.JSONDecodeError as e:
        print(f"FAIL {path} 不是合法 JSON: {e} (line {e.lineno} column {e.colno})")
        return 1
    if not isinstance(root, dict):
        print("FAIL 顶层必须是对象 ({...})")
        return 1

    ids = {}
    if "notices" in root:
        arr = root["notices"]
        if not isinstance(arr, list):
            print("FAIL notices 必须是数组")
            return 1
        if not arr:
            warnings.append("notices 是空数组 —— 等于全网无通知, 确认是否有意")
        for i, n in enumerate(arr):
            check_notice(n, f"notices[{i}]", ids)
        count = len(arr)
    else:
        check_notice(root, "根对象", ids)
        count = 1

    for w in warnings:
        print(f"WARN {w}")
    if errors:
        for e in errors:
            print(f"FAIL {e}")
        print(f"\n校验失败: {len(errors)} 个错误。格式说明见 docs/notice.md")
        return 1
    print(f"OK {path} 校验通过: {count} 条通知, {len(warnings)} 条提醒")
    return 0


if __name__ == "__main__":
    sys.exit(main())
