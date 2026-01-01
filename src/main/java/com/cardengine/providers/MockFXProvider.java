package com.cardengine.providers;

import com.cardengine.common.Currency;
import com.cardengine.common.Money;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;

/**
 * Mock FX provider with hardcoded exchange rates.
 *
 * In production, this would fetch real-time rates from an FX API.
 */
@Component
@Slf4j
public class MockFXProvider implements FXProvider {

    // Mock exchange rates (base: USD)
    private static final Map<String, BigDecimal> RATES = new HashMap<>();

    static {
        RATES.put("USD_USD", BigDecimal.ONE);
        RATES.put("USD_EUR", new BigDecimal("0.92"));
        RATES.put("USD_GBP", new BigDecimal("0.79"));
        RATES.put("USD_USDC", BigDecimal.ONE);  // 1:1 for stablecoins
        RATES.put("USD_USDT", BigDecimal.ONE);

        RATES.put("EUR_USD", new BigDecimal("1.09"));
        RATES.put("EUR_EUR", BigDecimal.ONE);
        RATES.put("EUR_GBP", new BigDecimal("0.86"));

        RATES.put("GBP_USD", new BigDecimal("1.27"));
        RATES.put("GBP_EUR", new BigDecimal("1.16"));
        RATES.put("GBP_GBP", BigDecimal.ONE);

        RATES.put("USDC_USD", BigDecimal.ONE);
        RATES.put("USDT_USD", BigDecimal.ONE);
    }

    @Override
    public BigDecimal getExchangeRate(Currency from, Currency to) {
        if (from == to) {
            return BigDecimal.ONE;
        }

        String key = from.name() + "_" + to.name();
        BigDecimal rate = RATES.get(key);

        if (rate == null) {
            // If direct rate not available, convert through USD
            BigDecimal fromToUsd = RATES.getOrDefault(from.name() + "_USD", BigDecimal.ONE);
            BigDecimal usdToTarget = RATES.getOrDefault("USD_" + to.name(), BigDecimal.ONE);
            rate = fromToUsd.multiply(usdToTarget);
        }

        log.debug("FX rate {} -> {}: {}", from, to, rate);
        return rate;
    }

    @Override
    public Money convert(Money money, Currency toCurrency) {
        if (money.getCurrency() == toCurrency) {
            return money;
        }

        BigDecimal rate = getExchangeRate(money.getCurrency(), toCurrency);
        BigDecimal convertedAmount = money.getAmount()
            .multiply(rate)
            .setScale(2, RoundingMode.HALF_UP);

        return Money.of(convertedAmount, toCurrency);
    }
}
