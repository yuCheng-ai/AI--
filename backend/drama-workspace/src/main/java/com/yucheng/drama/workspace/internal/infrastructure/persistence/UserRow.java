package com.yucheng.drama.workspace.internal.infrastructure.persistence;

public record UserRow(String id, String username, String passwordHash, boolean enabled) {}
