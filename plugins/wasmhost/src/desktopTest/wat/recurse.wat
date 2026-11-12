;; Calls itself until the stack runs out.
(module
  (func $down (call $down))
  (func (export "run") (result i32)
    (call $down)
    (i32.const 0)))
