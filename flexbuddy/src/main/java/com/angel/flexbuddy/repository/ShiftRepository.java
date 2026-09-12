package com.angel.flexbuddy.repository;

import java.util.List;
import java.util.Optional;
import java.time.LocalDate;

import com.angel.flexbuddy.model.Shift;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;


public interface ShiftRepository extends JpaRepository<Shift, Long> {

    List<Shift> findAllByOwnerEmailIgnoreCaseOrderByDateDescStartTimeDesc(String email);

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
}
