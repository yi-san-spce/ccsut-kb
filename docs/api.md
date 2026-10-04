# 长沙工业学院教务系统 · 接口笔记

> 站点 `tls.ccsut.cn` 在深信服 aTrust 零信任网关后面（`zts.ccsut.cn`），
> 所有请求必须带浏览器已登录的 Cookie（存 `.session/cookie.txt`）。
> Cookie 失效后重新在浏览器 DevTools 里复制即可，已抓的缓存不受影响。

## 通用

- Base: `https://tls.ccsut.cn`，全部走 `X-Requested-With: XMLHttpRequest`（AJAX 接口）
- 请求头里的 `User-Agent` 要和抓会话时的浏览器一致
- `xnxq` = 学年学期，如 `2026-2027-1`；`bjid` = 班级加密 id（`encodeId`，URL 安全）

## 接口

### 1. 班级树（学院→专业→班级）
`POST /admin/jwxtgld/xscx/getXssjYxxx`
form: `sznj=<年级，空=全部>&xm=<班级名搜索，可空>`
返回数组，每项：
```json
{"yxmc": "软件工程学院", "zys": 4, "bjs": 19, "xsrs": 0,
 "zyxxList": [{"zymc": "软件工程", "bjxxList": [{"id": "...", "bjmc": "25软工1班", "encodeId": "WG..."}]}]}
```
年级取值：2024 / 2025 / 2026（空 = 全部 228 个班）。

### 2. 学期元数据（周次 + 作息）
`POST /admin/api/getZclistByXnxq`
form: `xnxq=2026-2027-1&role=xs&userId=`
返回 `data`：
- `zclist[]`：`{zc, minrq, maxrq, rqfw}` —— 每周日期范围（第 1 周周一 = 开学日）
- `jcsjszList[]`：`{jc, kssj, jssj, djs}` —— 每节课上下课时间（10 节）
- `dqzc`：当前周次；`jsxq.pkzdzc`：排课最大周

### 3. 班级课表
`GET /admin/jwxtgld/kbcx/getBjkb?xnxq=..&bjid=..&xqid=&week=<周次>&sfsykb=`
- 一次返回该班"所选周有课"的**全部课程条目**（每条 = 单节课），条目自带 `zc` 周次范围，
  所以对 1..20 周全部请求后按组合键去重即得整学期课表。
- 条目关键字段：`kcmc` 课名 / `tmc` 教师 / `croommc` 教室 / `xingqi` 星期(1-7) /
  `djc` 第几节(起始) / `djs` 连堂节数 / `zc` 周次串("1-4,6-12") / `type`(1=普通课,9=体育分项占位) /
  `jxbmc` 教学班 / `jxbzc` 合班班级 / `fxlxmc` 体育分项名 / `bjrs` 班级人数 / `xkrs` 选课人数
- 去重键：`(type,kcmc,xingqi,djc,zc,croommc,tmc,jxbid,fxlxmc)`
  （`id`/`pkid` 跨周不稳定，体育占位条目每周 id 都变）

### 4. 其他（备用，未用）
- `POST /admin/system/jxzy/jsxx/getXqrqxx` form `xnxq&zcStr` → 某周周一~周日日期
- `/admin/api/getRqListByWeek`、`/admin/api/getKcxxdetail`（课程详情）
- `/admin/jwxtgld/kbcx/kckblist|roomkblist|xskblist`（课程/教室/学生个人课表页面）

## 抓取

```bash
python3 scripts/scrape.py               # 全量抓取 + 生成 data/dataset_*.json
python3 scripts/scrape.py --limit 3     # 冒烟测试
python3 scripts/scrape.py --build-only  # 只用缓存重建数据集
```
全量约 4600 个请求，2 分钟左右。产物：`data/dataset_2026-2027-1_vN.json` +
`data/latest.json`（版本指针 + sha256，未来放到静态托管上就是 APP 的更新清单）。
