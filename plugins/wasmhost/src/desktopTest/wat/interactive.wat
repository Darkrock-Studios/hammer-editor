;; An interactive action: outputs markdown, one button, and a message.
(module
  (import "extism:host/env" "alloc" (func $alloc (param i64) (result i64)))
  (import "extism:host/env" "store_u8" (func $store_u8 (param i64 i32)))
  (import "extism:host/env" "output_set" (func $output_set (param i64 i64)))
  (memory 1)
  (data (i32.const 0) "{\22markdown\22:\22# Names\22,\22buttons\22:[{\22id\22:\22a\22,\22label\22:\22Aldric\22}],\22message\22:\22Made\22}")
  (func (export "action") (result i32)
    (local $block i64) (local $i i32)
    (local.set $block (call $alloc (i64.const 79)))
    (block $done
      (loop $next
        (br_if $done (i32.ge_u (local.get $i) (i32.const 79)))
        (call $store_u8 (i64.add (local.get $block) (i64.extend_i32_u (local.get $i))) (i32.load8_u (local.get $i)))
        (local.set $i (i32.add (local.get $i) (i32.const 1)))
        (br $next)))
    (call $output_set (local.get $block) (i64.const 79))
    (i32.const 0)))
