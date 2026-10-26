#!/usr/bin/env python3
"""문서 교차 점검 스크립트 (표준 라이브러리만). 저장소 루트에서 실행: python3 docs/overnight/checks.py

읽기 전용이다. 아무것도 수정하지 않고 마크다운 보고서를 표준 출력으로 낸다.
점검 항목:
  A. 각 spec.md의 FR-###, SC-###이 같은 기능의 quickstart.md / tasks.md에 등장하는지
  B. 마크다운 상대 링크가 깨졌는지
  C. docs/domain.md 용어집이 '쓰지 않는 말'로 정한 단어가 본문에 쓰였는지 (후보, 사람이 판단)
  D. 이벤트·상태·오류 코드 이름이 문서마다 같은지 (한 파일에만 나오는 대문자 토큰 = 오타 후보)
"""
import re
import sys
from collections import defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SPECS = sorted((ROOT / "specs").glob("[0-9][0-9][0-9]-*"))
MD_FILES = sorted(p for p in ROOT.rglob("*.md")
                  if not any(part in {".git", ".claude", ".specify", "graphify-out"} for part in p.parts))

KNOWN_CLASSES = {
    "GroupBuy", "GroupBuyStatus", "GroupBuyCategory", "GroupBuyRepository", "GroupBuyService",
    "GroupBuyController", "GroupBuyResponse", "GroupBuyRequest", "GroupBuyEvents", "GroupBuyEventLogger",
    "GroupBuyQueryController", "GroupBuyQueryService", "GroupBuyListQuery", "GroupBuySearchRepository",
    "GroupBuySummary", "GroupBuyDetail", "GroupBuyCloseService", "GroupBuyCloser", "GroupBuyChanged",
    "GroupBuyCancelled", "GroupBuyCompleted", "GroupBuyFailed", "GroupBuyDetailIT", "GroupBuyEventLogger",
}
CANONICAL_EVENTS = {"GroupBuyChanged", "GroupBuyCancelled", "GroupBuyCompleted", "GroupBuyFailed"}
CANONICAL_STATUS = {"OPEN", "PENDING_CLOSE", "COMPLETED", "FAILED", "CANCELLED"}
SUSPECT_STATUS = {"CLOSED", "SUCCESS", "SUCCESSFUL", "REJECTED", "EXPIRED", "PENDING", "DONE", "FINISHED"}
# SQL 키워드·환경변수·테스트 값처럼 이름 불일치와 무관한 토큰은 '한 파일에만 나오는 토큰' 목록에서 뺀다
NOISE_TOKENS = {"COUNT", "LIMIT", "WHERE", "LAZY", "FULLTEXT", "PATCH", "DB_URL", "DB_USER", "DB_PASSWORD",
                "SNACK", "NULL", "TRUE", "FALSE", "SELECT", "UPDATE", "INSERT", "DELETE", "UNIQUE", "CHECK"}


def rel(p: Path) -> str:
    return str(p.relative_to(ROOT))


def text(p: Path) -> str:
    return p.read_text(encoding="utf-8")


def section_a():
    rows, notes = [], []
    for d in SPECS:
        spec = d / "spec.md"
        if not spec.exists():
            continue
        s = text(spec)
        frs = sorted(set(re.findall(r"^- \*\*(FR-\d+)\*\*", s, re.M)))
        scs = sorted(set(re.findall(r"^- \*\*(SC-\d+)\*\*", s, re.M)))
        for name in ("quickstart.md", "tasks.md"):
            f = d / name
            if not f.exists():
                notes.append(f"`{d.name}`: `{name}` 없음 (아직 작성 전)")
                continue
            body = text(f)
            missing_fr = [x for x in frs if x not in body]
            missing_sc = [x for x in scs if x not in body]
            rows.append((d.name, name, len(frs), len(scs), missing_fr, missing_sc))
    return rows, notes


def section_b():
    broken = []
    link = re.compile(r"\[[^\]]*\]\(([^)\s]+)\)")
    for p in MD_FILES:
        in_fence = False
        for i, line in enumerate(text(p).split("\n"), 1):
            if line.strip().startswith("```"):
                in_fence = not in_fence
            if in_fence:
                continue
            for m in link.finditer(line):
                target = m.group(1)
                if re.match(r"^(https?:|mailto:|#)", target):
                    continue
                path = target.split("#")[0]
                if not path:
                    continue
                if not (p.parent / path).resolve().exists():
                    broken.append((rel(p), i, target))
    return broken


def section_c():
    dm = ROOT / "docs" / "domain.md"
    forbidden = defaultdict(set)  # word -> canonical terms
    if dm.exists():
        in_glossary = False
        for line in text(dm).split("\n"):
            if line.startswith("## 1."):
                in_glossary = True
                continue
            if in_glossary and line.startswith("## "):
                break
            if in_glossary and line.startswith("|") and "---" not in line:
                cells = [c.strip() for c in line.strip("|").split("|")]
                if len(cells) >= 4 and cells[0] != "용어":
                    for w in re.split(r"[,、]\s*", cells[-1]):
                        w = w.strip()
                        if w:
                            forbidden[w].add(cells[0])
    hits = defaultdict(list)
    skip = {"docs/domain.md", "docs/cross-check.md", "docs/cross-check-2.md"}
    for p in MD_FILES:
        if rel(p) in skip or rel(p).startswith("docs/overnight/"):
            continue
        for i, line in enumerate(text(p).split("\n"), 1):
            for w in forbidden:
                if w in line:
                    hits[w].append((rel(p), i, line.strip()[:90]))
    return forbidden, hits


def section_d():
    events_seen = defaultdict(set)
    unknown_ids = defaultdict(set)
    status_suspect = defaultdict(set)
    token_files = defaultdict(set)
    for p in MD_FILES:
        t = text(p)
        for m in re.finditer(r"\bGroupBuy[A-Z][A-Za-z]*\b", t):
            name = m.group(0)
            if name in CANONICAL_EVENTS:
                events_seen[name].add(rel(p))
            elif name not in KNOWN_CLASSES:
                unknown_ids[name].add(rel(p))
        for m in re.finditer(r"`([A-Z][A-Z_]{3,})`", t):
            tok = m.group(1)
            token_files[tok].add(rel(p))
            if tok in SUSPECT_STATUS:
                status_suspect[tok].add(rel(p))
    only_one = {t: next(iter(f)) for t, f in token_files.items()
                if len(f) == 1 and t not in CANONICAL_STATUS and t not in NOISE_TOKENS}
    return events_seen, unknown_ids, status_suspect, only_one


def main():
    out = []
    a_rows, a_notes = section_a()
    out.append("## A. 요구사항 ↔ 검증 시나리오·태스크 추적성\n")
    out.append("| 기능 | 문서 | FR 수 | SC 수 | 문서에 없는 FR | 문서에 없는 SC |")
    out.append("|------|------|-------|-------|----------------|----------------|")
    for name, doc, nfr, nsc, mfr, msc in a_rows:
        out.append(f"| {name} | {doc} | {nfr} | {nsc} | {', '.join(mfr) or '-'} | {', '.join(msc) or '-'} |")
    out.append("")
    for n in a_notes:
        out.append(f"- {n}")
    out.append("")

    broken = section_b()
    out.append("## B. 깨진 상대 링크\n")
    out.append("없음\n" if not broken else "\n".join(f"- `{f}:{i}` → `{t}`" for f, i, t in broken) + "\n")

    forbidden, hits = section_c()
    out.append("## C. 용어집의 '쓰지 않는 말' 사용 후보 (사람이 판단)\n")
    out.append(f"용어집에서 읽은 금지어: {', '.join(sorted(forbidden)) or '(읽지 못함)'}\n")
    if not hits:
        out.append("사용 후보 없음\n")
    for w, lst in sorted(hits.items()):
        out.append(f"- `{w}` (대응 용어: {', '.join(sorted(forbidden[w]))}) — {len(lst)}곳")
        for f, i, snippet in lst[:6]:
            out.append(f"  - `{f}:{i}` {snippet}")
        if len(lst) > 6:
            out.append(f"  - … 외 {len(lst) - 6}곳")
    out.append("")

    ev, unknown, suspect, only_one = section_d()
    out.append("## D. 이름 일관성\n")
    out.append("**이벤트**: " + ", ".join(f"`{e}` ({len(f)}개 파일)" for e, f in sorted(ev.items())) + "\n")
    out.append("**알 수 없는 `GroupBuy*` 식별자** (오타 또는 새 클래스 후보):")
    out.append("없음" if not unknown else "\n".join(f"- `{n}` ← {', '.join(sorted(f))}" for n, f in sorted(unknown.items())))
    out.append("\n**표준에 없는 상태명 후보**:")
    out.append("없음" if not suspect else "\n".join(f"- `{n}` ← {', '.join(sorted(f))}" for n, f in sorted(suspect.items())))
    out.append("\n**한 파일에만 나오는 `UPPER_SNAKE` 토큰** (오타 후보 또는 한 곳에서만 정의된 코드):")
    for tok, f in sorted(only_one.items()):
        out.append(f"- `{tok}` ← `{f}`")
    print("\n".join(out))


if __name__ == "__main__":
    sys.exit(main())
