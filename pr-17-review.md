### Test Coverage

| Component | Tests | Status |
|-----------|-------|--------|
| `JwtAuthService` | Integration tests via controller test | ✅ Present |
| `JwtTokenService` | Token generation/validation | ✅ Implicit via auth flow |
| `RefreshTokenService` | Store/find/rotate logic | ⚠️ Needs explicit rotation test |
| Startup fail-fast | `JwtSecretFailFastTest` | ✅ Testcontainers-based |

### Planned: Refresh Token Rotation Tests

**File:** `src/test/java/com/dendi/peony/service/auth/JwtAuthServiceRotationTest.java`

**Three scenarios to implement:**

1. **Valid refresh token rotation**
   - Given: Active, non-expired refresh token in DB
   - When: Client calls `/api/v1/auth/refresh` with valid token
   - Then: Service validates signature, finds active token, rotates it (marks old as revoked), returns new refresh token + profile

2. **Expired refresh token rejection**
   - Given: Refresh token in DB but `expiresAt < now`
   - When: Client calls `/api/v1/auth/refresh` with expired token
   - Then: Service throws `IllegalArgumentException("Refresh token expired")`

3. **Replayed (already-rotated) refresh token rejection**
   - Given: Refresh token in DB but `revokedAt != null` (previously rotated)
   - When: Client reuses old token after rotation
   - Then: Service throws `IllegalArgumentException("Invalid or revoked refresh token")`

---

## Recommendation

✅ **APPROVED:** PR implements ADR-005 JWT auth cutover correctly. The hard cutover is acceptable since no client is deployed (per ADR-005 rationale).

**Action items:**
1. ✅ Merge PR #17
2. ⏳ Implement `JwtAuthServiceRotationTest.java` with all three scenarios above
3. Run full test suite: `./gradlew test`
