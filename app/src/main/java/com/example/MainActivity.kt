package com.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.screens.ConverterScreen
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.ReaderScreen
import com.example.ui.screens.ToolsScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.ConverterViewModel
import java.io.File

enum class ScreenTab(
    val route: String,
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    CONVERT("convert", "Convert", Icons.Filled.AutoAwesome, Icons.Outlined.AutoAwesome),
    LIBRARY("library", "My Books", Icons.Filled.CollectionsBookmark, Icons.Outlined.CollectionsBookmark),
    READER("reader", "Reader", Icons.Filled.MenuBook, Icons.Outlined.MenuBook),
    TOOLS("tools", "Tools", Icons.Filled.Tune, Icons.Outlined.Tune)
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                val viewModel: ConverterViewModel = viewModel()
                MainAppContent(
                    viewModel = viewModel,
                    onShareEpub = { filePath, title -> shareEpub(filePath, title) }
                )
            }
        }
    }

    private fun shareEpub(filePath: String, title: String) {
        try {
            val file = File(filePath)
            if (!file.exists()) {
                Toast.makeText(this, "EPUB file not found", Toast.LENGTH_SHORT).show()
                return
            }

            val uri: Uri = FileProvider.getUriForFile(
                this,
                "$packageName.fileprovider",
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/epub+zip"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, title)
                putExtra(Intent.EXTRA_TEXT, "Here is '$title' converted with PDF to EPUB Converter.")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            startActivity(Intent.createChooser(shareIntent, "Share '$title' via"))
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to share: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }
}

@Composable
fun MainAppContent(
    viewModel: ConverterViewModel,
    onShareEpub: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by rememberSaveable { mutableStateOf(ScreenTab.CONVERT) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            // Hide bottom nav bar when reading to allow distraction-free experience
            if (selectedTab != ScreenTab.READER) {
                NavigationBar(
                    modifier = Modifier
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .testTag("bottom_nav_bar"),
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = androidx.compose.ui.unit.Dp(3f)
                ) {
                    ScreenTab.values().forEach { tab ->
                        val isSelected = selectedTab == tab
                        NavigationBarItem(
                            selected = isSelected,
                            onClick = { selectedTab = tab },
                            icon = {
                                Icon(
                                    imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                                    contentDescription = tab.title
                                )
                            },
                            label = { Text(tab.title) },
                            modifier = Modifier.testTag("nav_tab_${tab.route}")
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            color = MaterialTheme.colorScheme.background
        ) {
            Crossfade(
                targetState = selectedTab,
                animationSpec = tween(durationMillis = 200),
                label = "tab_crossfade"
            ) { tab ->
                when (tab) {
                    ScreenTab.CONVERT -> ConverterScreen(
                        viewModel = viewModel,
                        onNavigateToReader = { selectedTab = ScreenTab.READER },
                        onShareEpub = onShareEpub
                    )
                    ScreenTab.LIBRARY -> LibraryScreen(
                        viewModel = viewModel,
                        onOpenReader = { book ->
                            viewModel.openBookInReader(book)
                            selectedTab = ScreenTab.READER
                        },
                        onShareEpub = onShareEpub,
                        onNavigateToConvert = { selectedTab = ScreenTab.CONVERT }
                    )
                    ScreenTab.READER -> ReaderScreen(
                        viewModel = viewModel,
                        onNavigateToLibrary = { selectedTab = ScreenTab.LIBRARY }
                    )
                    ScreenTab.TOOLS -> ToolsScreen(
                        viewModel = viewModel
                    )
                }
            }
        }
    }
}
