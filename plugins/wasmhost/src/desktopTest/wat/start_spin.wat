;; Its start function never returns.
(module
  (func $spin (loop $again (br $again)))
  (start $spin))
