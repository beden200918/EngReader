#!/usr/bin/env python3
"""把 ECDICT 的 ecdict.csv 编译成 App 使用的 dictionary.db"""
import csv, os, sqlite3, sys, time, urllib.request

CANDIDATES = [
    "https://raw.githubusercontent.com/skywind3000/ECDICT/master/ecdict.csv",
    "https://raw.githubusercontent.com/skywind3000/ECDICT/master/ecdict.mini.csv",
]

def ensure_csv(path):
    if os.path.exists(path) and os.path.getsize(path) > 500_000:
        return
    err = None
    for url in CANDIDATES:
        try:
            print("下载词库:", url)
            urllib.request.urlretrieve(url, path)
            if os.path.getsize(path) > 500_000:
                print("完成，%.1f MB" % (os.path.getsize(path) / 1048576))
                return
        except Exception as e:
            err = e
            print("失败:", e)
    raise SystemExit("无法获取词库 CSV: %s" % err)

def clean(s):
    return (s or "").replace("\\n", "\n").strip()

def main():
    src = sys.argv[1] if len(sys.argv) > 1 else "ecdict.csv"
    out = sys.argv[2] if len(sys.argv) > 2 else "app/src/main/assets/dictionary.db"
    ensure_csv(src)
    os.makedirs(os.path.dirname(out), exist_ok=True)
    if os.path.exists(out):
        os.remove(out)

    con = sqlite3.connect(out)
    cur = con.cursor()
    cur.executescript("""
        PRAGMA journal_mode=OFF;
        PRAGMA synchronous=OFF;
        CREATE TABLE dict(id INTEGER PRIMARY KEY, word TEXT, phonetic TEXT,
                          definition TEXT, translation TEXT, pos TEXT, frq INTEGER);
        CREATE TABLE forms(form TEXT, lemma TEXT);
        CREATE TABLE meta(k TEXT PRIMARY KEY, v TEXT);
    """)

    words, forms, n = [], [], 0
    with open(src, newline="", encoding="utf-8", errors="ignore") as f:
        for row in csv.DictReader(f):
            w = (row.get("word") or "").strip()
            if not w:
                continue
            try:
                frq = int(float(row.get("frq") or 0))
            except ValueError:
                frq = 0
            words.append((w, clean(row.get("phonetic")), clean(row.get("definition")),
                          clean(row.get("translation")), clean(row.get("pos")), frq))

            # exchange 字段形如 p:ran/d:run/i:running/3:runs，拆成 词形 -> 原形
            ex = (row.get("exchange") or "").strip()
            if ex:
                for part in ex.split("/"):
                    if ":" not in part:
                        continue
                    k, v = part.split(":", 1)
                    if k in ("p", "d", "i", "3", "r", "t", "s"):
                        for fv in v.split(","):
                            fv = fv.strip().lower()
                            if fv and fv != w.lower():
                                forms.append((fv, w.lower()))

            n += 1
            if len(words) >= 5000:
                cur.executemany("INSERT INTO dict(word,phonetic,definition,translation,pos,frq) VALUES(?,?,?,?,?,?)", words)
                words = []
            if len(forms) >= 20000:
                cur.executemany("INSERT INTO forms(form,lemma) VALUES(?,?)", forms)
                forms = []

    if words:
        cur.executemany("INSERT INTO dict(word,phonetic,definition,translation,pos,frq) VALUES(?,?,?,?,?,?)", words)
    if forms:
        cur.executemany("INSERT INTO forms(form,lemma) VALUES(?,?)", forms)

    cur.executescript("""
        CREATE INDEX idx_dict_word ON dict(word COLLATE NOCASE);
        CREATE INDEX idx_forms_form ON forms(form);
    """)
    cur.execute("INSERT INTO meta VALUES('version', ?)", (time.strftime("%Y-%m-%d"),))
    cur.execute("INSERT INTO meta VALUES('entries', ?)", (str(n),))
    con.commit()
    con.execute("VACUUM")
    con.close()
    print("完成：%d 条，%.1f MB -> %s" % (n, os.path.getsize(out) / 1048576, out))

if __name__ == "__main__":
    main()
