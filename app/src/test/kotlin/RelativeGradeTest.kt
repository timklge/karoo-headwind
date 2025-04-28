import de.timklge.karooheadwind.datatypes.RelativeGradeDataType
import org.junit.Test
import kotlin.test.assertEquals

class RelativeGradeTest {
    @Test
    fun testHeadwind(){
        val grade1 = RelativeGradeDataType.estimateRelativeGrade(
            actualGrade = 0.02, // 2%
            riderSpeed = 8.0,   // m/s (~28.8 km/h)
            windSpeed = 5.0,    // m/s (18 km/h)
            windDirectionDegrees = 0.0, // Direct headwind
            totalMass = 80.0    // kg
        )

        assertEquals(0.046, grade1, 0.01) // Expected relative grade is approximately 4.6%
    }

    @Test
    fun testStrongHeadwind() {
        val grade1 = RelativeGradeDataType.estimateRelativeGrade(
            actualGrade = 0.02, // 2%
            riderSpeed = 8.0,   // m/s (~28.8 km/h)
            windSpeed = 10.0,   // m/s (36 km/h)
            windDirectionDegrees = 0.0, // Direct headwind
            totalMass = 80.0    // kg
        )

        assertEquals(0.086, grade1, 0.01) // Expected relative grade is approximately 8.6%
    }

    @Test
    fun testDiagonalHeadwind() {
        val grade1 = RelativeGradeDataType.estimateRelativeGrade(
            actualGrade = 0.02, // 2%
            riderSpeed = 8.0,   // m/s (~28.8 km/h)
            windSpeed = 5.0,    // m/s (18 km/h)
            windDirectionDegrees = 45.0, // Diagonal headwind
            totalMass = 80.0    // kg
        )

        assertEquals(0.037, grade1, 0.01) // Expected relative grade is approximately 3.7%
    }

    @Test
    fun testTailwind(){
        val grade2 = RelativeGradeDataType.estimateRelativeGrade(
            actualGrade = 0.02, // 2%
            riderSpeed = 8.0,   // m/s
            windSpeed = 5.0,    // m/s
            windDirectionDegrees = 180.0, // Direct tailwind
            totalMass = 80.0    // kg
        )

        assertEquals(0.014, grade2, 0.01) // Expected relative grade is approximately 1.4%
    }
}