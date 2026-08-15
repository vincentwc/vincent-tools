INSERT INTO vin_dict (
    code, name, description, status, sort_no, version, deleted,
    created_by, created_at, updated_by, updated_at
) VALUES (
    'ORDER_STATUS', 'Order Status', 'Example order status dictionary', 1, 1, 0, 0,
    'example', CURRENT_TIMESTAMP(3), 'example', CURRENT_TIMESTAMP(3)
);

INSERT INTO vin_dict_item (
    dict_id, tenant_id, code, name, description, status, sort_no, version, deleted,
    created_by, created_at, updated_by, updated_at
)
SELECT id, '0', 'CREATED', 'Created', 'Default created status', 1, 1, 0, 0,
       'example', CURRENT_TIMESTAMP(3), 'example', CURRENT_TIMESTAMP(3)
FROM vin_dict
WHERE code = 'ORDER_STATUS';

INSERT INTO vin_dict_item (
    dict_id, tenant_id, code, name, description, status, sort_no, version, deleted,
    created_by, created_at, updated_by, updated_at
)
SELECT id, 'tenant-a', 'WAIT_CONFIRM', 'Wait Confirm', 'Tenant wait-confirm status', 1, 2, 0, 0,
       'example', CURRENT_TIMESTAMP(3), 'example', CURRENT_TIMESTAMP(3)
FROM vin_dict
WHERE code = 'ORDER_STATUS';
