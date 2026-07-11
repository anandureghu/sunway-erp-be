package com.erp.repo;

import com.erp.domain.AttendanceMonthArchive;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AttendanceMonthArchiveRepository extends JpaRepository<AttendanceMonthArchive, Long> {

    boolean existsByCompanyIdAndPeriodYearAndPeriodMonth(Long companyId, int periodYear, int periodMonth);

    void deleteByCompanyIdAndPeriodYearAndPeriodMonth(Long companyId, int periodYear, int periodMonth);

    /**
     * Archived rows for a company, optionally narrowed by month and/or an
     * employee-code / name fragment. Nulls mean "no filter" for that dimension.
     */
    @Query("""
            SELECT a FROM AttendanceMonthArchive a
            WHERE a.companyId = :companyId
              AND (:year IS NULL OR a.periodYear = :year)
              AND (:month IS NULL OR a.periodMonth = :month)
              AND (:code IS NULL
                   OR LOWER(a.employeeNo) LIKE LOWER(CONCAT('%', :code, '%'))
                   OR LOWER(a.employeeName) LIKE LOWER(CONCAT('%', :code, '%')))
            ORDER BY a.periodYear DESC, a.periodMonth DESC, a.employeeName ASC
            """)
    List<AttendanceMonthArchive> search(
            @Param("companyId") Long companyId,
            @Param("year") Integer year,
            @Param("month") Integer month,
            @Param("code") String code
    );

    /** Paged variant of {@link #search} — true DB pagination for archived rows. */
    @Query("""
            SELECT a FROM AttendanceMonthArchive a
            WHERE a.companyId = :companyId
              AND (:year IS NULL OR a.periodYear = :year)
              AND (:month IS NULL OR a.periodMonth = :month)
              AND (:code IS NULL
                   OR LOWER(a.employeeNo) LIKE LOWER(CONCAT('%', :code, '%'))
                   OR LOWER(a.employeeName) LIKE LOWER(CONCAT('%', :code, '%')))
            ORDER BY a.periodYear DESC, a.periodMonth DESC, a.employeeName ASC
            """)
    Page<AttendanceMonthArchive> searchPaged(
            @Param("companyId") Long companyId,
            @Param("year") Integer year,
            @Param("month") Integer month,
            @Param("code") String code,
            Pageable pageable
    );
}
