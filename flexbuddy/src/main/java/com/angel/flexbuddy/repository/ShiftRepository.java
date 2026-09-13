package com.angel.flexbuddy.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.time.LocalDate;

import com.angel.flexbuddy.model.Shift;
import com.angel.flexbuddy.model.ShiftStatus;
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
              and s.status in :statuses
              and (:station = '' or lower(s.station) = lower(:station))
              and (:query = '' or lower(s.station) like lower(concat('%', :query, '%')))
            order by s.date desc, s.startTime desc
            """)
    List<Shift> findFiltered(
            @Param("email") String email,
            @Param("fromDate") LocalDate from,
            @Param("toDate") LocalDate to,
            @Param("station") String station,
            @Param("query") String query,
            @Param("statuses") Collection<ShiftStatus> statuses
    );

    List<Shift> findByOwnerEmailIgnoreCaseAndStatusAndDateBetweenOrderByDateAscStartTimeAsc(
            String email, ShiftStatus status, LocalDate from, LocalDate to);

    @Query("""
            select s from Shift s join fetch s.owner o
            where s.status = com.angel.flexbuddy.model.ShiftStatus.SCHEDULED
              and s.date between :fromDate and :toDate
              and o.remindBeforeMinutes is not null
              and exists (select p.id from PushSubscription p where p.owner = o)
            """)
    List<Shift> findScheduledWithLeadTime(@Param("fromDate") LocalDate from, @Param("toDate") LocalDate to);

    @Query("""
            select s from Shift s join fetch s.owner o
            where s.status = com.angel.flexbuddy.model.ShiftStatus.SCHEDULED
              and s.date between :fromDate and :toDate
              and o.remindConfirm = true
              and exists (select p.id from PushSubscription p where p.owner = o)
            """)
    List<Shift> findScheduledWithConfirmNudges(@Param("fromDate") LocalDate from, @Param("toDate") LocalDate to);

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
            update shift set deleted_at = :deletedAt, delete_batch = :batch
            where owner_id = (select id from app_users where lower(email) = lower(:email))
              and deleted_at is null
            """, nativeQuery = true)
    int softDeleteAll(@Param("email") String email, @Param("deletedAt") java.time.Instant deletedAt,
            @Param("batch") String batch);

    @Modifying
    @Query(value = """
            update shift set deleted_at = :deletedAt, delete_batch = :batch
            where id = :id
              and owner_id = (select id from app_users where lower(email) = lower(:email))
              and deleted_at is null
            """, nativeQuery = true)
    int softDelete(@Param("email") String email, @Param("id") Long id,
            @Param("deletedAt") java.time.Instant deletedAt, @Param("batch") String batch);

    @Modifying
    @Query(value = """
            update shift set deleted_at = null, delete_batch = null
            where id = :id
              and owner_id = (select id from app_users where lower(email) = lower(:email))
              and deleted_at is not null
            """, nativeQuery = true)
    int restoreDeleted(@Param("email") String email, @Param("id") Long id);

    @Modifying
    @Query(value = """
            update shift set deleted_at = null, delete_batch = null
            where owner_id = (select id from app_users where lower(email) = lower(:email))
              and delete_batch = :batch and deleted_at is not null
            """, nativeQuery = true)
    int restoreBatch(@Param("email") String email, @Param("batch") String batch);

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
              and deleted_at < :cutoff
            """, nativeQuery = true)
    int purgeDeletedBefore(@Param("email") String email, @Param("cutoff") java.time.Instant cutoff);

    @Modifying
    @Query(value = """
            delete from shift where owner_id = (select id from app_users where lower(email) = lower(:email))
              and delete_batch = :batch
            """, nativeQuery = true)
    int deleteBatch(@Param("email") String email, @Param("batch") String batch);
}
