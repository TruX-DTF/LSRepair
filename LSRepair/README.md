Live Search Fixer
-----------------

1. Fix buggy methods with signature-similar methods.
  - `java -cp "target/dependency/*" -Xmx1g live.search.main.MainProcess <detailed parameters see the source code file>`

2. Fix buggy methods with syntactic- or semantic- similar methods.
  - `java -cp "target/dependency/*" -Xmx1g live.search.syn.sem.fixer.SynSemFixer <detailed parameters see the source code file>`