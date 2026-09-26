;; An action that reports progress once, then outputs "done". A cancelled run stops at the report.
(module
  (import "extism:host/env" "alloc" (func $alloc (param i64) (result i64)))
  (import "extism:host/env" "store_u8" (func $store_u8 (param i64 i32)))
  (import "extism:host/env" "output_set" (func $output_set (param i64 i64)))
  (import "extism:host/user" "hammer_progress" (func $progress (param i64)))
  (memory 1)
  (data (i32.const 0) "{\22fraction\22:0.5,\22message\22:\22Half\22}")
  (data (i32.const 64) "done")
  ;; Copies len bytes of this module's memory from at into a fresh block.
  (func $copy (param $at i32) (param $len i32) (result i64)
    (local $block i64) (local $i i32)
    (local.set $block (call $alloc (i64.extend_i32_u (local.get $len))))
    (block $done
      (loop $next
        (br_if $done (i32.ge_u (local.get $i) (local.get $len)))
        (call $store_u8
          (i64.add (local.get $block) (i64.extend_i32_u (local.get $i)))
          (i32.load8_u (i32.add (local.get $at) (local.get $i))))
        (local.set $i (i32.add (local.get $i) (i32.const 1)))
        (br $next)))
    (local.get $block))
  (func (export "action") (result i32)
    (call $progress (call $copy (i32.const 0) (i32.const 33)))
    (call $output_set (call $copy (i32.const 64) (i32.const 4)) (i64.const 4))
    (i32.const 0)))
