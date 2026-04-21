package com.fitcoach.session;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SessionRepository extends JpaRepository<Session, Long> {
    Page<Session> findByUserId(Long userId, Pageable pageable);
    List<Session> findByUserIdOrderBySessionDateDesc(Long userId);
    long countByUserId(Long userId);

    @Query("select coalesce(sum(s.reps), 0) from Session s where s.userId = ?1")
    Long sumRepsByUserId(Long userId);

    @Query("select s from Session s where s.userId = ?1 and s.sessionDate >= ?2")
    List<Session> findByUserIdSince(Long userId, String dateStr);

    @Query("""
        select s from Session s
        where s.userId = :uid
          and (:action is null or s.action = :action)
          and (:startDate is null or s.sessionDate >= :startDate)
          and (:endDate is null or s.sessionDate <= :endDate)
        """)
    Page<Session> search(@Param("uid") Long uid,
                         @Param("action") String action,
                         @Param("startDate") String startDate,
                         @Param("endDate") String endDate,
                         Pageable pageable);

    // 时间窗口聚合（leaderboard 使用）
    @Query("""
        select s.userId as userId, sum(s.reps) as totalReps, avg(coalesce(s.score,0)) as avgScore
        from Session s
        where s.sessionDate >= :startDate
        group by s.userId
        order by totalReps desc
        """)
    List<SessionAggRow> aggregateSince(@Param("startDate") String startDate, Pageable pageable);

    long countBySessionDateGreaterThanEqual(String dateStr);

    @Query("""
        select s.action as action, count(s) as cnt
        from Session s
        group by s.action
        """)
    List<ActionCountRow> countByAction();

    interface SessionAggRow {
        Long getUserId();
        Long getTotalReps();
        Double getAvgScore();
    }

    interface ActionCountRow {
        String getAction();
        Long getCnt();
    }
}
