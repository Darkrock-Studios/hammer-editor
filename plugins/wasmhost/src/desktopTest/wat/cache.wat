;; get: outputs the cached value of the key its input holds. set: its input is key=value, and an
;; empty value removes the key.
(module
  (import "extism:host/env" "input_length" (func $input_length (result i64)))
  (import "extism:host/env" "input_load_u8" (func $input_load_u8 (param i64) (result i32)))
  (import "extism:host/env" "alloc" (func $alloc (param i64) (result i64)))
  (import "extism:host/env" "store_u8" (func $store_u8 (param i64 i32)))
  (import "extism:host/env" "length" (func $length (param i64) (result i64)))
  (import "extism:host/env" "output_set" (func $output_set (param i64 i64)))
  (import "extism:host/user" "hammer_cache_get" (func $cache_get (param i64) (result i64)))
  (import "extism:host/user" "hammer_cache_set" (func $cache_set (param i64 i64)))

  ;; A new block holding input bytes [from, from + len).
  (func $copy (param $from i64) (param $len i64) (result i64)
    (local $i i64) (local $block i64)
    (local.set $block (call $alloc (local.get $len)))
    (block $done
      (loop $next
        (br_if $done (i64.ge_u (local.get $i) (local.get $len)))
        (call $store_u8 (i64.add (local.get $block) (local.get $i))
          (call $input_load_u8 (i64.add (local.get $from) (local.get $i))))
        (local.set $i (i64.add (local.get $i) (i64.const 1)))
        (br $next)))
    (local.get $block))

  (func (export "get") (result i32)
    (local $value i64)
    (local.set $value (call $cache_get (call $copy (i64.const 0) (call $input_length))))
    (if (i64.ne (local.get $value) (i64.const 0))
      (then (call $output_set (local.get $value) (call $length (local.get $value)))))
    (i32.const 0))

  (func (export "set") (result i32)
    (local $len i64) (local $eq i64)
    (local.set $len (call $input_length))
    (block $found
      (loop $next
        (br_if $found (i32.eq (call $input_load_u8 (local.get $eq)) (i32.const 61)))
        (local.set $eq (i64.add (local.get $eq) (i64.const 1)))
        (br $next)))
    (call $cache_set
      (call $copy (i64.const 0) (local.get $eq))
      (call $copy (i64.add (local.get $eq) (i64.const 1)) (i64.sub (i64.sub (local.get $len) (local.get $eq)) (i64.const 1))))
    (i32.const 0)))
