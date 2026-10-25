;; "fetch" sends the HTTP request its input holds and outputs the response's body. The initializer sends
;; the request in the package's resource "request", if it has one.
(module
  (import "extism:host/env" "input_length" (func $input_length (result i64)))
  (import "extism:host/env" "input_load_u8" (func $input_load_u8 (param i64) (result i32)))
  (import "extism:host/env" "alloc" (func $alloc (param i64) (result i64)))
  (import "extism:host/env" "store_u8" (func $store_u8 (param i64 i32)))
  (import "extism:host/env" "length" (func $length (param i64) (result i64)))
  (import "extism:host/env" "output_set" (func $output_set (param i64 i64)))
  (import "extism:host/env" "http_request" (func $http_request (param i64 i64) (result i64)))
  (import "extism:host/user" "hammer_resource" (func $resource (param i64) (result i64)))
  (memory (export "memory") 1)
  (data (i32.const 0) "request")

  (func (export "_initialize")
    (local $name i64) (local $i i64) (local $request i64)
    (local.set $name (call $alloc (i64.const 7)))
    (block $done
      (loop $next
        (br_if $done (i64.ge_u (local.get $i) (i64.const 7)))
        (call $store_u8 (i64.add (local.get $name) (local.get $i)) (i32.load8_u (i32.wrap_i64 (local.get $i))))
        (local.set $i (i64.add (local.get $i) (i64.const 1)))
        (br $next)))
    (local.set $request (call $resource (local.get $name)))
    (if (i64.ne (local.get $request) (i64.const 0))
      (then (drop (call $http_request (local.get $request) (i64.const 0))))))

  (func (export "fetch") (result i32)
    (local $i i64) (local $len i64) (local $request i64) (local $body i64)
    (local.set $len (call $input_length))
    (local.set $request (call $alloc (local.get $len)))
    (block $done
      (loop $next
        (br_if $done (i64.ge_u (local.get $i) (local.get $len)))
        (call $store_u8 (i64.add (local.get $request) (local.get $i)) (call $input_load_u8 (local.get $i)))
        (local.set $i (i64.add (local.get $i) (i64.const 1)))
        (br $next)))
    (local.set $body (call $http_request (local.get $request) (i64.const 0)))
    (if (i64.ne (local.get $body) (i64.const 0))
      (then (call $output_set (local.get $body) (call $length (local.get $body)))))
    (i32.const 0)))
