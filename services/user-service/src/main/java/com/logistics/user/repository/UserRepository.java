package com.logistics.user.repository;

import com.logistics.user.entity.User;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Optional;

@ApplicationScoped
public class UserRepository implements PanacheRepositoryBase<User, String> {

    public Optional<User> findByUsername(String username) {
        return find("username", username).firstResultOptional();
    }

    public Optional<User> findByEmail(String email) {
        return find("email", email).firstResultOptional();
    }

    public Optional<User> findByUsernameOrEmail(String identifier) {
        return find("username = ?1 or email = ?1", identifier).firstResultOptional();
    }

    public boolean existsByUsername(String username) {
        return count("username", username) > 0;
    }

    public boolean existsByEmail(String email) {
        return count("email", email) > 0;
    }

    public boolean existsByEmailAndIdNot(String email, String excludeId) {
        return count("email = ?1 and id != ?2", email, excludeId) > 0;
    }

    public long countActiveAdmins() {
        return count("role = ?1 and status = ?2", com.logistics.user.entity.UserRole.ADMIN, com.logistics.user.entity.UserStatus.ACTIVE);
    }
}
