package com.erp.service.salary;

import com.erp.domain.Employee;
import com.erp.domain.salary.EmployeeBankDetails;
import com.erp.dto.salary.BankDetailsRequestDTO;
import com.erp.dto.salary.BankDetailsResponseDTO;
import com.erp.repo.EmployeeRepository;
import com.erp.repo.salary.EmployeeBankDetailsRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

@Service
public class EmployeeBankDetailsService {

    private final EmployeeRepository employeeRepo;
    private final EmployeeBankDetailsRepository bankRepo;

    public EmployeeBankDetailsService(
            EmployeeRepository employeeRepo,
            EmployeeBankDetailsRepository bankRepo) {
        this.employeeRepo = employeeRepo;
        this.bankRepo = bankRepo;
    }

    /* ================= CREATE ================= */

    @Transactional
    public void createBank(Long employeeId, BankDetailsRequestDTO dto) {

        Employee employee = employeeRepo.findById(employeeId)
                .orElseThrow(() -> new RuntimeException("Employee not found"));

        if (bankRepo.findByEmployee(employee).isPresent()) {
            throw new RuntimeException("Bank details already exist");
        }

        EmployeeBankDetails bank = new EmployeeBankDetails();
        bank.setEmployee(employee);

        mapBank(bank, dto);

        bankRepo.save(bank);
    }

    /* ================= UPDATE ================= */

    @Transactional
    public void updateBank(Long employeeId, BankDetailsRequestDTO dto) {

        Employee employee = employeeRepo.findById(employeeId)
                .orElseThrow(() -> new RuntimeException("Employee not found"));

        EmployeeBankDetails bank = bankRepo.findByEmployee(employee)
                .orElseThrow(() -> new RuntimeException("Bank details not found"));

        mapBank(bank, dto);

        bankRepo.save(bank);
    }

    /* ================= GET ================= */

    public BankDetailsResponseDTO getBankDetails(Long employeeId) {

        Employee employee = employeeRepo.findById(employeeId)
                .orElseThrow(() -> new RuntimeException("Employee not found"));

        EmployeeBankDetails bank =
                bankRepo.findByEmployee(employee).orElse(null);

        if (bank == null) return null;

        return toResponseDTO(bank);
    }

    /* ================= MAPPERS ================= */

    private void mapBank(EmployeeBankDetails bank,
                         BankDetailsRequestDTO dto) {

        bank.setBankName(dto.getBankName());
        bank.setBankShortName(dto.getBankShortName() != null ? dto.getBankShortName().trim() : null);
        bank.setBankBranch(dto.getBankBranch());
        bank.setAccountType(dto.getAccountType());
        bank.setAccountNo(dto.getAccountNo());
        bank.setIban(dto.getIban());
        bank.setCountry(dto.getCountry());
        bank.setState(dto.getState());
        bank.setCity(dto.getCity());
        bank.setRemarks(dto.getRemarks());
    }

    private BankDetailsResponseDTO toResponseDTO(EmployeeBankDetails bank) {

        BankDetailsResponseDTO dto = new BankDetailsResponseDTO();
        dto.setBankName(bank.getBankName());
        dto.setBankShortName(bank.getBankShortName());
        dto.setBankBranch(bank.getBankBranch());
        dto.setAccountType(bank.getAccountType());
        dto.setAccountNo(bank.getAccountNo());
        dto.setIban(bank.getIban());
        dto.setCountry(bank.getCountry());
        dto.setState(bank.getState());
        dto.setCity(bank.getCity());
        dto.setRemarks(bank.getRemarks());

        return dto;
    }
}
