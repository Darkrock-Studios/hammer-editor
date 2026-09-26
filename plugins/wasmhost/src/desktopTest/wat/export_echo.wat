;; An "export", a "command", and an "action" that output their request unchanged, so tests can see what a plugin is sent.
(module
  (import "extism:host/env" "input_offset" (func $input_offset (result i64)))
  (import "extism:host/env" "input_length" (func $input_length (result i64)))
  (import "extism:host/env" "output_set" (func $output_set (param i64 i64)))
  (func $echo (export "export") (export "command") (export "action") (result i32)
    (call $output_set (call $input_offset) (call $input_length))
    (i32.const 0)))
