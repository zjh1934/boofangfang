package com.boofangfang.app

import android.content.Context
import org.json.JSONObject
import java.io.File

data class ModelFile(val label: String, val file: File)

/** 扫描 app 专属目录与公开 Download/千问模型库 下的 GGUF */
object Models {
    const val DOWNLOAD_MODELS = "Download/千问模型库"

    fun modelRoot(ctx: Context): File = File(ctx.getExternalFilesDir(null), "models").apply { mkdirs() }

    fun findAll(ctx: Context): List<ModelFile> {
        val dirs = mutableListOf(modelRoot(ctx))
        android.os.Environment.getExternalStorageDirectory()?.let {
            dirs.add(File(it, DOWNLOAD_MODELS))
            dirs.add(File(it, "Download"))
        }
        return dirs.filter { it.exists() }
            .flatMap { it.listFiles().orEmpty().toList() }
            .filter { it.extension.equals("gguf", true) }
            .map { ModelFile(it.name, it) }
    }

    fun tokenizerFor(ctx: Context, talker: File): File? {
        val dir = talker.parentFile ?: return null
        val mf = File(dir, "manifest.json")
        if (mf.exists()) runCatching {
            val tok = JSONObject(mf.readText()).optString("tokenizer")
            if (tok.isNotEmpty() && File(dir, tok).exists()) return File(dir, tok)
        }
        return listOf("qwen-tokenizer-12hz-Q8_0.gguf", "tokenizer.gguf")
            .map { File(dir, it) }.firstOrNull { it.exists() }
            ?: dir.listFiles()?.firstOrNull { it.name.contains("tokenizer", true) && it.extension.equals("gguf", true) }
    }
}
