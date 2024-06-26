package org.tkit.onecx.permission.domain.models;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public record PermissionAction(String roleName, String productName, String applicationId, String resource, String action) {
}
