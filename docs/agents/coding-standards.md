# Coding Standards

This document specifies the coding conventions that agents must follow when working with this Java/Spring Boot codebase. These standards are enforced by **Checkstyle**, **Spotless (Google Java Format)**, and **IDE settings**.

## Overview

This project enforces **Google Java Style** with additional custom rules. Agents should never ignore these standards—they are automatically enforced in the CI pipeline via `gradlew check` which runs:
- `checkstyleMain` / `checkstyleTest` - validates Checkstyle rules
- `spotlessApply` - formats code to Google Java Style

## Tools

| Tool | Purpose | Configuration Location |
|------|----------|----------------------|
| **Checkstyle** | Static analysis, naming conventions, whitespace, Javadoc | `checkstyle.xml` + `checkstyle-suppressions.xml` |
| **Spotless** | Auto-formatting to Google Java Style 1.32.0 | `build.gradle` |
| **IDE** | IntelliJ/Android Studio with Checkstyle plugin | `.idea/checkstyle-xml.xml` (optional) |

## Quick Reference for Agents

### 1. File Organization

```
src/main/java/com/dendi/peony/
├── PeonyApplication.java          # Main Spring Boot app
├── configuration/                  # Configuration classes
├── controller/                     # REST endpoints
├── dto/                            # Data Transfer Objects
├── entity/                         # JPA entities
├── repository/                     # Spring Data repositories
└── service/                        # Business logic
```

### 2. Package Naming Convention

- **Structure**: `com.dendi.peony.<module>` (lowercase, no underscores)
- **Layers**: Follow standard Spring layers: `controller` → `service` → `repository` → `entity`
- **DTOs/Data classes**: Use `dto/`, `data/`, or `model/` packages

### 3. Naming Conventions

#### Type Names (Classes, Interfaces)
| Rule | Example |
|------|---------|
| Start with uppercase letter | `UserController.java` |
| No underscores | `UserController.java` ✓ |
| DomainCamelCase for enums | `StatusEnum.java` ✓ |
| Type names must not contain abbreviations | `StringBuffer` ✗ → `StringBuilder` ✓ |

#### Members (Fields, Methods, Properties)
| Rule | Example |
|------|---------|
| Start with lowercase letter | `getUser()` ✓ |
| No underscores | `getUserName()` ✓ |
| LowerCamelCase | `firstName`, `lastName` ✓ |
| Single-letter only in params/locals | `List<String> items = ...; for (String item : items)` |

#### Parameters & Local Variables
- Same rules as members
- Prefer descriptive names over generic ones: `username` not `u`, `orderId` not `id`
- **Prefer `var` for local variables** when type is obvious and doesn't reduce readability
  - ✓ `var list = new ArrayList<>();` or `List<String> list = ...;`
  - ✗ `java.util.List list = ...` (use imports, not FQN)
  - ✓ `var userId = user.getId();` 
  - ✗ `String s = null;` (avoid shadowing with var if type is clear)
  
#### Type Parameters
| Context | Pattern | Example |
|---------|---------|---------|
| Classes/Interfaces | `T`, `E`, `K`, `V` | `class Repository<T>` |
| Methods | `T`, `R` | `<T> T process(T input)` |

#### Constants
| Rule | Example |
|------|---------|
| UPPERCASE with underscores | `public static final String STATUS_ENUM = ...` |
| Single letter allowed for simple ones | `private static final int MAX_RETRIES = 3;` |

#### Imports
- **Never use star imports** (`import java.util.*;`) ✗
- **Order**: 1) Static imports, 2) Third-party packages, 3) Java packages
- **Separate groups alphabetically within each category**

### 4. Code Structure

#### Use Imports, Not Fully Qualified Names
- **Always import types**, never use fully qualified names (FQN)
- Import individual classes, not wildcards
- This improves readability and maintains consistency

```java
// ❌ WRONG: FQN everywhere
java.util.List<String> list = new java.util.ArrayList<>();
java.lang.String name = ...;
java.util.function.Function<String, String> mapper = ...;

// ✅ CORRECT: Use imports
import java.util.List;
import java.util.ArrayList;
import java.lang.String; // Yes, this is allowed and recommended
import java.util.function.Function;

List<String> list = new ArrayList<>();
String name = ...;
Function<String, String> mapper = ...;
```

#### Class Layout (Checkstyle enforced)

```java
// 1. Package declaration
package com.dendi.peony.controller;

// 2. Imports (grouped, separated, no wildcards)
import java.util.ArrayList;           // Standard Java
import java.util.List;                // Use imports, not FQN!
import java.util.Collections;         // More specific: java.util.Collection ✗
import static java.lang.Math.max;     // Static imports separate

// 3. Empty line before class

// 4. Class/Javadoc
/**
 * UserController provides REST endpoints for user management.
 */
public class UserController {

    // 5. Instance variables (no Javadoc needed)
    private final UserService userService;

    // 6. Constructors
    public UserController(UserService userService) {
        this.userService = userService;
    }

    // 7. Public API (with Javadoc)
    /**
     * Retrieves a user by ID.
     * @param id the user ID
     * @return the user
     */
    public User getUserById(Long id) { ... }

    // 8. Private methods and constants
}
```

### 5. Braces & Indentation

#### Empty Blocks

#### Empty Blocks
```java
if (condition) {}              // Empty blocks have no spaces: '{}'
if (condition) {               // Non-empty blocks start on next line
    statement;
}
else if (condition) {          // Always use braces for if/else
    statement;
}
for (...) {                    // All loops have braces
    statement;
}
```

#### Curly Brace Style
- **Left curly**: End of line (K&R style)
  ```java
  if (condition) {
      // body
  } else {
      // body
  }
  ```
- **Right curly**: Same line as last statement
  ```java
  for (...) {
      statement;
  } while (condition);

  if (condition) {
      statement;
  }
  ```

#### Indentation
- **2 spaces per level**
- Catch blocks indented 2 more spaces than outer block
- Throws clause indented to same level as method body

```java
public void method() throws Exception {
    // Body at 0 indent

} catch (IOException e) {        // +2 indent
    // ...
} finally {                      // +2 indent
    // ...
}
```

### 6. Whitespace Rules

#### Line Length
- Maximum **100 characters** per line
- Exceptions: URLs, import statements, package declarations
- Long logical statements may break after commas

#### Operator Wrapping
- Binary operators at start of new line for readability:
  ```java
  if (condition1 ||
      condition2 &&
      condition3) { ... }

  List<String> items = list1
      .stream()
      .filter(Condition::check)
      .collect(Collectors.toList());
  ```

#### Annotations
- Place annotations on their own line before the element
- Multiple annotations on same line allowed if they fit:
  ```java
  @Deprecated
  @Override
  public void method() { ... }
  
  // Or multiple short ones on one line:
  @SuppressWarnings("unchecked")
  @NotNull
  private T getValue();
  ```

### 7. Javadoc Requirements

#### Required Javadoc
- **Public classes**: Yes (summary + description)
- **Public methods**: Yes (@param, @return, @throws as needed)
- **Public constructors**: Yes (description + @param)
- **Protected classes/methods**: May omit if simple
- **Private elements**: No Javadoc

#### Javadoc Style
```java
/**
 * Short description of what this method does.
 * 
 * More detailed explanation with examples, if necessary.
 * Use full sentences.
 * 
 * @param parameterName Description of parameter.
 * @return Description of return value.
 * @throws ExceptionType Description when thrown.
 */
public void method() { ... }
```

#### Forbidden Javadoc Patterns
- Don't say "this" unnecessarily: "This method returns..." ✗ → "Returns..." ✓
- Avoid generic patterns like "Returns the X" instead of specific description
- Don't use @return with code reference without explanation:
  ```java
  /**
   * @return the list of items
   */
  // Instead of:
  /**
   * @return {@code List<String>} (is a list)
   */
  ```

### 8. Comments & TODOs

#### TODO Format
```java
// Must be uppercase with colon
@Deprecated
TODO: Refactor this method to use the new service interface
```

#### Code Comments
- Prefer self-explanatory code over comments
- Use block comments for section breaks (e.g., before TODO lists, future work)
- Single-line comments must start with space after `//`

### 9. Exception Handling

#### Catch Blocks
- Never catch bare `Exception`
- Catch specific checked exceptions
- Empty catch blocks must have comment:
  ```java
  try {
      // ...
  } catch (IOException e) {
      // Handle IO error or log it
  }
  
  try {
      // ...
  } catch (IOException e) {
      throw new IllegalStateException("IO operation failed", e);
  }
  ```

### 10. Imports

#### Import Order
```java
// Static imports first (if any)
import static java.util.function.Function.identity;

// Third-party libraries (alphabetically)
import org.springframework.boot.SpringApplication;
import org.springframework.stereotype.Component;

// Java platform classes (alphabetically)
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
```

#### No Wildcard Imports
- **Never use wildcard imports** - always be specific:
  ```java
  // ❌ WRONG
  import java.util.*;
  import org.springframework.core.*;
  
  // ✅ CORRECT
  import java.util.List;
  import java.util.ArrayList;
  import java.util.Optional;
  import org.springframework.core.Ordered;
  import org.springframework.core.ResolvableType;
  ```

#### Import Groups
- Group related packages together
- Alphabetical within groups
- Single blank line between groups

#### Rule Summary
1. **Use imports, never FQN** (unless in a few specific cases)
2. **Never use wildcards** - always import specific classes
3. **Order**: Static → Third-party → Java platform
4. **Clean up**: Remove unused imports (Spotless handles this automatically)

#### When to Use var
- `var` is encouraged for local variables when the type is obvious from context
- Use `var` with diamond syntax: `var list = new ArrayList<>();`
- Don't use `var` when it reduces clarity or hides important type information

```java
// ✅ GOOD: Type is clear, var is concise
var list = new ArrayList<String>();
var optional = Optional.of("value");
var userId = user.getId();
var mapper = String::toUpperCase;

// ❌ AVOID: Type is not obvious from usage
var value = getValue();  // Better: String value = getValue();

// ✅ GOOD: Generic method with var parameter
public <T> T process(T item) { ... }
```

### 11. Common Anti-Patterns

| Pattern | Correct |
|---------|----------|
| `List<String> list = new ArrayList<>();` | ✓ (allowed) |
| `ArrayList<String> list = ...;` | ✗ → Use diamond syntax |
| `String s = null;` | ✗ → Use Optional or sentinel value |
| Multiple imports from same package on one line | ✗ → One import per line |
| `List<?> unknownType` | ✗ → Be explicit or use wildcard consistently |
| Class name in return type | ✗ → Use raw type or parameterized properly |

### 12. Testing Standards

#### Anti-Patterns Summary

When reviewing code, also check for:
- **FQN abuse**: Always use imports (`List list = ...`) not FQN (`java.util.List list = ...`)
- **Wildcard imports**: Be specific (`import java.util.List`), never `import java.util.*;`  
- **var overuse**: Use `var` only when type is obvious from context

#### Test Class Layout
```java
@ExtendWith(SpringExtension.class)
public class UserServiceTest {

    @InjectMocks
    private UserService userService;

    @Mock
    private UserRepository userRepository;

    @BeforeEach
    void setUp() { ... }

    @Test
    void testFindUserById_ReturnsUser_WhenExists() { ... }

    @Test
    void testFindUserById_ReturnsEmptyOptional_WhenNotFound() { ... }
}
```

#### Test Method Naming
- `test[Subject][Action]_[Outcome]_[Condition]` pattern
- Use descriptive outcome names, not just "success"/"failure"

#### Javadoc on Tests
- Public test methods need Javadoc explaining the scenario being tested
- Describe precondition and expected behavior

### 13. Spring Boot Specifics

#### Application Class
```java
@SpringBootApplication
public class PeonyApplication {

    public static void main(String[] args) {
        SpringApplication.run(PeonyApplication.class, args);
    }
}
```

#### Component Scanning
- Use `@Component`, `@Service`, `@Repository`, `@Controller` annotations
- Annotations go on their own line before the declaration:
  ```java
  @Service
  public class OrderService { ... }
  ```

### 14. Common Mistakes to Avoid

| Don't Do | Instead Do |
|----------|------------|
| `if (condition) { do(); } else { }` | Remove empty else block |
| `class A extends B implements C, D` | Order: extends first, then implements |
| Multiple blank lines between members | Single blank line only |
| `public String getMessage()` for getter | Follow standard naming: `getMessage()` ✓ |
| Magic numbers in code | Use named constants |

### 16. Fully Qualified Names (FQN) Policy

**General Rule:** Never use fully qualified names - always use imports.

```java
// ❌ WRONG: FQN everywhere reduces readability
void method() {
    java.util.List list = new java.util.ArrayList<>();
    java.lang.System.out.println("...");
    java.time.LocalDateTime now = ...;
}

// ✅ CORRECT: Use imports
import java.util.List;
import java.util.ArrayList;
import java.lang.System;
import java.time.LocalDateTime;

void method() {
    List list = new ArrayList<>();
    System.out.println("...");
    LocalDateTime now = ...;
}
```

**Exceptions (rare cases):**
1. **Static methods you're not importing**: Use FQN once, consider adding import for future use
   ```java
   // First time using a static method from a class without import
   java.util.Collections.emptyList();  // OK for one-time use
   
   // Then add an import (preferably) or keep using FQN consistently
   ```

2. **Ambiguous imports**: When two classes with same name exist in imported packages
   ```java
   // Rare case - when you truly need to distinguish
   import com.example.MyClass;
   import org.other.MyClass;
   
   MyClass myClass1 = new com.example.MyClass();  // Unambiguous context
   MyClass myClass2 = new org.other.MyClass();
   ```

### 17. var Usage Guidelines

Use `var` for local variables when:
- The type is obvious from the right-hand side assignment
- It improves readability by reducing visual clutter
- It doesn't hide important type information

```java
// ✅ Use var here - type is clear
var list = new ArrayList<>();
var map = new HashMap<String, Integer>();
var optional = Optional.of("value");
var result = service.getData();  // Type obvious from API

// ✅ Use var with diamond syntax (recommended pattern)
var collection = new HashSet<>(set);

// ❌ Don't use var here - type should be explicit
var unknown = getValue();  // String value = getValue(); is better

// ❌ Don't use var for instance fields
private java.util.List items;  // ✗ Use FQN or import + explicit type!
private List<String> items;    // ✓ Better

// ✅ Method parameters - be explicit
public void processData(List<String> items) { ... }
```

### 18. Working With the Tools

#### Checking Code Before Commit
```bash
# Run all checks
./gradlew check

# Just spotless formatting
./gradlew spotlessApply

# Check only main source
./gradlew checkstyleMain spotlessCheck
```

#### Fixing Issues
If Checkstyle fails:
1. Review the error message
2. Check if it's a legitimate style violation or false positive
3. If legitimate: fix the code
4. If false positive: add suppression in `checkstyle-suppressions.xml` (rarely needed)

#### Spotless Auto-Apply
Spotless will auto-format on save if configured in IDE. The Gradle task `spotlessApply` can be run to format the entire project.

### 19. When Rules Seem Conflicting

If a Checkstyle rule seems to conflict with a design decision:

1. **Check suppression policy**: Only suppress for legitimate reasons
2. **Use custom check**: Add new rules to `checkstyle.xml` if needed
3. **Document exception**: Create ADR in `docs/adr/` explaining why standard was broken
4. **Prioritize safety**: Never skip safety checks (e.g., missing Javadoc on public API)

### 20. Review Checklist for Generated Code

Before considering code complete:

- [ ] No Checkstyle violations (`gradlew checkstyleMain`)
- [ ] Passes Spotless formatting (`gradlew spotlessCheck`)
- [ ] Public API has Javadoc
- [ ] Package name follows convention
- [ ] Imports are clean (no unused, no star imports)
- [ ] Naming conventions followed
- [ ] Braces and indentation correct
- [ ] Whitespace is appropriate
- [ ] Comments explain "why", not "what"

## Additional Resources

- **Google Java Style**: https://google.github.io/styleguide/javaguide.html
- **Checkstyle Documentation**: http://checkstyle.org
- **Spotless Configuration**: See `build.gradle` for version and options
- **Project ADRs**: `docs/adr/` - Architectural decisions that may override defaults

## Enforcement Policy

Agents **must** follow these standards. Violations will cause CI pipeline failures. Never bypass checks unless:
1. There is a compelling technical reason documented in an ADR
2. The violation is temporary and part of an ongoing refactoring
