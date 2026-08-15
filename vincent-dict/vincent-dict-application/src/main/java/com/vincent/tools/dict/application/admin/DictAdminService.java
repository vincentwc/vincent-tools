package com.vincent.tools.dict.application.admin;

import com.vincent.tools.dict.application.admin.command.CreateDictCommand;
import com.vincent.tools.dict.application.admin.command.CreateItemCommand;
import com.vincent.tools.dict.application.admin.command.UpdateDictCommand;
import com.vincent.tools.dict.application.admin.command.UpdateItemCommand;
import com.vincent.tools.dict.application.admin.query.DictPageQuery;
import com.vincent.tools.dict.application.admin.query.ItemPageQuery;
import com.vincent.tools.dict.application.admin.view.DictDetail;
import com.vincent.tools.dict.application.admin.view.DictItemDetail;
import com.vincent.tools.dict.application.admin.view.DictSummary;

public interface DictAdminService {
    PageResult<DictSummary> pageDicts(DictPageQuery query);

    DictDetail getDict(long dictId, boolean includeDeleted);

    long createDict(CreateDictCommand command);

    void updateDict(long dictId, UpdateDictCommand command);

    void changeDictStatus(long dictId, boolean enabled);

    void deleteDict(long dictId);

    void restoreDict(long dictId);

    PageResult<DictItemDetail> pageItems(long dictId, ItemPageQuery query);

    long createDefaultItem(long dictId, CreateItemCommand command);

    long createTenantItem(long dictId, String tenantId, CreateItemCommand command);

    void updateItem(long itemId, UpdateItemCommand command);

    void changeItemStatus(long itemId, boolean enabled);

    void deleteItem(long itemId);

    void restoreItem(long itemId);
}
