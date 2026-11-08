"""Preserved independent assertions returned by the structural QA specialist.

Run from the repository root: python team/reports/TEAM-001-independent-check.py
No imports from the generator; no file writes. This checks team setup, not an app.
"""

from pathlib import Path
import json
import tomllib

p = Path(__file__).resolve().parents[2]
r = json.loads((p / "team/roster.json").read_text(encoding="utf-8-sig"))
roles = r["roles"]
by = {x["id"]: x for x in roles}
assert len(roles) == len(by) == 25
assert len(r["departments"]) == 7
assert {d["id"] for d in r["departments"]} == {
    "product", "finance", "architecture", "frontend", "backend-data", "creative", "qa"
}
members = []
for d in r["departments"]:
    ids = d["members"]
    members += ids
    assert len(ids) == len(set(ids)) == 3 and d["head"] in ids
    assert by[d["head"]]["kind"] == "head"
    assert by[d["head"]]["reports_to"] == "chief"
    assert all(by[i]["department"] == d["id"] for i in ids)
    assert all(
        by[i]["kind"] == "specialist" and by[i]["reports_to"] == d["head"]
        for i in ids if i != d["head"]
    )
assert len(members) == len(set(members)) == 21
court = {
    "court-critic": "critic",
    "court-implementation-advocate": "advocate",
    "court-user-advocate": "advocate",
    "court-judge": "judge",
}
assert len(r["court"]["members"]) == 4 and set(r["court"]["members"]) == set(court)
assert not set(members) & set(court)
assert set(members) | set(court) == set(by)
for i, kind in court.items():
    assert by[i]["kind"] == kind and by[i]["department"] == "court"
    assert by[i]["reports_to"] == ("chief" if kind == "judge" else "court-judge")
for folder, ext in [(".codex/agents", "toml"), (".cursor/agents", "md")]:
    fs = list((p / folder).glob("*." + ext))
    assert len(fs) == 25 and {f.stem for f in fs} == set(by)
for role in roles:
    i = role["id"]
    data = tomllib.loads((p / ".codex/agents" / f"{i}.toml").read_text(encoding="utf-8"))
    ins = data["developer_instructions"]
    md = (p / ".cursor/agents" / f"{i}.md").read_text(encoding="utf-8")
    assert data["name"] == i and data["description"].strip()
    assert i in ins and role["title"] in ins
    assert "AGENTS.md" in ins and "team/PROTOCOL.md" in ins
    assert f"Подчинение по процессу: {role['reports_to']}." in ins
    assert "Формат отчёта:" in ins and "доказательства" in ins
    assert f"name: {i}\n" in md and ins.strip() in md
    assert role["responsibilities"] and role["acceptance"]
    assert all(x in ins for x in role["responsibilities"] + role["acceptance"])
    if role["kind"] == "specialist":
        assert "Верни результат своему главе" in ins and "Не принимай свою работу сам." in ins
    elif role["kind"] == "head":
        d = next(d for d in r["departments"] if d["id"] == role["department"])
        assert all(j in ins for j in d["members"] if j != i)
        assert "реальные вызовы субагентов" in ins
        assert "Проверь работу каждого исполнителя" in ins and "независимый QA и суд" in ins
    else:
        assert "Ты независим от авторов проверяемой версии" in ins
        assert "После раскрытия обязателен обмен" in ins
print("ALL INDEPENDENT ASSERTIONS PASSED: 25 roles, reporting lines, 50 profiles and separation of review")
