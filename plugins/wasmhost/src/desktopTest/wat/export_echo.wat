;; An "export" and a "command" that output their request unchanged, and an "action" that outputs a page
;; whose state is its request, so tests can see what a plugin is sent.
(module
  (import "extism:host/env" "input_offset" (func $input_offset (result i64)))
  (import "extism:host/env" "input_length" (func $input_length (result i64)))
  (import "extism:host/env" "input_load_u8" (func $input_load_u8 (param i64) (result i32)))
  (import "extism:host/env" "alloc" (func $alloc (param i64) (result i64)))
  (import "extism:host/env" "store_u8" (func $store_u8 (param i64 i32)))
  (import "extism:host/env" "output_set" (func $output_set (param i64 i64)))
  (memory 1)
  ;; 17 bytes before the request, and 2 after it.
  (data (i32.const 0) "{\22page\22:{\22state\22:")
  (data (i32.const 32) "}}")
  (func $echo (export "export") (export "command") (result i32)
    (call $output_set (call $input_offset) (call $input_length))
    (i32.const 0))
  (func (export "action") (result i32)
    (local $length i64) (local $block i64) (local $i i64)
    (local.set $length (call $input_length))
    (local.set $block (call $alloc (i64.add (local.get $length) (i64.const 19))))
    (block $done
      (loop $next
        (br_if $done (i64.ge_u (local.get $i) (i64.const 17)))
        (call $store_u8 (i64.add (local.get $block) (local.get $i)) (i32.load8_u (i32.wrap_i64 (local.get $i))))
        (local.set $i (i64.add (local.get $i) (i64.const 1)))
        (br $next)))
    (local.set $i (i64.const 0))
    (block $done
      (loop $next
        (br_if $done (i64.ge_u (local.get $i) (local.get $length)))
        (call $store_u8 (i64.add (local.get $block) (i64.add (i64.const 17) (local.get $i))) (call $input_load_u8 (local.get $i)))
        (local.set $i (i64.add (local.get $i) (i64.const 1)))
        (br $next)))
    (call $store_u8 (i64.add (local.get $block) (i64.add (i64.const 17) (local.get $length))) (i32.load8_u (i32.const 32)))
    (call $store_u8 (i64.add (local.get $block) (i64.add (i64.const 18) (local.get $length))) (i32.load8_u (i32.const 33)))
    (call $output_set (local.get $block) (i64.add (local.get $length) (i64.const 19)))
    (i32.const 0)))
