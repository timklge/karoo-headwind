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

        assertEquals(0.052, grade1, 0.005) // Expected relative grade is approximately 5.2%
    }

    @Test
    fun testHeadwindLightweight(){
        val grade1 = RelativeGradeDataType.estimateRelativeGrade(
            actualGrade = 0.02, // 2%
            riderSpeed = 8.0,   // m/s (~28.8 km/h)
            windSpeed = 5.0,    // m/s (18 km/h)
            windDirectionDegrees = 0.0, // Direct headwind
            totalMass = 60.0    // kg
        )

        assertEquals(0.063, grade1, 0.005) // Expected relative grade is approximately 6.3%
    }

    @Test
    fun testHeadwindFlat(){
        val grade1 = RelativeGradeDataType.estimateRelativeGrade(
            actualGrade = 0.00, // 0%
            riderSpeed = 8.0,   // m/s (~28.8 km/h)
            windSpeed = 5.0,    // m/s (18 km/h)
            windDirectionDegrees = 0.0, // Direct headwind
            totalMass = 80.0    // kg
        )

        assertEquals(0.032, grade1, 0.005) // Expected relative grade is approximately 3.2%
    }

    @Test
    fun testHeadwindSteep(){
        val grade1 = RelativeGradeDataType.estimateRelativeGrade(
            actualGrade = 0.06, // 6%
            riderSpeed = 8.0,   // m/s (~28.8 km/h)
            windSpeed = 5.0,    // m/s (18 km/h)
            windDirectionDegrees = 0.0, // Direct headwind
            totalMass = 80.0    // kg
        )

        assertEquals(0.09, grade1, 0.005) // Expected relative grade is approximately 9%
    }

    @Test
    fun testHeadwindAtHighSpeed() {
        val grade1 = RelativeGradeDataType.estimateRelativeGrade(
            actualGrade = 0.02, // 2%
            riderSpeed = 12.0,   // m/s (~43.2 km/h)
            windSpeed = 5.0,    // m/s (18 km/h)
            windDirectionDegrees = 0.0, // Direct headwind
            totalMass = 80.0    // kg
        )

        assertEquals(0.065, grade1, 0.005) // Expected relative grade is approximately 6.5%
    }

    @Test
    fun testHeadwindAtLowSpeed() {
        val grade1 = RelativeGradeDataType.estimateRelativeGrade(
            actualGrade = 0.02, // 2%
            riderSpeed = 4.0,   // m/s (~14.4 km/h)
            windSpeed = 5.0,    // m/s (18 km/h)
            windDirectionDegrees = 0.0, // Direct headwind
            totalMass = 80.0    // kg
        )

        assertEquals(0.040, grade1, 0.005) // Expected relative grade is approximately 4.6%
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

        assertEquals(0.101, grade1, 0.005) // Expected relative grade is approximately 10.1%
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

        assertEquals(0.037, grade1, 0.005) // Expected relative grade is approximately 3.7%
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

        assertEquals(0.003, grade2, 0.005) // Expected relative grade is approximately 0.3%
    }

    @Test
    fun testStrongTailwind() {
        val grade2 = RelativeGradeDataType.estimateRelativeGrade(
            actualGrade = 0.00, // 0%
            riderSpeed = 8.0,   // m/s
            windSpeed = 10.0,   // m/s
            windDirectionDegrees = 180.0, // Direct tailwind
            totalMass = 80.0    // kg
        )

        assertEquals(-0.021, grade2, 0.005) // Expected relative grade is approximately -2.1%
    }
}