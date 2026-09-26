;; Counts to the i64 in its first eight input bytes and outputs nothing. For timing the interpreter.
(module
  (import "extism:host/env" "input_load_u64" (func $input_load_u64 (param i64) (result i64)))
  (func (export "run") (result i32)
    (local $n i64) (local $i i64) (local $sum i64)
    (local.set $n (call $input_load_u64 (i64.const 0)))
    (block $done
      (loop $next
        (br_if $done (i64.ge_u (local.get $i) (local.get $n)))
        (local.set $sum (i64.add (local.get $sum) (i64.mul (local.get $i) (i64.const 3))))
        (local.set $i (i64.add (local.get $i) (i64.const 1)))
        (br $next)))
    (i32.wrap_i64 (i64.and (local.get $sum) (i64.const 0))))
  (func (export "grow") (result i32)
    (memory.grow (i32.const 2000)))
  (memory 1))
