package com.doujinzip.android.ui

import com.doujinzip.android.data.formatCrashSummary
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppDiagnosticsTest {
    @Test
    fun reportOmitsConnectionSecrets() {
        val report = buildDiagnosticReport(
            state = MainUiState(
                host = "192.168.1.25",
                pairingCode = "123456",
                deviceName = "Private device name",
            ),
            appVersion = "0.5.0 (6)",
            androidVersion = "16 / SDK 36",
            device = "Test Device",
            orientation = "landscape",
        )

        assertTrue(report.contains("App: 0.5.0 (6)"))
        assertFalse(report.contains("192.168.1.25"))
        assertFalse(report.contains("123456"))
        assertFalse(report.contains("Private device name"))
        assertFalse(report.contains("Last crash:"))
    }

    @Test
    fun reportIncludesCrashTypesAndFramesWithoutMessagesOrPaths() {
        val cause = IllegalArgumentException("https://example.invalid/private?token=fixture")
        cause.stackTrace = arrayOf(StackTraceElement(
            "com.doujinzip.android.ui.FixtureReader", "openPage", "/private/fixture/Filename.kt", 42,
        ))
        val crash = formatCrashSummary(IllegalStateException("Synthetic private title", cause))
        val report = buildDiagnosticReport(MainUiState(), "1.0", "16", "Test Device", "portrait", crash)

        assertTrue(report.contains("Last crash:"))
        assertTrue(report.contains("IllegalStateException"))
        assertTrue(report.contains("IllegalArgumentException"))
        assertTrue(report.contains("FixtureReader.openPage:42"))
        assertFalse(report.contains("example.invalid"))
        assertFalse(report.contains("Synthetic private title"))
        assertFalse(report.contains("Filename.kt"))
    }
}
