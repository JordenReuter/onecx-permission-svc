package org.tkit.onecx.permission.domain.di;

import java.util.*;

import jakarta.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tkit.onecx.permission.domain.di.mappers.TemplateMapper;
import org.tkit.onecx.permission.domain.di.models.TemplateCommonData;
import org.tkit.onecx.permission.domain.di.models.TemplateTenantData;
import org.tkit.onecx.permission.domain.models.Application;
import org.tkit.onecx.permission.domain.models.Assignment;
import org.tkit.onecx.permission.domain.models.Permission;
import org.tkit.onecx.permission.domain.models.Role;
import org.tkit.quarkus.dataimport.DataImport;
import org.tkit.quarkus.dataimport.DataImportConfig;
import org.tkit.quarkus.dataimport.DataImportService;

import com.fasterxml.jackson.databind.ObjectMapper;

import gen.org.tkit.onecx.permission.domain.template.model.TemplateApplicationValueDTO;
import gen.org.tkit.onecx.permission.domain.template.model.TemplateImportDTO;
import gen.org.tkit.onecx.permission.domain.template.model.TemplateProductValueDTO;
import gen.org.tkit.onecx.permission.domain.template.model.TemplateRoleValueDTO;

@DataImport("template")
public class TemplateDataImportService implements DataImportService {

    private static final Logger log = LoggerFactory.getLogger(TemplateDataImportService.class);

    @Inject
    ObjectMapper objectMapper;

    @Inject
    PermissionTemplateService service;

    @Inject
    TemplateMapper mapper;

    @Inject
    TemplateConfig templateConfig;

    @Override
    public void importData(DataImportConfig config) {
        log.info("Import permissions from configuration {}", config);
        try {

            TemplateImportDTO data = objectMapper.readValue(config.getData(), TemplateImportDTO.class);

            var existingData = importProducts(data);

            List<String> tenants = templateConfig.config().tenants();
            importRoles(tenants, data.getRoles(), existingData);

        } catch (Exception ex) {
            throw new TemplateException(ex.getMessage(), ex);
        }
    }

    private TemplateCommonData importProducts(TemplateImportDTO dto) {

        Map<String, TemplateProductValueDTO> products = dto.getProducts();
        Set<String> productNames = new HashSet<>(products.keySet());

        dto.getRoles().forEach((k, v) -> {
            if (v.getAssignments() != null) {
                productNames.addAll(v.getAssignments().keySet());
            }
        });
        var data = service.getCommonData(productNames);

        // create or update apps
        var applications = new ArrayList<Application>();

        // create or update permission
        var permissions = new ArrayList<Permission>();

        // loop over products and create applications and permissions create/update request
        for (Map.Entry<String, TemplateProductValueDTO> item : products.entrySet()) {
            String productName = item.getKey();
            if (item.getValue() == null) {
                continue;
            }
            for (Map.Entry<String, TemplateApplicationValueDTO> entry : item.getValue().getApplications().entrySet()) {
                var appId = entry.getKey();
                var app = entry.getValue();

                // check the application of the product
                var exists = data.getApplication(productName, appId);
                if (!exists) {
                    var a = mapper.createApplication(productName, appId, app.getName(), app.getDescription());
                    applications.add(a);
                }

                // check application permission
                createPermission(productName, appId, app.getPermissions(), permissions, data);
            }
        }

        // create or update all applications and permissions in request, start Tx
        service.createApplicationsAndPermissions(applications, permissions);

        return data;
    }

    private void importRoles(List<String> tenants, Map<String, TemplateRoleValueDTO> dto, TemplateCommonData commonData) {

        var roleNames = dto.keySet();
        Set<String> productNames = new HashSet<>();
        dto.forEach((k, v) -> {
            if (v.getAssignments() != null) {
                productNames.addAll(v.getAssignments().keySet());
            }
        });

        for (String tenant : tenants) {

            var data = service.getTenantData(tenant, roleNames, productNames);

            var roles = new ArrayList<Role>();
            var assignments = new ArrayList<Assignment>();

            for (var dr : dto.entrySet()) {
                var role = data.getRole(dr.getKey());
                if (role == null) {
                    role = mapper.createRole(templateConfig.config().roleMapping().getOrDefault(dr.getKey(), dr.getKey()),
                            dr.getValue().getDescription());
                    roles.add(role);
                }
                assignments.addAll(createAssignments(role, dr.getValue(), data, commonData));
            }

            service.createTenantData(tenant, roles, assignments);
        }

    }

    private List<Assignment> createAssignments(Role role, TemplateRoleValueDTO dto, TemplateTenantData data,
            TemplateCommonData commonData) {
        List<Assignment> assignments = new ArrayList<>();
        if (dto.getAssignments() == null) {
            return assignments;
        }
        for (var aProduct : dto.getAssignments().entrySet()) {
            for (var aApp : aProduct.getValue().entrySet()) {
                for (var aResource : aApp.getValue().entrySet()) {
                    assignments.addAll(createAssignmentActions(data, role, aResource.getValue(), commonData, aProduct.getKey(),
                            aApp.getKey(), aResource.getKey()));
                }
            }
        }
        return assignments;
    }

    private List<Assignment> createAssignmentActions(TemplateTenantData data, Role role, List<String> actions,
            TemplateCommonData commonData, String productName, String appId, String resource) {
        List<Assignment> assignments = new ArrayList<>();
        for (var action : actions) {
            if (data.hasPermissionAction(role.getName(), productName, appId, resource, action)) {
                continue;
            }
            var permission = commonData.getPermission(productName, appId, resource, action);
            if (permission != null) {
                assignments.add(mapper.createAssignment(role, permission));
            } else {
                log.error("Template import role '{}' missing permission [{},{},{},{}]", role.getName(),
                        productName, appId, resource, action);
            }
        }
        return assignments;
    }

    private void createPermission(String productName, String appId, Map<String, Map<String, String>> dto,
            List<Permission> permissions, TemplateCommonData data) {
        for (Map.Entry<String, Map<String, String>> perm : dto.entrySet()) {
            var resource = perm.getKey();
            for (Map.Entry<String, String> action : perm.getValue().entrySet()) {
                var permDb = data.getPermission(productName, appId, resource, action.getKey());
                if (permDb == null) {
                    var permCreate = mapper.createPermission(productName, appId, resource, action.getKey(), action.getValue());
                    permissions.add(permCreate);
                    data.addPermission(permCreate);
                }
            }
        }
    }

    public static class TemplateException extends RuntimeException {

        public TemplateException(String message, Throwable ex) {
            super(message, ex);
        }
    }

}
