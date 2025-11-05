package com.erp.service;

import com.erp.domain.Employee;
import com.erp.dto.common.PageResponse;
import com.erp.dto.employee.EmployeeRequest;
import com.erp.dto.employee.EmployeeResponse;
import com.erp.repo.EmployeeRepository;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class EmployeeService {
    private final EmployeeRepository repo;
    public EmployeeService(EmployeeRepository repo) { this.repo = repo; }

    private static String nz(String s) { return s == null ? "" : s; }

    private EmployeeResponse map(Employee e) {
        EmployeeResponse r = new EmployeeResponse();
        r.setId(e.getId());
        r.setFirstName(nz(e.getFirstName()));
        r.setLastName(nz(e.getLastName()));
        r.setEmail(nz(e.getEmail()));
        r.setPhone(nz(e.getPhone()));
        r.setDepartment(nz(e.getDepartment()));
        r.setTitle(nz(e.getTitle()));
        r.setStatus(nz(e.getStatus()));
        r.setHiredAt(e.getHiredAt());   // LocalDate + @JsonFormat -> safe
        r.setSalary(e.getSalary());
        return r;
    }

    public EmployeeResponse create(EmployeeRequest req) {
        Employee e = new Employee();
        e.setFirstName(req.getFirstName());
        e.setLastName(req.getLastName());
        e.setEmail(req.getEmail());
        e.setPhone(req.getPhone());
        e.setDepartment(req.getDepartment());
        e.setTitle(req.getTitle());
        e.setStatus(req.getStatus());
        e.setHiredAt(req.getHiredAt());
        e.setSalary(req.getSalary());
        return map(repo.save(e));
    }

    public EmployeeResponse update(Long id, EmployeeRequest req) {
        Employee e = repo.findById(id).orElseThrow();
        e.setFirstName(req.getFirstName());
        e.setLastName(req.getLastName());
        e.setEmail(req.getEmail());
        e.setPhone(req.getPhone());
        e.setDepartment(req.getDepartment());
        e.setTitle(req.getTitle());
        e.setStatus(req.getStatus());
        e.setHiredAt(req.getHiredAt());
        e.setSalary(req.getSalary());
        return map(repo.save(e));
    }

    public void delete(Long id) { repo.deleteById(id); }

    @Transactional(readOnly = true)
    public EmployeeResponse get(Long id) {
        return repo.findById(id).map(this::map).orElseThrow();
    }

    @Transactional(readOnly = true)
    public PageResponse<EmployeeResponse> list(int page, int size) {
        // newest first so new records appear at page=0
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
        Page<Employee> p = repo.findAll(pageable);
        List<EmployeeResponse> content = p.getContent().stream().map(this::map).toList();
        return PageResponse.of(content, p.getTotalElements(), p.getTotalPages(), p.getNumber(), p.getSize());
    }
}
