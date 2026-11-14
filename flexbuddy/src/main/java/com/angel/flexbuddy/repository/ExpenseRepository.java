package com.angel.flexbuddy.repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.angel.flexbuddy.model.Expense;
import com.angel.flexbuddy.model.ExpenseCategory;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {
    Optional<Expense> findByIdAndOwnerEmailIgnoreCase(Long id, String email);

    List<Expense> findAllByShiftIdAndOwnerEmailIgnoreCaseOrderByDateDesc(Long shiftId, String email);

    @Query("""
            select e from Expense e left join e.shift s
            where lower(e.owner.email) = lower(:email)
              and e.date >= :fromDate and e.date <= :toDate
              and (:category is null or e.category = :category)
              and (:shiftId is null or s.id = :shiftId)
              and (:station = '' or (s is not null and lower(s.station) = lower(:station)))
              and (:query = '' or lower(coalesce(e.note, '')) like lower(concat('%', :query, '%'))
                    or (s is not null and lower(s.station) like lower(concat('%', :query, '%'))))
            order by e.date desc, e.id desc
            """)
    List<Expense> findFiltered(@Param("email") String email, @Param("fromDate") LocalDate from,
            @Param("toDate") LocalDate to, @Param("station") String station, @Param("query") String query,
            @Param("category") ExpenseCategory category, @Param("shiftId") Long shiftId);

    @Query(value = """
            select e.* from expense e join app_users u on u.id=e.owner_id
            where lower(u.email)=lower(:email) order by e.date desc, e.id desc
            """, nativeQuery = true)
    List<Expense> findAllIncludingDeleted(@Param("email") String email);

    /** The expense a create with this key made, trash included, so a repeat still finds it after it was deleted. */
    @Query(value = """
            select e.* from expense e join app_users u on u.id=e.owner_id
            where lower(u.email)=lower(:email) and e.create_request_id = :requestId
            """, nativeQuery = true)
    java.util.Optional<Expense> findByCreateRequestIdIncludingDeleted(@Param("email") String email, @Param("requestId") String requestId);

    @Query(value = """
            select e.* from expense e join app_users u on u.id=e.owner_id
            where lower(u.email)=lower(:email) and e.deleted_at is not null order by e.deleted_at desc
            """, nativeQuery = true)
    List<Expense> findTrash(@Param("email") String email);

    @Modifying
    @Query(value = """
            update expense set deleted_at=:deletedAt, delete_batch=:batch, sync_changed_at=:deletedAt
            where id=:id and owner_id=(select id from app_users where lower(email)=lower(:email))
              and deleted_at is null
            """, nativeQuery = true)
    int softDelete(@Param("email") String email, @Param("id") Long id,
            @Param("deletedAt") Instant deletedAt, @Param("batch") String batch);

    @Modifying
    @Query(value = """
            update expense set deleted_at=:deletedAt, delete_batch=:batch, sync_changed_at=:deletedAt
            where owner_id=(select id from app_users where lower(email)=lower(:email)) and deleted_at is null
            """, nativeQuery = true)
    int softDeleteAll(@Param("email") String email, @Param("deletedAt") Instant deletedAt,
            @Param("batch") String batch);

    @Modifying
    @Query(value = """
            update expense set deleted_at=null, delete_batch=null, sync_changed_at=:now
            where id=:id and owner_id=(select id from app_users where lower(email)=lower(:email))
              and deleted_at is not null
            """, nativeQuery = true)
    int restoreDeleted(@Param("email") String email, @Param("id") Long id, @Param("now") Instant now);

    @Modifying
    @Query(value = """
            update expense set deleted_at=null, delete_batch=null, sync_changed_at=:now
            where owner_id=(select id from app_users where lower(email)=lower(:email))
              and delete_batch=:batch and deleted_at is not null
            """, nativeQuery = true)
    int restoreBatch(@Param("email") String email, @Param("batch") String batch, @Param("now") Instant now);

    @Modifying
    @Query(value = """
            delete from expense where id=:id
              and owner_id=(select id from app_users where lower(email)=lower(:email)) and deleted_at is not null
            """, nativeQuery = true)
    int permanentlyDelete(@Param("email") String email, @Param("id") Long id);

    @Modifying
    @Query(value = """
            delete from expense where owner_id=(select id from app_users where lower(email)=lower(:email))
              and deleted_at is not null
            """, nativeQuery = true)
    int emptyTrash(@Param("email") String email);

    @Modifying
    @Query(value = "delete from expense where deleted_at < :cutoff", nativeQuery = true)
    int purgeDeletedBefore(@Param("cutoff") Instant cutoff);

    @Modifying
    @Query(value = """
            delete from expense where owner_id=(select id from app_users where lower(email)=lower(:email))
              and delete_batch=:batch
            """, nativeQuery = true)
    int deleteBatch(@Param("email") String email, @Param("batch") String batch);

    @Modifying
    @Query(value = "delete from expense where owner_id = :ownerId", nativeQuery = true)
    int deleteAllByOwnerIdIncludingTrash(@Param("ownerId") Long ownerId);

    /** Lock the owned row, including trash, while an API write checks its version and changes it (NA-06). */
    @Query(value = """
            select * from expense where id = :id
            and owner_id = (select id from app_users where lower(email) = lower(:email)) for update
            """, nativeQuery = true)
    Optional<Expense> lockIncludingDeleted(@Param("email") String email, @Param("id") Long id);

    /** Every expense of this account not in Recently deleted, in the API's order (NA-06). */
    @Query(value = """
            select e.* from expense e join app_users u on u.id = e.owner_id
            where lower(u.email) = lower(:email) and e.deleted_at is null
            order by e.date, e.id
            """, nativeQuery = true)
    List<Expense> findLiveForSync(@Param("email") String email);

    /** Expenses of this account changed after the given time, deleted ones included (NA-06). */
    @Query(value = """
            select e.* from expense e join app_users u on u.id = e.owner_id
            where lower(u.email) = lower(:email) and e.sync_changed_at > :since
            order by e.date, e.id
            """, nativeQuery = true)
    List<Expense> findChangedSince(@Param("email") String email, @Param("since") Instant since);

    /** The latest sync time among this account's expenses, deleted ones included; null without any. */
    @Query(value = """
            select max(e.sync_changed_at) from expense e join app_users u on u.id = e.owner_id
            where lower(u.email) = lower(:email)
            """, nativeQuery = true)
    Instant latestSyncChange(@Param("email") String email);
}