package com.jaymrli.teamknowledge;

import com.jaymrli.teamknowledge.api.ApiDtos.CreateProjectRequest;
import com.jaymrli.teamknowledge.api.ApiDtos.IngestDocumentRequest;
import com.jaymrli.teamknowledge.service.AccessControlService;
import com.jaymrli.teamknowledge.service.ForbiddenException;
import com.jaymrli.teamknowledge.service.DocumentIngestionService;
import com.jaymrli.teamknowledge.service.ProjectManagementService;
import com.jaymrli.teamknowledge.service.ProjectRoleService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class ProjectManagementWorkflowTest {
    @Autowired
    private ProjectManagementService projectManagement;

    @Autowired
    private AccessControlService accessControl;

    @Autowired
    private ProjectRoleService roleService;

    @Autowired
    private DocumentIngestionService ingestionService;

    @Test
    void shouldCreateProjectAndManageMembers() {
        var project = projectManagement.create("u_alice",
                new CreateProjectRequest("知识运营实验", "验证项目创建与成员权限", Set.of("u_bob")));
        assertThat(project.memberIds()).contains("u_alice", "u_bob");
        assertThat(project.memberRoles()).containsEntry("u_alice", "OWNER").containsEntry("u_bob", "EDITOR");
        assertThat(accessControl.visibleProjects("u_bob")).extracting(item -> item.id()).contains(project.id());

        projectManagement.updateMembers("u_alice", project.id(), Set.of("u_bob", "u_carol"));
        assertThat(accessControl.visibleProjects("u_carol")).extracting(item -> item.id()).contains(project.id());

        roleService.setRole("u_alice", project.id(), "u_bob", "VIEWER");
        assertThatThrownBy(() -> ingestionService.ingest("u_bob", project.id(), new IngestDocumentRequest(
                "只读写入测试", "# 内容\n测试", "docs://viewer-test", LocalDate.of(2026, 11, 12), 1, Set.of())))
                .isInstanceOf(ForbiddenException.class);

        assertThatThrownBy(() -> projectManagement.updateMembers("u_bob", project.id(), Set.of("u_alice")))
                .isInstanceOf(ForbiddenException.class);
    }
}
