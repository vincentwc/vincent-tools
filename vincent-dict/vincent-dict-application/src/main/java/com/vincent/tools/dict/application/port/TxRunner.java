package com.vincent.tools.dict.application.port;

import java.util.function.Supplier;

public interface TxRunner {
    <T> T required(Supplier<T> action);
}
