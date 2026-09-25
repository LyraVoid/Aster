package me.bmax.apatch.ui.home

import me.bmax.apatch.root.RootAccessProbeState
import me.bmax.apatch.root.RootAttention
import me.bmax.apatch.root.RootCapabilitySnapshot
import me.bmax.apatch.root.RootCheckPhase
import me.bmax.apatch.root.RootLayerState
import me.bmax.apatch.root.RootMode
import me.bmax.apatch.util.LatestVersionInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeStateMapperTest {
    @Test
    fun `checking never resolves to not installed`() {
        val state = HomeStateMapper.map(
            capability = RootCapabilitySnapshot(
                phase = RootCheckPhase.CHECKING,
                kernelPatch = RootLayerState.UNKNOWN,
            ),
            environment = null,
            showBackupWarning = false,
            update = HomeUpdateState.Idle,
        )

        assertEquals(HomeConclusion.CHECKING, state.conclusion)
        assertEquals(HomePrimaryAction.NONE, state.primaryAction)
        assertEquals(HomeDeviceDensity.DIAGNOSTIC, state.deviceDensity)
    }

    @Test
    fun `full apatch is quiet and uses compact device identity`() {
        val state = HomeStateMapper.map(
            capability = readyCapability(
                kernelPatch = RootLayerState.AVAILABLE,
                androidPatch = RootLayerState.AVAILABLE,
                mode = RootMode.FULL_APATCH,
            ),
            environment = environment(),
            showBackupWarning = false,
            update = HomeUpdateState.UpToDate,
        )

        assertEquals(HomeConclusion.FULL_APATCH, state.conclusion)
        assertEquals(HomePrimaryAction.NONE, state.primaryAction)
        assertEquals(HomeDeviceDensity.COMPACT, state.deviceDensity)
    }

    @Test
    fun `need reboot wins over update`() {
        val capability = readyCapability(
            kernelPatch = RootLayerState.NEED_REBOOT,
            androidPatch = RootLayerState.NEED_UPDATE,
            mode = RootMode.FULL_APATCH,
        ).copy(
            attention = setOf(RootAttention.NEED_REBOOT, RootAttention.NEED_UPDATE),
        )

        val state = HomeStateMapper.map(
            capability = capability,
            environment = environment(),
            showBackupWarning = false,
            update = HomeUpdateState.Idle,
        )

        assertEquals(HomeConclusion.NEED_REBOOT, state.conclusion)
        assertEquals(HomePrimaryAction.REBOOT, state.primaryAction)
        assertEquals(HomeDeviceDensity.DIAGNOSTIC, state.deviceDensity)
    }

    @Test
    fun `kernel patch only offers apatch installation`() {
        val state = HomeStateMapper.map(
            capability = readyCapability(
                kernelPatch = RootLayerState.AVAILABLE,
                androidPatch = RootLayerState.UNAVAILABLE,
                mode = RootMode.KERNEL_PATCH_ONLY,
            ).copy(attention = setOf(RootAttention.NEED_APATCH_INSTALL)),
            environment = environment(),
            showBackupWarning = false,
            update = HomeUpdateState.Idle,
        )

        assertEquals(HomeConclusion.KERNEL_PATCH_ONLY, state.conclusion)
        assertEquals(HomePrimaryAction.INSTALL_APATCH, state.primaryAction)
    }

    @Test
    fun `unreadable android patch keeps the apatch installation entry`() {
        // The attention flag is absent here, so only the layer state can offer the entry. Without
        // it the navigation hides the module page and no button is left to install the patch.
        val state = HomeStateMapper.map(
            capability = readyCapability(
                kernelPatch = RootLayerState.AVAILABLE,
                androidPatch = RootLayerState.UNKNOWN,
                mode = RootMode.KERNEL_PATCH_ONLY,
            ),
            environment = environment(),
            showBackupWarning = false,
            update = HomeUpdateState.Idle,
        )

        assertEquals(HomeConclusion.KERNEL_PATCH_ONLY, state.conclusion)
        assertEquals(HomePrimaryAction.INSTALL_APATCH, state.primaryAction)
    }

    @Test
    fun `failed android patch read keeps the apatch installation entry`() {
        val state = HomeStateMapper.map(
            capability = readyCapability(
                kernelPatch = RootLayerState.AVAILABLE,
                androidPatch = RootLayerState.ERROR,
                mode = RootMode.KERNEL_PATCH_ONLY,
            ),
            environment = environment(),
            showBackupWarning = false,
            update = HomeUpdateState.Idle,
        )

        assertEquals(HomePrimaryAction.INSTALL_APATCH, state.primaryAction)
    }

    @Test
    fun `a full installation offers no installation entry`() {
        val state = HomeStateMapper.map(
            capability = readyCapability(
                kernelPatch = RootLayerState.AVAILABLE,
                androidPatch = RootLayerState.AVAILABLE,
                mode = RootMode.FULL_APATCH,
            ),
            environment = environment(),
            showBackupWarning = false,
            update = HomeUpdateState.UpToDate,
        )

        assertEquals(HomePrimaryAction.NONE, state.primaryAction)
    }

    @Test
    fun `uninstall entry disappears without a root session`() {
        val state = HomeStateMapper.map(
            capability = readyCapability(
                kernelPatch = RootLayerState.AVAILABLE,
                androidPatch = RootLayerState.AVAILABLE,
                mode = RootMode.FULL_APATCH,
            ).copy(rootAccess = RootAccessProbeState.UNAVAILABLE),
            environment = environment(),
            showBackupWarning = false,
            update = HomeUpdateState.Idle,
        )

        assertFalse(state.canUninstallAnything())
    }

    @Test
    fun `uninstall entry disappears when no layer is installed`() {
        val state = HomeStateMapper.map(
            capability = readyCapability(
                kernelPatch = RootLayerState.UNAVAILABLE,
                androidPatch = RootLayerState.UNAVAILABLE,
                mode = RootMode.NONE,
            ),
            environment = environment(),
            showBackupWarning = false,
            update = HomeUpdateState.Idle,
        )

        assertFalse(state.canUninstallAnything())
    }

    @Test
    fun `uninstall entry survives as long as one layer can come off`() {
        val kernelOnly = HomeStateMapper.map(
            capability = readyCapability(
                kernelPatch = RootLayerState.AVAILABLE,
                androidPatch = RootLayerState.UNAVAILABLE,
                mode = RootMode.KERNEL_PATCH_ONLY,
            ),
            environment = environment(),
            showBackupWarning = false,
            update = HomeUpdateState.Idle,
        )
        val androidOnly = HomeStateMapper.map(
            capability = readyCapability(
                kernelPatch = RootLayerState.NEED_REBOOT,
                androidPatch = RootLayerState.AVAILABLE,
                mode = RootMode.FULL_APATCH,
            ),
            environment = environment(),
            showBackupWarning = false,
            update = HomeUpdateState.Idle,
        )

        assertTrue(kernelOnly.canUninstallAnything())
        assertTrue(androidOnly.canUninstallAnything())
    }

    @Test
    fun `root access error offers retry before install`() {
        val state = HomeStateMapper.map(
            capability = readyCapability(
                kernelPatch = RootLayerState.AVAILABLE,
                androidPatch = RootLayerState.UNKNOWN,
                mode = RootMode.KERNEL_PATCH_ONLY,
            ).copy(
                rootAccess = RootAccessProbeState.ERROR,
                attention = setOf(RootAttention.CHECK_FAILED, RootAttention.NEED_INSTALL),
            ),
            environment = environment(),
            showBackupWarning = false,
            update = HomeUpdateState.Idle,
        )

        assertEquals(HomeConclusion.CHECK_FAILED, state.conclusion)
        assertEquals(HomePrimaryAction.RETRY_CHECK, state.primaryAction)
    }

    @Test
    fun `update mapping distinguishes failure from up to date`() {
        assertEquals(
            HomeUpdateState.Failed,
            HomeStateMapper.resolveUpdateState(
                enabled = true,
                currentVersionCode = 100L,
                latest = LatestVersionInfo(),
            ),
        )
        assertEquals(
            HomeUpdateState.UpToDate,
            HomeStateMapper.resolveUpdateState(
                enabled = true,
                currentVersionCode = 100L,
                latest = LatestVersionInfo(versionCode = 100, downloadUrl = "https://example.com"),
            ),
        )
        assertEquals(
            HomeUpdateState.Available(
                versionCode = 101,
                downloadUrl = "https://example.com",
                changelog = "changes",
            ),
            HomeStateMapper.resolveUpdateState(
                enabled = true,
                currentVersionCode = 100L,
                latest = LatestVersionInfo(
                    versionCode = 101,
                    downloadUrl = "https://example.com",
                    changelog = "changes",
                ),
            ),
        )
    }

    @Test
    fun `installed patches without a root session require access recovery`() {
        listOf(RootAccessProbeState.UNAVAILABLE, RootAccessProbeState.BLOCKED, RootAccessProbeState.ERROR).forEach { access ->
            val state = HomeStateMapper.map(
                capability = readyCapability(
                    kernelPatch = RootLayerState.AVAILABLE,
                    androidPatch = RootLayerState.AVAILABLE,
                    mode = RootMode.FULL_APATCH,
                ).copy(rootAccess = access),
                environment = environment(),
                showBackupWarning = false,
                update = HomeUpdateState.Idle,
            )

            assertEquals(true, state.needsRootAccess())
            assertEquals(HomePrimaryAction.RETRY_CHECK, state.primaryAction)
            assertEquals(false, state.copy(capability = state.capability.copy(phase = RootCheckPhase.CHECKING)).needsRootAccess())
        }
    }

    @Test
    fun `a behind system patch offers the system patch update`() {
        val state = HomeStateMapper.map(
            capability = readyCapability(
                kernelPatch = RootLayerState.AVAILABLE,
                androidPatch = RootLayerState.NEED_UPDATE,
                mode = RootMode.FULL_APATCH,
            ).copy(attention = setOf(RootAttention.NEED_UPDATE)),
            environment = environment(),
            showBackupWarning = false,
            update = HomeUpdateState.Idle,
        )

        assertEquals(HomeConclusion.NEED_UPDATE, state.conclusion)
        assertEquals(HomePrimaryAction.UPDATE_APATCH, state.primaryAction)
    }

    @Test
    fun `update layer follows the layer that is behind`() {
        assertEquals(
            HomeUpdateLayer.ANDROID_PATCH,
            resolveUpdateLayer(
                readyCapability(
                    kernelPatch = RootLayerState.AVAILABLE,
                    androidPatch = RootLayerState.NEED_UPDATE,
                    mode = RootMode.FULL_APATCH,
                ),
            ),
        )
        assertEquals(
            HomeUpdateLayer.KERNEL_PATCH,
            resolveUpdateLayer(
                readyCapability(
                    kernelPatch = RootLayerState.NEED_UPDATE,
                    androidPatch = RootLayerState.AVAILABLE,
                    mode = RootMode.FULL_APATCH,
                ),
            ),
        )
        assertNull(
            resolveUpdateLayer(
                readyCapability(
                    kernelPatch = RootLayerState.AVAILABLE,
                    androidPatch = RootLayerState.AVAILABLE,
                    mode = RootMode.FULL_APATCH,
                ),
            ),
        )
    }

    @Test
    fun `both layers behind name the kernel patch and offer it first`() {
        val capability = readyCapability(
            kernelPatch = RootLayerState.NEED_UPDATE,
            androidPatch = RootLayerState.NEED_UPDATE,
            mode = RootMode.FULL_APATCH,
        ).copy(attention = setOf(RootAttention.NEED_UPDATE))

        val state = HomeStateMapper.map(
            capability = capability,
            environment = environment(),
            showBackupWarning = false,
            update = HomeUpdateState.Idle,
        )

        assertEquals(HomeUpdateLayer.KERNEL_PATCH, resolveUpdateLayer(capability))
        assertEquals(HomePrimaryAction.UPDATE_KERNEL_PATCH, state.primaryAction)
    }

    @Test
    fun `blocked kernel patch update keeps the layer usable without an update prompt`() {
        val state = HomeStateMapper.map(
            capability = readyCapability(
                kernelPatch = RootLayerState.NEED_UPDATE,
                androidPatch = RootLayerState.AVAILABLE,
                mode = RootMode.FULL_APATCH,
            ).copy(attention = setOf(RootAttention.NEED_UPDATE)),
            environment = environment(),
            showBackupWarning = false,
            update = HomeUpdateState.Idle,
            blockKernelPatchUpdate = true,
        )

        assertEquals(RootLayerState.AVAILABLE, state.capability.kernelPatch)
        assertFalse(state.capability.attention.contains(RootAttention.NEED_UPDATE))
        assertEquals(HomeConclusion.FULL_APATCH, state.conclusion)
        assertEquals(HomePrimaryAction.NONE, state.primaryAction)
    }

    @Test
    fun `blocking one update layer leaves the other layer visible`() {
        val state = HomeStateMapper.map(
            capability = readyCapability(
                kernelPatch = RootLayerState.NEED_UPDATE,
                androidPatch = RootLayerState.NEED_UPDATE,
                mode = RootMode.FULL_APATCH,
            ).copy(attention = setOf(RootAttention.NEED_UPDATE)),
            environment = environment(),
            showBackupWarning = false,
            update = HomeUpdateState.Idle,
            blockKernelPatchUpdate = true,
        )

        assertEquals(RootLayerState.AVAILABLE, state.capability.kernelPatch)
        assertEquals(RootLayerState.NEED_UPDATE, state.capability.androidPatch)
        assertTrue(state.capability.attention.contains(RootAttention.NEED_UPDATE))
        assertEquals(HomeConclusion.NEED_UPDATE, state.conclusion)
        assertEquals(HomePrimaryAction.UPDATE_APATCH, state.primaryAction)
    }

    @Test
    fun `blocked system patch update keeps full root quiet`() {
        val state = HomeStateMapper.map(
            capability = readyCapability(
                kernelPatch = RootLayerState.AVAILABLE,
                androidPatch = RootLayerState.NEED_UPDATE,
                mode = RootMode.FULL_APATCH,
            ).copy(attention = setOf(RootAttention.NEED_UPDATE)),
            environment = environment(),
            showBackupWarning = false,
            update = HomeUpdateState.Idle,
            blockAndroidPatchUpdate = true,
        )

        assertEquals(RootLayerState.AVAILABLE, state.capability.androidPatch)
        assertFalse(state.capability.attention.contains(RootAttention.NEED_UPDATE))
        assertEquals(HomeConclusion.FULL_APATCH, state.conclusion)
        assertEquals(HomePrimaryAction.NONE, state.primaryAction)
        assertTrue(state.canUninstallAnything())
    }

    @Test
    fun `each layer reports its own versions`() {
        // The kernel patch is measured in KernelPatch versions, the system patch in the patch build
        // the manager was compiled against. Both sides of the arrow have to come from one layer.
        assertEquals(
            "0.13.8" to "0.13.9",
            resolveUpdateVersions(
                layer = HomeUpdateLayer.KERNEL_PATCH,
                installedKernelPatch = "0.13.8",
                builtKernelPatch = "0.13.9",
                installedSystemPatch = "0",
                managerVersionCode = 11_350L,
            ),
        )
        assertEquals(
            "11349" to "11350",
            resolveUpdateVersions(
                layer = HomeUpdateLayer.ANDROID_PATCH,
                installedKernelPatch = "0.13.8",
                builtKernelPatch = "0.13.9",
                installedSystemPatch = "11349",
                managerVersionCode = 11_350L,
            ),
        )
    }

    @Test
    fun `an unreadable version claims no update`() {
        // The card printed the kernel patch versions whenever either layer was behind, so a system
        // patch that was behind announced a kernel patch update. A version that could not be read is
        // no better: it must not be shown as the near side of an arrow.
        assertNull(
            resolveUpdateVersions(
                layer = HomeUpdateLayer.ANDROID_PATCH,
                installedKernelPatch = "0.13.8",
                builtKernelPatch = "0.13.9",
                installedSystemPatch = "0",
                managerVersionCode = 11_350L,
            ),
        )
        assertNull(
            resolveUpdateVersions(
                layer = HomeUpdateLayer.KERNEL_PATCH,
                installedKernelPatch = "0",
                builtKernelPatch = "0.13.9",
                installedSystemPatch = "11349",
                managerVersionCode = 11_350L,
            ),
        )
        assertNull(
            resolveUpdateVersions(
                layer = HomeUpdateLayer.KERNEL_PATCH,
                installedKernelPatch = "unknown",
                builtKernelPatch = "0.13.9",
                installedSystemPatch = "11349",
                managerVersionCode = 11_350L,
            ),
        )
    }

    private fun readyCapability(
        kernelPatch: RootLayerState,
        androidPatch: RootLayerState,
        mode: RootMode,
    ): RootCapabilitySnapshot = RootCapabilitySnapshot(
        phase = RootCheckPhase.READY,
        kernelPatch = kernelPatch,
        androidPatch = androidPatch,
        rootAccess = RootAccessProbeState.AVAILABLE,
        mode = mode,
    )

    private fun environment(): HomeDeviceEnvironment = HomeDeviceEnvironment(
        manufacturer = "Google",
        brand = "google",
        model = "Pixel",
        androidRelease = "16",
        androidApi = 36,
        isPreview = false,
        kernelRelease = "6.1.0",
        fingerprint = "fingerprint",
        primaryAbi = "arm64-v8a",
        kmi = "android14-6.1",
        selinuxStatus = HomeSelinuxStatus.ENFORCING,
        jailbreakActive = false,
        managerVersionName = "1.0.0",
        managerVersionCode = 100L,
    )
}
