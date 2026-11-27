;; Holds a flag for the length of each call, as a Rust RefCell borrow does, and traps when a call finds it
;; already held: what a module stopped partway through a call leaves behind. "run" counts to the i64 in its
;; first eight input bytes while holding it, "panic" traps while holding it, and "refuse" returns 1 without
;; taking it.
(module
  (import "extism:host/env" "input_load_u64" (func $input_load_u64 (param i64) (result i64)))
  (global $borrowed (mut i32) (i32.const 0))
  (func $borrow
    (if (global.get $borrowed) (then unreachable))
    (global.set $borrowed (i32.const 1)))
  (func (export "run") (result i32)
    (local $n i64) (local $i i64)
    (call $borrow)
    (local.set $n (call $input_load_u64 (i64.const 0)))
    (block $done
      (loop $next
        (br_if $done (i64.ge_u (local.get $i) (local.get $n)))
        (local.set $i (i64.add (local.get $i) (i64.const 1)))
        (br $next)))
    (global.set $borrowed (i32.const 0))
    (i32.const 0))
  (func (export "panic") (result i32)
    (call $borrow)
    unreachable)
  (func (export "refuse") (result i32)
    (i32.const 1))
  (memory 1))
