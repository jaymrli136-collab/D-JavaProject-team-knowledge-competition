package com.jaymrli.teamknowledge.service;

import com.jaymrli.teamknowledge.api.ApiDtos.UserView;
import com.jaymrli.teamknowledge.repository.KnowledgeStore;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class UserDirectoryService {
    private final KnowledgeStore store;

    public UserDirectoryService(KnowledgeStore store) { this.store = store; }

    public List<UserView> list() {
        Map<String, String> users = new LinkedHashMap<>();
        store.allProjects().forEach(project -> project.memberIds().stream().sorted()
                .forEach(id -> users.putIfAbsent(id, displayName(id))));
        return users.entrySet().stream().map(entry -> new UserView(entry.getKey(), entry.getValue())).toList();
    }

    private String displayName(String id) {
        return switch (id) {
            case "u_alice" -> "Alice · 项目成员";
            case "u_bob" -> "Bob · 项目负责人";
            case "u_carol" -> "Carol · 内容负责人";
            default -> id;
        };
    }
}
