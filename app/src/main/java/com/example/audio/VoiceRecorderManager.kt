package com.example.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import java.io.File

private const val TAG = "VoiceRecorderManager"

class VoiceRecorderManager(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var startedAtMs: Long = 0L

    fun startRecording(): Result<File> {
        return try {
            stopAndDiscard()
            val file = File(context.cacheDir, "voice_task_${System.currentTimeMillis()}.m4a")
            outputFile = file
            val mr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            mr.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(16000)
                setAudioEncodingBitRate(64000)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            startedAtMs = System.currentTimeMillis()
            recorder = mr
            Result.success(file)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start audio recording", e)
            stopAndDiscard()
            Result.failure(e)
        }
    }

    fun getAmplitudeNormalized(): Float {
        return try {
            val amp = recorder?.maxAmplitude ?: 0
            (amp / 18000f).coerceIn(0.08f, 1f)
        } catch (_: Exception) {
            0.08f
        }
    }

    fun stopAndReadBytes(): Result<ByteArray> {
        return try {
            val file = outputFile ?: return Result.failure(IllegalStateException("Нет активной записи."))
            val elapsedMs = System.currentTimeMillis() - startedAtMs
            try {
                recorder?.stop()
            } catch (e: RuntimeException) {
                Log.w(TAG, "Recorder stop runtime exception (too short?)", e)
                stopAndDiscard()
                return Result.failure(
                    IllegalStateException("Запись слишком короткая. Говорите хотя бы 1 секунду.")
                )
            }
            recorder?.release()
            recorder = null
            if (elapsedMs < 350L || !file.exists() || file.length() == 0L) {
                file.delete()
                outputFile = null
                return Result.failure(
                    IllegalStateException("Запись пустая или слишком короткая. Попробуйте говорить чуть дольше.")
                )
            }
            val bytes = file.readBytes()
            file.delete()
            outputFile = null
            Result.success(bytes)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop recording", e)
            stopAndDiscard()
            Result.failure(e)
        }
    }

    fun stopAndDiscard() {
        try {
            recorder?.stop()
        } catch (_: Exception) {
        }
        try {
            recorder?.release()
        } catch (_: Exception) {
        }
        recorder = null
        startedAtMs = 0L
        outputFile?.delete()
        outputFile = null
    }
}
