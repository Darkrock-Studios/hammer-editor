;; Tries to grow its table by a million entries; returns what table.grow answers.
(module
  (table $refs 1 funcref)
  (func (export "grow") (result i32)
    (table.grow $refs (ref.null func) (i32.const 1000000))))
