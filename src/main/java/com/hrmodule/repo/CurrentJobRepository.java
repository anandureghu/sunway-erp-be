// src/main/java/com/hrmodule/repo/CurrentJobRepository.java
package com.hrmodule.repo;

import com.hrmodule.domain.CurrentJob;
import com.hrmodule.domain.Employee;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CurrentJobRepository extends JpaRepository<CurrentJob, Long> {
    Optional<CurrentJob> findByEmployee(Employee employee);
    void deleteByEmployee(Employee employee);
}
