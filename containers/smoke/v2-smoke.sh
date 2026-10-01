#!/bin/bash
# Smoke test for the x86-64-v2 image chain: does every pre-built binary in this
# layer actually start on the CPU it runs on? Run it INSIDE the image, on shem
# (a Sandy Bridge host without AVX2); the Jenkins "Smoke on shem" stage does.
# See docs/2026-10-01-x86-64-v2-workspace-image-plan.md (steps 4 and 5).
#
# Runs every check even after a failure, prints one line per check, and exits
# non-zero if any failed. A binary killed by SIGILL (exit 132) is reported
# distinctly: it needs a pinned baseline build or has to be dropped from v2.
# Layers add tools (mem: python modules; rust-nix: rust and holochain), so a
# tool that is absent from this layer is skipped with a note, not failed.
set -u

failed=0
TIMEOUT=${SMOKE_TIMEOUT:-60}

# The smoke pod starts the image with `cat`, not the Che entrypoint, so two
# things the entrypoint or a login shell would provide are missing:
#   - /opt/holochain/bin is on PATH only through ~/.bashrc;
#   - the running UID may have no /etc/passwd entry (the rust-nix layer ends on
#     USER 1000), and `import torch` then dies in getpass.getuser(). That is a
#     container-start detail, not a CPU fault, so give getpass a name to find.
[ -d /opt/holochain/bin ] && PATH="/opt/holochain/bin:$PATH"
if ! id -un >/dev/null 2>&1; then
    export USER=user LOGNAME=user
fi

report() { # status name detail
    printf '%-5s %s%s\n' "$1" "$2" "${3:+  -- $3}"
}

verdict() { # name rc output
    local name=$1 rc=$2 out=$3
    out=$(printf '%s' "$out" | head -n 1 | cut -c1-120)
    if [ "$rc" -eq 0 ]; then
        report ok "$name" "$out"
    elif [ "$rc" -eq 132 ]; then
        printf '%s\n' "FAIL (SIGILL: needs a baseline build)  $name"
        failed=$((failed + 1))
    else
        report FAIL "$name" "exit $rc: $out"
        failed=$((failed + 1))
    fi
}

check() { # name command [args...]
    local name=$1 out rc
    shift
    out=$(timeout "$TIMEOUT" "$@" 2>&1)
    rc=$?
    verdict "$name" "$rc" "$out"
}

# check_tool <binary> <args...>: skip when the binary is not in this layer.
check_tool() {
    local bin=$1
    shift
    if ! command -v "$bin" >/dev/null 2>&1; then
        report skip "$bin" "not present in this layer"
        return
    fi
    check "$bin $*" "$bin" "$@"
}

check "glibc starts (/bin/true)" /bin/true
check_tool node --version
check_tool mongod --version
# java prints its version on stderr; check() folds stderr in.
check_tool java -version
check_tool claude --version
check_tool codex --version

# Python modules exist only from udi-plus-mem upward. Exit 3 from the probe
# means "a module is missing", which is a skip; a crash on import is a FAIL.
if command -v python3 >/dev/null 2>&1; then
    probe='import importlib.util, sys
mods = ("torch", "chromadb", "sentence_transformers")
sys.exit(0 if all(importlib.util.find_spec(m) for m in mods) else 3)'
    python3 -c "$probe" >/dev/null 2>&1
    if [ $? -eq 0 ]; then
        check "python3 import torch, chromadb, sentence_transformers" \
            python3 -c "import torch, chromadb, sentence_transformers; print('imported')"
    else
        report skip "python3 import torch, chromadb, sentence_transformers" "modules not present in this layer"
    fi
else
    report skip "python3 imports" "python3 not present"
fi

check_tool rustc --version
check_tool cargo --version
check_tool sccache --version
check_tool holochain --version
check_tool hc --version
check_tool hcterm --version
check_tool chrome --version

echo
if [ "$failed" -eq 0 ]; then
    echo "v2 smoke: all checks passed"
else
    echo "v2 smoke: $failed check(s) FAILED"
fi
[ "$failed" -eq 0 ]
