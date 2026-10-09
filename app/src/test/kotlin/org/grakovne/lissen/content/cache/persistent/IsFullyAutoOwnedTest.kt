package org.grakovne.lissen.content.cache.persistent

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class IsFullyAutoOwnedTest {
  @Test
  fun `every cached chapter being owned is fully auto-owned`() {
    assertTrue(isFullyAutoOwned(owned = setOf("c0", "c1"), cached = setOf("c0", "c1")))
  }

  @Test
  fun `a manually downloaded chapter alongside owned ones is not fully auto-owned`() {
    assertFalse(isFullyAutoOwned(owned = setOf("c0"), cached = setOf("c0", "c1")))
  }

  @Test
  fun `nothing owned at all is not fully auto-owned`() {
    assertFalse(isFullyAutoOwned(owned = emptySet(), cached = setOf("c0")))
  }
}
