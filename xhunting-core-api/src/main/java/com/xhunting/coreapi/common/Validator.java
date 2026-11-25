package com.xhunting.coreapi.common;

import com.xhunting.coreapi.cases.Case;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * Thay cho thẻ {@code //@gotags: validate:"..."} trong main.proto (go-playground/validator):
 *
 * <pre>
 * tenant_id          validate:"required"
 * name               validate:"required,max=255"
 * priority           validate:"omitempty,oneof=low medium high critical"
 * status             validate:"oneof=open in_progress pending cancelled unverified verified"
 * expected_endpoints validate:"min=0,max=100000"
 * number_of_files    validate:"min=0,max=30"
 * filenames          validate:"max=30,unique"
 * description        validate:"max=255"
 * folder_path        validate:"required"
 * upload_token       validate:"required"
 * </pre>
 *
 * Java core không có validator dựa trên annotation, nên viết tay. Khi lên Spring, cùng các rule này
 * viết bằng Bean Validation: {@code @NotBlank @Size(max=255) String name;} và {@code @Valid} ở controller.
 */
public final class Validator {

    private Validator() {
    }

    public static void validateCase(Case c) {
        List<String> errors = new ArrayList<>();

        if (isBlank(c.getTenantId())) {
            errors.add("tenantId is required");
        }
        if (isBlank(c.getName())) {
            errors.add("name is required");
        } else if (c.getName().length() > 255) {
            errors.add("name must be at most 255 characters");
        }
        if (!isBlank(c.getPriority()) && !Constants.PRIORITIES.contains(c.getPriority())) {
            errors.add("priority must be one of " + Constants.PRIORITIES);
        }
        if (c.getStatus() != null && !Constants.CASE_STATUSES.contains(c.getStatus())) {
            errors.add("status must be one of " + Constants.CASE_STATUSES);
        }
        if (c.getExpectedEndpoints() < 0 || c.getExpectedEndpoints() > 100_000) { // _ trong số giống Go 100_000
            errors.add("expectedEndpoints must be between 0 and 100000");
        }
        List<String> filenames = c.getFilenames();
        if (filenames.size() > 30) {
            errors.add("filenames must have at most 30 items");
        }
        if (new HashSet<>(filenames).size() != filenames.size()) {
            errors.add("filenames must be unique");
        }
        if (c.getDescription() != null && c.getDescription().length() > 255) {
            errors.add("description must be at most 255 characters");
        }
        if (isBlank(c.getFolderPath())) {
            errors.add("folderPath is required");
        }
        if (isBlank(c.getUploadToken())) {
            errors.add("uploadToken is required");
        }

        if (!errors.isEmpty()) {
            throw ApiException.invalidArgument(String.join("; ", errors));
        }
    }

    /** {@code isCompressedFile} + {@code checkValidFiles} trong case_service_handler.go. */
    public static void checkValidFiles(List<String> files) {
        for (String file : files) {
            String lower = file.toLowerCase();
            boolean ok = Constants.COMPRESSED_EXTENSIONS.stream().anyMatch(lower::endsWith);
            if (!ok) {
                throw ApiException.invalidArgument("InvalidFileExtension: " + file);
            }
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
