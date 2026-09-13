package in.bushansirgur.billingsoftware.io;

public enum ArchiveDestination {
    LOCAL,
    S3;

    public static ArchiveDestination from(String value) {
        if (value == null || value.isBlank()) {
            return LOCAL;
        }
        String v = value.trim().toLowerCase();
        return switch (v) {
            case "s3", "aws", "amazon" -> S3;
            case "local", "disk", "file" -> LOCAL;
            default -> throw new IllegalArgumentException(
                    "Invalid destination '" + value + "'. Use 'local' or 's3'.");
        };
    }
}
