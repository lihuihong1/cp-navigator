# CP Navigator（OJ 助手）

一个纯本地的 Android 小工具，把常用 OJ 的**近期比赛**、**解题数**、**rating** 汇总在一屏。

> 仓库名是 `cp-navigator`，App 的桌面名称是「OJ 助手」，两者是同一个东西。

- 没有账号、没有服务器、没有任何上报，所有数据只存在本机
- 卸载即清空（`allowBackup="false"`，不进系统云备份，也不随换机迁移）
- 只读各站的公开页面 / 公开接口，不需要在 App 里登录任何账号

## 安装

在 [Releases](../../releases) 页下载 `oj-helper-<版本>.apk`，侧载安装（自签名，系统会提示「未知来源」，属正常）。

覆盖安装需要：包名相同 + 签名相同 + versionCode 更大。满足这三条时收藏与成绩会保留；否则系统会拒绝安装，只能先卸载。

## 功能

**比赛**

- 6 个源：Codeforces、AtCoder、洛谷、蓝桥云课、力扣、牛客
- 只显示**未来 7 天内**的比赛（窗口 = 今天 00:00 起 7 天），今天已结束的仍保留并标注「已结束」
- 两种视图：按平台分组 / 按日期分组（今天 / 明天 / 后天 / 具体日期）
- 分组可点击整行折叠；组内按开始时间升序
- 点击某场比赛先弹「访问比赛页面？」确认，再交给浏览器
- 每场比赛可收藏（☆），也可一键写入系统日历（走系统的插入界面，不申请日历权限）
- 开赛提醒：本地闹钟 + 通知，可选「比赛开始时 / 提前 5 / 10 / 15 / 30 / 60 分钟」

**我的**

- 手工填写各站用户名 / id，查询解题数与 rating
- 题解统计：按各站解题数画饼图，解题数为 0 的站点不参与
- 清空本地数据

## 比赛源

| 站点 | 数据来源 | 备注 |
| --- | --- | --- |
| Codeforces | `codeforces.com/api/contest.list` | 官方 API，`phase` 为 `BEFORE`/`CODING` 的才收 |
| AtCoder | `atcoder.jp/contests/` 首页 Upcoming 表 | 解析 HTML，时间自带 `+0900` |
| 洛谷 | `luogu.com.cn/contest/list?_contentOnly=1` | 需要同时带 `x-lentille-request` 与 `x-requested-with` 两个请求头才返回 JSON |
| 蓝桥云课 | `lanqiao.cn/api/v2/contests/` | 按 `status=not_finished` 过滤 |
| 力扣 | `leetcode.cn/graphql` 的 `contestUpcomingContests` | 只取即将开始的场次 |
| 牛客 | `ac.nowcoder.com/acm/contest/vip-index` | 服务端渲染的 HTML，按块解析 |

## 统计源

| 站点 | 解题数 | rating | 备注 |
| --- | --- | --- | --- |
| Codeforces | ojhunt，失败时回退 `user.status` 全量提交去重 | `user.info` | 活跃账号回退路径要拉上万条提交，较慢 |
| AtCoder | kenkoooo 的 `ac_rank` | `users/<handle>/history/json` 末条 | 不存在的 handle 返回空数组而非 404 |
| 力扣 | GraphQL `userProfileUserQuestionProgress` | — | 站点没有公开的竞赛分接口，显示为空 |
| 洛谷 | `api/user/search` 换 uid，再 `api/user/info` | `elo` 字段 | 接口只认数字 uid，不接受用户名直查 |
| 牛客 | ojhunt | 个人主页 `rate-score4` | 只认数字 ID，填用户名会报错 |
| VJudge | 页面内嵌的 `profile-header-data` JSON | — | 表格本身由前端 JS 填充，抓不到 |
| HDU | `userstatus.php`（GB18030 编码） | 同页 Rank | 页面结构较老，正则匹配 |

查询失败时按原因分五类提示：**信息不全 / 用户名有误 / 查询过于频繁 / 网络异常 / 接口失效**。

## 自己构建

仓库里只有源码，**不含 Gradle 工程、不含构建脚本**。目录就是标准 Android 工程的结构：

```
AndroidManifest.xml
src/com/ojhelper/*.java     （22 个文件，纯 framework API，无第三方依赖）
res/                        （图标、站点 logo、字符串）
```

步骤：

1. Android Studio 新建一个空项目（Empty Views Activity、Java、minSdk 24），包名随意
2. 把模板里的 `app/src/main/java` 与 `app/src/main/res` 清空，替换为本仓库的 `src/` 与 `res/`，`AndroidManifest.xml` 也一并替换
3. **补一个生成类**（源码里没有，缺它编译会报 `找不到符号: 类 BuildStamp`）：

```java
// src/main/java/com/ojhelper/BuildStamp.java
package com.ojhelper;

final class BuildStamp {
    static final String T = "2026-01-01 00:00";
}
```

4. Sync，然后 Run

`BuildStamp.T` 只显示在「我的 → 关于」的「编译于」一行，填什么都可以。

## 想和作者的版本共存

改 `applicationId`（例如 `com.example.ojhelper`），同时把 `namespace` 保持为 `com.ojhelper`，这样 22 个源文件不用动。

不改包名的话，你自己的签名和作者的签名不同，会得到 `INSTALL_FAILED_UPDATE_INCOMPATIBLE` —— 只能先卸载作者版本才能装，卸载会连数据一起删掉。

## 版本

| 版本 | 主要变化 |
| --- | --- |
| [v1.6.0](../../releases/tag/v1.6.0) | 题解统计饼图 |
| [v1.5.4](../../releases/tag/v1.5.4) | 开赛提醒；VJudge 解题数；CF / 牛客解题数改走 ojhunt |
| [v1.4.0](../../releases/tag/v1.4.0) | 原生 ICPC 榜单（直连 XCPCIO 数据） |
| [v1.3.10](../../releases/tag/v1.3.10) | 站点 logo、蓝桥云课源、榜单页；1.3.x 线只保留此版源码 |
| [v1.2.0](../../releases/tag/v1.2.0) | 按平台 / 按日期两种视图、平台多选、点击确认、AtCoder 源 |
| [v1.1.0](../../releases/tag/v1.1.0) | 改为手动点「查询」；修洛谷跳转死循环 |
| [v1.0.0](../../releases/tag/v1.0.0) | 首个版本：4 个比赛源、7 站统计、收藏、日历 |

## 已知限制

- 抓的是各站**公开页面**，站点改版就会失效，只能发新版修；不保证任何时效
- 牛客只认数字 ID；力扣没有公开竞赛分；VJudge 没有 rating；洛谷的 `elo` 多为空
- 时间统一按 `Asia/Shanghai` 显示
- 提醒依赖系统的精确闹钟权限（Android 12+），并且会被部分厂商的后台清理策略影响

## 隐私

不收集、不上传任何数据。唯一的对外请求就是查询上面列出的各站页面；其中 Codeforces / 牛客 / HDU 的解题数会经过第三方 `ojhunt.com`，等于把填写的 handle 发给它。

## 许可

[MIT](LICENSE)