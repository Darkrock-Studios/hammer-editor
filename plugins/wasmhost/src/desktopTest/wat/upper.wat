;; Outputs its input with ASCII letters upper-cased, a byte at a time: the slowest way to move text.
(module
  (import "extism:host/env" "input_length" (func $input_length (result i64)))
  (import "extism:host/env" "input_load_u8" (func $input_load_u8 (param i64) (result i32)))
  (import "extism:host/env" "alloc" (func $alloc (param i64) (result i64)))
  (import "extism:host/env" "store_u8" (func $store_u8 (param i64 i32)))
  (import "extism:host/env" "output_set" (func $output_set (param i64 i64)))
  (func (export "run") (result i32)
    (local $len i64) (local $i i64) (local $out i64) (local $c i32)
    (local.set $len (call $input_length))
    (local.set $out (call $alloc (local.get $len)))
    (block $done
      (loop $next
        (br_if $done (i64.ge_u (local.get $i) (local.get $len)))
        (local.set $c (call $input_load_u8 (local.get $i)))
        (if (i32.and (i32.ge_u (local.get $c) (i32.const 97)) (i32.le_u (local.get $c) (i32.const 122)))
          (then (local.set $c (i32.sub (local.get $c) (i32.const 32)))))
        (call $store_u8 (i64.add (local.get $out) (local.get $i)) (local.get $c))
        (local.set $i (i64.add (local.get $i) (i64.const 1)))
        (br $next)))
    (call $output_set (local.get $out) (local.get $len))
    (i32.const 0)))
