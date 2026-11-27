package com.poker.entity;

import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import java.util.UUID;

@Document(collection = "users")
public class User extends BaseDocument {

    @Indexed(unique = true)
    private String username;

    @Indexed(unique = true)
    private String email;

    private String passwordHash;
    private String displayName;

    protected User() {
    }

    public User(String username, String email, String passwordHash) {
        ensureId();
        this.username = username;
        this.email = email;
        this.passwordHash = passwordHash;
    }

    public String getUsername() {
        return username;
    }

    public User(UUID id, String username, String email, String passwordHash) {
        super(id);
        this.username = username;
        this.email = email;
        this.passwordHash = passwordHash;
    }

    public String getDisplayName() {
        return displayName == null || displayName.isBlank() ? username : displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }
}
