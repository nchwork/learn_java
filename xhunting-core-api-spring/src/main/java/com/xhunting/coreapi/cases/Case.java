package com.xhunting.coreapi.cases;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.xhunting.coreapi.tenants.TenantInfo;
import com.xhunting.coreapi.users.UserInfo;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * Model case. So với bản Java core, annotation thay cho code viết tay ở 3 chỗ:
 *
 * <ul>
 *   <li><b>Mapping MongoDB</b> ({@code MongoCaseRepository.toDocument/fromDocument} biến mất):
 *       {@code @Document("cases")} = collection, {@code @Id} = {@code _id} (String 24 hex được Spring tự đổi
 *       sang ObjectId), {@code @Field("createdByUserId")} = tên field trong DB khác tên trong Java,
 *       {@code @Transient} = không lưu DB. Go: tag {@code bson:"createdByUserId"}.</li>
 *   <li><b>Validate</b> ({@code Validator.validateCase} biến mất): Bean Validation, kích hoạt bởi
 *       {@code @Valid} ở controller. Go: tag {@code validate:"required,max=255"} trong main.proto.</li>
 *   <li><b>JSON</b>: giống bản core, Jackson dùng getter/setter; {@code @JsonIgnore} ẩn field.</li>
 * </ul>
 *
 * Một class gánh cả entity DB lẫn DTO của API giống struct Go dùng chung. Dự án Spring lớn thường tách
 * thành CaseEntity + CaseDto + mapper (MapStruct) để thay đổi DB không làm vỡ API.
 */
@Document("cases")
@CompoundIndex(name = "tenant_createdTime", def = "{'tenantId': 1, 'createdTime': -1}")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Case {

    @Id
    private String id;

    private long createdTime;
    private long updatedTime;

    @NotBlank(message = "tenantId is required")
    private String tenantId;

    @NotBlank(message = "name is required")
    @Size(max = 255, message = "name must be at most 255 characters")
    private String name;

    @Pattern(regexp = "low|medium|high|critical", message = "priority must be one of [low, medium, high, critical]")
    private String priority; // null hợp lệ (omitempty): @Pattern bỏ qua null

    @Pattern(regexp = "open|in_progress|pending|cancelled|unverified|verified",
            message = "status must be one of [open, in_progress, pending, cancelled, unverified, verified]")
    private String status;

    @Indexed
    private List<String> assigneeIds = new ArrayList<>();

    @Min(value = 0, message = "expectedEndpoints must be between 0 and 100000")
    @Max(value = 100_000, message = "expectedEndpoints must be between 0 and 100000")
    private int expectedEndpoints;

    @Indexed
    @Field("createdByUserId")
    private String creatorId;

    private int numberOfFiles;

    @Size(max = 30, message = "filenames must have at most 30 items")
    private List<String> filenames = new ArrayList<>();

    @Size(max = 255, message = "description must be at most 255 characters")
    private String description;

    @NotBlank(message = "folderPath is required")
    private String folderPath;

    @Transient
    private String latestExecutionId;

    // Bắt buộc trong request Create/Update, nhưng KHÔNG lưu vào DB.
    @Transient
    @NotBlank(message = "uploadToken is required")
    private String uploadToken;

    // Field "decorate": chỉ có trong response.
    @Transient
    private TenantInfo tenant;
    @Transient
    private List<UserInfo> assignees;
    @Transient
    private UserInfo creator;

    @JsonIgnore
    @Field("isDeleted")
    private boolean deleted;

    /**
     * Rule "unique" của validator Go không có sẵn trong Bean Validation. Cách nhanh: một method
     * {@code isXxx()} gắn {@code @AssertTrue}. (Cách bài bản: tự viết annotation {@code @UniqueElements}.)
     */
    @JsonIgnore
    @AssertTrue(message = "filenames must be unique")
    public boolean isFilenamesUnique() {
        return filenames == null || new HashSet<>(filenames).size() == filenames.size();
    }

    // ---- getter / setter ----

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public long getCreatedTime() { return createdTime; }
    public void setCreatedTime(long createdTime) { this.createdTime = createdTime; }

    public long getUpdatedTime() { return updatedTime; }
    public void setUpdatedTime(long updatedTime) { this.updatedTime = updatedTime; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public List<String> getAssigneeIds() { return assigneeIds; }
    public void setAssigneeIds(List<String> assigneeIds) {
        this.assigneeIds = assigneeIds == null ? new ArrayList<>() : new ArrayList<>(assigneeIds);
    }

    public int getExpectedEndpoints() { return expectedEndpoints; }
    public void setExpectedEndpoints(int expectedEndpoints) { this.expectedEndpoints = expectedEndpoints; }

    public String getCreatorId() { return creatorId; }
    public void setCreatorId(String creatorId) { this.creatorId = creatorId; }

    public int getNumberOfFiles() { return numberOfFiles; }
    public void setNumberOfFiles(int numberOfFiles) { this.numberOfFiles = numberOfFiles; }

    public List<String> getFilenames() { return filenames; }
    public void setFilenames(List<String> filenames) {
        this.filenames = filenames == null ? new ArrayList<>() : new ArrayList<>(filenames);
    }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getFolderPath() { return folderPath; }
    public void setFolderPath(String folderPath) { this.folderPath = folderPath; }

    public String getLatestExecutionId() { return latestExecutionId; }
    public void setLatestExecutionId(String latestExecutionId) { this.latestExecutionId = latestExecutionId; }

    public String getUploadToken() { return uploadToken; }
    public void setUploadToken(String uploadToken) { this.uploadToken = uploadToken; }

    public TenantInfo getTenant() { return tenant; }
    public void setTenant(TenantInfo tenant) { this.tenant = tenant; }

    public List<UserInfo> getAssignees() { return assignees; }
    public void setAssignees(List<UserInfo> assignees) { this.assignees = assignees; }

    public UserInfo getCreator() { return creator; }
    public void setCreator(UserInfo creator) { this.creator = creator; }

    public boolean isDeleted() { return deleted; }
    public void setDeleted(boolean deleted) { this.deleted = deleted; }
}
