package com.vincent.tools.dict.application.port;

import com.vincent.tools.common.core.PageResult;
import com.vincent.tools.dict.application.admin.query.DictPageQuery;
import com.vincent.tools.dict.application.admin.query.ItemPageQuery;
import com.vincent.tools.dict.application.admin.view.DictItemDetail;
import com.vincent.tools.dict.application.admin.view.DictSummary;
import com.vincent.tools.dict.domain.Dict;
import com.vincent.tools.dict.domain.DictCode;
import com.vincent.tools.dict.domain.DictItem;
import com.vincent.tools.dict.domain.ItemCode;
import com.vincent.tools.dict.domain.ItemCodeUsage;
import com.vincent.tools.dict.domain.TenantId;

import java.util.Optional;

public interface DictAdminRepository {
    PageResult<DictSummary> pageDicts(DictPageQuery query);

    Optional<Dict> findDict(long dictId);

    Optional<Dict> lockDict(long dictId);

    boolean existsDictCode(DictCode code);

    long insertDict(Dict dict);

    void updateDict(Dict dict);

    PageResult<DictItemDetail> pageItems(long dictId, ItemPageQuery query);

    Optional<DictItem> findItem(long itemId);

    ItemCodeUsage findItemCodeUsage(long dictId, ItemCode code, TenantId tenantId);

    int countUndeletedItems(long dictId);

    int countUndeletedItems(long dictId, TenantId tenantId);

    long insertItem(DictItem item);

    void updateItem(DictItem item);
}
