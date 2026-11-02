package in.bushansirgur.billingsoftware.util;

import in.bushansirgur.billingsoftware.entity.UserEntity;

import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class OperatorNames {

    private OperatorNames() {
    }

    public static String display(UserEntity user) {
        if (user == null) {
            return "";
        }
        return display(user.getFirstName(), user.getMiddleName(), user.getLastName(), user.getName());
    }

    public static String display(String firstName, String middleName, String lastName, String fallback) {
        String built = Stream.of(firstName, middleName, lastName)
                .filter(part -> part != null && !part.isBlank())
                .collect(Collectors.joining(" "));
        if (!built.isBlank()) {
            return built;
        }
        return fallback == null ? "" : fallback;
    }
}
