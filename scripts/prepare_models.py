#!/usr/bin/env python3
"""把手机 Download/千问模型库 里的 4 个 GGUF 整理成 App 需要的目录结构：
   <out>/base|customvoice|voicedesign/{talker.gguf, tokenizer.gguf, manifest.json}
用法: python3 prepare_models.py <GGUF所在目录> <输出目录>
"""
import json, shutil, sys
from pathlib import Path

MODES = {
    "base": "qwen-talker-1.7b-base-Q8_0.gguf",
    "customvoice": "qwen-talker-1.7b-customvoice-Q8_0.gguf",
    "voicedesign": "qwen-talker-1.7b-voicedesign-Q8_0.gguf",
}
TOK = "qwen-tokenizer-12hz-Q8_0.gguf"

def main():
    src, out = Path(sys.argv[1]), Path(sys.argv[2])
    tok = src / TOK
    if not tok.exists():
        sys.exit(f"找不到 {tok}")
    for mode, name in MODES.items():
        t = src / name
        if not t.exists():
            print(f"跳过 {mode}: 缺少 {name}"); continue
        d = out / mode; d.mkdir(parents=True, exist_ok=True)
        shutil.copy2(t, d / name)
        shutil.copy2(tok, d / TOK)
        (d / "manifest.json").write_text(json.dumps({
            "talker": name, "tokenizer": TOK,
            "mode": mode}, ensure_ascii=False, indent=2))
        print(f"OK {mode} -> {d}")
    print("完成后把整个输出目录拷到手机: Android/data/com.boofangfang.app/files/models/")

main()
