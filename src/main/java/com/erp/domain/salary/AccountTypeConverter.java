package com.erp.domain.salary;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class AccountTypeConverter implements AttributeConverter<AccountType, String> {

    @Override
    public String convertToDatabaseColumn(AccountType attribute) {
        return attribute != null ? attribute.name() : null;
    }

    @Override
    public AccountType convertToEntityAttribute(String dbData) {
        return AccountType.fromValue(dbData);
    }
}
