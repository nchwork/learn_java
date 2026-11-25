package com.xhunting.coreapi.tenants;

import java.util.Collection;
import java.util.List;

/** Tương đương {@code golang-common/tenants.TenantService}. */
public interface TenantService {

    List<TenantInfo> getTenantsByIds(Collection<String> ids);
}
