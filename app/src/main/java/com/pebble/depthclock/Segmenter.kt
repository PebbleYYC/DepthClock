package com.pebble.depthclock

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import kotlinx.coroutines.tasks.await

object Segmenter {

    /**
     * Runs on-device subject segmentation and returns an ARGB bitmap containing only the
     * foreground subject (everything else transparent). Returns null if no subject was found.
     */
    suspend fun cutout(source: Bitmap): Bitmap? {
        val options = SubjectSegmenterOptions.Builder()
            .enableForegroundBitmap()
            .build()
        val segmenter = SubjectSegmentation.getClient(options)
        try {
            val result = segmenter.process(InputImage.fromBitmap(source, 0)).await()
            // foregroundBitmap matches the source dimensions, with transparent background.
            return result.foregroundBitmap
        } finally {
            segmenter.close()
        }
    }
}
