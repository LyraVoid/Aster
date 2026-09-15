#!/usr/bin/env python3
"""Check that the supercall ABI the manager and the daemon were built against is still the
ABI the kernel side provides.

Neither the app nor apd can include a header from the KernelPatch repository, so both keep
their own copy of it: the C header under app/src/main/cpp for the native bridge, and the
constants in apd/src/supercall.rs for the daemon. Copies drift, and drift here does not fail
the build - it fails on a device, as a supercall that arrives with the wrong number or a
struct that is laid out differently. This checks the copies against the kernel side and fails
loudly instead.

Usage:
    scripts/check_kp_abi.py [--kp PATH] [--ref REF] [--repo OWNER/REPO]

    --kp    read the kernel side from a KernelPatch checkout instead of downloading it
    --ref   the revision to compare against (default: the version the manager reports, as read
            from app/src/main/cpp/version, e.g. 0.13.8 - which is also the release tag the
            artifacts come from)
    --repo  the KernelPatch repository the headers are fetched from
"""

import argparse
import pathlib
import re
import sys
import urllib.error
import urllib.request

ROOT = pathlib.Path(__file__).resolve().parent.parent

OUR_C_HEADER = ROOT / "app/src/main/cpp/uapi/scdefs.h"
OUR_CALLS_HEADER = ROOT / "app/src/main/cpp/supercall.h"
OUR_RUST_CALLS = ROOT / "apd/src/supercall.rs"
OUR_VERSION = ROOT / "app/src/main/cpp/version"

KP_HEADER = "kernel/patch/include/uapi/scdefs.h"

NAME = re.compile(r"^#define\s+((?:SUPERCALL|KPM)_[A-Z0-9_]+)", re.M)
VALUE = re.compile(r"^#define\s+((?:SUPERCALL|KPM)_[A-Z0-9_]+)\s+(0x[0-9a-fA-F]+|\d+)", re.M)
RUST_CONST = re.compile(r"^const\s+((?:SUPERCALL|KPM)_[A-Z0-9_]+)\s*:\s*\w+\s*=\s*(0x[0-9a-fA-F_]+|\d+);", re.M)
STRUCT = re.compile(r"^(?:typedef\s+)?struct\s+(\w+)\s*\{(.*?)^\}", re.M | re.S)
FIELD = re.compile(r"^\s+(?:unsigned\s+|signed\s+|const\s+)*[A-Za-z_][\w\s\*]*?\b(\w+)\s*(?:\[|;)", re.M)
RUST_FIELD = re.compile(r"^\s+(?:pub\s+)?(\w+)\s*:", re.M)
REFERENCED = re.compile(r"\b((?:SUPERCALL|KPM)_[A-Z0-9_]+)\b")


def read_version() -> str:
    parts = dict(re.findall(r"#define\s+(MAJOR|MINOR|PATCH)\s+(\d+)", OUR_VERSION.read_text()))
    return "{}.{}.{}".format(parts["MAJOR"], parts["MINOR"], parts["PATCH"])


def kernel_source(ref: str, repo: str, local: pathlib.Path | None) -> str:
    if local is not None:
        return (local / KP_HEADER).read_text()
    url = f"https://raw.githubusercontent.com/{repo}/{ref}/{KP_HEADER}"
    try:
        with urllib.request.urlopen(url, timeout=60) as response:
            return response.read().decode()
    except (urllib.error.URLError, urllib.error.HTTPError) as error:
        sys.exit(f"cannot read {url}: {error}\nPass --kp <checkout> to compare against a local copy.")


def struct_fields(source: str, rust: bool = False) -> dict[str, list[str]]:
    """Field names per struct, in the order they are declared: layout depends on the order."""
    fields = {}
    for name, body in STRUCT.findall(source):
        found = (RUST_FIELD if rust else FIELD).findall(body)
        if found:
            # A Rust mirror is named the Rust way (SuProfile); the kernel side names it su_profile.
            fields[rust_name(name) if rust else name] = found
    return fields


def rust_name(name: str) -> str:
    """SuProfile -> su_profile, so a hand-mirrored Rust struct can be compared by name."""
    return re.sub(r"(?<!^)(?=[A-Z])", "_", name).lower()


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--kp", type=pathlib.Path, default=None)
    parser.add_argument("--ref", default=None)
    parser.add_argument("--repo", default="lyravoid/KernelPatch-Aster")
    args = parser.parse_args()

    ref = args.ref or read_version()
    kernel = kernel_source(ref, args.repo, args.kp)

    # Not every name carries a number: the hello echo is a string the kernel logs. Existence and
    # value are therefore checked separately.
    kernel_names = set(NAME.findall(kernel))
    kernel_defines = {name: int(value, 0) for name, value in VALUE.findall(kernel)}
    kernel_fields = struct_fields(kernel)

    # Each copy is checked on its own: they overlap, and a name both of them define is exactly
    # where one copy can quietly shadow the other.
    ours = {
        "app/src/main/cpp/uapi/scdefs.h": {
            name: int(value, 0) for name, value in VALUE.findall(OUR_C_HEADER.read_text())
        },
        "apd/src/supercall.rs": {
            name: int(value.replace("_", ""), 0)
            for name, value in RUST_CONST.findall(OUR_RUST_CALLS.read_text())
        },
    }

    problems = []
    checked = 0
    for source, values in ours.items():
        for name, value in sorted(values.items()):
            checked += 1
            if name not in kernel_names:
                problems.append(f"{source}: {name} is no longer defined by the kernel side")
            elif kernel_defines[name] != value:
                problems.append(
                    f"{source}: {name} is {value:#x}, the kernel side defines {kernel_defines[name]:#x}"
                )

    # The two copies have to agree with each other as well as with the kernel.
    for name in sorted(set(ours["app/src/main/cpp/uapi/scdefs.h"]) & set(ours["apd/src/supercall.rs"])):
        c_value = ours["app/src/main/cpp/uapi/scdefs.h"][name]
        rust_value = ours["apd/src/supercall.rs"][name]
        if c_value != rust_value:
            problems.append(
                f"{name}: the header says {c_value:#x} while apd/src/supercall.rs says {rust_value:#x}"
            )

    # A name our call wrappers reference but do not define has to exist on the kernel side too.
    for name in sorted(set(REFERENCED.findall(OUR_CALLS_HEADER.read_text()))):
        if name not in kernel_names:
            problems.append(f"{name} is called by app/src/main/cpp/supercall.h but is not defined by the kernel side")

    mirrors = {
        "app/src/main/cpp/uapi/scdefs.h": struct_fields(OUR_C_HEADER.read_text()),
        "apd/src/supercall.rs": struct_fields(OUR_RUST_CALLS.read_text(), rust=True),
    }
    mirrored = 0
    for source, structs in mirrors.items():
        for struct, fields in sorted(structs.items()):
            if struct not in kernel_fields:
                problems.append(f"{source}: struct {struct} no longer exists on the kernel side")
                continue
            mirrored += 1
            # A mirrored struct is laid out by order, so ours has to be a prefix of the kernel's: a
            # field appended at the end is harmless, one inserted in the middle shifts every field
            # after it, and a rename is a different field entirely.
            kernel_here = kernel_fields[struct]
            if kernel_here[: len(fields)] != fields:
                problems.append(
                    f"{source}: struct {struct} is read as ({', '.join(fields)}) but the kernel side "
                    f"declares ({', '.join(kernel_here)})"
                )

    if problems:
        print(f"supercall ABI drift against {args.repo}@{ref}:")
        for problem in problems:
            print(f"  - {problem}")
        return 1

    print(f"supercall ABI matches {args.repo}@{ref}: {checked} numbers, {mirrored} mirrored struct(s)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
