;; An action that outputs the package's resources/reply.json, or nothing when it has none, so tests can
;; pack any reply.
(module
  (import "extism:host/env" "alloc" (func $alloc (param i64) (result i64)))
  (import "extism:host/env" "store_u8" (func $store_u8 (param i64 i32)))
  (import "extism:host/env" "length" (func $length (param i64) (result i64)))
  (import "extism:host/env" "output_set" (func $output_set (param i64 i64)))
  (import "extism:host/user" "hammer_resource" (func $resource (param i64) (result i64)))
  (memory 1)
  (data (i32.const 0) "reply.json")
  (func (export "action") (result i32)
    (local $name i64) (local $i i32) (local $value i64)
    (local.set $name (call $alloc (i64.const 10)))
    (block $done
      (loop $next
        (br_if $done (i32.ge_u (local.get $i) (i32.const 10)))
        (call $store_u8 (i64.add (local.get $name) (i64.extend_i32_u (local.get $i))) (i32.load8_u (local.get $i)))
        (local.set $i (i32.add (local.get $i) (i32.const 1)))
        (br $next)))
    (local.set $value (call $resource (local.get $name)))
    (if (i64.ne (local.get $value) (i64.const 0))
      (then (call $output_set (local.get $value) (call $length (local.get $value)))))
    (i32.const 0)))
