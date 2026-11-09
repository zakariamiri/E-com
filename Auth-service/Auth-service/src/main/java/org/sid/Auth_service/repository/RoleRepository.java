package org.sid.Auth_service.repository;

import org.sid.Auth_service.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

import java.util.Optional;

@RepositoryRestResource(exported = false)
public interface RoleRepository extends JpaRepository<Role, Long> {
    Optional<Role> findByName(String name);

    @Modifying
    @Query(value = "INSERT INTO auth_roles (name) VALUES (:name) ON CONFLICT (name) DO NOTHING",
            nativeQuery = true)
    void insertIfAbsent(@Param("name") String name);
}
