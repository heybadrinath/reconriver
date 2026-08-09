# ADR 0000: Pin the verified 2026 stable Java baseline

- Status: accepted
- Date: 2026-08-09

## Decision

Use Java 21, Spring Boot 4.1.0, Spring Modulith 2.1.0, Maven 3.9.16 through Maven Wrapper 3.3.4, and springdoc-openapi 3.1.0. Let Spring Boot manage Spring Batch 6.0.4, Testcontainers 2.0.5, compiler 3.15.0, and Surefire/Failsafe 3.5.6.

## Compatibility evidence

- [Spring Boot stable versions and Java requirements](https://spring.io/projects/spring-boot/)
- [Spring Modulith 2.1.0 source baseline](https://github.com/spring-projects/spring-modulith/blob/2.1.0/pom.xml)
- [Spring Boot 4.1.0 managed dependencies](https://github.com/spring-projects/spring-boot/blob/v4.1.0/platform/spring-boot-dependencies/build.gradle)
- [springdoc-openapi 3.1.0 release](https://github.com/springdoc/springdoc-openapi/releases/tag/v3.1.0)
- [Apache Maven stable download](https://maven.apache.org/download.cgi)
- [Maven Wrapper stable download](https://maven.apache.org/tools/wrapper/download.cgi)

## JUnit interpretation

Spring Boot 4.1.0 manages JUnit Jupiter 6.0.3. The requirement for “JUnit 5” is interpreted as the Jupiter programming model rather than a literal older 5.x pin. Pinning JUnit 5 would conflict with the confirmed latest stable Spring line and create unnecessary dependency-management drift.
