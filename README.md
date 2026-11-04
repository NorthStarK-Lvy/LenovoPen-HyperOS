# LenovoPen-HyperOS

为 HyperOS 平板的原生“手写笔与键盘”页面接入联想手写笔的 **Vector / Xposed 模块**。

保留原版系统组件与签名，通过运行时 Hook 显示连接状态、电量，解除“随手写”的连接限制，并提供基于 [miuix](https://github.com/YuKongA/miuix) 的详情页。

## 功能

- **原生连接状态**：根据真实蓝牙连接状态更新手写笔页面，断开后恢复未连接显示。
- **随手写**：笔已连接时解除入口置灰，同时放行随手写子页面的独立连接检查。输入法是否支持手写仍由系统判断。
- **动态笔名**：优先显示蓝牙别名，其次使用远端名称；名称更新会同步到状态卡片、详情页及后续连接通知。
- **真实电量**：读取系统已有蓝牙电量缓存；未知或断开时不显示伪造百分比。
- **miuix 详情页**：手动从原生页面打开，支持横屏双栏与系统深浅色；不接管系统导航或键盘页。
- **连接提醒**：允许通知后，重连时提示一次；电量可用时一并显示，点击打开详情。重复事件去重，断开后清除提示。

侧键单击速记、双击识屏由独立的 KSU 适配模块实现；本项目只显示功能说明，不接管按键或刷新率。

## 已验证环境

| 项目 | 环境 |
| --- | --- |
| 平板 / 笔 | Lenovo TB710FU / Tab Pen Plus |
| 系统 | HyperOS 移植环境 |
| 框架 | Vector v2.2，传统 Xposed API |
| 目标组件 | `com.miui.securitycore` / SecurityCoreAdd |
| 目标组件版本 | `4.4.4.3-4-260914.pad` |
| 参考组件 SHA256 | `529bb84e274c5772169c3af7f82bbc913ab29e29d1e400ad0fe8f863066b6aea` |

当前 Hook 依赖这个版本的类、字段和方法布局，其他版本需要重新核对。模块 APK 最低 Android 13；最低版本声明不等于所有系统组合均经过验证。

## 安装

1. 保留原版、能够正常加载的 SecurityCoreAdd.apk。
2. 安装模块 APK，在 Vector 中启用“联想手写笔适配”。
3. 作用域**只选择“系统功能组件” `com.miui.securitycore`**。
4. 重启后打开系统设置 → 手写笔与键盘 → 手写笔。
5. 需要连接提醒时，进入“手写笔详情”并允许通知，再断开、重连笔测试。

升级后需重新启动目标进程或重启。本模块没有实现 libxposed API 102 的热重载生命周期。

如果详情页显示“等待系统页面回应”，请先确认 Hook 已加载，并打开一次系统手写笔页。它表示近期未收到 Hook 端回应，不表示笔已经断开。

## 实现范围

- 原生页使用宿主 ClassLoader 的 Preference；Compose / miuix 仅在模块自己的 Activity 内运行。
- 主页面只调整 `t2.A.b(boolean)` 的页面渲染参数，不修改全局小米笔版本或主检测器的型号。
- 随手写页面只对该 Activity 创建的临时检测实例放行连接检查，条件是联想笔真实已连接；其他检测实例保持原逻辑。
- 小米专用 OTA、无线充电、捏压、滑动等功能不启用，不发送原生笔充电广播。
- 查询只读取已有蓝牙缓存，不扫描、不配对、不建立额外 GATT 连接。蓝牙长时间不更新电量缓存时，模块不会自行生成新读数。
- 后台连接提示依赖被注入的 SecurityCore 进程运行；本版没有常驻前台服务。开机前已经连接不会伪造一次重连通知。是否悬浮还受通知渠道和勿扰设置控制。
- 修改蓝牙别名只改变显示名，不改变识别用的远端设备名。完全无法取得匹配 HID 地址且远端名称也不匹配时，不会把任意蓝牙设备当作笔。

停用模块并重启可撤回界面修改，无需恢复系统分区。

## 构建

需要 **JDK 21、Android SDK Platform 37 与 Build Tools 37.0.0**。Gradle Wrapper 固定为 9.6.1，AGP 为 9.3.1。

设置 `JAVA_HOME`、`ANDROID_HOME`，或使用本机 `local.properties` 配置 SDK。首次构建需能访问 Google Maven、Maven Central 和 Xposed API 仓库。

```sh
./gradlew :app:assembleRelease :app:penPolicyTest :app:lintRelease
```

Windows 可使用 `gradlew.bat`，或把新缓存与临时文件放在项目内：

```powershell
.\build.ps1 -Online
```

构建结果为 `app/build/outputs/apk/release/app-release-unsigned.apk`。这是未签名构建，需要使用自己的模块证书签名后安装。

Windows 辅助脚本 `package.ps1` 会在本机 `build-cache/signing/` 创建普通测试证书，同时保留未签名与已签名 APK。该私钥不属于源码，也不会提交；发布升级包时应保留自己的证书。脚本不会重签系统 APK。

可选的 `local-env.ps1` 可设置 `JAVA_HOME`、`ANDROID_HOME`、`GRADLE_BIN` 或只读依赖缓存 `GRADLE_RO_DEP_CACHE`。该文件被 Git 忽略，不要把本机配置提交到仓库。

## 验证

- 编译、Android lint、6 项 JVM 状态逻辑测试已通过。
- 独立 miuix 页面完成设备启动检查；原生连接页面的 Hook 效果由设备使用者验证正常。
- 建议继续测试蓝牙改名、重连、通知权限、进入随手写、旋转、切换前后台，以及停用模块后的原生页面恢复。

测试：`./gradlew :app:penPolicyTest`。问题反馈请说明 ROM、组件版本、框架版本和操作顺序。提交日志前自行清除账号、蓝牙地址、设备标识和个人内容。

## 开源许可

本项目自身源码采用 [MIT](LICENSE) 许可。第三方组件保留原许可，见 [NOTICE.md](NOTICE.md)。

源码仓库不包含系统 APK、固件、反编译产物、签名私钥或设备日志。
