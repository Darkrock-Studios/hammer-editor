;; A module whose memory starts at 1100 pages, 68.75 MiB: past Hammer's default limit, so it loads
;; only for a manifest that asks for more.
(module
  (memory (export "memory") 1100)
  (func (export "action") (result i32) (i32.const 0)))
