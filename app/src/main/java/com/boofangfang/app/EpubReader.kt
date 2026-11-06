package com.boofangfang.app

import java.io.File
import java.io.InputStream
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.zip.ZipInputStream

/** 轻量电子书读取：.txt / .md 直读；.epub 按 spine 顺序抽取正文并去标签 */
object EpubReader {
    fun read(f: File): String = when (f.extension.lowercase()) {
        "epub" -> readEpub(f.inputStream())
        else -> f.readText(StandardCharsets.UTF_8)
    }

    fun readEpub(input: InputStream): String {
        val html = LinkedHashMap<String, String>()
        var opf: String? = null
        var root = ""
        ZipInputStream(input).use { zip ->
            var e = zip.nextEntry
            while (e != null) {
                val name = URLDecoder.decode(e.name, "UTF-8")
                when {
                    name.endsWith(".opf") -> opf = zip.readBytes().toString(StandardCharsets.UTF_8)
                    name.endsWith(".html") || name.endsWith(".xhtml") || name.endsWith(".htm") ->
                        html[name] = zip.readBytes().toString(StandardCharsets.UTF_8)
                }
                if (root.isEmpty()) root = name.substringBefore('/')
                zip.closeEntry(); e = zip.nextEntry
            }
        }
        val order = mutableListOf<String>()
        opf?.let {
            val manifest = Regex("""<item\b[^>]*id="([^"]+)"[^>]*href="([^"]+)"""").findAll(it)
                .associate { m -> m.groupValues[1] to m.groupValues[2] }
            val spine = Regex("""<itemref\b[^>]*idref="([^"]+)"""").findAll(it).map { m -> m.groupValues[1] }
            spine.forEach { id -> manifest[id]?.let { h -> order.add(resolve(root, h)) } }
        }
        if (order.isEmpty()) order.addAll(html.keys)
        return order.mapNotNull { html[it] ?: html[html.keys.firstOrNull { k -> k.endsWith(it) } ?: ""] }
            .joinToString("\n\n") { strip(it) }.replace(Regex("\n{3,}"), "\n\n").trim()
    }

    private fun resolve(root: String, href: String) =
        if (href.startsWith("/")) href.trimStart('/') else if (root.isEmpty()) href else "$root/$href"

    private fun strip(s: String): String {
        var t = s.replace(Regex("(?is)<(script|style)[^>]*>.*?</\\1>"), "")
        t = t.replace(Regex("(?i)<br\\s*/?>"), "\n").replace(Regex("(?i)</(p|div|h[1-6]|li|blockquote)>"), "\n")
        t = t.replace(Regex("<[^>]+>"), "")
        val sb = StringBuilder()
        t.split('\n').forEach { line ->
            val l = line.replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<")
                .replace("&gt;", ">").replace("&#39;", "'").replace("&quot;", "\"").trim()
            if (l.isNotEmpty()) sb.append(l).append('\n')
        }
        return sb.toString()
    }
}
