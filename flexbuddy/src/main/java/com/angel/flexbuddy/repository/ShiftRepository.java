package com.angel.flexbuddy.repository;

import java.util.List;
import java.util.Optional;
import java.time.LocalDate;

import com.angel.flexbuddy.model.Shift;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Modifying;


public interface ShiftRepository extends JpaRepository<Shift, Long> {

    List<Shift> findAllByOwnerEmailIgnoreCaseOrderByDateDescStartTimeDesc(String email);

    long countByOwnerEmailIgnoreCase(String email);

    Optional<Shift> findByIdAndOwnerEmailIgnoreCase(Long id, String email);

    @Query("""
            select s from Shift s
            where lower(s.owner.email) = lower(:email)
              and s.date >= :fromDate
              and s.date <= :toDate
              and (:station = '' or lower(s.station) = lower(:station))
              and (:query = '' or lower(s.station) like lower(concat('%', :query, '%')))
            order by s.date desc, s.startTime desc
            """)
    List<Shift> findFiltered(
            @Param("email") String email,
            @Param("fromDate") LocalDate from,
            @Param("toDate") LocalDate to,
            @Param("station") String station,
            @Param("query") String query
    );

    @Query("""
            select distinct s.station from Shift s
            where lower(s.owner.email) = lower(:email)
            order by s.station
            """)
    List<String> findDistinctStations(@Param("email") String email);

    @Query(value = """
            select s.* from shift s join app_users u on u.id = s.owner_id
            where lower(u.email) = lower(:email)
            order by s.date desc, s.start_time desc
            """, nativeQuery = true)
    List<Shift> findAllIncludingDeleted(@Param("email") String email);

    @Query(value = """
            select s.* from shift s join app_users u on u.id = s.owner_id
            where lower(u.email) = lower(:email) and s.deleted_at is not null
            order by s.deleted_at desc
            """, nativeQuery = true)
    List<Shift> findTrash(@Param("email") String email);

    @Modifying
    @Query(value = """
            update shift set deleted_at = :deletedAt, delete_batch = :batch, updated_at = :deletedAt
            where owner_id = (select id from app_users where lower(email) = lower(:email))
              and deleted_at is null
            """, nativeQuery = true)
    int softDeleteAll(@Param("email") String email, @Param("deletedAt") java.time.Instant deletedAt,
            @Param("batch") String batch);

    @Modifying
    @Query(value = """
            update shift set deleted_at = null, delete_batch = null, updated_at = :restoredAt
            where id = :id
              and owner_id = (select id from app_users where lower(email) = lower(:email))
              and deleted_at is not null
            """, nativeQuery = true)
    int restoreDeleted(@Param("email") String email, @Param("id") Long id,
            @Param("restoredAt") java.time.Instant restoredAt);

    @Modifying
    @Query(value = """
            update shift set deleted_at = null, delete_batch = null, updated_at = :restoredAt
            where owner_id = (select id from app_users where lower(email) = lower(:email))
              and delete_batch = :batch and deleted_at is not null
            """, nativeQuery = true)
    int restoreBatch(@Param("email") String email, @Param("batch") String batch,
            @Param("restoredAt") java.time.Instant restoredAt);

    @Modifying
    @Query(value = """
            delete from shift where id = :id
              and owner_id = (select id from app_users where lower(email) = lower(:email))
              and deleted_at is not null
            """, nativeQuery = true)
    int permanentlyDelete(@Param("email") String email, @Param("id") Long id);

    @Modifying
    @Query(value = """
            delete from shift where owner_id = (select id from app_users where lower(email) = lower(:email))
              and deleted_at is not null
            """, nativeQuery = true)
    int emptyTrash(@Param("email") String email);

    @Modifying
    @Query(value = "delete from shift where deleted_at < :cutoff", nativeQuery = true)
    int purgeDeletedBefore(@Param("cutoff") java.time.Instant cutoff);

    @Modifying
    @Query(value = """
            delete from shift where owner_id = (select id from app_users where lower(email) = lower(:email))
              and delete_batch = :batch
            """, nativeQuery = true)
    int deleteBatch(@Param("email") String email, @Param("batch") String batch);

    @Query(value = """
            select count(*) > 0 from shift
            where owner_id = (select id from app_users where lower(email) = lower(:email))
              and deleted_at is null and date = :date and start_time = :startTime
              and lower(station) = lower(:station)
            """, nativeQuery = true)
    boolean existsNaturalKey(@Param("email") String email, @Param("date") LocalDate date,
            @Param("startTime") java.time.LocalTime startTime, @Param("station") String station);
}
