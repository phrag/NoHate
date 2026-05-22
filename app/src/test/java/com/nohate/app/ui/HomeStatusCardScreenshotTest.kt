package com.nohate.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.ide.common.rendering.api.SessionParams
import com.nohate.app.ui.theme.NoHateTheme
import org.junit.Rule
import org.junit.Test

/**
 * Paparazzi snapshot tests for [HomeStatusCard]. Renders a few canonical states
 * (idle, mid-scan, finished) and diffs against committed golden PNGs in
 * `app/src/test/snapshots/`. Run with:
 *   ./gradlew :app:recordPaparazziDebug   # update goldens
 *   ./gradlew :app:verifyPaparazziDebug   # CI verification
 */
class HomeStatusCardScreenshotTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5,
        renderingMode = SessionParams.RenderingMode.SHRINK,
    )

    @Test
    fun idle_neverScanned_light() {
        paparazzi.snapshot {
            NoHateTheme(darkTheme = false) {
                Surface {
                    Box(Modifier.fillMaxWidth().padding(16.dp)) {
                        HomeStatusCard(
                            isScanning = false,
                            scanProgress = 0f,
                            lastScanAt = 0L,
                            lastScanTotal = 0,
                            lastScanFlagged = 0,
                            scanMsg = "",
                            scanDone = 0,
                            scanTotal = 0,
                            sessionActive = false,
                            onOpenReview = {},
                        )
                    }
                }
            }
        }
    }

    @Test
    fun scanning_midway_dark() {
        paparazzi.snapshot {
            NoHateTheme(darkTheme = true) {
                Surface {
                    Box(Modifier.fillMaxWidth().padding(16.dp)) {
                        HomeStatusCard(
                            isScanning = true,
                            scanProgress = 0.42f,
                            lastScanAt = 0L,
                            lastScanTotal = 0,
                            lastScanFlagged = 0,
                            scanMsg = "Classified 42/100",
                            scanDone = 42,
                            scanTotal = 100,
                            sessionActive = true,
                            onOpenReview = {},
                        )
                    }
                }
            }
        }
    }

    @Test
    fun finished_withFlags_light() {
        paparazzi.snapshot {
            NoHateTheme(darkTheme = false) {
                Surface {
                    Box(Modifier.fillMaxWidth().padding(16.dp)) {
                        HomeStatusCard(
                            isScanning = false,
                            scanProgress = 1f,
                            lastScanAt = 1_700_000_000_000L,
                            lastScanTotal = 240,
                            lastScanFlagged = 7,
                            scanMsg = "Done",
                            scanDone = 240,
                            scanTotal = 240,
                            sessionActive = true,
                            onOpenReview = {},
                        )
                    }
                }
            }
        }
    }
}
