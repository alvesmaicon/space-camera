#!/usr/bin/env python3
"""Localiza controles do Space Camera por rótulo no dump do uiautomator.

  ui.py find <rotulo>   -> "x y" do centro do nó com text/content-desc == rótulo
  ui.py shutter         -> "x y" do disparador (maior clicável sem rótulo na metade de baixo)
  ui.py count           -> número de nós (canário de tela errada)
"""
import re
import subprocess
import sys

DUMP = sys.argv[0].rsplit("/", 1)[0] + "/ui.xml"


def dump():
    subprocess.run(["adb", "shell", "uiautomator", "dump", "/sdcard/ui.xml"],
                   capture_output=True)
    subprocess.run(["adb", "pull", "-q", "/sdcard/ui.xml", DUMP], capture_output=True)
    return open(DUMP).read()


def nodes(xml):
    for m in re.finditer(r"<node [^>]*>", xml):
        n = m.group(0)
        b = list(map(int, re.findall(r"\d+", re.search(r'bounds="([^"]*)"', n)[1])))
        yield {
            "text": re.search(r' text="([^"]*)"', n)[1],
            "desc": re.search(r'content-desc="([^"]*)"', n)[1],
            "click": 'clickable="true"' in n,
            "b": b,
        }


xml = dump()
cmd = sys.argv[1]
ns = list(nodes(xml))
if cmd == "count":
    print(len(ns))
elif cmd == "find":
    alvo = sys.argv[2]
    for n in ns:
        if alvo in (n["text"], n["desc"]):
            x1, y1, x2, y2 = n["b"]
            print((x1 + x2) // 2, (y1 + y2) // 2)
            break
    else:
        sys.exit(f"rótulo não encontrado: {alvo}")
elif cmd == "shutter":
    candidatos = [n for n in ns if n["click"] and not n["text"] and not n["desc"]
                  and n["b"][1] > 1500 and 200 <= n["b"][2] - n["b"][0] <= 240
                  and abs((n["b"][0] + n["b"][2]) // 2 - 540) < 20]
    # Largura e centro, não quadratura: em Full o nó é recortado pela janela
    # (220x160) embora o botão apareça inteiro na tela.
    n = max(candidatos, key=lambda n: n["b"][2] - n["b"][0])
    x1, y1, x2, y2 = n["b"]
    print((x1 + x2) // 2, (y1 + y2) // 2)
