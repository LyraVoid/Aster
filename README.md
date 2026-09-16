<div align="center">
<img src='logo.png' width='180px' alt="Aster logo">

[![Latest Release](https://img.shields.io/github/v/release/LyraVoid/Aster?label=Release&logo=github)](https://github.com/LyraVoid/Aster/releases/latest)
[![Channel](https://img.shields.io/badge/Follow-Telegram-blue.svg?logo=telegram)](https://t.me/FolkPatch)
[![GitHub License](https://img.shields.io/github/license/LyraVoid/Aster?logo=gnu)](/LICENSE)

</div>

🌏 **README 语言:** [**English**](./README_EN.md) / [**中文**](./README.md) / [**日本語**](./README_JA.md)

Aster - 面向 APatch 能力链的 Miuix 单 UI Root 控制面

Aster 基于 [KernelPatch](https://github.com/bmax121/KernelPatch) 打造，把 Root 状态、内核模块（KPM）、系统模块（APM）与超级用户管理收进一套连贯的 Miuix 界面里。首页优先回答「当前 Root 是否可信」，而不是堆一张系统性能仪表盘。

[📚 阅读完整文档](https://aster.mysqil.com/) →

<!-- TODO: 截图表格，放 6 张到 docs/screenshots/1.png … 6.png。现在没有截图，先注释掉。
<table>
  <tr>
    <td><img alt="" src="docs/screenshots/1.png"></td>
    <td><img alt="" src="docs/screenshots/2.png"></td>
    <td><img alt="" src="docs/screenshots/3.png"></td>
  <tr>
  <tr>
    <td><img alt="" src="docs/screenshots/4.png"></td>
    <td><img alt="" src="docs/screenshots/5.png"></td>
    <td><img alt="" src="docs/screenshots/6.png"></td>
  <tr>
</table>
-->

---

## ✨ 介绍

### 🎨 核心功能

- [x] 基于 KernelPatch 的 Root 实现
- [x] 无需重新编译内核即可 Hook 内核函数

### 📱 前置要求

- **必须：** ARM64 架构的 Android 设备
- Android 内核 3.18 - 6.18
- 内核配置 `CONFIG_KALLSYMS=y`，并建议同时开启 `CONFIG_KALLSYMS_ALL=y`

### 🖥️ 界面与交互

- [x] Miuix 单 UI 壳：手机端五入口常驻左轨，一级导航不依赖悬浮底栏或 Pager
- [x] 一级入口不因 Root 状态消失；不可用时置灰，并给出原因与恢复入口
- [x] 左轨底部直接表达运行模式语义：`Full APatch` / `KernelPatch-only` / `Jailbreak` / `Unavailable`
- [x] 全景首页：壁纸只作为环境层，默认关闭，开启后仍保证状态与危险操作可读
- [x] 应用配色可跟随壁纸取色、系统 Material You 或预设色
- [x] 自定义字体与应用显示大小
- [x] 桌面图标可在 Aster 与 APatch 两种标记间切换，并选择是否跟随应用配色

### 📦 模块与扩展体系

- [x] **APM**：类 Magisk 模块系统，支持批量刷入与全量备份
- [x] **KPM**：内核模块系统（支持 `inline-hook` 与 `syscall-table-hook`），支持自动加载
- [x] 模块仓库：浏览并一键安装热门 APM / KPM
- [x] 模块 WebUI 支持
- [x] Magic mount

### 🛡️ 受保护的运行时操作

会改动系统属性的操作不直接生效，而是放进一个可回退的会话里：

- 预检先展示**实际的属性变化**，或源文件与目标文件的差别
- 正式保留前先做 **60 秒试运行**
- 应用失败、试运行到期或检测到安全模式时**自动尝试恢复**
- 保留只对**当前 boot ID** 有效，重启后保持关闭
- 恢复失败会明确报错并保留恢复记录，不会谎报已经恢复

状态隐藏只处理确实需要变化的四项 `ro.boot.*` 属性，不会实际重新锁定 Bootloader，不修改 ADB、调试、日志或 `persist.*` 配置，也不承诺绕过硬件认证。详见[受保护的运行时操作](docs/cn/runtime-safety.md)。

### ⚡ 技术特性

- [x] 基于 [KernelPatch](https://github.com/bmax121/KernelPatch)
- [x] 应用 UI 与 APModule 源码派生自 [KernelSU](https://github.com/tiann/KernelSU)

## 🔐 安全提示

**SuperKey 的权限高于 root。** 弱密钥或已泄露的密钥会导致设备被未授权控制。请务必使用强密钥，并妥善保管、避免外泄。

## 🌏 翻译

翻译由 LLM 维护。中文与英文是参考语言，不接受对这两种语言的 PR 修正。如果你想新增语言或改进已有翻译，请只针对该语言提交 PR。

## 🚀 下载安装

1. **下载：**
   从[发布页面](https://github.com/LyraVoid/Aster/releases/latest)获取最新版安装包

2. **安装：**
   将安装包安装到你的 Android 设备，按应用内引导完成部署

3. **开始使用：**
   阅读[完整文档](https://aster.mysqil.com/)，也可直接查看[仓库内文档](docs/)

## 🙏 开源致谢

本项目基于以下开源项目：

- [KernelPatch](https://github.com/bmax121/KernelPatch) - 核心组件
- [Magisk](https://github.com/topjohnwu/Magisk) - magiskpolicy
- [KernelSU](https://github.com/tiann/KernelSU) - 应用 UI 和类似 Magisk 的模块支持
- [APatch](https://github.com/bmax121/APatch) - 上游分支
- [Miuix](https://github.com/miuix-kotlin-multiplatform/miuix) - 界面组件库

## 📄 许可证

- Aster 遵循 [GNU General Public License v3 (GPL-3)](http://www.gnu.org/copyleft/gpl.html) 许可证开源。作为二改者或分发者，您需遵守以下标准：
- 若您修改了代码或在项目中集成了 Aster 并向第三方分发，您的整个项目必须同样采用 GPLv3 协议开源
- 分发二进制文件时，必须主动提供或承诺提供完整且可读的源代码
- 严禁对软件授权本身收取许可费；您可以针对分发、技术支持或定制开发收费
- 分发行为即代表您授予所有用户使用该项目涉及的您的相关专利
- 本软件“按原样”提供，不含任何担保，原作者不对因使用本软件造成的任何损失负责
- 任何违反上述条款的行为将导致您的 GPLv3 授权自动终止，届时您将失去分发 Aster 的合法权利

## 💬 社区交流

- Telegram 频道：[**@FolkPatch**](https://t.me/FolkPatch)
