;; Copies its input into a fresh block, sends it to hammer_dispatch, and outputs the reply.
(module
  (import "extism:host/env" "input_length" (func $input_length (result i64)))
  (import "extism:host/env" "input_load_u8" (func $input_load_u8 (param i64) (result i32)))
  (import "extism:host/env" "alloc" (func $alloc (param i64) (result i64)))
  (import "extism:host/env" "store_u8" (func $store_u8 (param i64 i32)))
  (import "extism:host/env" "length" (func $length (param i64) (result i64)))
  (import "extism:host/env" "output_set" (func $output_set (param i64 i64)))
  (import "extism:host/user" "hammer_dispatch" (func $dispatch (param i64) (result i64)))
  (func (export "run") (result i32)
    (local $len i64) (local $i i64) (local $block i64) (local $reply i64)
    (local.set $len (call $input_length))
    (local.set $block (call $alloc (local.get $len)))
    (block $done
      (loop $copy
        (br_if $done (i64.ge_u (local.get $i) (local.get $len)))
        (call $store_u8 (i64.add (local.get $block) (local.get $i)) (call $input_load_u8 (local.get $i)))
        (local.set $i (i64.add (local.get $i) (i64.const 1)))
        (br $copy)))
    (local.set $reply (call $dispatch (local.get $block)))
    (call $output_set (local.get $reply) (call $length (local.get $reply)))
    (i32.const 0)))
