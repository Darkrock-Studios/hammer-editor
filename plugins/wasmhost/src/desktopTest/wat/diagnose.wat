;; A "diagnose" that replies with fixed diagnostics, some of them out of range, so tests can see which
;; the host keeps, fixes as a string, a labelled object, and a number, which is dropped, and a severity.
(module
  (import "extism:host/env" "alloc" (func $alloc (param i64) (result i64)))
  (import "extism:host/env" "store_u8" (func $store_u8 (param i64 i32)))
  (import "extism:host/env" "output_set" (func $output_set (param i64 i64)))
  (memory (export "memory") 1)
  ;; Byte offsets into "Ét the the end": "the the" is bytes 4 to 11, 1 falls inside "É", 99 is past
  ;; the end, and paragraph 7 does not exist.
  (data (i32.const 0) "{\22diagnostics\22:[{\22paragraph\22:0,\22start\22:4,\22end\22:11,\22message\22:\22Repeated word\22,\22fixes\22:[\22the\22,{\22replacement\22:\22\22,\22label\22:\22Remove the repeat\22},7],\22severity\22:\22suggestion\22},{\22paragraph\22:0,\22start\22:1,\22end\22:4,\22message\22:\22Inside a character\22},{\22paragraph\22:0,\22start\22:4,\22end\22:99,\22message\22:\22Past the end\22},{\22paragraph\22:7,\22start\22:0,\22end\22:1,\22message\22:\22No such paragraph\22}],\22unknown\22:true}")
  (global $length i64 (i64.const 371))

  (func (export "diagnose") (result i32)
    (local $block i64) (local $i i64)
    (local.set $block (call $alloc (global.get $length)))
    (block $done
      (loop $next
        (br_if $done (i64.ge_u (local.get $i) (global.get $length)))
        (call $store_u8 (i64.add (local.get $block) (local.get $i)) (i32.load8_u (i32.wrap_i64 (local.get $i))))
        (local.set $i (i64.add (local.get $i) (i64.const 1)))
        (br $next)))
    (call $output_set (local.get $block) (global.get $length))
    (i32.const 0)))
