;; Its start function calls itself until the stack runs out.
(module
  (func $down (call $down))
  (start $down))
