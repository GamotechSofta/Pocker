package com.poker.mapper;

import com.poker.dto.UserResponse;
import com.poker.entity.User;

public final class UserMapper {

    private UserMapper() {
    }

    public static UserResponse toResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getDisplayName(),
                user.getEmail(),
                user.getCreatedAt()
        );
    }
}
