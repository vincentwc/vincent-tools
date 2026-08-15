package com.vincent.tools.dict.infra.mybatis;

import com.vincent.tools.dict.application.port.TxRunner;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Objects;
import java.util.function.Supplier;

public final class SpringTxRunner implements TxRunner {
    private final TransactionTemplate transactionTemplate;

    public SpringTxRunner(TransactionTemplate transactionTemplate) {
        this.transactionTemplate = Objects.requireNonNull(transactionTemplate, "transactionTemplate");
    }

    @Override
    public <T> T required(Supplier<T> action) {
        return transactionTemplate.execute(status -> action.get());
    }
}
