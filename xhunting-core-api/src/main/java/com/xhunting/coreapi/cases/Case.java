package com.xhunting.coreapi.cases;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.xhunting.coreapi.tenants.TenantInfo;
import com.xhunting.coreapi.users.UserInfo;
import java.util.ArrayList;
import java.util.List;

/**
 * Model case: gộp {@code message Case} trong main.proto và struct {@code cases.Case} của core-api.
 *
 * Khác biệt Go -> Java:
 * <ul>
 *   <li>Go struct + tag {@code json:"name,omitempty"}. Java: class có field private + getter/setter
 *       (JavaBean), Jackson dùng getter/setter để đọc/ghi JSON. {@code @JsonInclude(NON_NULL)} ~ omitempty.</li>
 *   <li>Đây là class MUTABLE (không phải record) vì handler sửa từng field như bản Go
 *       ({@code item.Status = ...}). Record phù hợp cho dữ liệu bất biến như {@link UploadToken}.</li>
 *   <li>Go slice nil vẫn {@code len()} được. Java List null thì NullPointerException, nên mọi
 *       List mặc định là {@code new ArrayList<>()} và setter đổi null thành list rỗng.</li>
 *   <li>Go copy struct bằng phép gán {@code b := *a}. Java gán chỉ copy THAM CHIẾU, nên cần
 *       {@link #copy()} thủ công (repository in-memory dùng để không lộ object nội bộ ra ngoài).</li>
 * </ul>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Case {

    private String id;
    private long createdTime;
    private long updatedTime;
    private String tenantId;
    private String name;
    private String priority;
    private String status;
    private List<String> assigneeIds = new ArrayList<>();
    private int expectedEndpoints;
    private String creatorId;
    private int numberOfFiles;
    private List<String> filenames = new ArrayList<>();
    private String description;
    private String folderPath;
    private String latestExecutionId;
    private String uploadToken;

    // Field "decorate" (decorateItem): chỉ có trong response, không lưu DB.
    private TenantInfo tenant;
    private List<UserInfo> assignees;
    private UserInfo creator;

    // Soft delete. Không xuất ra JSON (Go: json:"-").
    @JsonIgnore
    private boolean deleted;

    public Case copy() {
        Case c = new Case();
        c.id = id;
        c.createdTime = createdTime;
        c.updatedTime = updatedTime;
        c.tenantId = tenantId;
        c.name = name;
        c.priority = priority;
        c.status = status;
        c.assigneeIds = new ArrayList<>(assigneeIds);
        c.expectedEndpoints = expectedEndpoints;
        c.creatorId = creatorId;
        c.numberOfFiles = numberOfFiles;
        c.filenames = new ArrayList<>(filenames);
        c.description = description;
        c.folderPath = folderPath;
        c.latestExecutionId = latestExecutionId;
        c.uploadToken = uploadToken;
        c.deleted = deleted;
        return c;
    }

    // ---- getter / setter (IDE sinh tự động; Lombok @Data hoặc record giúp bớt đoạn này) ----

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
