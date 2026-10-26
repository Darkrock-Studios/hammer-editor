;; Pushes against the host's per-plugin memory limits; each export reads u64 arguments from its input.
;; "alloc" allocates one kernel block of the size given. "vars" sets the count given of variables, keyed
;; by index, to one value of the size given. "log" logs one message of the size given, the count given of times.
(module
  (import "extism:host/env" "alloc" (func $alloc (param i64) (result i64)))
  (import "extism:host/env" "store_u8" (func $store_u8 (param i64 i32)))
  (import "extism:host/env" "input_load_u64" (func $input_u64 (param i64) (result i64)))
  (import "extism:host/env" "var_set" (func $var_set (param i64 i64)))
  (import "extism:host/env" "log_info" (func $log_info (param i64)))

  ;; Four ASCII letters spelling $i in base 64, so every index under 64^4 has its own key.
  (func $key (param $i i64) (result i64)
    (local $block i64) (local $digit i64)
    (local.set $block (call $alloc (i64.const 4)))
    (block $done
      (loop $next
        (br_if $done (i64.ge_u (local.get $digit) (i64.const 4)))
        (call $store_u8
          (i64.add (local.get $block) (local.get $digit))
          (i32.add (i32.const 65) (i32.wrap_i64 (i64.and (local.get $i) (i64.const 63)))))
        (local.set $i (i64.shr_u (local.get $i) (i64.const 6)))
        (local.set $digit (i64.add (local.get $digit) (i64.const 1)))
        (br $next)))
    (local.get $block))

  (func (export "alloc") (result i32)
    (drop (call $alloc (call $input_u64 (i64.const 0))))
    (i32.const 0))

  (func (export "vars") (result i32)
    (local $count i64) (local $value i64) (local $i i64)
    (local.set $count (call $input_u64 (i64.const 0)))
    (local.set $value (call $alloc (call $input_u64 (i64.const 8))))
    (block $done
      (loop $next
        (br_if $done (i64.ge_u (local.get $i) (local.get $count)))
        (call $var_set (call $key (local.get $i)) (local.get $value))
        (local.set $i (i64.add (local.get $i) (i64.const 1)))
        (br $next)))
    (i32.const 0))

  (func (export "log") (result i32)
    (local $message i64) (local $count i64) (local $i i64)
    (local.set $message (call $alloc (call $input_u64 (i64.const 0))))
    (local.set $count (call $input_u64 (i64.const 8)))
    (block $done
      (loop $next
        (br_if $done (i64.ge_u (local.get $i) (local.get $count)))
        (call $log_info (local.get $message))
        (local.set $i (i64.add (local.get $i) (i64.const 1)))
        (br $next)))
    (i32.const 0)))
