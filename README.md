# QQ 短视频缓存浏览器

> ⬇️ **[下载最新 APK](https://github.com/joestataka/QQShortVideo/releases/latest)**

一个 Jetpack Compose 应用：读取 QQ 的短视频缓存目录，**以封面网格展示，点击封面即可播放对应视频**。

## 功能

单页扁平浏览（参考主流相册 App）：

```
QQ 短视频库 730 个视频          [最新 ↓]   ← 排序菜单
[🔍 搜索文件名 / 日期…]
( 全部 ) ( 2026-10 ) ( 2026-09 ) ...      ← 月份筛选 chips
┌──────┐ ┌──────┐ ┌──────┐
│ 封面  │ │ 封面  │ │ 封面  │              ← 每格：中央▶ + 底部日期·大小
└──────┘ └──────┘ └──────┘
```

- **搜索**：匹配哈希（文件名）或日期。
- **排序**：`最新↓ / 最早↑ / 最大↓ / 最小↑`；切换排序 / 月份 / 搜索时自动滚回顶部。
- **月份筛选**：顶部 chips，「全部」+ 各月。
- **右侧滚动条**：可拖动滑块按比例快速定位，点击轨道直接跳转（730 个视频不用一屏屏翻）。
- **点击封面** → 全屏播放（Shizuku/root 模式下会先把视频复制到应用缓存再播放）。
- **长按封面** → 进入**多选模式**：

  ```
  ✕  已选 2 个                              全选
  ┌──────┐ ┌──────┐ ┌──────┐
  │✓封面 │ │✓封面 │ │ 封面  │          ← 选中项：蓝边框 + 勾选角标
  └──────┘ └──────┘ └──────┘
        📤 分享    ℹ️ 详情    🗑 删除       ← 底部操作栏
  ```

  | 操作 | 说明 |
  | --- | --- |
  | 选择 | 长按进入选择模式；再点其它封面切换选中；顶部「全选 / 取消全选」（范围 = 当前搜索+月份筛选结果）|
  | 退出 | 左上角 ✕ 或系统返回键 |
  | 📤 分享 | 特权通道把所选视频批量复制到 `/sdcard/Movies/QQShortVideo/`，再走系统多文件分享 |
  | ℹ️ 详情 | 仅选中 1 个时可用：文件名 / 哈希 / 大小 / 修改时间 / 读取方式 / 封面 / 路径 |
  | 🗑 删除 | 批量删除视频本体 + 封面 + 空目录（有二次确认，显示数量与总大小，不可恢复）|


## 目录结构

```
/sdcard/Android/data/com.tencent.mobileqq/Tencent/MobileQQ/shortvideo/
├── <32位哈希>/<32位哈希>.mp4      视频本体，每个哈希目录一个同名 mp4
├── thumbs/<32位哈希>_0.png        对应的封面图（现成，直接可用）
└── Temp/                          临时目录（通常为空）
```

要点：
- 目录名是 32 位十六进制哈希；视频文件名 = 目录名（每目录最多一个）。
- 封面 `thumbs/<哈希>_0.png`，每个哈希最多一张（`_0`）。

实测数量（本机当前状态）：

| 项目 | 数量 |
| --- | --- |
| 哈希目录 | 5340 |
| **实际视频** | **730**（726 `mp4` + 2 `MP4` + 2 `mov`）|
| 封面 `thumbs/*_0.png` | 4021 |
| 视频里有封面的 | 729 |
| 有封面但视频已被清理的 | **3292** |
| 视频没有封面的 | 1 |

**结论：必须以实际视频文件为准（730），不能以封面为准**——否则会列出 3292 个"有封面、点开没视频"的幽灵条目。封面按 `<哈希>_0.png` 关联，缺失时显示占位。

## 二、权限（重点）

该目录位于**其他应用的 `Android/data`** 下，访问受限：

| 方案 | 说明 |
| --- | --- |
| **Shizuku（推荐，免 root）** | 通过 Shizuku 服务在 shell 进程(uid 2000)执行命令。需安装并启动 Shizuku，并在应用内授权。 |
| 所有文件访问权限（MANAGE_EXTERNAL_STORAGE） | Android 11/12 通常可读；**Android 13 起系统屏蔽 `Android/data`/`obb`，即使拥有该权限也读不到**。 |
| root | 需在 KernelSU / Magisk 中把本应用加入允许列表。 |

应用内策略（`data/RootShell.kt`）：
1. 若 Shizuku 可用且已授权 → 走 **Shizuku**（`IShizukuService.newProcess`，shell 身份）；
2. 否则回退 **root**（`su -c`）；
3. 都没有时给出引导页（可一键申请 Shizuku 授权 / 打开 KernelSU）。

> 首次安装后建议先在系统设置里授予「所有文件访问权限」；若列表仍为空，
> 说明系统屏蔽了 `Android/data`，此时请在 KernelSU / Magisk 里为本应用开启 root。

## 三、代码结构

```
app/src/main/java/com/java/myapplication/
├── MainActivity.kt                 入口
├── data/
│   ├── VideoRepository.kt         扫描（File 优先 / root 兜底）+ 封面缓存
│   └── RootShell.kt               su 执行封装
└── ui/
    ├── BrowserScreen.kt           封面网格主界面 + 权限引导
    ├── VideoGrid.kt               九宫格封面
    ├── PlayerScreen.kt            全屏播放（VideoView）
    └── StorageGate.kt             「所有文件访问权限」引导页
```

- 封面：`thumbs/<hash>_0.png` → `BitmapFactory` 采样解码，内存 `LruCache` 缓存。
- 播放：能直接读时用原路径；否则 `cp` 到应用私有缓存后再用系统 `VideoView` 播放（带进度条/暂停/拖动）。

## 四、构建

```bash
chmod +x ./setup_android_env.sh   # 首次：安装 JDK / Android SDK / Gradle
./setup_android_env.sh
source ~/.bashrc
./gradlew assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk
```

## 五、安装与授权

```bash
pm install -r app-debug.apk
# 所有文件访问权限（若设备支持）
appops set com.java.myapplication MANAGE_EXTERNAL_STORAGE allow
# 或：在 KernelSU / Magisk 管理器里把本应用加入允许列表（root 方案）
```
