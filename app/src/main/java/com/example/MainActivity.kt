package com.example

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.data.PreferencesManager
import com.example.model.LogLevel
import com.example.service.CaptchaAccessibilityService
import com.example.service.ScreenCaptureService
import com.example.ui.components.HorizontalNavigationFooter
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.LiveWebViewScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.TerminalScreen
import com.example.ui.theme.DeepNavyBg
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.MainViewModel
import com.example.util.Logger
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Global uncaught crash handler to preserve crash logs in terminal
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val stackTrace = Log.getStackTraceString(throwable)
                PreferencesManager(applicationContext).saveCrashReport(
                    "Thread: ${thread.name}\nException: ${throwable.javaClass.simpleName}: ${throwable.message}\n$stackTrace"
                )
                Logger.log("CRASH", "FATAL CRASH: ${throwable.message}\n$stackTrace", LogLevel.ERROR)
            } catch (_: Exception) {}
            defaultHandler?.uncaughtException(thread, throwable)
        }

        setContent {
            MyApplicationTheme {
                val coroutineScope = rememberCoroutineScope()
                val pagerState = rememberPagerState(pageCount = { 4 })
                val selectedTab by viewModel.selectedTab.collectAsState()
                val toastMessage by viewModel.toastMessage.collectAsState()

                // MediaProjection Activity Result Launcher
                val projectionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartActivityForResult()
                ) { result ->
                    if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                        try {
                            ScreenCaptureService.start(this, result.resultCode, result.data!!)
                            viewModel.setMediaProjectionAuthorized(true)
                            viewModel.refreshCurrentFrame()
                            Toast.makeText(this, "Screen Capture Framebuffer Authorized!", Toast.LENGTH_SHORT).show()
                            Logger.log("VISION", "Screen capture authorization accepted. ScreenCaptureService active.", LogLevel.VISION)
                        } catch (e: Throwable) {
                            Logger.log("VISION", "Failed to start ScreenCaptureService: ${e.message}", LogLevel.ERROR)
                            Toast.makeText(this, "Capture Start Error: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    } else {
                        viewModel.setMediaProjectionAuthorized(false)
                        Toast.makeText(this, "Screen capture permission declined.", Toast.LENGTH_SHORT).show()
                        Logger.log("VISION", "Screen capture permission declined by user.", LogLevel.INFO)
                    }
                }

                // Sync pagerState with ViewModel tab
                LaunchedEffect(pagerState.currentPage) {
                    viewModel.setSelectedTab(pagerState.currentPage)
                }

                LaunchedEffect(selectedTab) {
                    if (pagerState.currentPage != selectedTab) {
                        pagerState.animateScrollToPage(selectedTab)
                    }
                }

                // Display toast messages
                LaunchedEffect(toastMessage) {
                    toastMessage?.let { msg ->
                        Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show()
                        viewModel.clearToast()
                    }
                }

                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(DeepNavyBg),
                    contentWindowInsets = WindowInsets.statusBars,
                    bottomBar = {
                        HorizontalNavigationFooter(
                            selectedIndex = selectedTab,
                            onTabSelected = { targetIndex ->
                                coroutineScope.launch {
                                    viewModel.setSelectedTab(targetIndex)
                                    pagerState.animateScrollToPage(targetIndex)
                                }
                            }
                        )
                    }
                ) { innerPadding ->
                    HorizontalPager(
                        state = pagerState,
                        beyondViewportPageCount = 3,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) { page ->
                        when (page) {
                            0 -> DashboardScreen(
                                viewModel = viewModel,
                                onRequestMediaProjection = {
                                    if (CaptchaAccessibilityService.instance != null) {
                                        viewModel.setMediaProjectionAuthorized(true)
                                        viewModel.refreshCurrentFrame()
                                        Toast.makeText(this@MainActivity, "Live Screen Capture Active via Accessibility Engine!", Toast.LENGTH_SHORT).show()
                                        Logger.log("VISION", "Screen capture live via native Accessibility engine. Frame refreshed.", LogLevel.VISION)
                                    } else {
                                        try {
                                            val mpManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
                                            if (mpManager != null) {
                                                projectionLauncher.launch(mpManager.createScreenCaptureIntent())
                                            }
                                        } catch (e: Throwable) {
                                            Logger.log("VISION", "MediaProjection launch notice: ${e.message}", LogLevel.INFO)
                                        }
                                    }
                                }
                            )
                            1 -> SettingsScreen(viewModel = viewModel)
                            2 -> TerminalScreen()
                            3 -> LiveWebViewScreen(viewModel = viewModel)
                        }
                    }
                }
            }
        }
    }
}