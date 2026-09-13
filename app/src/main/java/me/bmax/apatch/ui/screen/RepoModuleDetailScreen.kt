package me.bmax.apatch.ui.screen

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.InstallScreenDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import me.bmax.apatch.R
import me.bmax.apatch.ui.repo.ModuleRepoPreferences
import me.bmax.apatch.ui.repo.RepoModule
import me.bmax.apatch.ui.repo.RepoVersion
import me.bmax.apatch.ui.repo.formatReleaseDate
import me.bmax.apatch.ui.viewmodel.RepoModuleViewModel
import me.bmax.apatch.util.DownloadListener
import me.bmax.apatch.util.download
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Community
import top.yukonga.miuix.kmp.icon.extended.Download
import top.yukonga.miuix.kmp.icon.extended.Help
import top.yukonga.miuix.kmp.icon.extended.Link
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical

/**
 * Everything a repository says about one module: who wrote it, where it lives, and every release it
 * published. The repository is read again from its url so the page survives being restored on its
 * own, which the API client's cache makes cheap.
 */
@Destination<RootGraph>
@Composable
fun RepoModuleDetailScreen(
    navigator: DestinationsNavigator,
    moduleId: String,
) {
    val viewModel = viewModel<RepoModuleViewModel>()
    val context = LocalContext.current
    // Resource lookups go through the configuration-aware provider, not the raw context.
    val resources = LocalResources.current
    val uriHandler = LocalUriHandler.current
    // Repository modules come from the manager module store, which is the one with a cluster.
    val repositoryUrl = ModuleRepoPreferences.repositoryUrl(forKernelModules = false)
    val module = viewModel.selectedModule

    LaunchedEffect(moduleId, repositoryUrl) {
        if (repositoryUrl.isBlank()) {
            navigator.popBackStack()
            return@LaunchedEffect
        }
        viewModel.selectModule(repositoryUrl, moduleId)
    }

    fun startDownload(url: String, fileName: String, description: String) {
        if (url.isBlank()) return
        Toast.makeText(context, description, Toast.LENGTH_SHORT).show()
        download(context, url, fileName, description)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = module?.name?.ifBlank { moduleId } ?: stringResource(R.string.online_module_title),
                navigationIcon = {
                    IconButton(onClick = { navigator.popBackStack() }) {
                        Icon(
                            imageVector = MiuixIcons.Back,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when {
                module != null -> ModuleDetailContent(
                    module = module,
                    onOpenLink = { url -> runCatching { uriHandler.openUri(url) } },
                    onDownloadVersion = { version ->
                        startDownload(
                            url = version.zipUrl,
                            fileName = "${module.name}-${version.version}.zip",
                            description = resources.getString(
                                R.string.online_module_download_start,
                                module.name,
                            ),
                        )
                    },
                    onDownloadLatest = {
                        module.latestRelease?.let { release ->
                            startDownload(
                                url = release.zipUrl,
                                fileName = "${module.name}-${release.version}.zip",
                                description = resources.getString(
                                    R.string.online_module_download_start,
                                    module.name,
                                ),
                            )
                        }
                    },
                )

                viewModel.isDetailLoading -> APModuleLoadingState()

                else -> Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    APModuleNoticeCard(
                        message = stringResource(R.string.online_module_load_error),
                        actionLabel = stringResource(R.string.apm_retry),
                        onAction = { viewModel.selectModule(repositoryUrl, moduleId) },
                    )
                }
            }
        }
    }

    DownloadListener(context) { uri ->
        navigator.navigate(InstallScreenDestination(uri, MODULE_TYPE.APM))
    }
}

@Composable
private fun ModuleDetailContent(
    module: RepoModule,
    onOpenLink: (String) -> Unit,
    onDownloadVersion: (RepoVersion) -> Unit,
    onDownloadLatest: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().overScrollVertical(),
        contentPadding = PaddingValues(top = 12.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                colors = CardDefaults.defaultColors(),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = module.name.ifBlank { module.id },
                            modifier = Modifier.weight(1f),
                            style = MiuixTheme.textStyles.title4,
                            fontWeight = FontWeight.SemiBold,
                        )
                        APModuleBadge(text = "APM")
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = listOfNotNull(
                            module.author.takeIf { it.isNotBlank() }
                                ?.let { stringResource(R.string.repo_module_by_author, it) },
                            module.version.takeIf { it.isNotBlank() },
                            module.license.takeIf { it.isNotBlank() },
                        ).joinToString(" · "),
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                    if (module.description.isNotBlank()) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = module.description,
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                }
            }
        }

        val links = listOfNotNull(
            module.homepage.takeIf { it.isNotBlank() }
                ?.let { Triple(R.string.repo_module_link_homepage, MiuixIcons.Community, it) },
            module.source.takeIf { it.isNotBlank() }
                ?.let { Triple(R.string.repo_module_link_source, MiuixIcons.Link, it) },
            module.support.takeIf { it.isNotBlank() }
                ?.let { Triple(R.string.repo_module_link_support, MiuixIcons.Help, it) },
        )

        if (links.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    colors = CardDefaults.defaultColors(),
                ) {
                    links.forEach { (title, icon, url) ->
                        BasicComponent(
                            title = stringResource(title),
                            summary = url,
                            startAction = {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )
                            },
                            onClick = { onOpenLink(url) },
                        )
                    }
                }
            }
        }

        item {
            Text(
                text = stringResource(R.string.repo_module_versions),
                modifier = Modifier.padding(start = 28.dp, top = 10.dp, bottom = 2.dp),
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }

        items(module.versions) { version ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                colors = CardDefaults.defaultColors(),
            ) {
                BasicComponent(
                    title = version.version.ifBlank { module.version },
                    summary = formatReleaseDate(version.timestamp),
                    endActions = {
                        IconButton(onClick = { onDownloadVersion(version) }) {
                            Icon(
                                imageVector = MiuixIcons.Download,
                                contentDescription = stringResource(R.string.online_module_download),
                            )
                        }
                    },
                    onClick = { onDownloadVersion(version) },
                )
            }
        }

        item {
            Spacer(Modifier.height(6.dp))
            Button(
                onClick = onDownloadLatest,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                colors = ButtonDefaults.buttonColorsPrimary(),
            ) {
                Text(stringResource(R.string.online_module_download_install))
            }
        }
    }
}
