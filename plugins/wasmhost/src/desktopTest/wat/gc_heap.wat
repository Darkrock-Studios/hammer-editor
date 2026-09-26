;; Wasm GC allocation, as Kotlin/Wasm plugins do it. hoard keeps every array it allocates, so it runs out
;; of guest heap; churn allocates 20,000 small arrays, as many small objects as a Kotlin call makes, and
;; keeps none of them.
(module
  (rec
    (type $node (struct (field (ref null $node)) (field (ref $bytes))))
    (type $bytes (array (mut i8))))
  (global $head (mut (ref null $node)) (ref.null $node))

  (func (export "hoard") (result i32)
    (loop $again
      (global.set $head (struct.new $node (global.get $head) (array.new_default $bytes (i32.const 16384))))
      (br $again))
    (i32.const 0))

  (func (export "churn") (result i32)
    (local $i i32)
    (loop $again
      (drop (array.new_default $bytes (i32.const 64)))
      (local.set $i (i32.add (local.get $i) (i32.const 1)))
      (br_if $again (i32.lt_u (local.get $i) (i32.const 20000))))
    (i32.const 0)))
