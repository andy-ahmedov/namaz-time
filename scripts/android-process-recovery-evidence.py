#!/usr/bin/env python3
"""Opt-in SIGKILL evidence on an explicitly selected debug Android emulator."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import time
import uuid

ROOT = Path(__file__).resolve().parents[1]
PACKAGE = "ru.namaztime.tv.debug"
PROCESS = PACKAGE + ":recoveryEvidence"
COMPONENT = PACKAGE + "/ru.namaztime.tv.presentation.ProcessRecoveryEvidenceActivity"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", required=True)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    output = args.output.resolve()
    if output.is_relative_to(ROOT):
        parser.error("Evidence output must be outside the repository")
    output.mkdir(parents=True, exist_ok=False)

    def adb(*parts, data=None, check=True):
        return subprocess.run(["adb", "-s", args.serial, *parts], input=data,
                              stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                              timeout=30, check=check)

    def shell(*parts, check=True):
        return adb("shell", *parts, check=check).stdout.decode().strip()

    if shell("getprop", "ro.kernel.qemu") != "1":
        raise RuntimeError("Refusing non-emulator target")
    shell("run-as", PACKAGE, "id")  # Must be a debuggable installation.
    if shell("pidof", PROCESS, check=False):
        raise RuntimeError("An evidence process is already running; inspect it before retrying")

    def protected_hashes():
        result = {}
        for name in ("databases/namaz-time.db", "databases/namaz-time.db-wal",
                     "files/datastore/operator_preferences.preferences_pb"):
            read = adb("shell", "-T", "run-as", PACKAGE, "cat", name, check=False)
            if read.returncode:
                raise RuntimeError("Expected existing debug application state: " + name)
            result[name] = hashlib.sha256(read.stdout).hexdigest()
        return result

    def kill_evidence(pid):
        if not isinstance(pid, int) or pid <= 1:
            raise RuntimeError("Invalid evidence PID")
        if shell("pidof", PROCESS, check=False) != str(pid):
            raise RuntimeError("PID no longer identifies the evidence process")
        shell("run-as", PACKAGE, "kill", "-9", str(pid))
        for _ in range(100):
            if not shell("pidof", PROCESS, check=False):
                return
            time.sleep(0.1)
        raise RuntimeError("Evidence process did not exit")

    before = protected_hashes()
    apk_path = shell("pm", "path", PACKAGE).removeprefix("package:")
    if not apk_path.startswith("/data/app/") or "\n" in apk_path:
        raise RuntimeError("Expected a single installed APK")
    installed_apk_sha256 = shell("sha256sum", apk_path).split()[0]
    results = []
    for boundary in ("transaction", "committed"):
        run_id = uuid.uuid4().hex
        remote = "files/recovery-evidence/" + run_id
        shell("run-as", PACKAGE, "mkdir", "-p", remote)
        fixtures = {
            "baseline.json": ROOT / "examples/synthetic-prayer-snapshot.json",
            "signed.json": ROOT / "fixtures/verification/synthetic-signed-snapshot.json",
            "public-key.json": ROOT / "fixtures/verification/phase1-public-key.json",
        }
        for name, source in fixtures.items():
            # All shell fragments are fixed literals plus a validated UUID hex string.
            adb("shell", "run-as", PACKAGE, "sh", "-c",
                "'cat > " + remote + "/" + name + "'", data=source.read_bytes())

        def launch(phase):
            shell("am", "start", "-W", "-n", COMPONENT, "--es", "run_id", run_id,
                  "--es", "boundary", boundary, "--es", "phase", phase)

        def wait_marker(name):
            deadline = time.monotonic() + 45
            while time.monotonic() < deadline:
                error = adb("shell", "-T", "run-as", PACKAGE, "cat", remote + "/error.json", check=False)
                if error.returncode == 0:
                    raise RuntimeError(error.stdout.decode())
                value = adb("shell", "-T", "run-as", PACKAGE, "cat", remote + "/" + name + ".json", check=False)
                if value.returncode == 0:
                    try:
                        return json.loads(value.stdout)
                    except json.JSONDecodeError:
                        pass  # Writer has not completed yet.
                time.sleep(0.2)
            raise RuntimeError("Timed out waiting for " + boundary + "/" + name)

        launch("prepare")
        ready = wait_marker("ready")
        kill_evidence(ready["pid"])
        launch("resume")
        passed = wait_marker("passed")
        if ready["pid"] == passed["pid"] or passed["network_requests"] != 0:
            raise RuntimeError("Recovery did not run offline in a new process")
        results.append({"run_id": run_id, "ready": ready, "passed": passed})
        (output / (boundary + ".json")).write_text(json.dumps(results[-1], indent=2) + "\n")
        kill_evidence(passed["pid"])

    after = protected_hashes()
    report = {"serial": args.serial, "sdk": shell("getprop", "ro.build.version.sdk"),
              "installed_apk_sha256": installed_apk_sha256,
              "commit": subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip(),
              "workspace_dirty": bool(subprocess.check_output(["git", "status", "--porcelain"], cwd=ROOT)),
              "protected_before": before, "protected_after": after, "results": results}
    (output / "report.json").write_text(json.dumps(report, indent=2) + "\n")
    if before != after:
        raise RuntimeError("Existing application state changed; inspect report before proceeding")
    print("process-recovery: PASS (two SIGKILL boundaries, offline resume, preserved preferences/state)")


if __name__ == "__main__":
    main()
