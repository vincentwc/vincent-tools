-- vincent-region reference data sample (GB administrative division codes)
-- For production, replace or extend with full national dataset.
SET NAMES utf8mb4;

INSERT INTO vin_region (code, name, level, parent_code) VALUES
('110000', '北京市', 1, '0'),
('440000', '广东省', 1, '0'),
('440100', '广州市', 2, '440000'),
('440300', '深圳市', 2, '440000'),
('440103', '荔湾区', 3, '440100'),
('440104', '越秀区', 3, '440100'),
('440303', '罗湖区', 3, '440300'),
('440304', '福田区', 3, '440300'),
('110101', '东城区', 3, '110000'),
('110102', '西城区', 3, '110000');
