package com.pebble.depthclock

import android.content.Context
import android.graphics.Bitmap
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await

object Segmenter {

    /**
     * Runs on-device subject segmentation and returns an ARGB bitmap containing only the
     * foreground subject (everything else transparent). Returns null if no subject was found.
     *
     * The segmentation model is "unbundled" — Google Play services downloads it on demand. A
     * sideloaded (non-Play-Store) install doesn't get the install-time download, so the first
     * run must fetch it. We request the module explicitly, then retry a few times while it lands.
     */
    suspend fun cutout(context: Context, source: Bitmap): Bitmap? {
        val options = SubjectSegmenterOptions.Builder()
            .enableForegroundBitmap()
            .build()
        val segmenter = SubjectSegmentation.getClient(options)
        try {
            // Kick off / confirm the one-time model download.
            try {
                val moduleInstall = ModuleInstall.getClient(context)
                val availability = moduleInstall.areModulesAvailable(segmenter).await()
                if (!availability.areModulesAvailable()) {
                    val request = ModuleInstallRequest.newBuilder().addApi(segmenter).build()
                    moduleInstall.installModules(request).await()
                }
            } catch (_: Throwable) {
                // If the ModuleInstall API is unavailable, the retry loop below still works:
                // the first process() call triggers an on-demand download.
            }

            var attempt = 0
            while (true) {
                try {
                    val result = segmenter.process(InputImage.fromBitmap(source, 0)).await()
                    return result.foregroundBitmap
                } catch (t: Throwable) {
                    val msg = t.message ?: ""
                    val stillDownloading = msg.contains("download", true) ||
                        msg.contains("module", true) ||
                        msg.contains("Waiting", true)
                    if (stillDownloading && attempt < 25) {
                        attempt++
                        delay(3000)
                    } else {
                        throw t
                    }
                }
            }
        } finally {
            segmenter.close()
        }
    }
}
