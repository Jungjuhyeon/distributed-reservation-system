# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Distributed reservation system built with Spring Boot 3.5.14, Java 21, Gradle (Groovy DSL).

**Core stack**: Spring Data JPA + MySQL, Spring Data Redis, Lombok

## Build & Run Commands

```bash
./gradlew build          # Build project
./gradlew bootRun        # Run application
./gradlew test           # Run all tests
./gradlew test --tests "com.jung.reservation.SomeTest"  # Run single test class
./gradlew test --tests "com.jung.reservation.SomeTest.methodName"  # Run single test method
./gradlew clean build    # Clean and rebuild
```

## Architecture

- **Package**: `com.jung.reservation`
- **Entry point**: `ReservationApplication.java`
- Fresh skeleton — no domain layers defined yet. Follow standard layered architecture: controller → service → repository → entity.

## Infrastructure

- **MySQL**: Primary datastore (requires running MySQL instance)
- **Redis**: For distributed locking / caching (requires running Redis instance)
- Configure database and Redis connections in `src/main/resources/application.properties`
