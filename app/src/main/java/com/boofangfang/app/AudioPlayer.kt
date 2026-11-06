package com.boofangfang.app

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** 24kHz/单声道/PCM16 WAV 播放器：支持逐段排队（边合成边播）与暂停/继续 */
class AudioPlayer(private val sampleRate: Int = 24000, private val onDone: () -> Unit = {}) {
    private var track: AudioTrack? = null
    @Volatile private var playing = false
    @Volatile private var paused = false
    private val queue = ArrayDeque<Pair<ShortArray, Int>>() // 数据, 已写位置
    private val lock = Any()

    fun start() {
        playing = true; paused = false
        Thread {
            android.os.Process.setThreadPriority(-16)
            val t = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                .setAudioFormat(AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setBufferSizeInBytes(sampleRate * 2)
                .setTransferMode(AudioTrack.MODE_STREAM).build()
            t.play(); track = t
            while (playing) {
                val chunk = synchronized(lock) {
                    val head = queue.firstOrNull()
                    if (head == null) null else {
                        val remain = head.first.size - head.second
                        val n = minOf(remain, 4800)
                        val copy = head.first.copyOfRange(head.second, head.second + n)
                        queue[0] = head.first to head.second + n
                        if (head.second + n >= head.first.size) queue.removeFirst()
                        copy
                    }
                }
                if (chunk == null) {
                    if (synchronized(lock) { queue.isEmpty() } && !hasMore) { playing = false; break }
                    Thread.sleep(20); continue
                }
                while (paused && playing) Thread.sleep(30)
                if (!playing) break
                t.write(chunk, 0, chunk.size)
            }
            t.stop(); t.release(); track = null
            onDone()
        }.start()
    }

    @Volatile var hasMore = true

    fun feed(pcm: FloatArray) {
        val shorts = ShortArray(pcm.size) { (pcm[it].coerceIn(-1f, 1f) * 32767).toInt().toShort() }
        synchronized(lock) { queue.addLast(shorts to 0) }
    }

    fun feedFile(f: File) {
        val bytes = f.readBytes()
        if (bytes.size < 44) return
        val buf = ByteBuffer.wrap(bytes, 44, bytes.size - 44).order(ByteOrder.LITTLE_ENDIAN)
        val shorts = ShortArray((bytes.size - 44) / 2) { buf.getShort() }
        synchronized(lock) { queue.addLast(shorts to 0) }
    }

    fun pause() { paused = true }
    fun resume() { paused = false }
    fun stop() { playing = false; synchronized(lock) { queue.clear() } }
    val isPlaying get() = playing && !paused
}
