"""Independent QA of frozen APP-001 partial concept/D1, not application tests.

Run from the repository root: python team/reports/APP-001-qa-calculation.py
Uses only Python's standard library; does not modify any input.
Oracle amounts/dates below come from SCREENS D1 and the QA assignment,
not from the fixture's `expected` section. That section is checked last.
"""

from datetime import date
from decimal import Decimal, localcontext
from hashlib import sha256
import json
from pathlib import Path
import re
import struct
import xml.etree.ElementTree as ET


ROOT = Path(__file__).resolve().parents[2]
INPUTS = (
    "docs/product/CONCEPT.md",
    "docs/product/SCREENS.md",
    "docs/finance/MODEL.md",
    "design/demo-data.json",
    "docs/architecture/STRUCTURE.md",
)
SNAPSHOT = "team/snapshots/app-concept-v1-partial.json"
MANIFEST_HASH = "7548a12d9d0b60064bbd113e38248e785c5f88ffac004fde31f2f2251384f892"


def require(condition, message):
    if not condition:
        raise AssertionError(message)


def money_minor(value):
    """Validate D1's canonical KZT/2 MoneyDTO, return integer minor units."""
    require(isinstance(value, dict), "MoneyDTO must be an object")
    require(set(value) == {"currency", "scale", "amount"}, "MoneyDTO keys")
    require(value["currency"] == "KZT", "D1 currency must be KZT")
    require(type(value["scale"]) is int and value["scale"] == 2, "D1 scale must be integer 2")
    amount = value["amount"]
    require(isinstance(amount, str), "MoneyDTO amount must be string")
    require(re.fullmatch(r"-?(?:0|[1-9][0-9]*)\.[0-9]{2}", amount) is not None, "noncanonical amount")
    require(amount != "-0.00", "negative zero is not canonical")
    negative = amount.startswith("-")
    whole, fraction = amount.lstrip("-").split(".")
    minor = int(whole) * 100 + int(fraction)
    return -minor if negative else minor


def cash(amount):
    return {"currency": "KZT", "scale": 2, "amount": amount}


def local_date(value):
    require(isinstance(value, str) and re.fullmatch(r"[0-9]{4}-[0-9]{2}-[0-9]{2}", value) is not None, "LocalDate shape")
    return date.fromisoformat(value)


def walk(value, path="$", key=None):
    yield path, key, value
    if isinstance(value, dict):
        for child_key, child in value.items():
            yield from walk(child, path + "." + child_key, child_key)
    elif isinstance(value, list):
        for index, child in enumerate(value):
            yield from walk(child, path + "[" + str(index) + "]", None)


def no_duplicate_keys(pairs):
    result = {}
    for key, value in pairs:
        require(key not in result, "duplicate JSON key: " + key)
        result[key] = value
    return result


def reject_constant(value):
    raise AssertionError("nonfinite JSON number: " + value)


def read_json(data):
    return json.loads(data.decode("utf-8-sig"), object_pairs_hook=no_duplicate_keys,
                      parse_float=Decimal, parse_constant=reject_constant)


def frozen_inputs():
    snapshot_bytes = (ROOT / SNAPSHOT).read_bytes()
    snapshot = read_json(snapshot_bytes)
    require(snapshot["version"] == "app-concept-v1-partial", "snapshot version")
    entries = snapshot["files"]
    require(len(entries) == 26 and len({row["path"] for row in entries}) == 26, "26 unique frozen paths")
    paths = [row["path"] for row in entries]
    # Snapshot JSON row order is immaterial. Canonical digest uses ordinal paths.
    raw = {}
    for row in entries:
        name = row["path"]
        require("\\" not in name and not name.startswith("/") and ".." not in name.split("/"), "POSIX relative path")
        raw[name] = (ROOT / name).read_bytes()
        actual = sha256(raw[name]).hexdigest()
        require(actual == row["sha256"], "frozen hash mismatch: " + name)
        print("PASS SHA256 " + actual + "  " + name)
    canonical = "".join(sha256(raw[name]).hexdigest() + "  " + name + "\n" for name in sorted(raw)).encode("utf-8")
    control = sha256(canonical).hexdigest()
    require(control == MANIFEST_HASH, "canonical 26-file manifest hash")
    print("PASS frozen files=26/26; canonical_manifest_sha256=" + control)
    print("SNAPSHOT_JSON_SHA256 " + sha256(snapshot_bytes).hexdigest())
    require(set(INPUTS) <= set(raw), "all independent D1 inputs are frozen")
    return raw, snapshot_bytes


def js_array(source, marker):
    """Read a known literal array as data; never execute the author's JS."""
    start = source.index(marker)
    depth, quoted, escaped = 0, False, False
    for position in range(start, len(source)):
        char = source[position]
        if quoted:
            if escaped:
                escaped = False
            elif char == "\\":
                escaped = True
            elif char == "'":
                quoted = False
        elif char == "'":
            quoted = True
        elif char == "[":
            depth += 1
        elif char == "]":
            depth -= 1
            if depth == 0:
                literal = source[start:position + 1]
                literal = re.sub(r"'([^'\\]*)'", lambda m: json.dumps(m.group(1), ensure_ascii=False), literal)
                return json.loads(literal, parse_float=Decimal)
    raise AssertionError("unterminated source literal array")


def display_minor(value):
    require(re.fullmatch(r"(?:0|[1-9][0-9]{0,2}(?: [0-9]{3})*),[0-9]{2}(?: KZT)?", value) is not None, "D1 display amount")
    return money_minor(cash(value.removesuffix(" KZT").replace(" ", "").replace(",", ".")))


def static_design(raw):
    manifest = read_json(raw["design/figma-manifest.json"])
    evidence = read_json(raw["design/figma-evidence.json"])
    state = read_json(raw["design/figma-state.json"])
    require(evidence["foundations"] == state, "foundation evidence/state agreement")
    require(manifest["fixture"] == {"id": "D1", "version": "1.0.0", "path": "design/demo-data.json", "currency": "KZT", "scale": 2, "month": "2026-10", "snapshotDate": "2026-10-30"}, "manifest D1 metadata")
    require(manifest["scope"]["prototypeInteractionsConfigured"] is False, "prototype must not be claimed configured")
    frames = manifest["frames"]
    require(len(frames) == 5 and {f["id"] for f in frames} == {"6:222", "6:223", "6:224", "6:225", "6:226"}, "five unique main frames")
    returned = evidence["overview"]["frames"] + evidence["screens"]["frames"] + evidence["mobile"]["frames"]
    require(len(returned) == 5 and len({f["id"] for f in returned}) == 5, "five unique successful build-return frames")
    returned_by_id = {f["id"]: f for f in returned}
    fields = ("id", "name", "w", "h", "descendants", "types", "imageFillNodes", "fonts")
    expected_names = ["SC-01 · Обзор / desktop", "SC-02 · Операции / desktop", "SC-03 · Цели / desktop", "SC-04 · Долги / desktop", "SC-01 · Обзор / mobile"]
    require([f["name"] for f in frames] == expected_names, "main frame identities")
    for frame in frames:
        require({key: frame[key] for key in fields} == returned_by_id[frame["id"]], "manifest/build-return agreement")
        require(frame["descendants"] == sum(frame["types"].values()) and frame["types"]["TEXT"] > 0 and frame["imageFillNodes"] == [], "reported editable node counts")
        require(frame["url"] == manifest["url"] + "?node-id=" + frame["id"].replace(":", "-"), "frame URL/id")
        require(frame["figmaStatus"] == "composition-saved", "saved status")
    require([(f["w"], f["h"]) for f in frames] == [(1440, 1120)] * 4 + [(390, 2020)], "frame dimensions")
    totals = (sum(f["types"]["TEXT"] for f in frames), sum(f["types"]["INSTANCE"] for f in frames))
    require(totals == (238, 36), "reported layer totals")
    checks = read_json(raw["design/figma-local-checks.json"])
    for name, digest in checks["hashes"].items():
        require(sha256(raw["design/" + name]).hexdigest() == digest, "author evidence hash consistency")
    print("PASS static build-return/manifest agreement: 5 frames, 238 TEXT, 36 INSTANCE, 0 reported image-fill nodes; not a fresh Figma read")
    core = raw["design/figma-core.js"].decode("utf-8-sig")
    overview = raw["design/figma-02-overview.js"].decode("utf-8-sig")
    screens = raw["design/figma-03-screens.js"].decode("utf-8-sig")
    mobile = raw["design/figma-04-mobile.js"].decode("utf-8-sig")
    sc02, rest = screens.split("// SC-03:", 1)
    sc03, sc04 = rest.split("// SC-04:", 1)
    sources = [overview, sc02, sc03, sc04, mobile]
    # Independent monetary token sets, not author's expected/count checker.
    overview_amounts = {5_000_000, 35_000_000, 30_000_000, 50_000_000, 15_000_000, 12_000_000, 3_000_000, 18_000_000, 60_000_000, 8_000_000}
    expected_sets = [overview_amounts, {50_000_000, 30_000_000, 3_000_000, 12_000_000, 15_000_000}, {18_000_000, 60_000_000}, {8_000_000, 5_000_000}, overview_amounts]
    for name, source, oracle in zip(expected_names, sources, expected_sets):
        literal_text = "\n".join(re.findall(r"'([^'\\]*)'", source))
        matches = re.findall(r"(?<![0-9])(?:[0-9]{1,3}(?: [0-9]{3})*|[0-9]+),[0-9]{2}(?: KZT)?", literal_text)
        require({display_minor(value) for value in matches} == oracle, "monetary literal set: " + name)
        require("n.name==='" + name + "'" in source, "root selector: " + name)
        require("200 000,00" not in source and "1200,999" not in source, "no invented balance or unsaved operation in main source")
    for source in (overview, mobile):
        require(js_array(source, "[['План бюджета'") == [["План бюджета", "350 000,00 KZT"], ["Потрачено", "300 000,00 KZT"]], "budget label/value literal pairs")
        category_rows = js_array(source, "[['Жильё'")
        require(category_rows == [["Жильё", "150 000,00", "150 000,00", 1], ["Продукты", "120 000,00", "150 000,00", Decimal("0.8")], ["Транспорт", "30 000,00", "50 000,00", Decimal("0.6")]], "category fact/plan/geometry literals")
        require(js_array(source, "[['Я должен'") == [["Я должен", "80 000,00 KZT"], ["Мне должны", "50 000,00 KZT"]], "separate debt label/value pairs")
        require("Остаток бюджета — разница плана и расходов месяца, не баланс счёта" in source and "Начальный баланс не задан" in source and "30%" in source and "30.10.2026" in source, "overview financial semantics")
    operation_rows = js_array(sc02, "[['25.10.2026'")
    require(operation_rows == [["25.10.2026", "Доход", "Зарплата", "500 000,00 KZT", True], ["20.10.2026", "Расход", "Транспорт", "30 000,00 KZT", False], ["12.10.2026", "Расход", "Продукты", "120 000,00 KZT", False], ["01.10.2026", "Расход", "Жильё", "150 000,00 KZT", False]], "independent SC02 rows")
    require("30%" in sc03 and "На 30.10.2026" in sc03 and "Накоплено до октября · пополнений в октябре нет" in sc03, "goal progress/date/history literals")
    require("Я должен · Пример А" in sc04 and "Мне должны · Пример Б" in sc04 and "15.11.2026" in sc04 and "20.11.2026" in sc04 and "Исходные суммы и прошлые возвраты не заданы" in sc04, "debt direction/date/history literals")
    require("Октябрь 2026" in core and "Демо · KZT" in core, "shared period/currency labels")
    require(all("01.10–31.10.2026" in source for source in (overview, sc02, mobile)), "monthly period labels")
    print("PASS static source D1 labels/amounts: SC01 desktop, SC02 four rows, SC03 30%, SC04 separate debts, SC01 mobile; no live text/visual proof")
    annotations = manifest["annotations"]
    require(len(annotations) == 2 and all(a["placeholder"] is True and a["compositionApplied"] is False for a in annotations), "annotation skeleton status")
    require(manifest["scripts"]["lastAppliedStage"] == "design/figma-04-mobile.js" and manifest["scripts"]["pendingStage"] == "design/figma-05-states-form.js", "pending stage 05")
    require("not-componentized" in manifest["components"]["mobileNavigation"], "mobile navigation limitation")
    require(all(evidence[key]["isError"] is True and "Starter plan" in evidence[key]["content"][0]["text"] for key in ("statesFormToolResult", "screenshot02ToolResult")), "saved quota failure evidence")
    require([f["previewPath"] for f in frames] == ["design/previews/SC-01-desktop.png", None, None, None, None], "one available final main PNG")
    png = raw["design/previews/SC-01-desktop.png"]
    require(png[:8] == b"\x89PNG\r\n\x1a\n" and struct.unpack(">II", png[16:24]) == (1440, 1120), "SC01 PNG header only")
    print("CONFIRMED PARTIAL: AN01/02 skeleton; stage05 not applied; mobile nav not componentized; final PNG only SC01 (header, not visual inspection)")


def graph_check(raw):
    architecture = raw["docs/architecture/STRUCTURE.md"].decode("utf-8-sig")
    mermaid = re.search(r"```mermaid\s+(.*?)```", architecture, re.S).group(1)
    labels = dict(re.findall(r'([A-Za-z][A-Za-z0-9]*)\["([^"]+)"\]', mermaid))
    edges = re.findall(r'([A-Za-z][A-Za-z0-9]*)(?:\["[^"]+"\])?\s*-->\s*([A-Za-z][A-Za-z0-9]*)(?:\["[^"]+"\])?', mermaid)
    expected_edges = {(labels[start], labels[end]) for start, end in edges}
    xml = ET.fromstring(raw["design/figjam-readback.xml"].decode("utf-8-sig"))
    nodes = {node.attrib["id"]: node.text for node in xml.findall("shape-with-text")}
    connectors = xml.findall("connector")
    actual_edges = set()
    for connector in connectors:
        attrs = connector.attrib
        require(attrs["connectorStart"] in nodes and attrs["connectorEnd"] in nodes, "FigJam edge reference")
        require(attrs["connectorStartCap"] == "NONE" and attrs["connectorEndCap"] == "ARROW_LINES", "FigJam arrow direction")
        actual_edges.add((nodes[attrs["connectorStart"]], nodes[attrs["connectorEnd"]]))
    require(len(nodes) == 10 and len(connectors) == 9 and len(actual_edges) == 9, "FigJam graph cardinality")
    require(set(nodes.values()) == set(labels.values()) and actual_edges == expected_edges, "FigJam graph must equal architecture Mermaid")
    structure = read_json(raw["design/figjam-structure.json"])
    require(structure["fileKey"] == "MhPgfNZu4tnw0CvovGHCwJ" and structure["url"] == "https://www.figma.com/board/" + structure["fileKey"] and structure["source"].startswith("docs/architecture/STRUCTURE.md#"), "FigJam metadata/source")
    require("not deployed services" in structure["kind"], "graph must remain module proposal")
    print("PASS local FigJam XML: 10 nodes, 9 directed edges, exact architecture Mermaid agreement; not deployed services or fresh Figma read")


def checker_self_checks():
    # These are checks of this QA tool only, not of any application parser.
    samples = {"0.00": 0, "-0.01": -1, "9007199254740993.01": 900719925474099301}
    with localcontext() as context:
        context.prec = 80
        for value, oracle in samples.items():
            require(money_minor(cash(value)) == oracle == int(Decimal(value) * 100), "tool exact-value self-check")
    invalid = (None, 1, 1.0, "1", "1.0", "01.00", "1.000", "1e2", "+1.00", "-0.00", "1,00", " 1.00", "NaN")
    for value in invalid:
        try:
            money_minor(cash(value))
        except AssertionError:
            continue
        raise AssertionError("QA checker accepted invalid amount: " + repr(value))
    for scale in (True, 2.0, 3):
        try:
            money_minor({"currency": "KZT", "scale": scale, "amount": "1.00"})
        except AssertionError:
            continue
        raise AssertionError("QA checker accepted invalid scale")
    require(local_date("2024-02-29") == date(2024, 2, 29), "leap-year self-check")
    for value in ("2026-02-29", "2026-04-31", "2026-10-01T00:00:00Z", "2026-1-01"):
        try:
            local_date(value)
        except (AssertionError, ValueError):
            continue
        raise AssertionError("QA checker accepted invalid date")
    print("PASS checker-only: zero/null distinction, extra decimals, large exact value, canonical format, calendar edges")


def main():
    print("APP-001 frozen partial calculation QA; concept/D1/local evidence only; no application or live Figma tests")
    raw, snapshot_bytes = frozen_inputs()
    checker_self_checks()
    fixture = json.loads(raw["design/demo-data.json"].decode("utf-8-sig"), object_pairs_hook=no_duplicate_keys,
                         parse_float=Decimal, parse_constant=reject_constant)
    require(fixture["fixtureId"] == "D1" and fixture["fixtureVersion"] == "1.0.0", "fixture identity")
    metadata = fixture["metadata"]
    require(metadata["label"] == "Демо" and metadata["purpose"] == "design-fixture" and metadata["isRuntimeBackup"] is False, "fixture/runtime distinction")
    require((metadata["currency"], metadata["scale"], metadata["rounding"]) == ("KZT", 2, "not-applied"), "precision/rounding metadata")
    month, snapshot = "2026-10", date(2026, 10, 30)
    start, end = date(2026, 10, 1), date(2026, 11, 1)
    require(metadata["month"] == month and local_date(metadata["snapshotDate"]) == snapshot, "month/snapshot")
    require(local_date(metadata["period"]["startInclusive"]) == start and local_date(metadata["period"]["endExclusive"]) == end, "half-open interval")
    money_count = date_count = 0
    for path, key, value in walk(fixture):
        if isinstance(value, dict) and isinstance(value.get("amount"), str):
            money_minor(value)
            money_count += 1
        if key in {"date", "snapshotDate", "dueDate", "startInclusive", "endExclusive"}:
            local_date(value)
            date_count += 1
        if key == "month":
            require(re.fullmatch(r"[0-9]{4}-[0-9]{2}", value) is not None, path)
            local_date(value + "-01")
            require(value == month, "D1 month mismatch at " + path)
    print(f"PASS canonical MoneyDTO: {money_count}; real LocalDate: {date_count}; month=2026-10, interval=[2026-10-01,2026-11-01), snapshot=2026-10-30")
    entities = sum((fixture[key] for key in ("accounts", "categories", "operations", "goals", "debts")), []) + [fixture["budget"]]
    ids = [entity["id"] for entity in entities]
    require(all(isinstance(identifier, str) and identifier for identifier in ids) and len(ids) == len(set(ids)), "entity IDs must be unique")
    require(all(type(entity["revision"]) is int and entity["revision"] > 0 for entity in entities), "entity revisions")
    accounts = {account["id"]: account for account in fixture["accounts"]}
    categories = {category["id"]: category for category in fixture["categories"]}
    require(set(accounts) == {"account-main"}, "D1 account set")
    account = accounts["account-main"]
    require(account["name"] == "Основной" and account["opening"] is None and account["balanceState"] == "unknown", "unknown opening/balance")
    require((account["currency"], account["scale"]) == ("KZT", 2), "account currency/scale")
    # Independent D1 oracle, transcribed from SCREENS, before reading fixture.expected.
    oracle_operations = {
        ("income", "2026-10-25", "category-salary", 50_000_000),
        ("expense", "2026-10-01", "category-housing", 15_000_000),
        ("expense", "2026-10-12", "category-groceries", 12_000_000),
        ("expense", "2026-10-20", "category-transport", 3_000_000),
    }
    operations = fixture["operations"]
    require(len(operations) == 4, "D1 must contain exactly four operations")
    require({(op["type"], op["date"], op["categoryId"], money_minor(op["amount"])) for op in operations} == oracle_operations, "operation oracle mismatch")
    for op in operations:
        require(op["accountId"] in accounts and op["categoryId"] in categories, "operation reference")
        require(categories[op["categoryId"]]["kind"] == op["type"], "category kind")
        require((op["amount"]["currency"], op["amount"]["scale"]) == (account["currency"], account["scale"]), "operation/account currency")
        require(money_minor(op["amount"]) > 0 and start <= local_date(op["date"]) < end, "operation positivity/period")
    income = sum(money_minor(op["amount"]) for op in operations if op["type"] == "income")
    expense = sum(money_minor(op["amount"]) for op in operations if op["type"] == "expense")
    require((income, expense) == (50_000_000, 30_000_000), "independent monthly totals")
    print("PASS operations=4; income=500000.00; expense=300000.00 KZT (minor: 50000000,30000000)")
    budget = fixture["budget"]
    require((budget["mode"], budget["month"], budget["currency"], budget["scale"]) == ("category", month, "KZT", 2), "budget discriminator")
    plans = {plan["categoryId"]: money_minor(plan["planned"]) for plan in budget["plans"]}
    require(len(plans) == len(budget["plans"]), "duplicate budget category")
    require(plans == {"category-housing": 15_000_000, "category-groceries": 15_000_000, "category-transport": 5_000_000}, "independent category plans")
    spent = {key: sum(money_minor(op["amount"]) for op in operations if op["type"] == "expense" and op["categoryId"] == key) for key in plans}
    require(all(key in categories and categories[key]["kind"] == "expense" for key in plans), "budget category references")
    remaining_by_category = {key: plans[key] - spent[key] for key in plans}
    require(list(remaining_by_category.values()) == [0, 3_000_000, 2_000_000], "independent category remaining")
    planned, remaining = sum(plans.values()), sum(plans.values()) - expense
    require((planned, remaining, sum(remaining_by_category.values())) == (35_000_000, 5_000_000, 5_000_000), "budget arithmetic/double counting")
    print("PASS budget planned=350000.00; spent=300000.00; remaining=50000.00; categories=0.00,30000.00,20000.00 KZT")
    require(income - expense == 20_000_000, "income-expense arithmetic")
    require(not any(key in {"balance", "available", "availableMoney"} for _, key, _ in walk(fixture)), "invented balance/available field")
    print("PASS opening=null -> balance unknown; income-expense=200000.00 KZT is a flow difference, not a balance")
    require(len(fixture["goals"]) == 1, "D1 goal count")
    goal = fixture["goals"][0]
    target, accumulated = money_minor(goal["target"]), money_minor(goal["accumulated"])
    require((target, accumulated) == (60_000_000, 18_000_000), "independent goal inputs")
    progress = Decimal(accumulated) * Decimal(100) / Decimal(target)
    require(progress == Decimal(30) and accumulated * 100 == target * 30, "exact independent goal progress")
    require(goal["snapshotDate"] == snapshot.isoformat() and goal["accumulatedBeforeMonth"] is True and goal["contributionsInMonth"] == [] and goal["ledgerEffect"] == "none", "goal is not an October operation")
    print("PASS goal=180000.00/600000.00 KZT -> exactly 30%; no October contributions or ledger effect")
    require(len(fixture["debts"]) == 2, "D1 debt count")
    debt_totals = {direction: 0 for direction in ("owe", "owedToMe")}
    for debt in fixture["debts"]:
        require(debt["direction"] in debt_totals, "debt direction")
        require(debt["snapshotDate"] == snapshot.isoformat() and debt["recordedBeforeMonth"] is True and debt["repaymentsInMonth"] == [] and debt["ledgerEffect"] == "none", "debt is not an October operation")
        require(debt["historyState"] == "not-provided" and not any(key == "principal" for _, key, _ in walk(debt)), "invented principal/history")
        require(debt["dueDate"] == {"owe": "2026-11-15", "owedToMe": "2026-11-20"}[debt["direction"]], "D1 debt due date")
        debt_totals[debt["direction"]] += money_minor(debt["outstanding"])
    require(debt_totals == {"owe": 8_000_000, "owedToMe": 5_000_000}, "independent separate debt totals")
    print("PASS debt owe=80000.00; owedToMe=50000.00 KZT separately; no invented principal, repayments or ledger effect")
    # expected is supplementary evidence, compared with the independently computed facts.
    expected = fixture["expected"]
    monthly = expected["monthly"]
    for key, result in {"income": income, "expense": expense, "budgetPlanned": planned, "budgetSpent": expense, "budgetRemaining": remaining}.items():
        require(money_minor(monthly[key]) == result, "fixture.expected mismatch: " + key)
    require(len(monthly["categories"]) == len(plans), "expected category count")
    require({row["categoryId"] for row in monthly["categories"]} == set(plans), "expected category references")
    for row in monthly["categories"]:
        key = row["categoryId"]
        require(tuple(money_minor(row[field]) for field in ("planned", "spent", "remaining")) == (plans[key], spent[key], remaining_by_category[key]), "expected category arithmetic")
    expected_snapshot = expected["snapshot"]
    require(expected_snapshot["snapshotDate"] == snapshot.isoformat(), "expected snapshot")
    require(expected_snapshot["goalProgress"] == [{"goalId": goal["id"], "percent": "30", "rounding": "not-applied", "calculation": "180000.00 / 600000.00 * 100 = 30%"}], "expected goal reference/progress")
    require({key: money_minor(value) for key, value in expected_snapshot["debtTotalsByDirection"].items()} == debt_totals, "expected separate debt totals")
    print(f"PASS references/revisions/unique IDs: {len(entities)} entities; fixture.expected agrees with independent oracle")
    require("v0.1.1" in raw["docs/product/CONCEPT.md"].decode("utf-8-sig"), "concept version")
    require(all((ROOT / name).read_bytes() == data for name, data in raw.items()), "input changed during QA run")
    static_design(raw)
    graph_check(raw)
    require(all((ROOT / name).read_bytes() == data for name, data in raw.items()) and (ROOT / SNAPSHOT).read_bytes() == snapshot_bytes, "frozen package changed during QA run")
    print("PASS all 26 frozen input files and snapshot bytes unchanged during run")
    print("RESULT PASS_CHECKED_PARTIAL_SCOPE; LIVE_FIGMA_VISUALS_PROTOTYPE_APP_NOT_PERFORMED; NO_GENERAL_APPROVE")


if __name__ == "__main__":
    main()
