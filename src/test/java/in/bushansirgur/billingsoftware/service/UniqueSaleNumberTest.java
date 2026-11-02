package in.bushansirgur.billingsoftware.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UniqueSaleNumberTest {

    @Test
    void formatsDeviceOperatorAndSequence() {
        assertEquals("12345678-0007-0000001", UniqueSaleNumber.format("12345678", "0007", 1));
        assertEquals("12345678-0007-9999999", UniqueSaleNumber.format("12345678", "0007", 9_999_999L));
    }

    @Test
    void rejectsNumbersOutsideTheOrdinanceShape() {
        assertThrows(IllegalArgumentException.class, () -> UniqueSaleNumber.format("1234567", "0007", 1));
        assertThrows(IllegalArgumentException.class, () -> UniqueSaleNumber.format("12345678", "007", 1));
        assertThrows(IllegalArgumentException.class, () -> UniqueSaleNumber.format("12345678", "0007", 0));
    }
}
