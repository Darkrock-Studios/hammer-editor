;; Never returns.
(module
  (func (export "run") (result i32)
    (loop $forever (br $forever))
    (i32.const 0)))
