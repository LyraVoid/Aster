<div align="center">
<img src='logo.png' width='180px' alt="Aster logo">

[![Latest Release](https://img.shields.io/github/v/release/LyraVoid/Aster?label=Release&logo=github)](https://github.com/LyraVoid/Aster/releases/latest)
[![Channel](https://img.shields.io/badge/Follow-Telegram-blue.svg?logo=telegram)](https://t.me/FolkPatch)
[![GitHub License](https://img.shields.io/github/license/LyraVoid/Aster?logo=gnu)](/LICENSE)

</div>

**Language:** [English](./README.md) / [中文](./README_CN.md) / [日本語](./README_JA.md)

Aster - A Miuix single-UI Root control surface for the APatch capability chain

Built on [KernelPatch](https://github.com/bmax121/KernelPatch), Aster brings Root state, kernel modules (KPM), system modules (APM) and superuser management into one coherent Miuix interface. Home answers "is Root trustworthy right now" first, instead of being a system performance dashboard.

[Read Full Documentation](https://aster.mysqil.com/)

<table>
  <tr>
    <td><img alt="" src="docs/screenshots/1.png"></td>
    <td><img alt="" src="docs/screenshots/2.png"></td>
    <td><img alt="" src="docs/screenshots/3.png"></td>
  </tr>
  <tr>
    <td><img alt="" src="docs/screenshots/4.png"></td>
    <td><img alt="" src="docs/screenshots/5.png"></td>
    <td><img alt="" src="docs/screenshots/6.png"></td>
  </tr>
</table>

---

## Introduction

### Core

- A Root implementation built on KernelPatch
- Hook kernel functions without recompiling the kernel

### Requirements

- **Required:** an ARM64 Android device
- Android kernel 3.18 - 6.18
- Kernel config `CONFIG_KALLSYMS=y`, with `CONFIG_KALLSYMS_ALL=y` recommended as well

### Interface

- A single Miuix UI shell: five entries live in a permanent left rail on phones, and the first level of navigation depends on neither a floating bottom bar nor a pager
- A first-level entry never disappears because of Root state; when it is unavailable it is dimmed and says why, with a way back
- The foot of the rail states the running mode directly: `Full APatch` / `KernelPatch-only` / `Jailbreak` / `Unavailable`
- A panoramic Home where the wallpaper is only an ambient layer, off by default and never at the cost of readable state or dangerous actions
- App colours can follow the wallpaper, the system Material You palette, or a preset
- Custom fonts and app display size
- The desktop icon can switch between the Aster and APatch marks, and choose whether it follows the app's colours

### Modules

- **APM**: a Magisk-like module system with batch install and full backup
- **KPM**: kernel modules (`inline-hook` and `syscall-table-hook`), with automatic loading
- A module repository for browsing and installing popular APM / KPM modules
- WebUI support for modules
- Magic mount

### Protected Runtime Operations

Operations that change system properties do not take effect directly. They run inside a session that can be rolled back:

- A pre-check first shows the **actual property changes**, or the difference between the source and target files
- Every change runs as a **60-second trial** before it can be kept
- A failed apply, an expired trial, or a detected safe mode triggers an **automatic recovery attempt**
- Keeping a change is valid only for the **current boot ID**; after a reboot it is off again
- A failed recovery reports the error and keeps the recovery record rather than claiming success

State hiding touches only the four `ro.boot.*` properties that actually need to change. It does not re-lock the bootloader, does not modify ADB, debugging, logging or `persist.*` configuration, and does not promise to defeat hardware attestation. See [Protected Runtime Operations](docs/cn/runtime-safety.md) (Chinese).

### Technical

- Built on [KernelPatch](https://github.com/bmax121/KernelPatch)
- The UI and the APModule source are derived from [KernelSU](https://github.com/tiann/KernelSU)

## Security Alert

The **SuperKey** has higher privileges than root access. Weak or compromised keys can lead to unauthorized control of your device. It is critical to use robust keys and safeguard them from exposure.

## Translation

Translations are managed by LLM. Chinese and English are the reference languages and do not accept PR corrections. If you want to contribute a new language or improve an existing translation, please open a PR with the specific language only.

## Download and Install

1. **Download:**
   Get the latest package from the [Releases page](https://github.com/LyraVoid/Aster/releases/latest)

2. **Install:**
   Install the package on your Android device and follow the in-app guide to deploy

3. **Get started:**
   Read the [full documentation](https://aster.mysqil.com/), or browse the [in-repo documents](docs/)

## Credits

This project is based on the following open-source projects:

- [KernelPatch](https://github.com/bmax121/KernelPatch) - the core
- [Magisk](https://github.com/topjohnwu/Magisk) - magiskpolicy
- [KernelSU](https://github.com/tiann/KernelSU) - app UI and Magisk-like module support
- [APatch](https://github.com/bmax121/APatch) - the upstream branch
- [Miuix](https://github.com/miuix-kotlin-multiplatform/miuix) - the interface component library

## License

- Aster is licensed under the [GNU General Public License v3 (GPL-3)](http://www.gnu.org/copyleft/gpl.html). As a modifier or distributor, you must meet the following:
- If you modify the code, or integrate Aster into a project and distribute it, your whole project must be released under the same GPLv3 licence
- When distributing binaries you must offer, or promise to offer, complete and readable source code
- You may not charge a licence fee for the software itself; you may charge for distribution, support or custom development
- Distribution grants every user a licence to any of your patents that the project involves
- The software is provided "as is", without warranty of any kind; the authors are not liable for any loss caused by its use
- Violating any of the above terminates your GPLv3 grant automatically, and with it your right to distribute Aster

## Community

- Telegram channel: [**@FolkPatch**](https://t.me/FolkPatch)
