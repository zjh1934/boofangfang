package com.boofangfang.app

import android.app.*
import android.content.Intent
import android.os.*
import androidx.core.app.NotificationCompat
import com.boofangfang.engine.TtsEngine
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors

/** 前台服务：后台逐段合成 + 写入输出目录 + 通知显示进度 */
class SpeakService : Service() {
    private val exe = Executors.newSingleThreadExecutor()
    private var player: AudioPlayer? = null
    private varBinder = Binder()
    @Volatile private var running = false

    class Binder : android.os.Binder()
    override fun onBind(intent: Intent) = binder

    override fun onCreate() {
        super.onCreate()
        val ch = NotificationChannel("tts", "播放播放合成", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        startForeground(1, notify("准备中…", 0, 0))
        player = AudioPlayer(onDone = { })
    }

    fun submit(text: String, ref: File?, instruct: String?, mode: String, play: Boolean) {
        val MODE_IDS = mapOf("base" to 0, "customvoice" to 1, "voicedesign" to 2)
        if (running) return
        running = true
        player?.start()
        exe.execute {
            val outDir = File(getExternalFilesDir(null), "outputs").apply { mkdirs() }
            val stamp = SimpleDateFormat("MMdd_HHmmss", Locale.CHINA).format(Date())
            val wav = File(outDir, "${mode}_$stamp.wav")
            val parts = text.chunked(180)
            var done = 0
            try {
                for (p in parts) {
                    if (!running) break
                    val pcm = TtsEngine.speak(MODE_IDS[mode] ?: 0, p, ref, if (mode == "voicedesign") instruct else null) ?: throw IllegalStateException("引擎返回空音频，查看日志 adb logcat -s BFF")
                    if (play) player?.feed(pcm)
                    appendWav(wav, pcm, done == 0)
                    done++
                    notify("正在合成 $mode：第 $done/${parts.size} 段", (done * 100 / parts.size), 0)
                }
                notify(if (running) "完成：${wav.name}" else "已停止", 100, 1)
            } catch (t: Throwable) {
                notify("失败：${t.message}", 0, 1)
            }
            running = false
        }
    }

    private fun appendWav(f: File, pcm: FloatArray, first: Boolean) {
        if (first) TtsEngine.saveWav(pcm, f)
        else {
            java.io.RandomAccessFile(f, "rw").use { raf ->
                raf.seek(raf.length())
                for (v in pcm) { val s = (v.coerceIn(-1f, 1f) * 32767).toInt(); raf.writeByte(s and 255); raf.writeByte((s shr 8) and 255) }
                val n = raf.length() - 8
                raf.seek(4); raf.writeByte(n and 255); raf.writeByte((n shr 8) and 255); raf.writeByte((n shr 16) and 255); raf.writeByte((n shr 24) and 255)
                val d = raf.length() - 44
                raf.seek(40); raf.writeByte(d and 255); raf.writeByte((d shr 8) and 255); raf.writeByte((d shr 16) and 255); raf.writeByte((d shr 24) and 255)
            }
        }
    }

    fun cancel() { running = false; TtsEngine.cancel(); player?.stop() }

    private fun notify(t: String, progress: Int, cancelable: Int): Notification {
        val b = NotificationCompat.Builder(this, "tts")
            .setSmallIcon(android.R.drawable.ic_media_play).setContentTitle("播放播放").setContentText(t)
            .setProgress(100, progress, progress !in 1..99).setOngoing(cancelable == 0)
        if (cancelable == 0) b.addAction(android.R.drawable.ic_delete, "停止",
            PendingIntent.getService(this, 0, Intent(this, SpeakService::class.java).setAction("stop"), PendingIntent.FLAG_IMMUTABLE))
        return b.build()
    }

    override fun onStartCommand(i: Intent?, flags: Int, startId: Int): Int {
        if (i?.action == "stop") cancel()
        return START_NOT_STICKY
    }

    override fun onDestroy() { cancel(); player = null; super.onDestroy() }
    val binder = Binder()
}
