;; Imports WASI, which the host refuses.
(module
  (import "wasi_snapshot_preview1" "fd_write" (func $fd_write (param i32 i32 i32 i32) (result i32)))
  (func (export "run") (result i32) (i32.const 0)))
