# Zrmt

ZCode 移动远程控制链接管理器（Android 原生应用）。

- 包名：`com.sundys.zrmt`
- 首页卡式管理：每张卡片对应一台电脑的远程访问链接
- 粘贴链接自动解析并展示 **电脑名称**（`name` 参数）与 **ZCode 版本号**（`app_version` 参数）
- 点击卡片在 APP 内通过 WebView 打开 zcode 操作界面（支持旋转、桌面/手机版切换、错误重试）
- 支持从其他应用"分享链接"到 Zrmt 快速添加
- 纯 Android 原生框架实现，**零第三方依赖**（无 androidx / Compose / 网络·存储库）
- 仅打包 `armeabi-v7a` 与 `arm64-v8a` 两种 ABI

## 链接格式

```
https://zcode.z.ai/remote/v4?sid=...&hash=...&t=...&mid=...&name=电脑名&app_version=版本号
```

链接须包含 `sid` 与 `hash` 参数方可保存（`name` 缺省显示"未命名电脑"，`app_version` 缺省不显示版本徽章）。

## 构建

要求：JDK 17、Android SDK（compileSdk 34）、Gradle 8.7。

```bash
./gradlew assembleRelease          # 生成双 ABI 拆分的签名 APK
# 输出：
# app/build/outputs/apk/release/app-armeabi-v7a-release.apk
# app/build/outputs/apk/release/app-arm64-v8a-release.apk
```

签名文件：`app/keystore/zrmt.keystore`（口令见 `app/build.gradle`，生产环境请更换为自己的密钥）。

### CI 自动编译（GitHub Actions）

`.github/workflows/android.yml`：push 到 `main`/`master` 或 PR 自动编译双 ABI 签名 APK；推送 `v*` 标签时自动构建并创建 Release 上传 APK。

签名通过以下 **Actions Secrets** 注入（环境变量），密钥文件不入库：

| Secret | 说明 |
|---|---|
| `ZRMT_KEYSTORE_BASE64` | `zrmt.keystore` 的 base64 内容（`base64 -w0 app/keystore/zrmt.keystore`） |
| `ZRMT_KEYSTORE_PASSWORD` | keystore 口令 |
| `ZRMT_KEY_ALIAS` | key 别名（`zrmt`） |
| `ZRMT_KEY_PASSWORD` | key 口令 |

本地构建同样支持同名环境变量（`ZRMT_KEYSTORE_FILE` / `ZRMT_KEYSTORE_PASSWORD` / `ZRMT_KEY_ALIAS` / `ZRMT_KEY_PASSWORD`）；未设置时回退到本机 `app/keystore/zrmt.keystore` 与默认口令。

## 安装到真机

```bash
adb install -r app/build/outputs/apk/release/app-arm64-v8a-release.apk
```

## 工程结构

```
app/src/main/java/com/sundys/zrmt/
├── MainActivity.kt      首页：卡片列表（点按打开 / 长按菜单：打开·编辑·复制·删除）
├── EditLinkActivity.kt  添加/编辑：粘贴即解析，实时预览，支持 ACTION_SEND 分享接入
├── RemoteActivity.kt    WebView 操作界面：视口适配、权限透传、错误重试、UA 切换
├── Link.kt              数据模型 + URL 解析（android.net.Uri）
└── LinkStore.kt         SharedPreferences + org.json 持久化
```
