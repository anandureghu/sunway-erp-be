package com.erp.seed;

import com.erp.domain.hr.Currency;
import com.erp.repo.hr.CurrencyRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class CurrencySeeder {

    @Bean
    CommandLineRunner seedCountryCurrencies(
            CurrencyRepository repository
    ) {
        return args -> {

            if (repository.count() > 0) return;

            List<Currency> data = List.of(
                    Currency.builder()
                            .countryName("Saudi Arabia")
                            .currencyName("Saudi Riyal")
                            .currencyCode("SAR")
                            .currencySymbol("﷼")
                            .build(),

                    Currency.builder()
                            .countryName("China")
                            .currencyName("Chinese Yuan")
                            .currencyCode("CNY")
                            .currencySymbol("¥")
                            .build(),

                    Currency.builder()
                            .countryName("United States")
                            .currencyName("US Dollar")
                            .currencyCode("USD")
                            .currencySymbol("$")
                            .build(),

                    Currency.builder()
                            .countryName("United Arab Emirates")
                            .currencyName("UAE Dirham")
                            .currencyCode("AED")
                            .currencySymbol("د.إ")
                            .build(),

                    Currency.builder()
                            .countryName("India")
                            .currencyName("Indian Rupee")
                            .currencyCode("INR")
                            .currencySymbol("₹")
                            .build(),

                    Currency.builder()
                            .countryName("Japan")
                            .currencyName("Japanese Yen")
                            .currencyCode("JPY")
                            .currencySymbol("¥")
                            .build(),

                    Currency.builder()
                            .countryName("Qatar")
                            .currencyName("Qatari Riyal")
                            .currencyCode("QAR")
                            .currencySymbol("﷼")
                            .build(),

                    Currency.builder()
                            .countryName("South Korea")
                            .currencyName("South Korean Won")
                            .currencyCode("KRW")
                            .currencySymbol("₩")
                            .build(),

                    Currency.builder()
                            .countryName("Kuwait")
                            .currencyName("Kuwaiti Dinar")
                            .currencyCode("KWD")
                            .currencySymbol("د.ك")
                            .build(),

                    Currency.builder()
                            .countryName("Oman")
                            .currencyName("Omani Rial")
                            .currencyCode("OMR")
                            .currencySymbol("﷼")
                            .build(),

                    Currency.builder()
                            .countryName("Bahrain")
                            .currencyName("Bahraini Dinar")
                            .currencyCode("BHD")
                            .currencySymbol("د.ب")
                            .build(),

                    Currency.builder()
                            .countryName("Iraq")
                            .currencyName("Iraqi Dinar")
                            .currencyCode("IQD")
                            .currencySymbol("ع.د")
                            .build(),

                    Currency.builder()
                            .countryName("Turkey")
                            .currencyName("Turkish Lira")
                            .currencyCode("TRY")
                            .currencySymbol("₺")
                            .build(),

                    Currency.builder()
                            .countryName("Jordan")
                            .currencyName("Jordanian Dinar")
                            .currencyCode("JOD")
                            .currencySymbol("د.ا")
                            .build(),

                    Currency.builder()
                            .countryName("Lebanon")
                            .currencyName("Lebanese Pound")
                            .currencyCode("LBP")
                            .currencySymbol("£")
                            .build(),

                    Currency.builder()
                            .countryName("Syria")
                            .currencyName("Syrian Pound")
                            .currencyCode("SYP")
                            .currencySymbol("£")
                            .build(),

                    Currency.builder()
                            .countryName("Russia")
                            .currencyName("Russian Ruble")
                            .currencyCode("RUB")
                            .currencySymbol("₽")
                            .build(),

                    Currency.builder()
                            .countryName("Iran")
                            .currencyName("Iranian Rial")
                            .currencyCode("IRR")
                            .currencySymbol("﷼")
                            .build(),

                    Currency.builder()
                            .countryName("Yemen")
                            .currencyName("Yemeni Rial")
                            .currencyCode("YER")
                            .currencySymbol("﷼")
                            .build(),

                    Currency.builder()
                            .countryName("Palestine")
                            .currencyName("Israeli New Shekel")
                            .currencyCode("ILS")
                            .currencySymbol("₪")
                            .build(),

                    Currency.builder()
                            .countryName("Israel")
                            .currencyName("Israeli New Shekel")
                            .currencyCode("ILS")
                            .currencySymbol("₪")
                            .build()
            );

            repository.saveAll(data);
        };
    }
}
