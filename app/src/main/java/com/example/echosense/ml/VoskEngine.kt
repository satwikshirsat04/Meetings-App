package com.echosense.ml

import android.content.Context
import android.util.Log
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import java.io.File
import java.io.IOException

class VoskEngine(private val context: Context) {

    private val TAG = "VoskEngine"
    private var model: Model? = null
    private var recognizer: Recognizer? = null
    var speechService: SpeechService? = null
    private var isModelLoaded = false

    private val MODEL_ASSET_PATH = "vosk-model-small-en-us-0.15"

    fun initModel(onComplete: (Boolean) -> Unit) {
        Thread {
            try {
                Log.d(TAG, "Initializing Vosk model...")

                val modelDir = File(context.filesDir, "vosk")

                if (!modelDir.exists() || !File(modelDir, "am/final.mdl").exists()) {
                    Log.d(TAG, "Extracting model from assets...")
                    copyAssetFolder(MODEL_ASSET_PATH, modelDir.absolutePath)
                }

                Log.d(TAG, "Loading model from: ${modelDir.absolutePath}")
                model = Model(modelDir.absolutePath)

                recognizer = Recognizer(model, 16000.0f)
                isModelLoaded = true

                Log.d(TAG, "Model loaded successfully")
                onComplete(true)

            } catch (e: Exception) {
                Log.e(TAG, "Model load failed", e)
                onComplete(false)
            }
        }.start()
    }

    private fun copyAssetFolder(assetFolder: String, targetPath: String) {
        val assetManager = context.assets
        val files = assetManager.list(assetFolder) ?: return

        val outDir = File(targetPath)
        outDir.mkdirs()

        for (file in files) {
            val inPath = "$assetFolder/$file"
            val outFile = File(targetPath, file)

            val subFiles = assetManager.list(inPath)
            if (subFiles != null && subFiles.isNotEmpty()) {
                copyAssetFolder(inPath, outFile.absolutePath)
            } else {
                assetManager.open(inPath).use { input ->
                    outFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
        }
        Log.d(TAG, "Copied: $assetFolder → $targetPath")
    }

    fun startListening(listener: RecognitionListener): Boolean {
        if (!isModelLoaded) return false

        return try {
            speechService = SpeechService(recognizer, 16000.0f)
            speechService?.startListening(listener)
            true
        } catch (e: Exception) {
            Log.e(TAG, "startListening failed", e)
            false
        }
    }

    fun stopListening() {
        try {
            speechService?.stop()
        } catch (e: Exception) {
            Log.e(TAG, "stopListening error", e)
        }
    }

    fun shutdown() {
        try {
            stopListening()
            recognizer?.close()
            model?.close()
            model = null
            recognizer = null
            isModelLoaded = false
        } catch (e: Exception) {
            Log.e(TAG, "shutdown error", e)
        }
    }
}
