package com.cardengine.providers;

import com.cardengine.common.Currency;
import com.cardengine.common.Money;

import java.math.BigDecimal;

/**
 * Foreign exchange provider interface.
 *
 * In production, this would integrate with FX providers like:
 * - Bloomberg
 * - Refinitiv
 * - Currency Cloud
 * - Circle (for crypto/stablecoin conversions)
 */
public interface FXProvider {

    /**
     * Get the exchange rate from one currency to another.
     *
     * @param from source currency
     * @param to target currency
     * @return exchange rate
     */
    BigDecimal getExchangeRate(Currency from, Currency to);

    /**
     * Convert money from one currency to another.
     *
     * @param money the money to convert
     * @param toCurrency target currency
     * @return converted money
     */
    Money convert(Money money, Currency toCurrency);
}
