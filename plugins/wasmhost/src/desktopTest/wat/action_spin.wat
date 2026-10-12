;; An action that reports progress once, so a test knows it is running, then never returns.
(module
  (import "extism:host/env" "alloc" (func $alloc (param i64) (result i64)))
  (import "extism:host/env" "store_u8" (func $store_u8 (param i64 i32)))
  (import "extism:host/user" "hammer_progress" (func $progress (param i64)))
  (func (export "action") (result i32)
    (local $report i64)
    (local.set $report (call $alloc (i64.const 2)))
    (call $store_u8 (local.get $report) (i32.const 123))
    (call $store_u8 (i64.add (local.get $report) (i64.const 1)) (i32.const 125))
    (call $progress (local.get $report))
    (loop $forever (br $forever))
    (i32.const 0)))
