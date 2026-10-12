;; read: outputs the resource named by its input, or nothing when the package has none by that name.
(module
  (import "extism:host/env" "input_length" (func $input_length (result i64)))
  (import "extism:host/env" "input_load_u8" (func $input_load_u8 (param i64) (result i32)))
  (import "extism:host/env" "alloc" (func $alloc (param i64) (result i64)))
  (import "extism:host/env" "store_u8" (func $store_u8 (param i64 i32)))
  (import "extism:host/env" "length" (func $length (param i64) (result i64)))
  (import "extism:host/env" "output_set" (func $output_set (param i64 i64)))
  (import "extism:host/user" "hammer_resource" (func $resource (param i64) (result i64)))

  (func (export "read") (result i32)
    (local $i i64) (local $len i64) (local $name i64) (local $value i64)
    (local.set $len (call $input_length))
    (local.set $name (call $alloc (local.get $len)))
    (block $done
      (loop $next
        (br_if $done (i64.ge_u (local.get $i) (local.get $len)))
        (call $store_u8 (i64.add (local.get $name) (local.get $i)) (call $input_load_u8 (local.get $i)))
        (local.set $i (i64.add (local.get $i) (i64.const 1)))
        (br $next)))
    (local.set $value (call $resource (local.get $name)))
    (if (i64.ne (local.get $value) (i64.const 0))
      (then (call $output_set (local.get $value) (call $length (local.get $value)))))
    (i32.const 0)))
