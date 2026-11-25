package com.xhunting.coreapi.tenants;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;

/** Tenant demo cố định. */
@Service
public class InMemoryTenantService implements TenantService {

    // Map.of: map BẤT BIẾN (Go không có khái niệm này cho map)
    private final Map<String, TenantInfo> tenants = Map.of(
            "tenant-a", new TenantInfo("tenant-a", "Tenant A"),
            "tenant-b", new TenantInfo("tenant-b", "Tenant B"));

    @Override
    public List<TenantInfo> getTenantsByIds(Collection<String> ids) {
        return ids.stream().map(tenants::get).filter(Objects::nonNull).toList();
    }
}
