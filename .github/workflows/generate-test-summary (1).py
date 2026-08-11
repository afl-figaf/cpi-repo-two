#!/usr/bin/env python3
"""Build a single top-level HTML summary of all per-iFlow Gradle test reports."""
import html
import os
import sys
import glob
import xml.etree.ElementTree as ET
from datetime import datetime, timezone

REPO = os.environ.get("GH_REPO", "")
BRANCH = os.environ.get("GH_BRANCH", "")
RUN_ID = os.environ.get("GH_RUN_ID", "")
RUN_URL = os.environ.get("GH_RUN_URL", "")
OUT = os.environ.get("SUMMARY_OUT", "test-summary.html")

rows = []
tot_tests = tot_fail = tot_skip = 0
tot_time = 0.0

# Every iFlow module that produced JUnit XML.
for results_dir in sorted(glob.glob("*/IntegrationFlow/*/build/test-results/test")):
    iflow_dir = results_dir.split("/build/")[0]
    parts = iflow_dir.split("/")
    package, iflow = parts[0], parts[2]

    tests = failures = skipped = 0
    time_s = 0.0
    failure_msgs = []

    for xml_file in sorted(glob.glob(os.path.join(results_dir, "*.xml"))):
        try:
            root = ET.parse(xml_file).getroot()
        except ET.ParseError:
            continue
        suites = [root] if root.tag == "testsuite" else root.iter("testsuite")
        for suite in suites:
            tests += int(suite.get("tests", 0))
            failures += int(suite.get("failures", 0)) + int(suite.get("errors", 0))
            skipped += int(suite.get("skipped", 0))
            time_s += float(suite.get("time", 0) or 0)
            for tc in suite.iter("testcase"):
                for child in tc:
                    if child.tag in ("failure", "error"):
                        msg = (child.get("message") or child.tag).strip()
                        failure_msgs.append((tc.get("name", ""), msg))

    if tests == 0 and not failure_msgs:
        continue

    # iFlow version from the CPI manifest (Bundle-Version).
    version = "—"
    mf = os.path.join(iflow_dir, "META-INF", "MANIFEST.MF")
    if os.path.isfile(mf):
        with open(mf, "r", encoding="utf-8", errors="replace") as fh:
            for line in fh:
                if line.startswith("Bundle-Version:"):
                    version = line.split(":", 1)[1].strip()
                    break

    report = os.path.join(iflow_dir, "build", "reports", "tests", "test", "index.html")
    report_rel = report if os.path.isfile(report) else None

    tot_tests += tests
    tot_fail += failures
    tot_skip += skipped
    tot_time += time_s
    rows.append(dict(package=package, iflow=iflow, version=version, tests=tests,
                     failures=failures, skipped=skipped, time=time_s,
                     report=report_rel, msgs=failure_msgs))

if not rows:
    print("No test results found; summary not generated.")
    sys.exit(0)

generated = datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M:%S UTC")
overall_ok = tot_fail == 0

def esc(s):
    return html.escape(str(s), quote=True)

out = []
out.append("""<!DOCTYPE html>
<html lang="en"><head><meta charset="utf-8">
<title>Figaf unit test summary</title>
<style>
 body{font-family:-apple-system,Segoe UI,Helvetica,Arial,sans-serif;margin:2rem;color:#1f2328;background:#fff}
 h1{font-size:1.5rem;margin:0 0 .25rem}
 .meta{color:#59636e;font-size:.85rem;margin-bottom:1.25rem}
 .meta a{color:#0969da}
 .cards{display:flex;gap:.75rem;flex-wrap:wrap;margin-bottom:1.5rem}
 .card{border:1px solid #d1d9e0;border-radius:6px;padding:.6rem 1rem;min-width:92px}
 .card .n{font-size:1.35rem;font-weight:600}
 .card .l{font-size:.72rem;color:#59636e;text-transform:uppercase;letter-spacing:.04em}
 .banner{border-radius:6px;padding:.6rem 1rem;margin-bottom:1.25rem;font-weight:600}
 .ok{background:#dafbe1;border:1px solid #4ac26b}
 .bad{background:#ffebe9;border:1px solid #ff818a}
 table{border-collapse:collapse;width:100%;font-size:.9rem}
 th,td{border:1px solid #d1d9e0;padding:.5rem .6rem;text-align:center}
 th{background:#f6f8fa;font-weight:600}
 td.l,th.l{text-align:left}
 tr.fail{background:#fff8f8}
 a{color:#0969da}
 .pass{color:#1a7f37;font-weight:600}
 .failtxt{color:#cf222e;font-weight:600}
 .msg{font-family:ui-monospace,SFMono-Regular,Consolas,monospace;font-size:.78rem;
      color:#cf222e;text-align:left;white-space:pre-wrap;word-break:break-word}
 .none{color:#8c959f}
</style></head><body>""")
out.append("<h1>Figaf unit test summary</h1>")

meta = [f"Generated {esc(generated)}"]
if REPO:
    meta.append(f"repo <strong>{esc(REPO)}</strong>")
if BRANCH:
    meta.append(f"branch <strong>{esc(BRANCH)}</strong>")
if RUN_ID:
    run = f"run <strong>{esc(RUN_ID)}</strong>"
    if RUN_URL:
        run = f'<a href="{esc(RUN_URL)}">{run}</a>'
    meta.append(run)
out.append(f'<div class="meta">{" &middot; ".join(meta)}</div>')

if overall_ok:
    out.append(f'<div class="banner ok">All {tot_tests} test(s) passed across {len(rows)} iFlow(s).</div>')
else:
    bad = sum(1 for r in rows if r["failures"])
    out.append(f'<div class="banner bad">{tot_fail} failure(s) in {bad} of {len(rows)} iFlow(s).</div>')

out.append('<div class="cards">')
for n, l in ((len(rows), "iFlows"), (tot_tests, "Tests"), (tot_fail, "Failures"),
             (tot_skip, "Skipped"), (f"{tot_time:.2f}s", "Duration")):
    out.append(f'<div class="card"><div class="n">{esc(n)}</div><div class="l">{esc(l)}</div></div>')
out.append("</div>")

out.append("<table><thead><tr><th class='l'>Package</th><th class='l'>iFlow</th>"
           "<th>Version</th><th>Tests</th><th>Failures</th><th>Time</th>"
           "<th>Status</th><th>Report</th></tr></thead><tbody>")

for r in sorted(rows, key=lambda x: (-x["failures"], x["package"], x["iflow"])):
    failed = r["failures"] > 0
    status = '<span class="failtxt">&#10007; FAILED</span>' if failed else '<span class="pass">&#10003; PASSED</span>'
    link = f'<a href="{esc(r["report"])}">open</a>' if r["report"] else '<span class="none">n/a</span>'
    out.append(
        f'<tr class="{"fail" if failed else ""}">'
        f'<td class="l">{esc(r["package"])}</td><td class="l">{esc(r["iflow"])}</td>'
        f'<td>{esc(r["version"])}</td><td>{r["tests"]}</td><td>{r["failures"]}</td>'
        f'<td>{r["time"]:.2f}s</td><td>{status}</td><td>{link}</td></tr>'
    )
    for name, msg in r["msgs"]:
        out.append(f'<tr class="fail"><td></td><td colspan="7" class="msg">'
                   f'{esc(name)}\n{esc(msg)}</td></tr>')

out.append("</tbody></table></body></html>")

with open(OUT, "w", encoding="utf-8") as fh:
    fh.write("\n".join(out))

print(f"Wrote {OUT}: {len(rows)} iFlow(s), {tot_tests} test(s), {tot_fail} failure(s)")
