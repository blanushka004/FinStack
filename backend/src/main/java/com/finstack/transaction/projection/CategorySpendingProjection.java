package com.finstack.transaction.projection;

import java.math.BigDecimal;
import java.util.UUID;

public interface CategorySpendingProjection {
    UUID getCategoryId();
    String getCategoryName();
    BigDecimal getAmount();
}
