;; A module whose table starts at 100,001 entries: one past Hammer's limit.
(module
  (table 100001 funcref)
  (func (export "action") (result i32) (i32.const 0)))
