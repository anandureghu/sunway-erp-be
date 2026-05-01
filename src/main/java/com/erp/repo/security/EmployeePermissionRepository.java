package com.erp.repo.security;

import com.erp.domain.security.EmployeePermission;
import com.erp.domain.security.HrModule;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface EmployeePermissionRepository extends JpaRepository<EmployeePermission, Long> {
        List<EmployeePermission> findByEmployee_Id(Long employeeId);

        Optional<EmployeePermission> findByEmployee_IdAndModule(Long employeeId, HrModule module);

        void deleteByEmployee_Id(Long employeeId);

    List<EmployeePermission> findByEmployeeId(Long employeeId);

    Optional<EmployeePermission> findByEmployeeIdAndModule(Long employeeId, HrModule module);

    void deleteByEmployeeId(Long employeeId);
}
