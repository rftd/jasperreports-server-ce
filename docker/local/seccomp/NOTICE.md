# Chromium seccomp profile

Based on the Moby default seccomp profile (Apache-2.0):
https://github.com/moby/profiles/blob/4b8214fc4971e06172938a1f1554ccff6647ee80/seccomp/default.json

Upstream Git blob: `6e5e47e242892aaa290f916ba1045f9213ef0b84`.
The accompanying LICENSE is preserved from the same upstream revision.

OpenJRS change: append one allow rule for `clone`, `setns` and `unshare` so
unprivileged Chromium can create its sandbox namespaces. All other upstream
rules and the default-deny action are preserved. This profile applies only to
the application service; PostgreSQL and the initializer use Docker defaults.
The runtime remains non-root with no-new-privileges enabled. No privileged
container, added SYS_ADMIN capability or disabled Chromium sandbox is used.
