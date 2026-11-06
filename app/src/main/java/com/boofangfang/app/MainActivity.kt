package com.boofangfang.app

import android.Manifest
import android.content.*
import android.net.Uri
import android.os.*
import android.provider.OpenableColumns
import android.view.*
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.boofangfang.engine.TtsEngine
import java.io.File

class MainActivity : AppCompatActivity() {
    private lateinit var tvMode: TextView
    private lateinit var tvBook: TextView
    private lateinit var etText: android.widget.EditText
    private lateinit var btnSpeak: Button
    private lateinit var btnCancel: Button
    private lateinit var seek: SeekBar
    private lateinit var tvLog: TextView
    private lateinit var spinner: ProgressBar
    private lateinit var player: AudioPlayer

    private val modes = listOf(
        Triple("base", "标准朗读", "指定内置音色/方言的零样本朗读（无需参考音频）"),
        Triple("customvoice", "音色克隆", "用一段参考音频克隆声音后朗读"),
        Triple("voicedesign", "声音设计", "用一句话描述设计全新声音（如：温柔的年轻女声，语速偏慢）")
    )
    private var mode = 0
    private var refFile: File? = null
    private var refName: String? = null
    private var svc: SpeakService? = null
    private var bound = false

    private val conn = object : ServiceConnection {
        override fun onServiceConnected(n: ComponentName?, b: IBinder) { svc = (b as SpeakService.Binder).let { null }; bound = true }
        override fun onServiceDisconnected(n: ComponentName?) { bound = false }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ActivityCompat.requestPermissions(this,
            arrayOf(Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.READ_EXTERNAL_STORAGE), 1)

        val root = ScrollView(this)
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(40, 30, 40, 40) }
        root.addView(col)
        setContentView(root)

        col.addView(TextView(this).apply { text = "播放播放"; textSize = 26f; setTextColor(0xFF2F6BFF.toInt()); setPadding(0, 20, 0, 10) })
        col.addView(TextView(this).apply { text = "Qwen3-Talker 1.7B · GGUF 本地离线"; textSize = 12f; setTextColor(0xFF8A93A6.toInt()) })

        // ---- 模式切换 ----
        val modeRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 24, 0, 8) }
        val modeBtns = modes.mapIndexed { i, m ->
            Button(this).apply {
                text = m.second; tag = i; textSize = 12f
                setOnClickListener { mode = i; refreshMode() }
                modeRow.addView(this, LinearLayout.LayoutParams(0, 110, 1f).apply { rightMargin = 12 })
            }
        }
        col.addView(modeRow)
        tvMode = TextView(this).apply { textSize = 13f; setPadding(0, 4, 0, 12); setTextColor(0xFFCFD6E4.toInt()) }
        col.addView(tvMode)
        etText = EditText(this).apply {
            hint = "描述你想要的声音，例如：温柔的年轻女声，语速偏慢"; visibility = View.GONE
            minHeight = 160; setPadding(20, 20, 20, 20); setBackgroundColor(0xFF1A1F2B.toInt()); setTextColor(0xFFE6EAF2.toInt()); setHintTextColor(0xFF6B7488.toInt())
        }
        col.addView(etText, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 12 })
        val refRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; visibility = View.GONE }
        val btnRef = Button(this).apply { text = "选择参考音频"; textSize = 12f; setOnClickListener { pickRef() } }
        tvBook = TextView(this).apply { text = "未选择"; textSize = 12f; setPadding(16, 0, 0, 0); setTextColor(0xFF8A93A6.toInt()); maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.MIDDLE }
        refRow.addView(btnRef); refRow.addView(tvBook, LinearLayout.LayoutParams(0, -2, 1f))
        col.addView(refRow, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 12 })

        // ---- 顶部：电子书 + 自定义输入 ----
        val topRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val btnBook = Button(this).apply { text = "导入电子书"; textSize = 12f; setOnClickListener { pickBook() } }
        val btnFolder = Button(this).apply { text = "扫描模型库"; textSize = 12f; setOnClickListener { scanModels() } }
        topRow.addView(btnBook); topRow.addView(btnFolder, LinearLayout.LayoutParams(-2, -2).apply { leftMargin = 12 })
        col.addView(topRow, LinearLayout.LayoutParams(-1, -2).apply { topMargin = 6; bottomMargin = 12 })
        tvBook = tvBook // keep
        val bookInfo = TextView(this).apply { id = 1001; textSize = 12f; setTextColor(0xFF8A93A6.toInt()) }
        col.addView(bookInfo)

        val tvInputLabel = TextView(this).apply { text = "自定义输入"; textSize = 14f; setPadding(0, 16, 0, 6) }
        col.addView(tvInputLabel)
        val etInput = EditText(this).apply {
            hint = "直接输入要朗读的文字…"; minHeight = 220; gravity = Gravity.TOP
            setPadding(20, 20, 20, 20); setBackgroundColor(0xFF1A1F2B.toInt()); setTextColor(0xFFE6EAF2.toInt()); setHintTextColor(0xFF6B7488.toInt())
        }
        col.addView(etInput, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 12 })

        val row2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val btnUseBook = Button(this).apply { text = "用全书"; textSize = 12f; isEnabled = false }
        val btnUsePage = Button(this).apply { text = "用当前页"; textSize = 12f; isEnabled = false; setOnClickListener {
            val t = bookText; if (t != null) { val page = t.chunked(1200).getOrNull(pageIdx).orEmpty(); etInput.setText(page) } } }
        val tvPage = TextView(this).apply { textSize = 12f; setTextColor(0xFF8A93A6.toInt()); setPadding(12, 0, 0, 0) }
        row2.addView(btnUseBook); row2.addView(btnUsePage, LinearLayout.LayoutParams(-2, -2).apply { leftMargin = 12 }); row2.addView(tvPage, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = 12 })
        col.addView(row2, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 12 })

        // ---- 控制 ----
        val btnRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        btnSpeak = Button(this).apply { text = "开始播放"; setOnClickListener { start(etInput.text.toString()) } }
        btnCancel = Button(this).apply { text = "停止"; isEnabled = false; setOnClickListener { stopAll() } }
        spinner = ProgressBar(this).apply { visibility = View.GONE; indeterminate = true }
        btnRow.addView(btnSpeak); btnRow.addView(btnCancel, LinearLayout.LayoutParams(-2, -2).apply { leftMargin = 16 }); btnRow.addView(spinner, LinearLayout.LayoutParams(-2, -2).apply { leftMargin = 16 })
        col.addView(btnRow)

        val pauseRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val btnPrev = Button(this).apply { text = "⏮"; textSize = 18f; setOnClickListener { pageIdx = (pageIdx - 1).coerceAtLeast(0); showPage(tvPage) } }
        val btnPlay = Button(this).apply { text = "⏯"; textSize = 18f; setOnClickListener { if (player.isPlaying) { player.pause(); btnPlay.text = "▶" } else { player.resume(); btnPlay.text = "⏸" } } }
        val btnNext = Button(this).apply { text = "⏭"; textSize = 18f; setOnClickListener { pageIdx++; showPage(tvPage) } }
        seek = SeekBar(this).apply { max = 100; setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) { if (fromUser && bookText != null) { pageIdx = p; showPage(tvPage) } }
            override fun onStartTrackingTouch(s: SeekBar?) {}; override fun onStopTrackingTouch(s: SeekBar?) {} }) }
        pauseRow.addView(btnPrev); pauseRow.addView(btnPlay, LinearLayout.LayoutParams(-2, -2).apply { leftMargin = 16 }); pauseRow.addView(btnNext, LinearLayout.LayoutParams(-2, -2).apply { leftMargin = 16 }); pauseRow.addView(seek, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = 16 })
        col.addView(pauseRow, LinearLayout.LayoutParams(-1, -2).apply { topMargin = 8; bottomMargin = 8 })

        // ---- 底部：产出音频 ----
        col.addView(TextView(this).apply { text = "产出音频"; textSize = 14f; setPadding(0, 16, 0, 6) })
        tvLog = TextView(this).apply { textSize = 12f; setTextColor(0xFF8A93A6.toInt()); setBackgroundColor(0xFF12161F.toInt()); setPadding(20, 20, 20, 20); minHeight = 200 }
        col.addView(tvLog)
        val outDir = File(getExternalFilesDir(null), "outputs")
        val btnOut = Button(this).apply { text = "打开输出目录"; textSize = 12f; setOnClickListener {
            outDir.mkdirs(); val i = Intent(Intent.ACTION_VIEW).apply { setDataAndType(Uri.fromFile(outDir), "resource/folder"); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            if (i.resolveActivity(packageManager) != null) startActivity(i) else toast(outDir.absolutePath) } }
        col.addView(btnOut, LinearLayout.LayoutParams(-1, -2).apply { topMargin = 12 })

        player = AudioPlayer(onDone = { runOnUiThread { btnPlay.text = "▶" } })
        refreshMode()
        loadEngine()
    }

    private var bookText: String? = null
    private var pageIdx = 0
    private var bookName = ""

    private fun refreshMode() {
        val m = modes[mode]
        tvMode.text = "当前：${m.second} · ${m.third}"
        etText.visibility = if (mode == 2) View.VISIBLE else View.GONE
        (tvMode.parent as ViewGroup).findViewWithTag<Button?>(mode) // no-op
        for (i in 0 until 3) { /* 视觉可再优化 */ }
    }

    private fun loadEngine() {
        spinner.visibility = View.VISIBLE
        log("正在加载 GGUF（约 2.4GB，首次较慢）…")
        Thread {
            val dir = Models.modelRoot(this)
            val r = TtsEngine.load(dir, Runtime.getRuntime().availableProcessors().coerceAtMost(8))
            runOnUiThread {
                spinner.visibility = View.GONE
                if (r.isSuccess) log("引擎就绪 ✅ ${dir.absolutePath}")
                else log("引擎加载失败：${r.exceptionOrNull()?.message}\n请先点「扫描模型库」准备模型文件。\n提示：可将 Download/千问模型库 的 4 个 gguf 放到\n${dir.absolutePath}/base/ 等目录（见 README）")
            }
        }.start()
    }

    private fun scanModels() {
        val all = Models.findAll(this)
        val sb = StringBuilder("找到 ${all.size} 个 GGUF：\n")
        all.forEach { sb.append("· ${it.label} (${it.file.length() / 1024 / 1024}MB)\n") }
        sb.append("\n缺少 tokenizer 时请确认 qwen-tokenizer-12hz-Q8_0.gguf 在同目录。")
        log(sb.toString())
        loadEngine()
    }

    private fun pickBook() = startActivityForResult(
        Intent(Intent.ACTION_GET_CONTENT).apply { type = "*/*"; addCategory(Intent.CATEGORY_OPENABLE) }, 10)
    private fun pickRef() = startActivityForResult(
        Intent(Intent.ACTION_GET_CONTENT).apply { type = "audio/*"; addCategory(Intent.CATEGORY_OPENABLE) }, 11)

    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        val uri = data?.data ?: return
        val name = queryName(uri) ?: "file"
        if (req == 10) {
            Thread {
                try {
                    val tmp = File(cacheDir, name)
                    contentResolver.openInputStream(uri)!!.use { tmp.outputStream().use { o -> it.copyTo(o) } }
                    val text = EpubReader.read(tmp)
                    bookText = text; pageIdx = 0; bookName = name
                    runOnUiThread {
                        log("已导入《$name》 共 ${text.length} 字，约 ${(text.length / 1200) + 1} 页")
                        seek.max = maxOf(1, (text.length / 1200))
                        showPage(findViewById(1001) as TextView)
                    }
                } catch (t: Throwable) { runOnUiThread { log("导入失败：${t.message}") } }
            }.start()
        } else if (req == 11) {
            Thread {
                try {
                    val tmp = File(cacheDir, "ref_$name")
                    contentResolver.openInputStream(uri)!!.use { tmp.outputStream().use { o -> it.copyTo(o) } }
                    refFile = tmp; refName = name
                    runOnUiThread { tvBook.text = name }
                } catch (t: Throwable) { runOnUiThread { log("参考音频读取失败：${t.message}") } }
            }.start()
        }
    }

    private fun showPage(tv: TextView) {
        val t = bookText ?: return
        val pages = t.chunked(1200)
        tv.text = "《$bookName》 第 ${pageIdx + 1}/${pages.size} 页"
        seek.progress = pageIdx
    }

    private fun start(text: String) {
        if (text.isBlank()) { toast("请先输入文字或导入电子书"); return }
        if (!TtsEngine.isLoaded) { log("引擎未就绪"); loadEngine(); return }
        if (mode == 1 && refFile == null) { toast("克隆模式请先选择参考音频"); return }
        btnSpeak.isEnabled = false; btnCancel.isEnabled = true; spinner.visibility = View.VISIBLE
        log("开始合成（${modes[mode].second}）…")
        val ref = if (mode == 1) refFile else null
        val instruct = if (mode == 2) findViewById<EditText>(etText.id).text.toString().ifBlank { null } else null
        Thread {
            val t0 = System.currentTimeMillis()
            val pcm = try {
                TtsEngine.speak(mode, text.take(1200), ref, if (mode == 2) instruct else null)
            } catch (t: Throwable) { runOnUiThread { log("合成异常：${t.message}") }; null }
            runOnUiThread {
                spinner.visibility = View.GONE; btnSpeak.isEnabled = true; btnCancel.isEnabled = false
                if (pcm == null) { log("合成失败，详见 logcat: BFF"); return@runOnUiThread }
                val secs = pcm.size / 24000f
                player.feed(pcm); player.hasMore = false
                val out = File(File(getExternalFilesDir(null), "outputs").apply { mkdirs() }, "out_${System.currentTimeMillis()}.wav")
                TtsEngine.saveWav(pcm, out)
                log("完成 ✅ 用时 ${(System.currentTimeMillis() - t0) / 1000}s，音频 ${"%.1f".format(secs)}s\n已保存：${out.absolutePath}")
            }
        }.start()
    }

    private fun stopAll() {
        TtsEngine.cancel(); player.stop()
        btnSpeak.isEnabled = true; btnCancel.isEnabled = false; spinner.visibility = View.GONE
        log("已停止")
    }

    private fun log(s: String) { tvLog.append(if (tvLog.text.isEmpty()) s else "\n——\n$s"); tvLog.post { (rootView.parent as? ScrollView)?.fullScroll(View.FOCUS_DOWN) } }
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
    private fun queryName(uri: Uri): String? {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) return c.getString(0) } ; return uri.lastPathSegment
    }

    override fun onDestroy() { player.stop(); TtsEngine.destroy(); super.onDestroy() }
}
