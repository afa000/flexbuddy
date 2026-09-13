package com.angel.flexbuddy.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.angel.flexbuddy.model.AppUser;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    Optional<AppUser> findByCalendarToken(String calendarToken);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "delete from app_users where id = :id", nativeQuery = true)
    int deleteAccountById(@Param("id") Long id);
}
