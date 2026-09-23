package com.erp.service.platform;

import com.erp.domain.platform.PlatformSettings;
import com.erp.dto.platform.PlatformSettingsRequest;
import com.erp.dto.platform.PlatformSettingsResponse;
import com.erp.repo.platform.PlatformSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PlatformSettingsService {

    private final PlatformSettingsRepository repository;

    @Transactional
    public PlatformSettings getOrCreate() {
        return repository.findAll().stream()
                .findFirst()
                .orElseGet(() -> repository.save(PlatformSettings.builder().build()));
    }

    @Transactional
    public PlatformSettingsResponse get() {
        return toDto(getOrCreate());
    }

    @Transactional
    public PlatformSettingsResponse update(PlatformSettingsRequest request) {
        PlatformSettings settings = getOrCreate();
        settings.setStreet(request.getStreet());
        settings.setCity(request.getCity());
        settings.setState(request.getState());
        settings.setCountry(request.getCountry());
        settings.setBankName(request.getBankName());
        settings.setIban(request.getIban());
        return toDto(repository.save(settings));
    }

    private PlatformSettingsResponse toDto(PlatformSettings settings) {
        return PlatformSettingsResponse.builder()
                .id(settings.getId())
                .street(settings.getStreet())
                .city(settings.getCity())
                .state(settings.getState())
                .country(settings.getCountry())
                .bankName(settings.getBankName())
                .iban(settings.getIban())
                .build();
    }
}
