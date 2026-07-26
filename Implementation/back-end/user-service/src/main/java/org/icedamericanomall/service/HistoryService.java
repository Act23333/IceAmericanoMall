package org.icedamericanomall.service;

import java.util.List;

public interface HistoryService {
    void record(Long userId, Long productId);
    List<Long> list(Long userId, int size);
    void clear(Long userId);
}
