package ca.cgagnier.wlednativeandroid.repository

import ca.cgagnier.wlednativeandroid.model.Branch
import org.junit.Assert.assertEquals
import org.junit.Test

class ConvertersTest {

    private val converters = Converters()

    @Test
    fun toBranch_convertsCorrectly() {
        assertEquals(Branch.STABLE, converters.toBranch("STABLE"))
        assertEquals(Branch.BETA, converters.toBranch("BETA"))
        assertEquals(Branch.UNKNOWN, converters.toBranch("UNKNOWN"))
        assertEquals(Branch.STABLE, converters.toBranch("stable"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun toBranch_throwsExceptionForInvalidValue() {
        converters.toBranch("INVALID")
    }

    @Test
    fun fromBranch_convertsCorrectly() {
        assertEquals("STABLE", converters.fromBranch(Branch.STABLE))
        assertEquals("BETA", converters.fromBranch(Branch.BETA))
        assertEquals("UNKNOWN", converters.fromBranch(Branch.UNKNOWN))
    }
}
