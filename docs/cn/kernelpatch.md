# 内核侧：KernelPatch-Aster

内核侧来自 [lyravoid/KernelPatch-Aster](https://github.com/lyravoid/KernelPatch-Aster)，它是
**上游 `bmax121/KernelPatch` 加一个提交**：让内核信任本管理器，并接受本管理器发布包的签名形状。
除了这两处，不带任何其他内核改动（FolkPatch 的那些内核扩展有意不进来）。

## 一个提交里改了什么

- `kernel/patch/android/userd.c` 与 `lkm/manager/apk_sign.c` 的可信管理器表：只留
  `me.yuki.aster` 与 Aster 发布证书的 SHA-256（上游原本是 `me.bmax.apatch` 与 demo 包）。
  内核会把 `/data/app` 里第一个匹配到的 `base.apk` 立为管理器，多留一项就会让装了 APatch
  的机器被 APatch 抢先、Aster 拿不到 root。
- 同一对文件里的签名方案判定：v2 有效时，旁边还有 v3/v3.1 不再取消资格（Aster 发的是
  v2+v3，v3 是将来轮换密钥要用的）；v1 仍然拒绝。信任的判据始终是 **v2 块里的证书摘要**。

## 仓库里的位置与更新

- 工作副本：`KernelPatch/`（仓库根目录下、由 `.gitignore` 排除，不进本仓库历史）。
  拿到的办法：`git clone https://github.com/lyravoid/KernelPatch-Aster.git KernelPatch`。
- 改内核侧：在 `KernelPatch/` 里改 → 提交并推到该仓库 → 其 CI（`build.yml`）自动构建并
  把 `kpimg-android`、`kptools-android` 与 7 个 `<kmi>_kernelpatch.ko` 发到以 `version`
  文件命名的 release（当前 `0.13.9`）。
- 跟随上游：`git -C KernelPatch remote add up https://github.com/bmax121/KernelPatch.git`（只配
  一次），然后 `git -C KernelPatch fetch up && git -C KernelPatch rebase up/main` 把那一个提交
  挪到新的上游之上。**改完必须更新 `version`**，否则新产物会覆盖旧 tag 的同名 release 资产，
  而管理器那边仍以为在与旧版本对话。

## 管理器这边怎么接

- `app/build.gradle.kts` 的三个下载任务从上面那个 release 取 `kpimg`、`kptools` 与各 KMI 的
  `kernelpatch.ko`，版本号读自 `app/src/main/cpp/version`——**它必须与 `KernelPatch/version`
  一致**，两边不同就会出现"补丁组件与当前管理器版本不匹配"。
- `0.10.7` 的 `kpatch` 兼容件仍取上游仓库：那是给更老补丁用的固定历史二进制。
- 管理器自身的 root 走签名授权：内核比对管理器 APK 的证书摘要，所以**发布签名必须一直是同一把
  密钥**，换钥等于换应用（用户要重装、已打补丁的设备要重打补丁）。

## 超调用 ABI 校验

`app/src/main/cpp/{supercall.h,uapi/scdefs.h}` 与 `apd/src/supercall.rs` 各存了一份超调用号与
结构体（都无法直接 include 另一个仓库的头）。漂移不会让构建失败，只会在真机上炸，所以有：

```bash
python3 scripts/check_kp_abi.py              # 优先用本仓库旁的 KernelPatch/，核对版本后逐项比对
python3 scripts/check_kp_abi.py --kp <路径>   # 指定别的检出
```

CI 每次构建都跑一次（无本地树时按 `version` 从 GitHub 拉对应 tag 的头文件）。它检查：每个号
必须与内核一致、被调用的号必须存在、两份副本之间同名号必须一致、镜像结构体的字段序列必须是内核
字段序列的前缀（布局取决于顺序，尾部追加安全，中间插入/改名/换序会报错）。
