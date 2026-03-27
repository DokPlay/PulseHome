# Upgrade Plan: PulseHome (20260327075554)

- **Generated**: 2026-03-27 07:55:54
- **HEAD Branch**: analyzer
- **HEAD Commit ID**: e3954dc

## Available Tools

**JDKs**
- JDK 21.0.9: `C:\Program Files\Eclipse Adoptium\jdk-21.0.9.10-hotspot\bin` (current JAVA_HOME, used in Step 2 baseline)
- JDK 25.0.2: `C:\Users\serrg\AppData\Roaming\Code\User\globalStorage\pleiades.java-extension-pack-jdk\java\latest\bin` (target JDK, used from Step 3 onwards)

**Build Tools**
- Maven 3.9.11: `C:\tools\apache-maven-3.9.11\bin` (no wrapper present; recommended guideline is Maven 4.0+ for Java 25, but Maven 3.9.11 is latest available and maven-compiler-plugin 3.13.0 handles `--release 25` correctly)

## Guidelines

> Note: You can add any specific guidelines or constraints for the upgrade process here if needed, bullet points are preferred.

## Options

- Working branch: appmod/java-upgrade-20260327075554
- Run tests before and after the upgrade: true

## Upgrade Goals

- Upgrade Java from 21 to 25 (latest LTS as of March 2026)

### Technology Stack

| Technology/Dependency     | Current         | Min Compatible | Why Incompatible                                                                 |
| ------------------------- | --------------- | -------------- | -------------------------------------------------------------------------------- |
| Java                      | 21              | 25             | User requested: upgrade to latest LTS                                            |
| Maven                     | 3.9.11          | 3.9.11         | Guideline recommends 4.0+ for Java 25; Maven 4.x not available via install tool; 3.9.11 is latest 3.x and handles --release 25 via compiler plugin |
| maven-compiler-plugin     | 3.13.0          | 3.13.0         | Compatible; supports `--release 25`                                              |
| maven-surefire-plugin     | 3.2.5           | 3.1.0          | Compatible                                                                       |
| Spring Boot               | 3.3.5           | 3.2.0          | Compatible with Java 25 (requires Java 17+)                                      |
| Apache Avro               | 1.11.5          | 1.11.5         | Compatible with Java 25                                                          |
| Protobuf Java             | 3.25.5          | 3.25.5         | Compatible                                                                       |
| gRPC                      | 1.68.1          | 1.68.1         | Compatible                                                                       |
| grpc-spring-boot-starter  | 3.1.0.RELEASE   | 3.1.0.RELEASE  | Compatible (net.devh Spring Boot 3.x line)                                       |
| javax.annotation-api      | 1.3.2           | 1.3.2          | Provided scope; used for gRPC generated code; no Java 25 breaking changes        |
| protobuf-maven-plugin     | 0.6.1           | 0.6.1          | Compatible for code generation; invokes protoc binary                            |

### Derived Upgrades

- `java.version` property in parent `pom.xml`: `21` → `25` (drives `maven.compiler.release` for all modules)

## Upgrade Steps

- **Step 1: Setup Environment**
  - **Rationale**: Confirm JDK 25.0.2 is available on this machine. No installations required since JDK 25 is already present.
  - **Changes to Make**:
    - [ ] Verify JDK 25.0.2 at `C:\Users\serrg\AppData\Roaming\Code\User\globalStorage\pleiades.java-extension-pack-jdk\java\latest\bin`
  - **Verification**:
    - Command: `"C:\Users\serrg\AppData\Roaming\Code\User\globalStorage\pleiades.java-extension-pack-jdk\java\latest\bin\java" -version`
    - Expected: Java 25-class version output

---

- **Step 2: Setup Baseline**
  - **Rationale**: Establish pre-upgrade compilation and test results with current JDK 21 for comparison.
  - **Changes to Make**:
    - [ ] Stash any uncommitted changes
    - [ ] Run compile + test with JDK 21 and document results
  - **Verification**:
    - Command: `mvn clean test-compile -q` then `mvn clean test`
    - JDK: `C:\Program Files\Eclipse Adoptium\jdk-21.0.9.10-hotspot\bin`
    - Expected: Document SUCCESS/FAILURE and test counts (acceptance baseline)

---

- **Step 3: Upgrade Java Version to 25**
  - **Rationale**: Update `java.version` to 25 in the parent POM; all modules inherit `maven.compiler.release` from this property. This single change drives the entire compilation target change.
  - **Changes to Make**:
    - [ ] In `pom.xml` (root): change `<java.version>21</java.version>` → `<java.version>25</java.version>`
    - [ ] Fix any compilation errors that arise from stricter Java 25 checks or removed APIs
  - **Verification**:
    - Command: `mvn clean test-compile -q`
    - JDK: `C:\Users\serrg\AppData\Roaming\Code\User\globalStorage\pleiades.java-extension-pack-jdk\java\latest\bin`
    - Expected: Compilation SUCCESS for all modules (tests may fail — fixed in Step 4)

---

- **Step 4: Final Validation**
  - **Rationale**: Verify all upgrade goals are met; full test suite must pass at 100% with JDK 25.
  - **Changes to Make**:
    - [ ] Verify `java.version=25` in root `pom.xml`
    - [ ] Resolve any TODOs or workarounds from Step 3
    - [ ] Run full test suite; fix ALL failures iteratively
  - **Verification**:
    - Command: `mvn clean test`
    - JDK: `C:\Users\serrg\AppData\Roaming\Code\User\globalStorage\pleiades.java-extension-pack-jdk\java\latest\bin`
    - Expected: Compilation SUCCESS + 100% tests pass

## Key Challenges

- **Maven 3.9.11 running on JDK 25**
  - **Challenge**: The upgrade guideline recommends Maven 4.0+ for Java 25. Maven 4.0.x is not installable via available tools.
  - **Strategy**: Maven 3.9.11 is the latest 3.x release and runs on newer JVMs without issues; `maven-compiler-plugin` 3.13.0 handles source/release 25 independently. If Maven itself fails to start on JDK 25, switch `JAVA_HOME` back to JDK 21 while setting `--release 25` via the compiler plugin property.

- **Potential Java 25 API removals**
  - **Challenge**: Java 25 may deprecate or remove APIs used by dependencies (e.g. legacy reflection APIs, Security Manager removed in Java 17+, finalization references).
  - **Strategy**: Compile with JDK 25 and resolve any errors. All key dependencies (Spring Boot 3.3.x, gRPC 1.68.x, Avro 1.11.x) already target Java 17+ and are expected to be compatible.

## Plan Review

- All sections populated.
- JDK 25.0.2 already present — no installation step needed beyond verification.
- Single-property change (`java.version=25`) drives all modules through inherited `maven.compiler.release`.
- No intermediate versions required: Java 21 → 25 is a 4-version step within the modern Java cadence; all key deps already target 17+.
- No known unfixable limitations.
