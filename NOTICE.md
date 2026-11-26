# 第三方许可

RELEGION 自有源码按 [MIT](LICENSE) 发布。依赖及衍生代码沿用各自的许可证。

## 互联适配

`com.alyssum.relegion.hyperconnect` 由 HyperConnectToolkit v1.6.0 适配而来。主要改动为 RELEGION 命名空间、libxposed API 102 接入、按配置核对进程状态、部分安装回退、日志脱敏，以及独立的 miuix 界面。该部分仍按 Apache-2.0 分发。 [HyperConnectToolkit](https://github.com/silverpoetry/HyperConnectToolkit/tree/v1.6.0) · [Apache-2.0 全文](app/src/main/assets/licenses/HyperConnectToolkit-Apache-2.0.txt)

## 构建依赖与设计参考

- 界面使用 miuix、Compose 与 AndroidX。 [miuix](https://github.com/YuKongA/miuix) · [AndroidX](https://github.com/androidx/androidx)
- Kotlin 与协程由 JetBrains 开发。 [Kotlin](https://github.com/JetBrains/kotlin) · [kotlinx.coroutines](https://github.com/Kotlin/kotlinx.coroutines)
- Xposed 集成使用 libxposed API 和 Service API。 [libxposed API](https://github.com/libxposed/api) · [libxposed Service](https://github.com/libxposed/service)
- 蓝牙固件传输使用 Nordic Android DFU 库，采用 BSD-3-Clause 许可。 [Nordic Android DFU](https://github.com/NordicSemiconductor/Android-DFU-Library) · [许可证全文](app/src/main/assets/licenses/Nordic-Android-DFU-BSD-3-Clause.txt)
- 折叠侧栏和关于页参考 KernelSU 与 HyperCeiler 的布局。项目的 Compose 页面、动态背景和图标为独立实现。 [KernelSU](https://github.com/tiann/KernelSU) · [HyperCeiler](https://github.com/ReChronoRain/HyperCeiler)
