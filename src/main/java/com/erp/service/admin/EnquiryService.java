package com.erp.service.admin;

import com.erp.domain.admin.Enquiry;
import com.erp.domain.admin.EnquiryChannel;
import com.erp.domain.admin.EnquiryStatus;
import com.erp.dto.admin.EnquiryResponseDTO;
import com.erp.dto.admin.EnquiryUpdateRequest;
import com.erp.dto.admin.PublicEnquiryRequest;
import com.erp.dto.admin.PublicEnquiryResponse;
import com.erp.exception.NotFoundException;
import com.erp.repo.admin.EnquiryRepository;
import com.erp.service.notification.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Slf4j
public class EnquiryService {

    private final EnquiryRepository enquiryRepository;
    private final EmailService emailService;

    @Value("${app.enquiry.to:info@q7softwaresolutions.com}")
    private String enquiryInbox;

    @Transactional
    public PublicEnquiryResponse submitPublic(PublicEnquiryRequest request) {
        String email = normalizeEmail(request.getEmail());
        boolean created = false;

        Enquiry enquiry = enquiryRepository.findByEmailIgnoreCase(email).orElse(null);
        if (enquiry == null) {
            enquiry = Enquiry.builder()
                    .email(email)
                    .status(EnquiryStatus.NEW)
                    .build();
            created = true;
        }

        enquiry.setName(trimToNull(request.getName()));
        enquiry.setPhone(trimToNull(request.getPhone()));
        enquiry.setInterest(trimToNull(request.getInterest()));
        enquiry.setMessage(trimToNull(request.getMessage()));
        enquiry.setChannel(request.getChannel());
        // Re-open for follow-up when the same lead submits again
        if (!created && enquiry.getStatus() == EnquiryStatus.CLOSED) {
            enquiry.setStatus(EnquiryStatus.NEW);
        }

        enquiry = enquiryRepository.save(enquiry);

        if (request.getChannel() == EnquiryChannel.EMAIL) {
            sendEnquiryEmail(enquiry);
        }

        return PublicEnquiryResponse.builder()
                .id(enquiry.getId())
                .created(created)
                .build();
    }

    @Transactional(readOnly = true)
    public Page<EnquiryResponseDTO> list(EnquiryStatus status, String search, Pageable pageable) {
        String q = StringUtils.hasText(search) ? search.trim() : null;
        return enquiryRepository.search(status, q, pageable).map(e -> toDto(e));
    }

    @Transactional(readOnly = true)
    public EnquiryResponseDTO getById(Long id) {
        return toDto(findOrThrow(id));
    }

    @Transactional
    public EnquiryResponseDTO update(Long id, EnquiryUpdateRequest request) {
        Enquiry enquiry = findOrThrow(id);
        if (request.getStatus() != null) {
            enquiry.setStatus(request.getStatus());
        }
        if (request.getNotes() != null) {
            enquiry.setNotes(request.getNotes().isBlank() ? null : request.getNotes().trim());
        }
        return toDto(enquiryRepository.save(enquiry));
    }

    private Enquiry findOrThrow(Long id) {
        return enquiryRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Enquiry not found: " + id));
    }

    private void sendEnquiryEmail(Enquiry enquiry) {
        if (!StringUtils.hasText(enquiryInbox)) {
            log.warn("app.enquiry.to is empty; skipping enquiry notification email");
            return;
        }
        String subject = "New enquiry from " + enquiry.getName()
                + (StringUtils.hasText(enquiry.getInterest()) ? " — " + enquiry.getInterest() : "");
        String body = """
                Name: %s
                Email: %s
                Phone: %s
                Interest: %s
                Channel: %s

                Message:
                %s
                """.formatted(
                nullToDash(enquiry.getName()),
                nullToDash(enquiry.getEmail()),
                nullToDash(enquiry.getPhone()),
                nullToDash(enquiry.getInterest()),
                enquiry.getChannel() != null ? enquiry.getChannel().name() : "—",
                nullToDash(enquiry.getMessage())
        );
        try {
            emailService.sendPlainText(enquiryInbox.trim(), subject, body);
        } catch (Exception e) {
            // Enquiry is already saved; don't fail the public submit on mail errors
            log.error("Failed to send enquiry notification email for {}", enquiry.getEmail(), e);
        }
    }

    private static EnquiryResponseDTO toDto(Enquiry e) {
        return EnquiryResponseDTO.builder()
                .id(e.getId())
                .name(e.getName())
                .email(e.getEmail())
                .phone(e.getPhone())
                .interest(e.getInterest())
                .message(e.getMessage())
                .channel(e.getChannel())
                .status(e.getStatus())
                .notes(e.getNotes())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .build();
    }

    private static String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email is required");
        }
        return email.trim().toLowerCase();
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String nullToDash(String value) {
        return StringUtils.hasText(value) ? value : "—";
    }
}
