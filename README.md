# 浮光 · Android 屏幕助手

针对 Android 10+ 的原生 Java 应用。点击可拖动的悬浮球，预览当前屏幕截图，向 DeepSeek 提问，在悬浮面板中阅读、复制回答和追问。默认模型 `deepseek-flash`，接口固定为官方 HTTPS 地址。

## 安装与使用

1. 将交付的 `浮光-0.1.2-debug.apk` 发送到手机并安装。若 ColorOS 提示，请仅为本次使用的文件管理器或浏览器允许安装该 APK。
2. 打开“浮光”，填写自己的 DeepSeek API Key，保存。不要把完整密钥发送给别人。API 调用使用你的开放平台账户额度。
3. 点击“① 允许显示悬浮窗”，在系统页面允许浮光悬浮显示，再返回。
4. 点击“② 启动浮光”，按需允许通知，然后在系统屏幕共享弹窗中选择共享整个屏幕。应用自动退到后台。
5. 在其他 App 中点击蓝色悬浮球。确认预览画面，输入问题并点击发送。
6. 在面板里继续追问；追问保留本轮截图与对话，最多六次。点击“换一张屏幕”清空本轮并重新截屏。
7. “收起”返回悬浮球，再点击可展开本次对话。长按悬浮球、点击常驻通知中的“停止助手”，或返回首页点“停止助手”，即可结束屏幕共享。

## OPPO Reno12 / ColorOS

系统设置的名称随版本不同：可在设置中搜索“悬浮窗”，找到浮光并允许。如果切换 App 后悬浮球消失，检查“应用管理 → 浮光 → 电池使用/耗电管理”中的后台活动设置，必要时在最近任务中锁定应用。无需开启无障碍、通讯录、麦克风或照片库权限。

锁屏、系统终止共享、旋转屏幕或进程被清理后，请回到首页重新启动。Android 新版的每次新屏幕共享会话均需要系统授权。受保护的银行、密码、版权视频等页面可能显示黑屏。请在发送前检查截图。

## 当前范围与限制

- 这是可安装的首版测试 APK，使用调试签名，未上架应用商店。
- 实现截图理解与问答；没有接入网页搜索工具，不承诺实时联网检索或提供检索来源。
- 使用系统 MediaProjection 与悬浮窗；共享会话存在期间安卓会显示屏幕共享指示和前台服务通知。只有点击悬浮球才将画面编码为待发送截图，点击发送后才上传。
- 截图与对话在内存中处理，不写入相册。新截图或结束服务时清空对话。截图上传给 DeepSeek 后，其服务端处理遵循平台规则。
- 密钥通过 Android Keystore 的 AES-GCM 加密保存在应用私有偏好设置中，不嵌入安装包、不写入日志，排除密钥备份及设备迁移。卸载会清除本机设置。
- 当前采用非流式回答，等待完成后展示；未实现框选区域、语音提问、历史记录或 OCR 本地模式。
- 已完成编译、十一项单元测试及 Android Lint；尚未在 Reno12 真机或模拟器上运行，也未用真实 API Key 做端到端测试。悬浮窗与软键盘行为、系统授权、后台保活及网络返回仍需真机验证。

## 构建

当前电脑的 SDK、Gradle、依赖缓存位于 `D:\CodexAndroidToolchain`，复用 `D:\utilities\java\windows-x64\zulu-jdk17.0.7`。

```powershell
powershell -ExecutionPolicy Bypass -File .\build-local.ps1
```

该脚本不下载新工具，不修改系统环境变量；环境设置仅影响当前脚本进程。其他电脑可安装 Android SDK 35、Build Tools 34.0.0、Java 17 和 Gradle 8.9，设置工具位置后运行 `gradle testDebugUnitTest assembleDebug lintDebug`。已附 Gradle Wrapper；在 D 盘设置 `GRADLE_USER_HOME` 后也可使用 `gradlew.bat`。

GitHub Actions 配置位于 `.github/workflows/android.yml`。推送代码后可在仓库的 Actions 页面查看构建，完成后从本次运行的 Artifacts 下载 `Fuguang-debug`，解压得到 APK。请勿提交自己的 API Key 或签名私钥。

云端构建默认使用临时调试签名，因此不能保证直接覆盖安装本机交付的 APK，也不能保证不同云端运行之间的安装包可互相覆盖。需要长期分发更新时，应单独配置固定签名并通过 GitHub Secrets 管理，不要把签名私钥写进仓库。

## 文件结构

- `MainActivity.java`：连接设置、悬浮窗权限、系统屏幕共享授权。
- `ScreenService.java`：前台服务、悬浮球、截图、预览及对话面板。
- `Payload.java` / `DeepSeek.java`：JSON 生成、响应校验、HTTPS 调用。
- `Secrets.java`：Android Keystore 密钥加密。
- `PayloadTest.java`：特殊字符、图片结构、追问历史及错误响应测试。

## 参考

- [DeepSeek 图片输入文档](https://api-docs.deepseek.com/guides/vision/)
- [Android 屏幕投影](https://developer.android.com/media/grow/media-projection)
- [Figma 可编辑界面稿](https://www.figma.com/design/ppYLgoasiA818WwPEGakBe)

Figma 为首版视觉规格，使用 Material 组件与可编辑文本；界面预览图中的回答和背景是示意，并非真机截图。
