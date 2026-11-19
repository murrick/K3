#!/usr/bin/env python3
"""Check acknowledged and interrupted canonical private publication in an isolated guest."""
import argparse
import hashlib
import importlib.util
import json
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile

ROOT = Path(__file__).resolve().parents[1]
sys.dont_write_bytecode = True
spec = importlib.util.spec_from_file_location("block", ROOT / "scripts/qualify-context-block-device.py")
block = importlib.util.module_from_spec(spec)
spec.loader.exec_module(block)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument("--marker-window", action="store_true", help="Cut after the canonical marker move and before its barrier")
    mode.add_argument("--trust", action="store_true", help="Qualify the saved trust vector after ACK")
    args = parser.parse_args()
    trust = args.trust
    base = ROOT / "target/persistence-vm"
    manifest = json.loads((base / "manifest.json").read_text())
    if manifest["java_profile"] != "baseline":
        raise ValueError("Prepare the baseline guest first")
    output = ROOT / ("target/context-trust-recovery" if trust else "target/context-private-publication")
    output.mkdir(exist_ok=True)
    run = Path(tempfile.mkdtemp(prefix="run-", dir=output))
    print("Private publication evidence:", run, flush=True)
    root = run / "rootfs"
    shutil.copytree(base / "rootfs", root, symlinks=True)
    staging = ROOT / "kanger-qualification/src/org/kanger/storage/dumb2"
    sources = [staging / ("ContextPrivatePublication" + name + ".java")
               for name in ("Witness", "BlockRunner")]
    runner = "ContextPrivatePublicationBlockRunner"
    if trust:
        runner = "ContextTrustRecoveryBlockRunner"
        sources = [sources[0], staging / "ContextTrustRecoveryWitness.java", staging / (runner + ".java")]
    jar = ROOT / "kanger-console/target/kanger-console-3.3-SNAPSHOT.jar"
    digest = hashlib.sha256(jar.read_bytes()).hexdigest()
    if manifest["sha256"][str(jar.relative_to(ROOT))] != digest or (
        hashlib.sha256((root / "app/kanger.jar").read_bytes()).hexdigest() != digest
    ):
        raise ValueError("Baseline provider mismatch")
    classes = root / "app/private-classes"
    subprocess.run(["javac", "-source", "8", "-target", "8", "-Xlint:-options",
                    "-cp", str(jar), "-d", str(classes), *map(str, sources)], check=True)
    expected = {source.stem + ".class" for source in sources}
    if {p.name for p in classes.rglob("*.class")} != expected:
        raise ValueError("Unexpected private qualification class")
    classpath = "/app/private-classes:/app/kanger.jar"
    marker_inputs = []
    if args.marker_window:
        original = ROOT / "kanger-data-dumb2/src/org/kanger/storage/dumb2/RevisionStore.java"
        source = run / "RevisionStore.java"
        anchor = "            // A visible next marker is not yet a durable acknowledgement."
        code = original.read_text()
        if code.count(anchor) != 1:
            raise ValueError("Canonical marker boundary changed")
        pause = """            if (next == 3 && path.toAbsolutePath().normalize().toString().equals("/store/Source.revision")) {
                if (read(path) != next) throw new AssertionError("Canonical marker selection");
                System.out.println("KANGER_PRIVATE_MARKER_VISIBLE revision=3");
                try {
                    for (;;) Thread.sleep(1000);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Qualification pause interrupted", interrupted);
                }
            }
"""
        source.write_text(code.replace(anchor, pause + anchor))
        overlay = root / "app/marker-overlay"
        subprocess.run(["javac", "-source", "8", "-target", "8", "-Xlint:-options",
                        "-cp", str(jar), "-d", str(overlay), str(source)], check=True)
        compiled = list(overlay.rglob("*.class"))
        if [p.name for p in compiled] != ["RevisionStore.class"]:
            raise ValueError("Unexpected provider overlay")
        marker_inputs = [original, source, *compiled]
    init = root / "sbin/init"
    launch = "/app/classes:/app/kanger.jar org.kanger.storage.dumb2.ContextBlockDeviceRunner"
    if init.read_text().count(launch) != 1:
        raise ValueError("Unexpected guest init")
    entry = classpath + " org.kanger.storage.dumb2." + runner
    init_text = init.read_text().replace(launch, entry)
    if args.marker_window:
        init_text = init_text.replace("export KANGER_HOME=KANGER", """export KANGER_HOME=KANGER
cp=/app/private-classes:/app/kanger.jar
if [ "$phase" = write-resume-native-private ]; then cp=/app/marker-overlay:$cp; fi""")
        init_text = init_text.replace(entry, '"$cp" org.kanger.storage.dumb2.' + runner)
    init.write_text(init_text)
    template = run / "template.ext4"
    with template.open("wb") as image:
        image.truncate(512 * 1024 * 1024)
    subprocess.run(["mkfs.ext4", "-q", "-F", "-m", "0", "-d", str(root), str(template)], check=True)
    controls = block.controls(run)
    previous = "KANGER_BLOCK_PREVIOUS_ACK operation=private previous=2"
    next_ack = "KANGER_BLOCK_NEXT_ACK operation=private revision=3"
    recovered = "KANGER_BLOCK_NATIVE_RECOVERED operation=private previous=2 selected="
    results = {}
    if trust:
        image = run / "stable.ext4"
        subprocess.run(["cp", "--sparse=always", str(template), str(image)], check=True)
        write = block.boot(run, image, "write-trust", manifest, expected_marker="KANGER_TRUST_ACK revision=2")
        read = block.boot(run, image, "read-trust", manifest, expected_marker="KANGER_TRUST_RECOVERED revision=2")
        if write["exit_code"] != -9 or read["exit_code"] != -9 or not read["journal_recovery_seen"]:
            raise ValueError("Cold boot or power cut evidence incomplete")
        results["ack"] = dict(write=write, cold_read=read, selected_revision=2)
        print("TRUST_COLD_RECOVERY_PASS", flush=True)
    cases = ("seed", "ack", "first-write", "last-flush-before", "last-flush-after")
    if args.marker_window:
        cases = ("seed", "marker-window")
    for name in (() if trust else cases):
        case = run / name
        case.mkdir()
        image = case / "stable.ext4"
        subprocess.run(["cp", "--sparse=always", str(template if name == "seed" else seed),
                        str(image)], check=True)
        fault = dict(command=1, ordinal=1, edge="after") if name == "first-write" else None
        if name.startswith("last-flush-"):
            ordinal = results["ack"]["write"]["post_previous_ack_stats"]["flushes"]
            if ordinal < 1:
                raise ValueError("Acknowledged witness reached no FLUSH after release")
            fault = dict(command=3, ordinal=ordinal, edge=name.rsplit("-", 1)[1])
        phase = "write-native-private" if name == "seed" else "write-resume-native-private"
        expected_marker = previous if name == "seed" else next_ack
        if name == "marker-window":
            expected_marker = "KANGER_PRIVATE_MARKER_VISIBLE revision=3"
        write = block.boot(case, image, phase, manifest, previous_marker=previous,
                           expected_marker=expected_marker,
                           release_previous=name != "seed", fault=fault, timeout_seconds=600)
        read = block.boot(case, image, "read-native-private", manifest,
                          expected_marker=recovered, expected_prefix=True, timeout_seconds=600)
        serial = (case / "read-native-private-serial.log").read_text().splitlines()
        selected, = [int(line.removeprefix(recovered)) for line in serial if line.startswith(recovered)]
        if selected not in {"seed": (2,), "ack": (3,)}.get(name, (2, 3)):
            raise ValueError(f"Unexpected selected revision for {name}: {selected}")
        if name == "marker-window" and (write["previous_ack_stats"] is None or write["next_application_ack_observed"]):
            raise ValueError("Exact marker cut must precede publication ACK")
        if fault and (not write["cut_reached"] or write["next_application_ack_observed"]):
            raise ValueError("Expected an actual interrupted publication")
        if write["exit_code"] != -9 or read["exit_code"] != -9 or not read["journal_recovery_seen"]:
            raise ValueError("Cold boot or power cut evidence incomplete")
        if fault:
            ledger = [json.loads(line) for line in (case / (phase + "-block.jsonl")).read_text().splitlines()]
            reached, cut = ledger[-2:]
            if (reached["event"] != "fault_reached" or cut["event"] != "power_cut"
                or any(reached[key] != value for key, value in fault.items())):
                raise ValueError(f"Wrong cut boundary for {name}")
        results[name] = dict(write=write, cold_read=read, selected_revision=selected)
        (case / "result.json").write_text(json.dumps(results[name], indent=2) + "\n")
        if name == "seed":
            seed = image
        print("PRIVATE_PUBLICATION_PASS", name, selected, flush=True)
    inputs = [Path(__file__), Path(spec.origin), jar, *sources, init, template,
              *classes.rglob("*.class"), *marker_inputs, base / "manifest.json"]
    logs = [*run.rglob("*.log"), *run.rglob("*.jsonl"), *run.rglob("*-command.json"),
            *run.rglob("result.json")]
    report = dict(scope=("Saved trust group after ACK" if trust else "Canonical marker window before confirmation" if args.marker_window else "Canonical private publication, post-ACK, first WRITE and last sampled FLUSH cuts"),
                  controls=controls, cases=results, full_cut_matrix=False,
                  producer_marker_overlay=args.marker_window,
                  provider_sha256=digest,
                  sha256={str(p.relative_to(ROOT)): hashlib.sha256(p.read_bytes()).hexdigest()
                          for p in inputs + logs})
    (run / "report.json").write_text(json.dumps(report, indent=2) + "\n")


if __name__ == "__main__":
    main()
