Data Prepare
-------------
### A. Parse Java code and create search space for signature-similar methods.
1. Getting all Java code files to accelerate the process of parsing code.
  - `cd ../JavaCodeParser`
  - `mvn compile`
  - `mvn install`
  - `cd ../DataPreparer`
  - `mvn compile`
  - `mvn dependency:copy-dependencies`
  - `mvn package`
  - `java -cp "target/dependency/*" -Xmx1g data.javaFile.getter.MainProcess <projectsPath> <outputPath> <numberOfWorkers>`

2. Parse all Java code files to obtain all method info.
  - `java -cp "target/dependency/*" -Xmx1g data.javaCode.akka.parser.MainParser <output-file-of-previous-step> <outputPath> <numberOfWorkers> 2`
  
3. Create search space for signature-similar methods.
  - `java -cp "target/dependency/*" -Xmx1g data.method.signature.SignatureSearchSpace <output-path-of-previous-step> <numberOfWorkers>`

### B. Parse suspicious methods.
1. Read info of all suspicious methods that contain the suspicious code lines.
  - `java -cp "target/dependency/*" -Xmx1g data.suspicious.methods.SuspiciousMethod`

### C. Create search space for syntactic-similar methods.
1. Learn method code features with CNNs.
  - `cd ../FeatureLearner`
  - `mvn compile`
  - `mvn dependency:copy-dependencies`
  - `java -cp "target/dependency/*" -Xmx<BigMemory>g method.body.feature.learning.Main <The specific parameters see the original code file>`
2. Create search space for syntactic-similar methods.
  - `cd ../DataPreparer`
  - `java -cp "target/dependency/*" -Xmx2g data.method.syntactic.SyntacticSearchSpace <The specific parameters see the original code file>`

### D. Create search space for semantic-similar methods.
1. Search methods semantic-similar to suspicious methods with [Facoy](https://github.com/facoy/facoy).

2. Parse Facoy results to create search space for semantic-similar methods.
  - `java -cp "target/dependency/*" -Xmx1g data.method.semantic.SemanticSearchSpace <outputPath>
  
  