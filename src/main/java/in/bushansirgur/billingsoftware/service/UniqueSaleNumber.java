package in.bushansirgur.billingsoftware.service;

/**
 * Unique sale number required by Ordinance H-18, Appendix 29:
 * 8-digit fiscal device number, 4-digit operator code, 7-digit sequence.
 */
public final class UniqueSaleNumber {

    private UniqueSaleNumber() {
    }

    public static String format(String individualNumber, String operatorCode, long sequence) {
        if (individualNumber == null || !individualNumber.matches("\\d{8}")) {
            throw new IllegalArgumentException("Индивидуалният номер на фискалното устройство трябва да е 8 цифри");
        }
        if (operatorCode == null || !operatorCode.matches("\\d{4}")) {
            throw new IllegalArgumentException("Кодът на оператора трябва да е 4 цифри");
        }
        if (sequence < 1 || sequence > 9_999_999L) {
            throw new IllegalArgumentException("Поредният номер на продажбата е извън 0000001–9999999");
        }
        return individualNumber + "-" + operatorCode + "-" + String.format("%07d", sequence);
    }
}
