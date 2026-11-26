<h1 align="center">RELEGION</h1>
<p align="center"><strong>我[新]澎湃</strong></p>
<p align="center">联想平板的 HyperOS 适配模块 · Vector / libxposed API 102</p>

RELEGION 把适配联想硬件的几个入口放在一起。连接手写笔后，可以直接在小米设置里查看连接状态，也可以在模块里配置侧键、查看电量，或调整互联和游戏高刷。

## 功能

| 页面 | 可以做什么 |
| --- | --- |
| 手写笔 | 显示蓝牙名称、连接状态、电量与设备信息。支持连接提醒、随手写入口以及单击、双击和笔记应用优先设置。 |
| 互联 | 配置适用于本机的通知、双通道及应用流转选项，查看目标进程的启用状态。 |
| 游戏高刷 | 控制 Android 游戏默认帧率和 PowerKeeper 高刷适配。实际游戏帧率仍由游戏、系统策略和温控共同决定。 |
| 关于 | 在模块里设定小米「关于平板」显示的机型名称。 |

侧键动作包含小米创作速记、超级小爱识屏、截屏、打开应用和短视频控制。侧键服务需要配套的原生桥接资源和 root 权限。蓝牙电量、宿主接口和动作支持因笔型号与系统版本而异。

## Vector 作用域

构建并安装 APK 后，在支持 libxposed API 102 的 Vector 中启用模块，按所需功能勾选作用域。加载作用域后重启相应宿主进程，涉及系统框架时重启设备。

| 用途 | 作用域 |
| --- | --- |
| 小米手写笔设置 | `com.miui.securitycore` |
| 关于平板机型 | `com.android.settings` |
| 小米互联、妙享 | `com.milink.service`、`com.xiaomi.mirror` |
| 游戏高刷及系统层笔按键适配 | 系统框架、`com.miui.powerkeeper` |

## 从源码构建

使用 JDK 21、Gradle 9.6.1 和 Android SDK Platform 37。通过 `ANDROID_HOME` 指定 SDK 目录，或使用 Android Studio 的 SDK 配置。首次构建需要解析 Android Gradle 插件和 Maven 依赖。

<details>
<summary>准备手写笔图标资源</summary>

在本地提供有使用许可的 `pen_picasso`、`pen_parker`、`pen_omas`、`pen_sheaffer`，也可以将下面的资源别名保存到 `app/src/main/res/values/relegion-local-pen-assets.xml`，用项目自带的笔图标完成编译。

```xml
<resources>
    <item name="pen_picasso" type="drawable">@drawable/ic_pen</item>
    <item name="pen_parker" type="drawable">@drawable/ic_pen</item>
    <item name="pen_omas" type="drawable">@drawable/ic_pen</item>
    <item name="pen_sheaffer" type="drawable">@drawable/ic_pen</item>
</resources>
```

</details>

```sh
gradle :app:assembleRelease :app:penPolicyTest :app:lintRelease
```

Release APK 输出到 `app/build/outputs/apk/release/`，使用你自己的证书签名后安装。

## 许可

项目自有源码使用 MIT。互联适配衍生自 HyperConnectToolkit，保留 Apache-2.0 许可和署名。 [MIT](LICENSE) · [第三方许可](NOTICE.md)
